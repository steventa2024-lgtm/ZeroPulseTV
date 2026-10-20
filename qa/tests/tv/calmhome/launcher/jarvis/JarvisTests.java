package tv.calmhome.launcher.jarvis;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JVM unit tests for the TV's JARVIS link code (launcher/src/.../jarvis). Run by qa/run-tests.ps1.
 * Usage: java tv.calmhome.launcher.jarvis.JarvisTests <launcher/src directory>
 */
public class JarvisTests {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    static <T> void eq(String name, T expected, T actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(name + (ok ? "" : "  expected=" + expected + " actual=" + actual), ok);
    }

    public static void main(String[] args) throws Exception {
        pairingCode();
        signatures();
        json();
        suggestions();
        websocket();
        plaintextRefused();
        noSecretsLogged(args.length > 0 ? Paths.get(args[0]) : null);
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void pairingCode() {
        // Vectors produced by the gateway's pairing-code.ts (OpenPulse), so both sides agree.
        eq("code: check vector 1 matches the gateway", "NS7PNXBE", PairingCode.checkFor("ABCDEFGH", repeat("ab", 32)));
        eq("code: check vector 2 matches the gateway", "RQBBYE6Z", PairingCode.checkFor("7K4Q9XWM", repeat("0123456789abcdef", 4)));
        PairingCode c = PairingCode.parse("7k4q-9xwm rqbb-ye6z");
        check("code: parses lowercase with separators", c != null && c.secret.equals("7K4Q9XWM") && c.check.equals("RQBBYE6Z"));
        check("code: matches the certificate it was made for", c != null && c.matches(repeat("0123456789abcdef", 4)));
        check("code: refuses any other certificate", c != null && !c.matches(repeat("0123456789abcdee", 4)));
        eq("code: look-alikes map O→0, I/L→1", "0111", PairingCode.normalise("oIlL").substring(0, 4));
        check("code: too short is refused", PairingCode.parse("7K4Q-9XWM-RQBB") == null);
        check("code: letters outside the alphabet are refused", PairingCode.parse("7K4Q-9XWM-RQBB-YE6U") == null);
        eq("code: TV code is the first 8 hex of the device id", "1A2B-3C4D", PairingCode.tvCode("1a2b3c4d" + repeat("0", 24)));
        check("address: IPv4 accepted", JarvisSession.isIpv4("192.168.4.33"));
        check("address: names, codes and bad octets refused", !JarvisSession.isIpv4("jarvis.local")
                && !JarvisSession.isIpv4("192.168.4.337K4Q-9XWM") && !JarvisSession.isIpv4("192.168.4.256")
                && !JarvisSession.isIpv4(""));
        boolean threw = false;
        try { PinnedTrust.pinned("not-a-fingerprint"); } catch (IllegalArgumentException e) { threw = true; }
        check("pin: malformed pin is refused", threw);
    }

    static void signatures() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        boolean all = true;
        for (int i = 0; i < 200; i++) {   // covers r/s with leading zero bytes and 33-byte DER integers
            KeyPair kp = g.generateKeyPair();
            byte[] msg = ("openpulse-connect|v1|n|" + i).getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(msg);
            byte[] p1363 = DeviceSigner.derToP1363(s.sign());
            Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
            v.initVerify(kp.getPublic());
            v.update(msg);
            all &= p1363.length == 64 && v.verify(p1363);
        }
        check("signature: DER → P1363 verifies for 200 random keys", all);
        KeyPair kp = g.generateKeyPair();
        eq("device id: 32 hex characters", 32, DeviceSigner.deviceId(kp.getPublic().getEncoded()).length());
    }

    static void json() {
        Map<String, Object> m = Json.obj(Json.parse("{\"a\":1,\"b\":[true,null,\"x\\n\\u00e9\"],\"c\":{\"d\":2.5}}"));
        eq("json: integer", 1L, m.get("a"));
        eq("json: nested string with escapes", "x\né", ((List<?>) m.get("b")).get(2));
        eq("json: double", 2.5, Json.obj(m, "c").get("d"));
        String w = Json.write(Json.map("s", "q\"u\\o ", "n", 3, "l", Arrays.asList(1, "2")));
        eq("json: round trip", w, Json.write(Json.parse(w)));
        boolean threw = false;
        try { Json.parse("{\"a\":1} trailing"); } catch (IllegalArgumentException e) { threw = true; }
        check("json: trailing data refused", threw);
        threw = false;
        try { Json.parse(repeat("[", 100) + repeat("]", 100)); } catch (IllegalArgumentException e) { threw = true; }
        check("json: absurd nesting refused", threw);
    }

    static void suggestions() {
        TvSuggestion s = TvSuggestion.find("Sure.\n[[tv:open:YouTube]]");
        check("suggestion: open", s != null && s.kind == TvSuggestion.Kind.OPEN && s.target.equals("YouTube"));
        eq("suggestion: becomes a TV command", "open YouTube", s == null ? null : s.asCommand());
        TvSuggestion i = TvSuggestion.find("[[tv:input:HDMI 2]]");
        eq("suggestion: input", "switch to HDMI 2", i == null ? null : i.asCommand());
        TvSuggestion h = TvSuggestion.find("[[TV:HOME]]");
        eq("suggestion: home", "home", h == null ? null : h.asCommand());
        check("suggestion: unknown verbs ignored", TvSuggestion.find("[[tv:exec:rm -rf]] [[tv:open:]]") == null);
        eq("suggestion: tag and Markdown removed for display", "Hello world\n• one", TvSuggestion.clean("## Hello **world**\n- one [[tv:home]]"));
    }

    static void websocket() {
        // RFC 6455 section 1.3 example.
        eq("ws: accept key", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WsClient.acceptFor("dGhlIHNhbXBsZSBub25jZQ=="));
        byte[] mask = {1, 2, 3, 4};
        byte[] f = WsClient.encodeFrame(1, "Hi".getBytes(StandardCharsets.UTF_8), mask);
        check("ws: small frame is FIN+text, masked", f[0] == (byte) 0x81 && f[1] == (byte) 0x82 && f.length == 8
                && (f[6] ^ 1) == 'H' && (f[7] ^ 2) == 'i');
        byte[] big = WsClient.encodeFrame(1, new byte[70000], mask);
        check("ws: 64-bit length for large frames", big[1] == (byte) 0xFF && big.length == 70000 + 14);
        byte[] mid = WsClient.encodeFrame(1, new byte[300], mask);
        check("ws: 16-bit length", mid[1] == (byte) 0xFE && ((mid[2] & 0xff) << 8 | (mid[3] & 0xff)) == 300);
    }

    /** A plaintext WebSocket server on the port must not get a connection: TLS fails, nothing is sent. */
    static void plaintextRefused() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            final StringBuilder received = new StringBuilder();
            Thread t = new Thread(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(3000);
                    byte[] buf = new byte[4096];
                    int n = s.getInputStream().read(buf);
                    if (n > 0) received.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                    s.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                } catch (IOException ignored) { /* client gave up */ }
            });
            t.start();
            boolean refused = false;
            try {
                WsClient.connect("127.0.0.1", server.getLocalPort(), "/tv", PinnedTrust.pinned(repeat("ab", 32)),
                        3000, 3000, new WsClient.Listener() {
                            public void onText(String text) { }
                            public void onClosed(int code, String reason, Exception error) { }
                        });
            } catch (IOException e) {
                refused = true;
            }
            t.join(5000);
            check("downgrade: a plaintext server is refused", refused);
            check("downgrade: no HTTP request was sent in clear text", !received.toString().contains("GET /tv"));
        }
    }

    /** The JARVIS code must never print or log: no secrets can reach logcat this way. */
    static void noSecretsLogged(Path src) throws IOException {
        if (src == null) return;
        List<Path> files;
        try (Stream<Path> s = Files.walk(src)) {
            files = s.filter(p -> p.toString().endsWith(".java")
                    && (p.toString().contains("jarvis") || p.getFileName().toString().equals("KeystoreCredentials.java")
                    || p.getFileName().toString().equals("AssistantActivity.java")))
                    .collect(Collectors.toList());
        }
        List<String> offenders = files.stream().filter(p -> {
            try {
                String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                return text.contains("android.util.Log") || text.contains("Log.") && text.contains("import android.util")
                        || text.contains("System.out") || text.contains("System.err") || text.contains("printStackTrace");
            } catch (IOException e) {
                return true;
            }
        }).map(p -> p.getFileName().toString()).collect(Collectors.toList());
        check("no logging or printing in the JARVIS code (" + files.size() + " files)" + (offenders.isEmpty() ? "" : " " + offenders),
                offenders.isEmpty() && files.size() >= 8);
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
