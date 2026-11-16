package tv.calmhome.launcher.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * ZeroPulse Web's address bar and network policy (pure Java, unit-tested).
 *
 * Policy for this beta: HTTPS only, and no navigation to this TV or the local network by literal address or
 * local name. The network check sees only the URL text: it cannot see what a public name resolves to (DNS
 * rebinding) or requests a page makes outside WebView's request interception, so it reduces, but does not
 * remove, a web page's ability to reach local devices.
 */
public final class WebAddress {

    /** Search engines that need no account or API key. */
    public enum Engine {
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/do/search?query="),
        BRAVE("Brave Search", "https://search.brave.com/search?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        GOOGLE("Google", "https://www.google.com/search?q=");

        public final String label, prefix;

        Engine(String label, String prefix) {
            this.label = label;
            this.prefix = prefix;
        }
    }

    /** Outcome of a typed entry or of a URL a page tries to open. */
    public static final class Result {
        public enum Kind { NAVIGATE, SEARCH, BLOCKED_HTTP, BLOCKED_LOCAL, BLOCKED_SCHEME, INVALID }

        public final Kind kind;
        /** URL to load for NAVIGATE/SEARCH; null otherwise. */
        public final String url;

        Result(Kind kind, String url) {
            this.kind = kind;
            this.url = url;
        }

        public boolean loads() {
            return kind == Kind.NAVIGATE || kind == Kind.SEARCH;
        }
    }

    public static final int MAX_INPUT = 2048;

    private WebAddress() { }

    /** Turns what the user typed into a URL to load, a search, or a refusal with a reason. */
    public static Result fromInput(String typed, Engine engine) {
        String s = typed == null ? "" : typed.trim();
        if (s.isEmpty() || s.length() > MAX_INPUT) return new Result(Result.Kind.INVALID, null);
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) return new Result(Result.Kind.BLOCKED_HTTP, null);
        if (lower.startsWith("https://")) return check(s);
        if (hasScheme(lower)) return new Result(Result.Kind.BLOCKED_SCHEME, null);
        if (looksLikeHost(s)) {
            Result r = check("https://" + s);
            if (r.kind != Result.Kind.INVALID) return r;
        }
        return search(s, engine);
    }

    /** Policy for a URL a page navigates to (links, redirects, subresources). */
    public static Result check(String url) {
        if (url == null || url.length() > 8192) return new Result(Result.Kind.INVALID, null);
        URI u;
        try {
            u = new URI(url);
        } catch (URISyntaxException e) {
            return new Result(Result.Kind.INVALID, null);
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http")) return new Result(Result.Kind.BLOCKED_HTTP, null);
        if (!scheme.equals("https")) return new Result(Result.Kind.BLOCKED_SCHEME, null);
        String host = u.getHost();
        if (host == null || host.isEmpty()) {
            // java.net.URI rejects some hosts browsers accept (e.g. "127.1", "2130706433"): judge the raw
            // authority instead, and never let such an address through.
            String auth = u.getRawAuthority();
            if (auth == null || auth.isEmpty()) return new Result(Result.Kind.INVALID, null);
            String h = auth.substring(auth.lastIndexOf('@') + 1);
            if (!h.startsWith("[") && h.lastIndexOf(':') > 0) h = h.substring(0, h.lastIndexOf(':'));
            return new Result(isLocal(h) ? Result.Kind.BLOCKED_LOCAL : Result.Kind.INVALID, null);
        }
        if (isLocal(host)) return new Result(Result.Kind.BLOCKED_LOCAL, null);
        if (u.getRawUserInfo() != null) return new Result(Result.Kind.INVALID, null);
        return new Result(Result.Kind.NAVIGATE, url);
    }

    static Result search(String query, Engine engine) {
        try {
            return new Result(Result.Kind.SEARCH, engine.prefix + URLEncoder.encode(query, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return new Result(Result.Kind.INVALID, null);
        }
    }

    /** "scheme:..." where scheme is a valid URI scheme (but not "host:port"). */
    private static boolean hasScheme(String lower) {
        int colon = lower.indexOf(':');
        if (colon <= 0) return false;
        String scheme = lower.substring(0, colon);
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) return false;
        String rest = lower.substring(colon + 1);
        return !rest.matches("\\d{1,5}(/.*)?");   // example.com:8443 is a host with a port
    }

    /** No spaces and a dot or port, e.g. "example.com", "example.com/path", "example.com:8443". */
    static boolean looksLikeHost(String s) {
        if (s.contains(" ") || s.startsWith(".")) return false;
        String host = s.split("[/?#]", 2)[0];
        if (host.isEmpty()) return false;
        if (host.startsWith("[")) return host.contains("]");   // IPv6 literal
        String name = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
        return name.contains(".") && !name.endsWith(".") || host.contains(":") || name.equalsIgnoreCase("localhost");
    }

    /** Loopback, link-local, private and local-only names and addresses (IPv4 and IPv6 literals). */
    public static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".lan")
                || h.endsWith(".home.arpa") || h.endsWith(".internal")) return true;
        int[] v4 = ipv4(h);
        if (v4 != null) {
            int a = v4[0], b = v4[1];
            return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168) || (a == 100 && b >= 64 && b <= 127) || a >= 224;
        }
        if (h.contains(":")) {
            if (h.equals("::") || h.equals("::1")) return true;
            if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") || h.startsWith("fe9")
                    || h.startsWith("fea") || h.startsWith("feb") || h.startsWith("ff")) return true;
            int last = h.lastIndexOf(':');
            if (h.startsWith("::ffff:") && ipv4(h.substring(last + 1)) != null) return isLocal(h.substring(last + 1));
        }
        return false;
    }

    /**
     * Dotted-quad IPv4. Browsers also accept shortened, octal and hex forms ("2130706433", "0177.1", "0x7f.1" are
     * all 127.0.0.1); any such numeric form is reported as 0.0.0.0, i.e. treated as local, rather than decoded.
     */
    private static int[] ipv4(String h) {
        String[] p = h.split("\\.", -1);
        if (p.length > 4) return null;
        boolean canonical = p.length == 4;
        for (String part : p) {
            if (!part.matches("\\d+|0x[0-9a-f]*")) return null;
            if (part.startsWith("0x") || (part.length() > 1 && part.startsWith("0")) || part.length() > 3) canonical = false;
        }
        if (!canonical) return new int[]{0, 0, 0, 0};
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = Integer.parseInt(p[i]);
            if (out[i] > 255) return new int[]{0, 0, 0, 0};
        }
        return out;
    }
}
