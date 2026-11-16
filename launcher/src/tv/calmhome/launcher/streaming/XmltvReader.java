package tv.calmhome.launcher.streaming;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import tv.calmhome.launcher.core.Guide;

/**
 * Streams an XMLTV guide (plain or gzip) into {@link GuideData.Writer} without loading it into memory.
 *
 * Safety: the pull parser does not process document type declarations, so external entities and entity
 * expansion ("billion laughs") are never resolved; an undeclared entity is a parse error. Work is bounded: at most
 * {@link #MAX_UNPACKED} bytes after decompression, {@link #MAX_PROGRAMMES} programmes kept (only those from 12 h ago
 * to 8 days ahead), {@link #MAX_CHANNELS} channels, and capped text lengths.
 */
public final class XmltvReader {

    public static final long MAX_UNPACKED = 512L * 1024 * 1024;
    public static final int MAX_PROGRAMMES = 200_000, MAX_CHANNELS = 20_000;
    static final long PAST_MS = 12 * 3_600_000L, AHEAD_MS = 8 * 24 * 3_600_000L;

    public static final class Stats {
        public int channels, programmes, outsideWindow, invalid;
        public boolean unzoned, truncated;

        public String summary() {
            StringBuilder b = new StringBuilder(channels + " channels, " + programmes + " programmes");
            if (invalid > 0) b.append(" · ").append(invalid).append(" unreadable entries left out");
            if (truncated) b.append(" · guide too large, the rest was not read");
            if (unzoned) b.append(" · times without a time zone were read as UTC");
            return b.toString();
        }
    }

    public static class GuideFormatException extends IOException {
        private static final long serialVersionUID = 1L;

        GuideFormatException(String m) {
            super(m);
        }
    }

    private XmltvReader() { }

    /** Wraps gzip input transparently (by magic number) and caps the unpacked size. */
    static InputStream unpack(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024);
        in.mark(2);
        int a = in.read(), b = in.read();
        in.reset();
        InputStream s = (a == 0x1f && b == 0x8b) ? new GZIPInputStream(in, 64 * 1024) : in;
        return new java.io.FilterInputStream(s) {
            long count;

            @Override public int read() throws IOException {
                int r = super.read();
                if (r >= 0 && ++count > MAX_UNPACKED) throw new GuideFormatException("too large");
                return r;
            }

            @Override public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n > 0 && (count += n) > MAX_UNPACKED) throw new GuideFormatException("too large");
                return n;
            }
        };
    }

    public static Stats read(InputStream raw, GuideData.Writer out, long now) throws IOException {
        Stats st = new Stats();
        XmlPullParser p = Xml.newPullParser();
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            p.setInput(unpack(raw), null);
            boolean sawTv = false;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                if (ev != XmlPullParser.START_TAG) continue;
                switch (p.getName()) {
                    case "tv": sawTv = true; break;
                    case "channel": channel(p, out, st); break;
                    case "programme": programme(p, out, st, now); break;
                    default: break;
                }
            }
            if (!sawTv) throw new GuideFormatException("not XMLTV");
        } catch (XmlPullParserException | RuntimeException e) {
            if (st.truncated) return st;
            throw new GuideFormatException("unreadable XMLTV");
        } catch (GuideFormatException e) {
            if ("too large".equals(e.getMessage())) {
                st.truncated = true;
                return st;
            }
            throw e;
        }
        return st;
    }

    private static void channel(XmlPullParser p, GuideData.Writer out, Stats st) throws IOException, XmlPullParserException {
        String id = clip(p.getAttributeValue(null, "id"), 200);
        List<String> names = new ArrayList<>();
        int depth = p.getDepth();
        for (int ev = p.next(); !(ev == XmlPullParser.END_TAG && p.getDepth() == depth); ev = p.next()) {
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev == XmlPullParser.START_TAG && "display-name".equals(p.getName()) && names.size() < 10) {
                String n = clip(p.nextText(), 200);
                if (n != null && !n.isEmpty()) names.add(n);
            }
        }
        if (id == null || id.isEmpty()) {
            st.invalid++;
            return;
        }
        if (st.channels >= MAX_CHANNELS) {
            st.truncated = true;
            return;
        }
        out.channel(id, names);
        st.channels++;
    }

    private static void programme(XmlPullParser p, GuideData.Writer out, Stats st, long now)
            throws IOException, XmlPullParserException {
        String ch = clip(p.getAttributeValue(null, "channel"), 200);
        Guide.Time start = Guide.time(p.getAttributeValue(null, "start"));
        Guide.Time stop = Guide.time(p.getAttributeValue(null, "stop"));
        String title = null, sub = null, desc = null, cat = null, ep = null;
        int depth = p.getDepth();
        for (int ev = p.next(); !(ev == XmlPullParser.END_TAG && p.getDepth() == depth); ev = p.next()) {
            if (ev == XmlPullParser.END_DOCUMENT) break;
            if (ev != XmlPullParser.START_TAG) continue;
            switch (p.getName()) {
                case "title": if (title == null) title = clip(p.nextText(), 300); break;
                case "sub-title": if (sub == null) sub = clip(p.nextText(), 300); break;
                case "desc": if (desc == null) desc = clip(p.nextText(), 2000); break;
                case "category": if (cat == null) cat = clip(p.nextText(), 100); break;
                case "episode-num":
                    String system = p.getAttributeValue(null, "system");
                    String v = clip(p.nextText(), 60);
                    if (ep == null || "onscreen".equals(system)) ep = v;
                    break;
                default: break;
            }
        }
        long s = start.ms, e = stop.ms;
        if (ch == null || ch.isEmpty() || title == null || title.isEmpty() || s < 0) {
            st.invalid++;
            return;
        }
        if (e <= s) e = s + 30 * 60_000L;               // missing stop: shown as 30 minutes, never longer
        if (e < now - PAST_MS || s > now + AHEAD_MS) {
            st.outsideWindow++;
            return;
        }
        if (st.programmes >= MAX_PROGRAMMES) {
            st.truncated = true;
            return;
        }
        if (!start.zoned) st.unzoned = true;
        out.programme(ch, s, e, title, sub, desc, cat, ep);
        st.programmes++;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
