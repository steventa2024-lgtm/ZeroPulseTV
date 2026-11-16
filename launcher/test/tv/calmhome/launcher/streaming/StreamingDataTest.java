package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.HttpsFetch;

/** Store and importer against a fake HTTPS server (no network). Hosts are example.org placeholders. */
@RunWith(RobolectricTestRunner.class)
public class StreamingDataTest {

    private final Context c = RuntimeEnvironment.getApplication();
    private final Map<String, Object[]> routes = new HashMap<>();
    private StreamingStore store;
    private SourceImporter importer;

    private final HttpsFetch.Opener fake = url -> {
        final Object[] r = routes.get(url.toString());
        if (r == null) throw new java.net.UnknownHostException("no route");
        return new HttpsFetch.Response() {
            @Override public int status() { return (Integer) r[0]; }
            @Override public String header(String name) { return "Location".equals(name) ? (String) r[1] : null; }
            @Override public InputStream body() { return new ByteArrayInputStream(((String) r[1]).getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() { }
        };
    };

    private static final String LIST = "#EXTM3U\n"
            + "#EXTINF:-1 tvg-id=\"a\" group-title=\"News\",Alpha\nhttps://s.example.org/a.m3u8\n"
            + "#EXTINF:-1 group-title=\"Music\",Beta\nhttps://s.example.org/b.m3u8\n"
            + "#EXTINF:-1,Plain HTTP\nhttp://s.example.org/c.m3u8\n";

    @Before public void setUp() {
        store = new StreamingStore(c);
        importer = new SourceImporter(store, c.getContentResolver(), fake);
    }

    @After public void tearDown() {
        store.close();
    }

    @Test public void importsAnHttpsChannelList() {
        routes.put("https://lists.example.org/tv.m3u", new Object[]{200, LIST});
        SourceImporter.Outcome o = importer.addUrl("https://lists.example.org/tv.m3u");
        assertTrue(o.message, o.ok);
        assertTrue(o.message.contains("2 streams"));
        assertTrue(o.message.contains("1 HTTP-only left out"));
        assertFalse("message shows the host, not the full URL", o.message.contains("/tv.m3u"));
        assertEquals(2, store.channels().size());
        assertEquals("Alpha", store.channels().get(0).name);
        assertEquals("News", store.channels().get(0).group);
        assertEquals(1, store.sources().size());
        assertEquals(2, store.sources().get(0).channels);
    }

    @Test public void aStreamLinkBecomesOneEntry() {
        routes.put("https://live.example.org/master.m3u8", new Object[]{200, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8\n"});
        assertTrue(importer.addUrl("https://live.example.org/master.m3u8").ok);
        assertEquals(1, store.channels().size());
        assertEquals("https://live.example.org/master.m3u8", store.channels().get(0).url);
    }

    /** Found on the TCL: DASH and MP4 links were refused, and an MP4 was downloaded up to 8 MB first. */
    @Test public void dashAndMediaFilesAreSingleStreamsWithoutDownloadingThem() {
        routes.put("https://media.example.org/films/tears.mpd", new Object[]{200,
                "<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\"></MPD>"});
        SourceImporter.Outcome dash = importer.addUrl("https://media.example.org/films/tears.mpd");
        assertTrue(dash.message, dash.ok);
        assertTrue(dash.message.contains("tears"));

        final int[] read = {0};
        HttpsFetch.Opener huge = url -> new HttpsFetch.Response() {
            @Override public int status() { return 200; }
            @Override public String header(String name) { return null; }
            @Override public InputStream body() {
                return new InputStream() {          // an "endless" MP4: ftyp box, then zeros forever
                    final byte[] start = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
                    @Override public int read() {
                        int i = read[0]++;
                        if (i > 1024 * 1024) throw new AssertionError("read far past the header");
                        return i < start.length ? start[i] : 0;
                    }
                };
            }
            @Override public void close() { }
        };
        SourceImporter.Outcome mp4 = new SourceImporter(store, c.getContentResolver(), huge).addUrl("https://media.example.org/bbb-480p.mp4");
        assertTrue(mp4.message, mp4.ok);
        assertTrue(mp4.message.contains("bbb-480p"));
        assertTrue("only the header was read, not the video", read[0] < 64 * 1024);
        assertEquals(2, store.channels().size());
    }

    @Test public void singleStreamNames() {
        assertEquals("tears", SourceImporter.streamName("https://s.example.org/a/tears.mpd"));
        assertEquals("s.example.org", SourceImporter.streamName("https://s.example.org/hls/master.m3u8"));
        assertEquals("s.example.org", SourceImporter.streamName("https://s.example.org/"));
        assertEquals("Big Film", SourceImporter.streamName("https://s.example.org/Big%20Film.mp4?token=x"));
    }

    @Test public void refusalsStoreNothing() {
        assertFalse(importer.addUrl("http://lists.example.org/tv.m3u").ok);
        routes.put("https://lists.example.org/page", new Object[]{200, "<html>Not found</html>"});
        assertFalse(importer.addUrl("https://lists.example.org/page").ok);
        routes.put("https://lists.example.org/http-only.m3u", new Object[]{200, "#EXTM3U\n#EXTINF:-1,X\nhttp://s.example.org/x\n"});
        SourceImporter.Outcome o = importer.addUrl("https://lists.example.org/http-only.m3u");
        assertFalse(o.ok);
        assertTrue(o.message.contains("No playable HTTPS streams"));
        routes.put("https://lists.example.org/redirect", new Object[]{302, "http://lists.example.org/tv.m3u"});
        assertFalse(importer.addUrl("https://lists.example.org/redirect").ok);
        assertTrue(store.sources().isEmpty());
        assertTrue(store.channels().isEmpty());
    }

    @Test public void failedRefreshKeepsChannelsAndReportsIt() {
        routes.put("https://lists.example.org/tv.m3u", new Object[]{200, LIST});
        importer.addUrl("https://lists.example.org/tv.m3u");
        routes.put("https://lists.example.org/tv.m3u", new Object[]{404, ""});
        SourceImporter.Outcome o = importer.refresh(store.sources().get(0));
        assertFalse(o.ok);
        assertEquals(2, store.channels().size());
        assertTrue(store.sources().get(0).status.startsWith("Last refresh failed"));
    }

    @Test public void favouritesRecentsAndProgressSurviveRefreshAndRemoval() {
        routes.put("https://lists.example.org/tv.m3u", new Object[]{200, LIST});
        importer.addUrl("https://lists.example.org/tv.m3u");
        String a = "https://s.example.org/a.m3u8";
        store.setFavourite(a, true);
        store.markWatched(a, "Alpha");
        store.saveProgress(a, 61_000, 3_600_000);
        importer.refresh(store.sources().get(0));
        assertTrue(store.isFavourite(a));
        store.removeSource(store.sources().get(0).id);
        assertTrue("channels go with their source", store.channels().isEmpty());
        assertTrue(store.isFavourite(a));
        assertEquals(a, store.recentUrls(5).get(0));
        assertEquals(61_000, store.progress(a));
        store.setFavourite(a, false);
        assertFalse(store.isFavourite(a));
    }

    /** A video stopped at (or near) its end starts from the beginning next time. */
    @Test public void progressAtTheEndMeansStartAgain() {
        String a = "https://s.example.org/film.mpd";
        store.saveProgress(a, 734_000, 734_000);
        assertEquals("ended", 0, store.progress(a));
        store.saveProgress(a, 720_000, 734_000);
        assertEquals("credits (last 30 s)", 0, store.progress(a));
        store.saveProgress(a, 3_500_000, 3_600_000);
        assertEquals("past 95 %", 0, store.progress(a));
        store.saveProgress(a, 600_000, 734_000);
        assertEquals(600_000, store.progress(a));
        android.content.ContentValues old = new android.content.ContentValues();   // a row saved before the rule
        old.put("url", a);
        old.put("position_ms", 734_000L);
        old.put("duration_ms", 734_000L);
        old.put("updated", 1L);
        store.getWritableDatabase().insertWithOnConflict("progress", null, old, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
        assertEquals(0, store.progress(a));
    }

    @Test public void sameLinkTwiceIsOneSource() {
        routes.put("https://lists.example.org/tv.m3u", new Object[]{200, LIST});
        importer.addUrl("https://lists.example.org/tv.m3u");
        importer.addUrl("https://lists.example.org/tv.m3u");
        assertEquals(1, store.sources().size());
        assertEquals(2, store.channels().size());
    }
}
