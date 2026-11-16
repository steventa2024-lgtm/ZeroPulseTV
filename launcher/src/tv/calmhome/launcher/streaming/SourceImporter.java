package tv.calmhome.launcher.streaming;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import tv.calmhome.launcher.core.HttpsFetch;
import tv.calmhome.launcher.core.M3uParser;

/**
 * Reads a playlist source (HTTPS link or a file the user picked) into {@link StreamingStore}. Runs on a
 * background thread; every result has a message for the user. URLs are never logged or shown in full (they may
 * carry a token): screens show only the host or file name.
 */
public final class SourceImporter {

    public static final class Outcome {
        public final boolean ok;
        public final String message;

        Outcome(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private final StreamingStore store;
    private final ContentResolver resolver;
    private final HttpsFetch.Opener opener;

    public SourceImporter(StreamingStore store, ContentResolver resolver, HttpsFetch.Opener opener) {
        this.store = store;
        this.resolver = resolver;
        this.opener = opener;
    }

    /**
     * Adds (or refreshes) an HTTPS link: an M3U playlist, or a single stream (HLS, DASH or a media file). Only the
     * first bytes are read to recognise a media file, so a video is never downloaded just to add it.
     */
    public Outcome addUrl(String url) {
        String text;
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(
                HttpsFetch.stream(url, M3uParser.MAX_CHARS, opener), 256)) {
            in.mark(256);
            byte[] head = new byte[189];
            int n = 0;
            for (int r; n < head.length && (r = in.read(head, n, head.length - n)) > 0; ) n += r;
            if (isMediaFile(head, n)) return addSingle(url, "a media file");
            in.reset();
            text = new String(HttpsFetch.read(in, M3uParser.MAX_CHARS), java.nio.charset.StandardCharsets.UTF_8);
        } catch (HttpsFetch.FetchException e) {
            return new Outcome(false, e.getMessage());
        } catch (IOException e) {
            return new Outcome(false, HttpsFetch.Reason.NETWORK.message);
        }
        String host = host(url);
        M3uParser.Result r = M3uParser.parse(text);
        long id;
        switch (r.kind) {
            case MEDIA_MANIFEST:
                return addSingle(url, "an HLS stream");
            case CHANNEL_LIST:
                if (r.entries.isEmpty()) return new Outcome(false, "No playable HTTPS streams in this playlist ("
                        + M3uParser.summary(r) + ").");
                id = store.addSource(StreamingStore.KIND_URL, url.trim(), host);
                store.replaceChannels(id, r.entries, M3uParser.summary(r));
                if (r.guideUrl != null) store.setGuideUrl(id, r.guideUrl);
                return new Outcome(true, "Added " + host + ": " + M3uParser.summary(r) + "."
                        + (r.guideUrl != null ? " It names a guide; you can add it below." : ""));
            default:
                if (text.contains("<MPD")) return addSingle(url, "a DASH stream");
                return new Outcome(false, "That link isn't an M3U playlist or a stream ZeroPulse recognises.");
        }
    }

    private Outcome addSingle(String url, String what) {
        String name = streamName(url);
        long id = store.addSource(StreamingStore.KIND_URL, url.trim(), name);
        store.replaceChannels(id, java.util.Collections.singletonList(M3uParser.single(name, url.trim())), "1 stream");
        return new Outcome(true, "Added " + name + ": " + what + ".");
    }

    /** MP4/MOV (ftyp), Matroska/WebM (EBML), MPEG-TS (sync bytes), MP3 (ID3) or Ogg, from the first bytes. */
    static boolean isMediaFile(byte[] b, int n) {
        if (n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return true;
        if (n >= 4 && (b[0] & 0xff) == 0x1a && (b[1] & 0xff) == 0x45 && (b[2] & 0xff) == 0xdf && (b[3] & 0xff) == 0xa3) return true;
        if (n >= 189 && b[0] == 0x47 && b[188] == 0x47) return true;
        if (n >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return n >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
    }

    /** "tears" for …/tears.mpd; the host for generic names such as master.m3u8 or index.m3u8. */
    static String streamName(String url) {
        String host = host(url);
        try {
            String path = new java.net.URI(url.trim()).getPath();
            if (path == null) return host;
            String last = path.substring(path.lastIndexOf('/') + 1);
            int dot = last.lastIndexOf('.');
            String base = dot > 0 ? last.substring(0, dot) : last;
            if (base.isEmpty() || base.matches("(?i)master|index|playlist|manifest|prog_index|stream|video|chunklist.*")) return host;
            return base.length() > 60 ? base.substring(0, 60) : base;
        } catch (java.net.URISyntaxException e) {
            return host;
        }
    }

    /** Adds a playlist document chosen with the system picker, keeping read access for later refreshes. */
    public Outcome addFile(Uri uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException notPersistable) {
            // Still readable now; a later refresh may need the file to be picked again.
        }
        String name = displayName(uri);
        String text;
        try {
            text = readFile(uri);
        } catch (IOException e) {
            return new Outcome(false, "Couldn't read " + name + ".");
        }
        M3uParser.Result r = M3uParser.parse(text);
        if (r.kind == M3uParser.Kind.MEDIA_MANIFEST) {
            return new Outcome(false, name + " is a stream manifest with relative parts; add its HTTPS link instead.");
        }
        if (r.kind != M3uParser.Kind.CHANNEL_LIST) return new Outcome(false, name + " isn't an M3U playlist.");
        if (r.entries.isEmpty()) return new Outcome(false, "No playable HTTPS streams in " + name + " (" + M3uParser.summary(r) + ").");
        long id = store.addSource(StreamingStore.KIND_FILE, uri.toString(), name);
        store.replaceChannels(id, r.entries, M3uParser.summary(r));
        if (r.guideUrl != null) store.setGuideUrl(id, r.guideUrl);
        return new Outcome(true, "Added " + name + ": " + M3uParser.summary(r) + ".");
    }

    /** Re-reads a source; its channels are replaced only when the new read succeeds. */
    public Outcome refresh(StreamingStore.Source s) {
        Outcome o = StreamingStore.KIND_FILE.equals(s.kind) ? addFile(Uri.parse(s.location)) : addUrl(s.location);
        if (!o.ok) store.setStatus(s.id, "Last refresh failed: " + o.message);
        return o;
    }

    // ------------------------------------------------------------------ XMLTV guides

    /** Compressed download limit for a guide (the unpacked limit is {@link XmltvReader#MAX_UNPACKED}). */
    public static final long MAX_GUIDE_DOWNLOAD = 150L * 1024 * 1024;

    /** Adds (or refreshes) an HTTPS XMLTV guide. The previous guide stays if reading fails. */
    public Outcome addGuideUrl(String url) {
        String host = host(url);
        try (InputStream in = HttpsFetch.stream(url, MAX_GUIDE_DOWNLOAD, opener)) {
            GuideData g = new GuideData(store);
            return writeGuide(g, g.addSource(StreamingStore.KIND_URL, url.trim(), host), in, host);
        } catch (HttpsFetch.FetchException e) {
            return new Outcome(false, e.getMessage());
        } catch (IOException e) {
            return new Outcome(false, "Couldn't read the guide from " + host + ".");
        }
    }

    public Outcome addGuideFile(Uri uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException notPersistable) {
            // Still readable now.
        }
        String name = displayName(uri);
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return new Outcome(false, "Couldn't read " + name + ".");
            GuideData g = new GuideData(store);
            return writeGuide(g, g.addSource(StreamingStore.KIND_FILE, uri.toString(), name), in, name);
        } catch (IOException | SecurityException e) {
            return new Outcome(false, "Couldn't read " + name + ".");
        }
    }

    public Outcome refreshGuide(GuideData.GuideSource s) {
        Outcome o = StreamingStore.KIND_FILE.equals(s.kind) ? addGuideFile(Uri.parse(s.location)) : addGuideUrl(s.location);
        if (!o.ok) new GuideData(store).setStatus(s.id, "Last refresh failed: " + o.message);
        return o;
    }

    private Outcome writeGuide(GuideData g, long id, InputStream in, String label) throws IOException {
        Outcome result;
        try (GuideData.Writer w = g.writer(id)) {
            XmltvReader.Stats st = XmltvReader.read(in, w, System.currentTimeMillis());
            if (st.programmes == 0) {
                result = new Outcome(false, "No programmes for the coming days in " + label + ".");
            } else {
                w.commit(st.summary(), st.unzoned);
                result = new Outcome(true, "Guide from " + label + ": " + st.summary() + ".");
            }
        } catch (XmltvReader.GuideFormatException e) {
            result = new Outcome(false, label + " isn't a readable XMLTV guide.");
        }
        // A guide that never loaded isn't kept as an empty entry; one that loaded before keeps its old data.
        if (!result.ok) g.removeIfNeverLoaded(id);
        return result;
    }

    private String readFile(Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("no stream");
            byte[] buf = new byte[16 * 1024];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > M3uParser.MAX_CHARS) throw new IOException("too large");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (RuntimeException ignored) {
            // Fall back below.
        }
        return "the playlist file";
    }

    static String host(String url) {
        try {
            String h = new java.net.URI(url.trim()).getHost();
            return h != null ? h : "playlist";
        } catch (java.net.URISyntaxException e) {
            return "playlist";
        }
    }
}
