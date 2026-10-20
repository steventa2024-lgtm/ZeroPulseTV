package tv.calmhome.launcher.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * Minimal RFC 6455 WebSocket client, TLS 1.3 only, with the trust decision delegated to PinnedTrust.
 * There is deliberately no way to open a plaintext connection with this class.
 */
public final class WsClient implements Closeable {

    public interface Listener {
        void onText(String text);
        /** Always called exactly once. error is null for an orderly close. */
        void onClosed(int code, String reason, Exception error);
    }

    static final int MAX_MESSAGE = 1 << 20;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SSLSocket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Listener listener;
    private volatile boolean closed;
    private boolean reported;

    private WsClient(SSLSocket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.listener = listener;
    }

    /**
     * Connects, completes TLS (the trust manager accepts or refuses the PC here) and the WebSocket upgrade,
     * then starts a reader thread. readTimeoutMs bounds silence afterwards (the gateway ticks every 15 s).
     */
    public static WsClient connect(String host, int port, String path, TrustManager trust,
                                   int connectTimeoutMs, int readTimeoutMs, Listener listener) throws IOException {
        SSLContext ctx;
        try {
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{trust}, new SecureRandom());
        } catch (Exception e) {
            throw new IOException("TLS unavailable", e);
        }
        Socket raw = new Socket();
        SSLSocket s = null;
        try {
            raw.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            raw.setSoTimeout(connectTimeoutMs);
            s = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true);
            s.setEnabledProtocols(new String[]{"TLSv1.3"});
            s.setSoTimeout(connectTimeoutMs);
            s.startHandshake();
            WsClient c = new WsClient(s, listener);
            c.upgrade(host, port, path);
            s.setSoTimeout(readTimeoutMs);
            Thread t = new Thread(c::readLoop, "zp-jarvis-ws");
            t.setDaemon(true);
            t.start();
            return c;
        } catch (IOException | RuntimeException e) {
            try { if (s != null) s.close(); else raw.close(); } catch (IOException ignored) { /* closing */ }
            throw e;
        }
    }

    public synchronized void sendText(String text) throws IOException {
        if (closed) throw new IOException("closed");
        out.write(encodeFrame(0x1, text.getBytes(StandardCharsets.UTF_8), mask()));
        out.flush();
    }

    @Override
    public void close() {
        close(1000, "bye");
    }

    public void close(int code, String reason) {
        synchronized (this) {
            if (!closed) {
                closed = true;
                try {
                    byte[] r = reason.getBytes(StandardCharsets.UTF_8);
                    byte[] p = new byte[2 + r.length];
                    p[0] = (byte) (code >> 8);
                    p[1] = (byte) code;
                    System.arraycopy(r, 0, p, 2, r.length);
                    out.write(encodeFrame(0x8, p, mask()));
                    out.flush();
                } catch (IOException ignored) { /* the socket is going away anyway */ }
            }
        }
        try { socket.close(); } catch (IOException ignored) { /* already closed */ }
        report(code, reason, null);
    }

    // ------------------------------------------------------------------------------------------------

    private void upgrade(String host, int port, String path) throws IOException {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String head = readHead();
        String[] lines = head.split("\r\n");
        if (lines.length == 0 || !lines[0].matches("HTTP/1\\.1 101( .*)?"))
            throw new IOException("PC refused the connection (" + (lines.length > 0 ? lines[0] : "no reply") + ")");
        String accept = null;
        for (String l : lines) {
            int c = l.indexOf(':');
            if (c > 0 && l.substring(0, c).trim().toLowerCase(Locale.ROOT).equals("sec-websocket-accept"))
                accept = l.substring(c + 1).trim();
        }
        if (!acceptFor(key).equals(accept)) throw new IOException("bad WebSocket handshake");
    }

    private String readHead() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int last = 0;
        while (last != 0x0d0a0d0a) {   // up to the blank line ending the headers
            int c = in.read();
            if (c < 0) throw new EOFException("connection closed during handshake");
            b.write(c);
            if (b.size() > 8192) throw new IOException("handshake reply too large");
            last = (last << 8) | c;
        }
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    static String acceptFor(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder().encodeToString(sha1.digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void readLoop() {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        try {
            while (!closed) {
                int b0 = readByte(), b1 = readByte();
                boolean fin = (b0 & 0x80) != 0;
                int op = b0 & 0x0f;
                if ((b1 & 0x80) != 0) throw new IOException("server frames must not be masked");
                long len = b1 & 0x7f;
                if (len == 126) len = (readByte() << 8) | readByte();
                else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
                }
                if (len < 0 || len > MAX_MESSAGE) throw new IOException("frame too large");
                byte[] payload = readFully((int) len);
                if (op == 0x8) {
                    int code = payload.length >= 2 ? ((payload[0] & 0xff) << 8) | (payload[1] & 0xff) : 1005;
                    String reason = payload.length > 2 ? new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8) : "";
                    synchronized (this) {
                        if (!closed) {
                            byte[] echo = payload.length >= 2 ? new byte[]{payload[0], payload[1]} : new byte[]{3, (byte) 0xe8};
                            try { out.write(encodeFrame(0x8, echo, mask())); out.flush(); }
                            catch (IOException ignored) { /* best effort */ }
                            closed = true;
                        }
                    }
                    try { socket.close(); } catch (IOException ignored) { /* closing */ }
                    report(code, reason, null);
                    return;
                } else if (op == 0x9) {
                    synchronized (this) { out.write(encodeFrame(0xA, payload, mask())); out.flush(); }
                } else if (op == 0x1 || op == 0x0) {
                    if (message.size() + payload.length > MAX_MESSAGE) throw new IOException("message too large");
                    message.write(payload);
                    if (fin) {
                        String text = new String(message.toByteArray(), StandardCharsets.UTF_8);
                        message.reset();
                        listener.onText(text);
                    }
                }
                // binary (0x2) and pong (0xA) frames are ignored
            }
        } catch (SocketTimeoutException e) {
            closeQuietly();
            report(1006, "PC stopped responding", e);
        } catch (Exception e) {
            closeQuietly();
            report(1006, closed ? "closed" : "connection lost", closed ? null : e);
        }
    }

    private void closeQuietly() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { /* closing */ }
    }

    private synchronized void report(int code, String reason, Exception error) {
        if (reported) return;
        reported = true;
        listener.onClosed(code, reason, error);
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("connection closed");
        return b;
    }

    private byte[] readFully(int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new EOFException("connection closed");
            off += r;
        }
        return buf;
    }

    private static byte[] mask() {
        byte[] m = new byte[4];
        RANDOM.nextBytes(m);
        return m;
    }

    /** A single client frame (FIN set, masked). */
    static byte[] encodeFrame(int opcode, byte[] payload, byte[] mask) {
        ByteArrayOutputStream f = new ByteArrayOutputStream(payload.length + 14);
        f.write(0x80 | opcode);
        int n = payload.length;
        if (n < 126) f.write(0x80 | n);
        else if (n < 65536) {
            f.write(0x80 | 126);
            f.write(n >> 8);
            f.write(n & 0xff);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) f.write((int) (((long) n >> (8 * i)) & 0xff));
        }
        f.write(mask, 0, 4);
        for (int i = 0; i < n; i++) f.write(payload[i] ^ mask[i & 3]);
        return f.toByteArray();
    }
}
