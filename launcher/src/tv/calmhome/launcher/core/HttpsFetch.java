package tv.calmhome.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/**
 * Downloads a small text file (playlist, guide) over HTTPS only (pure Java, unit-tested with a fake opener).
 * Redirects are followed here, one hop at a time, so an HTTPS → HTTP downgrade is refused instead of being
 * followed. Certificates are checked by the platform's normal rules; nothing here relaxes them. Errors carry
 * a reason for the user and never include the URL, which may contain a token.
 */
public final class HttpsFetch {

    public static final int MAX_REDIRECTS = 5;
    public static final int CONNECT_TIMEOUT_MS = 10_000, READ_TIMEOUT_MS = 20_000;

    public enum Reason {
        HTTP_NOT_SUPPORTED("This beta only uses HTTPS links, and this one is HTTP."),
        HTTPS_DOWNGRADE("The link redirected to an unencrypted HTTP address, so ZeroPulse stopped."),
        TOO_MANY_REDIRECTS("The link redirected too many times."),
        INVALID_URL("That isn't a valid HTTPS address."),
        TLS("The server's security certificate wasn't accepted."),
        NETWORK("The server couldn't be reached. Check the TV's internet connection."),
        TIMEOUT("The server took too long to answer."),
        ACCESS_DENIED("The server refused access (it may need a login, or may not allow your region)."),
        NOT_FOUND("Nothing was found at that address (it may have moved or expired)."),
        HTTP_STATUS("The server answered with an error."),
        TOO_LARGE("The file is larger than ZeroPulse accepts.");

        public final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    public static final class FetchException extends IOException {
        private static final long serialVersionUID = 1L;
        public final Reason reason;
        public final int status;

        FetchException(Reason reason, int status) {
            super(reason.message + (status > 0 ? " (HTTP " + status + ")" : ""));
            this.reason = reason;
            this.status = status;
        }
    }

    /** One HTTP exchange; the real one is {@link #DEFAULT}, tests supply a fake. */
    public interface Opener {
        Response open(URL url) throws IOException;
    }

    public interface Response extends AutoCloseable {
        int status() throws IOException;
        String header(String name);
        InputStream body() throws IOException;
        @Override void close();
    }

    public static final Opener DEFAULT = url -> {
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "ZeroPulseTV/2.2");
        c.setRequestProperty("Accept-Encoding", "identity");
        return new Response() {
            @Override public int status() throws IOException { return c.getResponseCode(); }
            @Override public String header(String name) { return c.getHeaderField(name); }
            @Override public InputStream body() throws IOException { return c.getInputStream(); }
            @Override public void close() { c.disconnect(); }
        };
    };

    private HttpsFetch() { }

    /** Fetches {@code url} and decodes it as UTF-8. Throws {@link FetchException} with a user-facing reason. */
    public static String text(String url, int maxBytes, Opener opener) throws FetchException {
        return new String(bytes(url, maxBytes, opener), StandardCharsets.UTF_8);
    }

    /** Fetches {@code url} (HTTPS only, at most {@code maxBytes}). */
    public static byte[] bytes(String url, int maxBytes, Opener opener) throws FetchException {
        Response r = open(url, opener);
        try {
            return read(r.body(), maxBytes);
        } catch (FetchException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw map(e);
        } finally {
            r.close();
        }
    }

    /**
     * Opens {@code url} for streaming (large files such as guides). The stream fails with {@link Reason#TOO_LARGE}
     * after {@code maxBytes}; closing it closes the connection.
     */
    public static InputStream stream(String url, final long maxBytes, Opener opener) throws FetchException {
        final Response r = open(url, opener);
        final InputStream body;
        try {
            body = r.body();
        } catch (IOException | RuntimeException e) {
            r.close();
            throw map(e);
        }
        return new java.io.FilterInputStream(body) {
            long count;

            @Override public int read() throws IOException {
                int b = super.read();
                if (b >= 0 && ++count > maxBytes) throw new FetchException(Reason.TOO_LARGE, 0);
                return b;
            }

            @Override public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n > 0 && (count += n) > maxBytes) throw new FetchException(Reason.TOO_LARGE, 0);
                return n;
            }

            @Override public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    r.close();
                }
            }
        };
    }

    /** Follows HTTPS redirects and returns the open 200 response (caller closes it). */
    static Response open(String url, Opener opener) throws FetchException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URL u = httpsUrl(current, hop > 0);
            Response r = null;
            try {
                r = opener.open(u);
                int status = r.status();
                if (status >= 300 && status < 400 && r.header("Location") != null) {
                    current = resolve(u, r.header("Location"));
                    r.close();
                    continue;
                }
                if (status == 401 || status == 403 || status == 451) throw new FetchException(Reason.ACCESS_DENIED, status);
                if (status == 404 || status == 410) throw new FetchException(Reason.NOT_FOUND, status);
                if (status != 200) throw new FetchException(Reason.HTTP_STATUS, status);
                return r;
            } catch (FetchException e) {
                if (r != null) r.close();
                throw e;
            } catch (IOException | RuntimeException e) {
                if (r != null) r.close();
                throw map(e);
            }
        }
        throw new FetchException(Reason.TOO_MANY_REDIRECTS, 0);
    }

    static FetchException map(Exception e) {
        if (e instanceof FetchException) return (FetchException) e;
        if (e instanceof SSLException) return new FetchException(Reason.TLS, 0);
        if (e instanceof SocketTimeoutException) return new FetchException(Reason.TIMEOUT, 0);
        if (e instanceof UnknownHostException) return new FetchException(Reason.NETWORK, 0);
        return new FetchException(Reason.NETWORK, 0);
    }

    static URL httpsUrl(String s, boolean redirected) throws FetchException {
        String lower = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) throw new FetchException(redirected ? Reason.HTTPS_DOWNGRADE : Reason.HTTP_NOT_SUPPORTED, 0);
        if (!lower.startsWith("https://")) throw new FetchException(Reason.INVALID_URL, 0);
        try {
            URI uri = new URI(s.trim());
            if (uri.getHost() == null) throw new FetchException(Reason.INVALID_URL, 0);
            return uri.toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new FetchException(Reason.INVALID_URL, 0);
        }
    }

    static String resolve(URL base, String location) throws FetchException {
        try {
            return base.toURI().resolve(location.trim()).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new FetchException(Reason.INVALID_URL, 0);
        }
    }

    public static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        try (InputStream s = in) {
            int n;
            while ((n = s.read(buf)) > 0) {
                if (out.size() + n > max) throw new FetchException(Reason.TOO_LARGE, 0);
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }
}
