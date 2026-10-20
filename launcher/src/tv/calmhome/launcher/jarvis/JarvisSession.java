package tv.calmhome.launcher.jarvis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Connection policy and state for the Assistant's JARVIS link. Runs everything on one background thread.
 *
 *  - Nothing happens until start() (the Assistant screen became visible); stop() closes the link and
 *    cancels retries. Home never calls this class.
 *  - A connection attempt is bounded (connectTimeoutMs); network failures retry after 3, 8 and 20 s,
 *    then stay Offline until retry().
 *  - A certificate change, a revoked pairing or a refused code never retries on its own.
 */
public final class JarvisSession {

    public enum State {
        NOT_CONFIGURED, CONNECTING, WAITING_APPROVAL, CONNECTED, OFFLINE, CERT_CHANGED, REVOKED, PAIRING_FAILED
    }

    /** Saved pairing. The token is a secret: CredentialStore keeps it Keystore-encrypted on the TV. */
    public static final class Pairing {
        public final String host;
        public final int port;
        public final String fingerprint;
        public final String token;
        public Pairing(String host, int port, String fingerprint, String token) {
            this.host = host;
            this.port = port;
            this.fingerprint = fingerprint;
            this.token = token;
        }
    }

    public interface CredentialStore {
        Pairing load();
        void save(Pairing p);
        /** Delete the token, the pin and the device key. */
        void clear();
        DeviceSigner signer() throws Exception;
    }

    /** Called on the session thread; the Android side posts to the UI thread. */
    public interface Listener {
        void onState(State state, String detail);
        /** state: delta | final | error | aborted | timeout | busy */
        void onReply(String state, String text);
        void onHistory(List<String[]> messages);
    }

    public static final int[] RETRY_SECONDS = {3, 8, 20};
    static final int APPROVAL_POLL_MS = 3000;
    static final long APPROVAL_LIMIT_MS = 10 * 60_000L;
    static final long BUSY_HINT_MS = 20_000;
    static final long REPLY_LIMIT_MS = 120_000;

    private final CredentialStore store;
    private final Listener listener;
    private final String displayName;
    private final String version;
    final int connectTimeoutMs;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "zp-jarvis");
        t.setDaemon(true);
        return t;
    });

    private GatewayLink link;
    private State state = State.NOT_CONFIGURED;
    private boolean active;
    private int attempt;
    private ScheduledFuture<?> retry, watchdog, busyHint;
    private long generation;
    private String runId;
    private long pairingSince;

    public JarvisSession(CredentialStore store, Listener listener, String displayName, String version,
                         int connectTimeoutMs) {
        this.store = store;
        this.listener = listener;
        this.displayName = displayName;
        this.version = version;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    // ------------------------------------------------------------------ public API (any thread)

    public void start() {
        exec.execute(() -> {
            active = true;
            attempt = 0;
            Pairing p = store.load();
            if (p == null) set(State.NOT_CONFIGURED, "");
            else if (state == State.CERT_CHANGED || state == State.REVOKED) set(state, "");
            else connect(p);
        });
    }

    public void stop() {
        exec.execute(() -> {
            active = false;
            generation++;
            cancelTimers();
            closeLink();
        });
    }

    public void retry() {
        exec.execute(() -> {
            attempt = 0;
            Pairing p = store.load();
            if (p == null) set(State.NOT_CONFIGURED, "");
            else connect(p);
        });
    }

    /** Dotted IPv4 literal with octets 0-255 (the TV link is LAN-only: no DNS lookups). */
    public static boolean isIpv4(String host) {
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        for (String part : host.split("\\.")) if (Integer.parseInt(part) > 255) return false;
        return true;
    }

    /** Pair with the PC using the code from `openpulse tv invite`. */
    public void pair(String host, int port, String typedCode) {
        exec.execute(() -> {
            if (!isIpv4(host)) {
                set(State.PAIRING_FAILED, "Enter the PC's IP address, for example 192.168.4.33.");
                return;
            }
            PairingCode code = PairingCode.parse(typedCode);
            if (code == null) {
                set(State.PAIRING_FAILED, "The code has 16 letters and numbers, as shown on the PC.");
                return;
            }
            closeLink();
            cancelTimers();
            pairingSince = System.currentTimeMillis();
            pairAttempt(host, port, code, ++generation);
        });
    }

    /** After the PC certificate was rotated: verify the new one with a fresh code, keep the pairing. */
    public void reverify(String typedCode) {
        exec.execute(() -> {
            Pairing p = store.load();
            PairingCode code = PairingCode.parse(typedCode);
            if (p == null) { set(State.NOT_CONFIGURED, ""); return; }
            if (code == null) { set(State.CERT_CHANGED, "The code has 16 letters and numbers, as shown on the PC."); return; }
            set(State.CONNECTING, "");
            PinnedTrust trust = PinnedTrust.pairing(code);
            try {
                GatewayLink l = GatewayLink.open(p.host, p.port, trust, store.signer(), "zeropulse-tv", displayName,
                        version, Json.map("deviceToken", p.token), connectTimeoutMs, events(++generation));
                store.save(new Pairing(p.host, p.port, trust.observed(), p.token));
                connected(l);
            } catch (GatewayLink.Refused r) {
                refused(r);
            } catch (Exception e) {
                if (GatewayLink.mismatchIn(e) != null) set(State.CERT_CHANGED, "That code is not for this PC's certificate.");
                else offline(e);
            }
        });
    }

    public void cancelPairing() {
        exec.execute(() -> {
            generation++;
            cancelTimers();
            Pairing p = store.load();
            set(p == null ? State.NOT_CONFIGURED : State.OFFLINE, "");
        });
    }

    public void send(String text) {
        exec.execute(() -> {
            if (state != State.CONNECTED || link == null) {
                listener.onReply("error", "JARVIS is not connected.");
                return;
            }
            try {
                Map<String, Object> r = link.request("tv.chat.send", Json.map("text", text), 8000);
                runId = Json.str(r, "runId");
                armWatchdog();
            } catch (GatewayLink.Refused r) {
                listener.onReply("error", r.getMessage() != null ? r.getMessage() : "JARVIS refused that.");
            } catch (IOException e) {
                listener.onReply("error", "The message did not reach the PC.");
            }
        });
    }

    public void abort() {
        exec.execute(() -> {
            cancelReplyTimers();
            if (link != null) try { link.request("tv.chat.abort", Json.map(), 5000); } catch (Exception ignored) { /* best effort */ }
        });
    }

    public void newConversation() {
        exec.execute(() -> {
            if (link == null) return;
            try {
                link.request("tv.chat.reset", Json.map(), 8000);
                listener.onHistory(new ArrayList<>());
            } catch (Exception e) {
                listener.onReply("error", "Could not start a new conversation.");
            }
        });
    }

    /** Close the link but stay paired. */
    public void disconnect() {
        exec.execute(() -> {
            generation++;
            cancelTimers();
            closeLink();
            set(store.load() == null ? State.NOT_CONFIGURED : State.OFFLINE, "Disconnected");
        });
    }

    /** Forget this PC: ask the gateway to delete the pairing if reachable, then delete everything locally. */
    public void forget() {
        exec.execute(() -> {
            generation++;
            cancelTimers();
            if (link != null && state == State.CONNECTED)
                try { link.request("tv.unpair", Json.map(), 4000); } catch (Exception ignored) { /* offline: local delete only */ }
            closeLink();
            store.clear();
            set(State.NOT_CONFIGURED, "");
        });
    }

    public void shutdown() {
        stop();
        exec.shutdown();
    }

    // ------------------------------------------------------------------ session thread

    private void connect(Pairing p) {
        long gen = ++generation;
        cancelTimers();
        closeLink();
        set(State.CONNECTING, "");
        try {
            GatewayLink l = GatewayLink.open(p.host, p.port, PinnedTrust.pinned(p.fingerprint), store.signer(),
                    "zeropulse-tv", displayName, version, Json.map("deviceToken", p.token), connectTimeoutMs, events(gen));
            if (gen != generation || !active) { l.close(); return; }
            attempt = 0;
            connected(l);
        } catch (GatewayLink.Refused r) {
            refused(r);
        } catch (Exception e) {
            if (GatewayLink.mismatchIn(e) != null) {
                set(State.CERT_CHANGED, "The PC's certificate changed. Enter a new code from `openpulse tv invite`.");
                return;
            }
            offline(e);
        }
    }

    private void pairAttempt(String host, int port, PairingCode code, long gen) {
        if (gen != generation) return;
        if (System.currentTimeMillis() - pairingSince > APPROVAL_LIMIT_MS) {
            set(State.PAIRING_FAILED, "No approval arrived. Create a new code on the PC.");
            return;
        }
        if (state != State.WAITING_APPROVAL) set(State.CONNECTING, "");
        PinnedTrust trust = PinnedTrust.pairing(code);
        try {
            GatewayLink l = GatewayLink.open(host, port, trust, store.signer(), "zeropulse-tv", displayName, version,
                    Json.map("pairingCode", code.secret), connectTimeoutMs, events(gen));
            String token = l.hello().deviceToken;
            if (token == null) {
                l.close();
                set(State.PAIRING_FAILED, "The PC did not issue a pairing. Try a new code.");
                return;
            }
            store.save(new Pairing(host, port, trust.observed(), token));
            attempt = 0;
            connected(l);
        } catch (GatewayLink.Refused r) {
            if ("PAIRING_REQUIRED".equals(r.code)) {
                set(State.WAITING_APPROVAL, tvCode());
                retry = exec.schedule(() -> pairAttempt(host, port, code, gen), APPROVAL_POLL_MS, TimeUnit.MILLISECONDS);
            } else {
                set(State.PAIRING_FAILED, r.getMessage() != null ? r.getMessage() : "The PC refused the code.");
            }
        } catch (Exception e) {
            if (GatewayLink.mismatchIn(e) != null)
                set(State.PAIRING_FAILED, "This PC's certificate does not match the code. Check the address and code.");
            else if (state == State.WAITING_APPROVAL)
                retry = exec.schedule(() -> pairAttempt(host, port, code, gen), APPROVAL_POLL_MS, TimeUnit.MILLISECONDS);
            else
                set(State.PAIRING_FAILED, "Could not reach the PC at " + host + ":" + port + ".");
        }
    }

    private String tvCode() {
        try {
            return PairingCode.tvCode(DeviceSigner.deviceId(store.signer().publicKeySpki()));
        } catch (Exception e) {
            return "";
        }
    }

    private void connected(GatewayLink l) {
        link = l;
        String name = l.hello().assistantName;
        set(State.CONNECTED, name != null ? name : "JARVIS");
        try {
            Map<String, Object> h = l.request("tv.chat.history", Json.map("limit", 20), 8000);
            List<String[]> out = new ArrayList<>();
            for (Object o : Json.list(h, "messages")) {
                Map<String, Object> m = Json.obj(o);
                if (m != null) out.add(new String[]{Json.str(m, "role"), Json.str(m, "text")});
            }
            listener.onHistory(out);
            if (Json.bool(h, "running")) listener.onReply("busy", "");
        } catch (Exception ignored) {
            // history is a convenience; the conversation still works without it
        }
    }

    private void refused(GatewayLink.Refused r) {
        closeLink();
        if ("UNAUTHORIZED".equals(r.code) && "revoked".equals(r.reason())) {
            store.clear();
            set(State.REVOKED, "This TV was removed on the PC. Pair again to reconnect.");
        } else {
            set(State.OFFLINE, r.getMessage() != null ? r.getMessage() : "The PC refused the connection.");
        }
    }

    private void offline(Exception e) {
        closeLink();
        if (!active) { set(State.OFFLINE, ""); return; }
        if (attempt < RETRY_SECONDS.length) {
            int wait = RETRY_SECONDS[attempt++];
            set(State.OFFLINE, "Can't reach the PC. Retrying in " + wait + " s…");
            long gen = generation;
            retry = exec.schedule(() -> {
                if (gen != generation || !active) return;
                Pairing p = store.load();
                if (p != null) connect(p);
            }, wait, TimeUnit.SECONDS);
        } else {
            set(State.OFFLINE, "Can't reach the PC. Is it on, with OpenPulse running?");
        }
    }

    private GatewayLink.Events events(long gen) {
        return new GatewayLink.Events() {
            @Override
            public void onEvent(String event, Map<String, Object> payload) {
                exec.execute(() -> {
                    if (gen != generation) return;
                    handleEvent(event, payload);
                });
            }

            @Override
            public void onClosed(int code, String reason, Exception error) {
                exec.execute(() -> {
                    if (gen != generation) return;
                    link = null;
                    cancelReplyTimers();
                    if (code == 1008 && reason != null && reason.contains("revoked")) {
                        store.clear();
                        set(State.REVOKED, "This TV was removed on the PC. Pair again to reconnect.");
                    } else if (active) {
                        Pairing p = store.load();
                        if (code == 1012 && p != null) connect(p);     // certificate rotated: find out now
                        else offline(error != null ? error : new IOException(reason));
                    } else {
                        set(State.OFFLINE, "");
                    }
                });
            }
        };
    }

    private void handleEvent(String event, Map<String, Object> p) {
        if ("chat".equals(event)) {
            String s = Json.str(p, "state");
            String text = Json.str(p, "text");
            if ("delta".equals(s)) {
                cancelBusyHint();
                listener.onReply("delta", text != null ? text : "");
            } else if ("final".equals(s)) {
                cancelReplyTimers();
                listener.onReply("final", text != null ? text : "");
            } else if ("error".equals(s)) {
                cancelReplyTimers();
                String err = Json.str(p, "error");
                listener.onReply("error", err != null ? err : "JARVIS ran into a problem.");
            } else if ("aborted".equals(s)) {
                cancelReplyTimers();
                listener.onReply("aborted", "");
            }
        } else if ("agent".equals(event)) {
            String phase = Json.str(p, "phase");
            if ("start".equals(phase)) { cancelBusyHint(); listener.onReply("busy", ""); }
        } else if ("shutdown".equals(event)) {
            set(State.OFFLINE, "OpenPulse on the PC is shutting down.");
        }
    }

    private void armWatchdog() {
        cancelReplyTimers();
        busyHint = exec.schedule(() -> listener.onReply("busy", "JARVIS is busy; still waiting…"), BUSY_HINT_MS, TimeUnit.MILLISECONDS);
        watchdog = exec.schedule(() -> {
            if (link != null) try { link.request("tv.chat.abort", Json.map(), 5000); } catch (Exception ignored) { /* best effort */ }
            listener.onReply("timeout", "JARVIS did not answer in time.");
        }, REPLY_LIMIT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelBusyHint() {
        if (busyHint != null) busyHint.cancel(false);
        busyHint = null;
    }

    private void cancelReplyTimers() {
        cancelBusyHint();
        if (watchdog != null) watchdog.cancel(false);
        watchdog = null;
    }

    private void cancelTimers() {
        if (retry != null) retry.cancel(false);
        retry = null;
        cancelReplyTimers();
    }

    private void closeLink() {
        GatewayLink l = link;
        link = null;
        if (l != null) l.close();
    }

    private void set(State s, String detail) {
        state = s;
        listener.onState(s, detail);
    }

    /** For tests: run a task on the session thread and wait for it. */
    void sync() throws Exception {
        exec.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }
}
