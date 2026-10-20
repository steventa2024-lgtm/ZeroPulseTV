package tv.calmhome.launcher.jarvis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The TV side of the OpenPulse TV link (packages/gateway/src/tvlink/server.ts in OpenPulse): connect over
 * pinned TLS, prove the device key, then make requests and receive events. Blocking calls; use from a
 * background thread.
 */
public final class GatewayLink implements WsClient.Listener {

    public static final int PROTOCOL = 3;
    public static final String PATH = "/tv";

    public interface Events {
        void onEvent(String event, Map<String, Object> payload);
        void onClosed(int code, String reason, Exception error);
    }

    /** The gateway answered with an error (UNAUTHORIZED, PAIRING_REQUIRED, FORBIDDEN, …). */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;
        public final String code;
        public final transient Map<String, Object> details;
        Refused(String code, String message, Map<String, Object> details) {
            super(message);
            this.code = code;
            this.details = details;
        }
        public String reason() {
            return Json.str(details, "reason");
        }
    }

    public static final class Hello {
        public final String deviceToken;
        public final String assistantName;
        public final List<Object> methods;
        Hello(Map<String, Object> p) {
            this.deviceToken = Json.str(Json.obj(p, "auth"), "deviceToken");
            this.assistantName = Json.str(Json.obj(p, "server"), "assistantName");
            this.methods = Json.list(p, "methods");
        }
    }

    private final AtomicInteger ids = new AtomicInteger();
    private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final CompletableFuture<String> challenge = new CompletableFuture<>();
    private volatile Events events;
    private volatile boolean helloDone;
    private WsClient ws;
    private Hello hello;

    private GatewayLink() { }

    public Hello hello() {
        return hello;
    }

    /**
     * Opens the link. auth is {"deviceToken": …} or {"pairingCode": secret}. Throws Refused for a gateway
     * decision (e.g. PAIRING_REQUIRED), IOException for network or TLS failures; a certificate refused by
     * the trust manager shows up as an IOException whose cause chain contains PinnedTrust.Mismatch.
     */
    public static GatewayLink open(String host, int port, PinnedTrust trust, DeviceSigner signer,
                                   String clientId, String displayName, String version,
                                   Map<String, Object> auth, int timeoutMs, Events events)
            throws IOException, Refused {
        GatewayLink link = new GatewayLink();
        link.ws = WsClient.connect(host, port, PATH, trust, timeoutMs, 45_000, link);
        try {
            String nonce = link.challenge.get(timeoutMs, TimeUnit.MILLISECONDS);
            long signedAt = System.currentTimeMillis();
            String payload = "openpulse-connect|v1|" + nonce + "|" + signedAt + "|" + clientId + "|tv";
            byte[] spki = signer.publicKeySpki();
            String signature;
            try {
                signature = Base64.getEncoder().encodeToString(
                        DeviceSigner.derToP1363(signer.signDer(payload.getBytes(StandardCharsets.UTF_8))));
            } catch (GeneralSecurityException e) {
                throw new IOException("device key unavailable", e);
            }
            Map<String, Object> params = Json.map(
                    "minProtocol", PROTOCOL, "maxProtocol", PROTOCOL,
                    "client", Json.map("id", clientId, "displayName", displayName, "version", version,
                            "platform", "android-tv"),
                    "device", Json.map("id", DeviceSigner.deviceId(spki),
                            "publicKey", Base64.getEncoder().encodeToString(spki),
                            "signature", signature, "signedAt", signedAt, "nonce", nonce),
                    "auth", auth);
            link.hello = new Hello(link.call("connect", params, timeoutMs));
            link.events = events;
            link.helloDone = true;
            return link;
        } catch (InterruptedException e) {
            link.ws.close();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (TimeoutException e) {
            link.ws.close(1000, "timeout");
            throw new IOException("PC did not answer in time");
        } catch (ExecutionException e) {
            link.ws.close();
            throw new IOException("connection lost", e.getCause());
        } catch (IOException | Refused e) {
            link.ws.close();
            throw e;
        }
    }

    public Map<String, Object> request(String method, Map<String, Object> params, int timeoutMs)
            throws IOException, Refused {
        try {
            return call(method, params, timeoutMs);
        } catch (TimeoutException e) {
            throw new IOException("PC did not answer in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    public void close() {
        ws.close(1000, "assistant closed");
    }

    private Map<String, Object> call(String method, Map<String, Object> params, int timeoutMs)
            throws IOException, Refused, TimeoutException, InterruptedException {
        String id = "t" + ids.incrementAndGet();
        CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
        pending.put(id, f);
        try {
            ws.sendText(Json.write(Json.map("type", "req", "id", id, "method", method, "params", params)));
            Map<String, Object> res = f.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (Json.bool(res, "ok")) {
                Map<String, Object> payload = Json.obj(res, "payload");
                return payload != null ? payload : Json.map();
            }
            Map<String, Object> err = Json.obj(res, "error");
            throw new Refused(Json.str(err, "code"), Json.str(err, "message"), Json.obj(err, "details"));
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException ? (IOException) c : new IOException("connection lost", c);
        } finally {
            pending.remove(id);
        }
    }

    @Override
    public void onText(String text) {
        Map<String, Object> frame;
        try {
            frame = Json.obj(Json.parse(text));
        } catch (RuntimeException e) {
            return;
        }
        if (frame == null) return;
        String type = Json.str(frame, "type");
        if ("res".equals(type)) {
            CompletableFuture<Map<String, Object>> f = pending.get(Json.str(frame, "id"));
            if (f != null) f.complete(frame);
        } else if ("event".equals(type)) {
            String event = Json.str(frame, "event");
            Map<String, Object> payload = Json.obj(frame, "payload");
            if ("connect.challenge".equals(event)) {
                String nonce = Json.str(payload, "nonce");
                if (nonce != null) challenge.complete(nonce);
            } else if (helloDone && events != null && event != null) {
                events.onEvent(event, payload != null ? payload : Json.map());
            }
        }
    }

    @Override
    public void onClosed(int code, String reason, Exception error) {
        IOException lost = new IOException("connection closed (" + code + ")", error);
        challenge.completeExceptionally(lost);
        for (CompletableFuture<Map<String, Object>> f : pending.values()) f.completeExceptionally(lost);
        Events e = events;
        if (helloDone && e != null) e.onClosed(code, reason, error);
    }

    /** True if the failure was the trust manager refusing the PC's certificate. */
    public static PinnedTrust.Mismatch mismatchIn(Throwable t) {
        for (int i = 0; t != null && i < 8; i++, t = t.getCause())
            if (t instanceof PinnedTrust.Mismatch) return (PinnedTrust.Mismatch) t;
        return null;
    }
}
