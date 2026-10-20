package tv.calmhome.launcher.jarvis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The TV's real JARVIS client code (GatewayLink, JarvisSession, PinnedTrust, WsClient) against the real
 * OpenPulse TV link (packages/gateway/test/tvlink-harness.ts: actual runtime, agent service, device store
 * and TLS listener with a scripted model) on this PC. Usage:
 *   java tv.calmhome.launcher.jarvis.JarvisIntegrationTests <OpenPulse packages/gateway directory>
 */
public class JarvisIntegrationTests {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    // ---- harness process -----------------------------------------------------------------------

    static Process harness;
    static Writer harnessIn;
    static BufferedReader harnessOut;

    static Map<String, Object> cmd(String c) throws IOException {
        harnessIn.write(c + "\n");
        harnessIn.flush();
        return Json.obj(Json.parse(harnessOut.readLine()));
    }

    // ---- TV side fakes: software key, in-memory store, recorded events ---------------------------

    static final class MemoryStore implements JarvisSession.CredentialStore {
        volatile JarvisSession.Pairing pairing;
        volatile KeyPair key;
        int clears;
        public JarvisSession.Pairing load() { return pairing; }
        public void save(JarvisSession.Pairing p) { pairing = p; }
        public void clear() { pairing = null; key = null; clears++; }
        public DeviceSigner signer() throws Exception {
            if (key == null) {
                KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec("secp256r1"));
                key = g.generateKeyPair();
            }
            final KeyPair k = key;
            return new DeviceSigner() {
                public byte[] publicKeySpki() { return k.getPublic().getEncoded(); }
                public byte[] signDer(byte[] data) throws java.security.GeneralSecurityException {
                    Signature s = Signature.getInstance("SHA256withECDSA");
                    s.initSign(k.getPrivate());
                    s.update(data);
                    return s.sign();
                }
            };
        }
    }

    static final class Recorder implements JarvisSession.Listener {
        final BlockingQueue<String> states = new LinkedBlockingQueue<>();
        final List<String> replies = new CopyOnWriteArrayList<>();
        volatile JarvisSession.State last;
        volatile String lastDetail = "";
        public void onState(JarvisSession.State s, String detail) {
            last = s;
            lastDetail = detail;
            states.add(s + "|" + detail);
        }
        public void onReply(String state, String text) { replies.add(state + "|" + text); }
        public void onHistory(List<String[]> m) { }

        boolean await(JarvisSession.State want, long ms) throws InterruptedException {
            long end = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < end) {
                String s = states.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (s != null && s.startsWith(want + "|")) return true;
            }
            return false;
        }

        boolean awaitReply(Predicate<String> p, long ms) throws InterruptedException {
            long end = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < end) {
                for (String r : replies) if (p.test(r)) return true;
                Thread.sleep(50);
            }
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        File gatewayDir = new File(args[0]);
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npx", "tsx", "test/tvlink-harness.ts").directory(gatewayDir);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        harness = pb.start();
        harnessIn = new OutputStreamWriter(harness.getOutputStream(), StandardCharsets.UTF_8);
        harnessOut = new BufferedReader(new InputStreamReader(harness.getInputStream(), StandardCharsets.UTF_8));
        Map<String, Object> ready = Json.obj(Json.parse(harnessOut.readLine()));
        int port = (int) Json.num(ready, "port", 0);
        String fingerprint = Json.str(ready, "fingerprint");
        String code = Json.str(ready, "code");
        try {
            run(port, fingerprint, code);
        } finally {
            try { cmd("exit"); } catch (Exception ignored) { /* already gone */ }
            harness.waitFor(10, TimeUnit.SECONDS);
            harness.destroyForcibly();
        }
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void run(int port, String fingerprint, String code) throws Exception {
        MemoryStore store = new MemoryStore();
        Recorder rec = new Recorder();
        JarvisSession session = new JarvisSession(store, rec, "Test TV", "2.1", 4000);
        session.start();
        check("start with no pairing: Not configured, no connection", rec.await(JarvisSession.State.NOT_CONFIGURED, 3000));

        // 1. A code whose check half does not match this PC's certificate: refused inside TLS, nothing sent.
        String bad = code.substring(0, code.length() - 1) + (code.endsWith("0") ? "1" : "0");
        session.pair("127.0.0.1", port, bad);
        check("pairing: certificate not matching the code is refused", rec.await(JarvisSession.State.PAIRING_FAILED, 8000)
                && rec.lastDetail.contains("certificate"));
        check("pairing: nothing reached the gateway (no pending request)", Json.num(cmd("pending"), "pending", -1) == 0);

        // 2. The real code: certificate verified, then explicit approval on the PC.
        session.pair("127.0.0.1", port, code);
        check("pairing: waits for approval on the PC", rec.await(JarvisSession.State.WAITING_APPROVAL, 8000));
        String tvCode = PairingCode.tvCode(DeviceSigner.deviceId(store.signer().publicKeySpki()));
        check("pairing: TV shows the TV code the PC shows (" + tvCode + ")", rec.lastDetail.equals(tvCode));
        check("pairing: not connected before approval", store.pairing == null);
        Map<String, Object> approved = cmd("approve");
        check("pairing: PC sees the same TV code", tvCode.replace("-", "").equals(Json.str(approved, "tvCode")));
        check("pairing: connects after approval", rec.await(JarvisSession.State.CONNECTED, 10000));
        check("pairing: pin saved = gateway certificate SHA-256", store.pairing != null && fingerprint.equals(store.pairing.fingerprint));
        check("pairing: device token issued and stored", store.pairing != null && store.pairing.token != null && store.pairing.token.length() > 20);

        // 3. Chat with the real agent service.
        session.send("Status report");
        check("chat: thinking state from real activity", rec.awaitReply(r -> r.startsWith("busy|"), 10000));
        check("chat: real reply arrives", rec.awaitReply(r -> r.startsWith("final|Good evening. All systems nominal."), 15000));
        String finalReply = "";
        for (String r : rec.replies) if (r.startsWith("final|")) finalReply = r.substring(6);
        TvSuggestion sug = TvSuggestion.find(finalReply);
        check("chat: TV suggestion parsed as a button, not run", sug != null && sug.asCommand().equals("open YouTube"));

        // 4. The TV credential cannot call anything else.
        GatewayLink raw = GatewayLink.open("127.0.0.1", port, PinnedTrust.pinned(store.pairing.fingerprint), store.signer(),
                "zeropulse-tv", "Test TV", "2.1", Json.map("deviceToken", store.pairing.token), 4000, null);
        String[] forbidden = {"config.get", "chat.send", "exec.approval.resolve", "device.pair.list", "status", "logs.tail"};
        boolean allForbidden = true;
        for (String m : forbidden) {
            try { raw.request(m, Json.map(), 4000); allForbidden = false; }
            catch (GatewayLink.Refused r) { allForbidden &= "FORBIDDEN".equals(r.code); }
        }
        check("scope: owner methods are refused for the TV", allForbidden);
        boolean slash = false;
        try { raw.request("tv.chat.send", Json.map("text", "/approve x allow-always"), 4000); }
        catch (GatewayLink.Refused r) { slash = "FORBIDDEN".equals(r.code); }
        check("scope: slash commands refused", slash);
        Map<String, Object> status = raw.request("tv.status", Json.map(), 4000);
        check("status: verified assistant status", "JARVIS".equals(Json.str(status, "assistantName")) && status.containsKey("busy"));
        raw.close();

        // 5. Gateway stopped → Offline with bounded retries; back up → reconnects by itself.
        cmd("stop");
        check("offline: gateway shutdown → Offline", rec.await(JarvisSession.State.OFFLINE, 8000));
        cmd("start");
        check("reconnect: comes back after the gateway restarts", rec.await(JarvisSession.State.CONNECTED, 30000));

        // 6. Certificate rotation → refused by the pin → re-verified with a new code, pairing kept.
        String newFp = Json.str(cmd("rotate"), "fingerprint");
        check("pin: rotated certificate is refused (PC identity changed)", rec.await(JarvisSession.State.CERT_CHANGED, 10000));
        String newCode = Json.str(cmd("invite"), "code");
        session.reverify(newCode);
        check("pin: re-verified with a new code from the PC", rec.await(JarvisSession.State.CONNECTED, 10000)
                && newFp.equals(store.pairing.fingerprint));

        // 7. Revocation on the PC → Revoked, local credentials deleted.
        cmd("revoke");
        check("revoke: PC removal → Revoked on the TV", rec.await(JarvisSession.State.REVOKED, 8000));
        check("revoke: local token, pin and key deleted", store.pairing == null && store.key == null);

        // 8. Pair again, then Forget on the TV removes the device on the PC too.
        session.pair("127.0.0.1", port, Json.str(cmd("invite"), "code"));
        rec.await(JarvisSession.State.WAITING_APPROVAL, 8000);
        cmd("approve");
        check("forget: re-paired", rec.await(JarvisSession.State.CONNECTED, 10000));
        session.forget();
        check("forget: TV back to Not configured", rec.await(JarvisSession.State.NOT_CONFIGURED, 8000));
        check("forget: device removed on the PC", Json.num(cmd("devices"), "devices", -1) == 0);
        session.shutdown();

        // 9. Bounded timeout: a PC that accepts TCP but never answers.
        try (ServerSocket silent = new ServerSocket(0)) {
            List<Socket> held = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                try { while (true) held.add(silent.accept()); } catch (IOException ignored) { /* closed */ }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            MemoryStore s2 = new MemoryStore();
            s2.pairing = new JarvisSession.Pairing("127.0.0.1", silent.getLocalPort(), fingerprint, "unused-token-value");
            Recorder r2 = new Recorder();
            JarvisSession quiet = new JarvisSession(s2, r2, "Test TV", "2.1", 2000);
            long t0 = System.currentTimeMillis();
            quiet.start();
            boolean off = r2.await(JarvisSession.State.OFFLINE, 8000);
            long took = System.currentTimeMillis() - t0;
            check("timeout: silent PC → Offline in " + took + " ms (limit 2 s + margin)", off && took < 4000);
            r2.states.clear();
            quiet.stop();
            Thread.sleep(4000);    // first retry would be at 3 s
            check("timeout: no retries after the Assistant closes", r2.states.stream().noneMatch(x -> x.startsWith("CONNECTING")));
            quiet.shutdown();
        }
    }
}
