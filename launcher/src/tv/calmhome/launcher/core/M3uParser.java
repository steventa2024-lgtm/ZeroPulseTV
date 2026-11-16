package tv.calmhome.launcher.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an M3U/M3U8 file (pure Java, unit-tested). Tells a channel list (#EXTINF entries, each pointing at a
 * stream) apart from a media manifest (an HLS stream itself, with #EXT-X- tags), and keeps only entries this beta
 * can play: HTTPS. Work is bounded by {@link #MAX_CHARS}, {@link #MAX_LINE} and {@link #MAX_ENTRIES}. Nothing is
 * logged, so tokens inside playlist URLs never reach a log.
 */
public final class M3uParser {

    public static final int MAX_CHARS = 8 * 1024 * 1024;
    public static final int MAX_LINE = 16 * 1024;
    public static final int MAX_ENTRIES = 20_000;
    static final int MAX_NAME = 200;
    static final int MAX_ATTR = 1024;

    public enum Kind {
        /** #EXTINF entries: a list of channels or videos. */
        CHANNEL_LIST,
        /** An HLS playlist (#EXT-X-...): the URL that served it is itself one playable stream. */
        MEDIA_MANIFEST,
        /** Neither: not an M3U file (an HTML error page, for example). */
        NOT_A_PLAYLIST
    }

    public static final class Entry {
        public final String name, url, tvgId, tvgName, logo, group;

        Entry(String name, String url, String tvgId, String tvgName, String logo, String group) {
            this.name = name;
            this.url = url;
            this.tvgId = tvgId;
            this.tvgName = tvgName;
            this.logo = logo;
            this.group = group;
        }
    }

    public static final class Result {
        public final Kind kind;
        public final List<Entry> entries;
        /** Entries left out, by reason: HTTP-only (this beta is HTTPS-only), other schemes, malformed, repeats. */
        public final int skippedHttp, skippedUnsupported, skippedInvalid, duplicates;
        /** True when the input exceeded a limit and the rest was not read. */
        public final boolean truncated;
        /** HTTPS XMLTV guide named in the #EXTM3U header (x-tvg-url / url-tvg), or null. Never fetched automatically. */
        public String guideUrl;

        Result(Kind kind, List<Entry> entries, int http, int unsupported, int invalid, int dup, boolean truncated) {
            this.kind = kind;
            this.entries = entries;
            this.skippedHttp = http;
            this.skippedUnsupported = unsupported;
            this.skippedInvalid = invalid;
            this.duplicates = dup;
            this.truncated = truncated;
        }
    }

    /** A source that is itself one stream (a media manifest): one entry named {@code name}. */
    public static Entry single(String name, String url) {
        return new Entry(clip(name, MAX_NAME), url, null, null, null, null);
    }

    /** One line for the user: how many entries were kept and why others were left out. */
    public static String summary(Result r) {
        StringBuilder b = new StringBuilder(r.entries.size() + (r.entries.size() == 1 ? " stream" : " streams"));
        if (r.skippedHttp > 0) b.append(" · ").append(r.skippedHttp).append(" HTTP-only left out");
        if (r.skippedUnsupported > 0) b.append(" · ").append(r.skippedUnsupported).append(" unsupported");
        if (r.skippedInvalid > 0) b.append(" · ").append(r.skippedInvalid).append(" malformed");
        if (r.duplicates > 0) b.append(" · ").append(r.duplicates).append(" duplicates");
        if (r.truncated) b.append(" · list too long, rest not read");
        return b.toString();
    }

    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    private M3uParser() { }

    public static Result parse(String text) {
        List<Entry> out = new ArrayList<>();
        if (text == null) return new Result(Kind.NOT_A_PLAYLIST, out, 0, 0, 0, 0, false);
        boolean truncated = false;
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
            truncated = true;
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\r\n|\r|\n");
        boolean header = false, sawInf = false;
        String guide = null;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("#EXTM3U")) {
                header = true;
                if (guide == null) guide = headerGuide(l);
                continue;
            }
            if (l.startsWith("#EXT-X-TARGETDURATION") || l.startsWith("#EXT-X-STREAM-INF")
                    || l.startsWith("#EXT-X-MEDIA-SEQUENCE") || l.startsWith("#EXT-X-PLAYLIST-TYPE")) {
                return new Result(Kind.MEDIA_MANIFEST, out, 0, 0, 0, 0, false);
            }
            if (l.startsWith("#EXTINF")) sawInf = true;
        }
        if (!sawInf && !header) return new Result(Kind.NOT_A_PLAYLIST, out, 0, 0, 0, 0, truncated);

        int http = 0, unsupported = 0, invalid = 0, dup = 0;
        Set<String> seen = new HashSet<>();
        String name = null, tvgId = null, tvgName = null, logo = null, group = null, extgrp = null;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.length() > MAX_LINE) {
                invalid++;
                name = tvgId = tvgName = logo = group = extgrp = null;
                continue;
            }
            if (l.startsWith("#EXTINF")) {
                int comma = nameComma(l);
                String attrs = comma < 0 ? l : l.substring(0, comma);
                name = comma < 0 ? null : clip(l.substring(comma + 1).trim(), MAX_NAME);
                tvgId = tvgName = logo = group = null;
                Matcher m = ATTR.matcher(attrs);
                while (m.find()) {
                    String k = m.group(1).toLowerCase(Locale.ROOT), v = clip(m.group(2).trim(), MAX_ATTR);
                    if (v.isEmpty()) continue;
                    switch (k) {
                        case "tvg-id": tvgId = v; break;
                        case "tvg-name": tvgName = v; break;
                        case "tvg-logo": logo = v; break;
                        case "group-title": group = v; break;
                        default: break;
                    }
                }
                continue;
            }
            if (l.startsWith("#EXTGRP:")) {
                extgrp = clip(l.substring(8).trim(), MAX_ATTR);
                continue;
            }
            if (l.startsWith("#")) continue;                       // other directives (#EXTVLCOPT, ...) ignored

            // A URL line.
            String url = l;
            String scheme = scheme(url);
            if (scheme == null) invalid++;
            else if (scheme.equals("http")) http++;
            else if (!scheme.equals("https")) unsupported++;
            else if (host(url) == null) invalid++;
            else if (!seen.add(url)) dup++;
            else if (out.size() >= MAX_ENTRIES) truncated = true;
            else {
                String display = name != null && !name.isEmpty() ? name : (tvgName != null ? tvgName : host(url));
                String g = group != null ? group : extgrp;
                String art = logo != null && "https".equals(scheme(logo)) && host(logo) != null ? logo : null;
                out.add(new Entry(display, url, tvgId, tvgName, art, g));
            }
            name = tvgId = tvgName = logo = group = extgrp = null;
        }
        Result res = new Result(Kind.CHANNEL_LIST, out, http, unsupported, invalid, dup, truncated);
        res.guideUrl = guide;
        return res;
    }

    /** First HTTPS URL in x-tvg-url / url-tvg (they may hold several, comma-separated). */
    static String headerGuide(String header) {
        Matcher m = ATTR.matcher(header);
        while (m.find()) {
            String k = m.group(1).toLowerCase(Locale.ROOT);
            if (!k.equals("x-tvg-url") && !k.equals("url-tvg")) continue;
            for (String u : m.group(2).split(",")) {
                u = u.trim();
                if ("https".equals(scheme(u)) && host(u) != null && u.length() <= MAX_ATTR) return u;
            }
        }
        return null;
    }

    /** Index of the comma that starts the display name: the first comma outside quoted attribute values. */
    static int nameComma(String extinf) {
        boolean quoted = false;
        for (int i = 0; i < extinf.length(); i++) {
            char ch = extinf.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) return i;
        }
        return -1;
    }

    static String scheme(String url) {
        int c = url.indexOf(':');
        if (c <= 0) return null;
        String s = url.substring(0, c).toLowerCase(Locale.ROOT);
        return s.matches("[a-z][a-z0-9+.-]*") ? s : null;
    }

    static String host(String url) {
        try {
            String h = new URI(url).getHost();
            return h == null || h.isEmpty() ? null : h;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
