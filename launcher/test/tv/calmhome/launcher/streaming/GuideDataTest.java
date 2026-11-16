package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.data.ModuleData;

@RunWith(RobolectricTestRunner.class)
public class GuideDataTest {

    private final Context c = RuntimeEnvironment.getApplication();
    private final long now = System.currentTimeMillis();
    private StreamingStore store;
    private GuideData guide;

    @Before public void open() {
        store = new StreamingStore(c);
        guide = new GuideData(store);
    }

    @After public void close() {
        store.close();
    }

    static String t(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms)) + " +0000";
    }

    static String xmltv(long now) {
        long h = 3_600_000L;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n<tv>"
                + "<channel id=\"news.example\"><display-name>World News</display-name><display-name>World News HD</display-name></channel>"
                + "<channel id=\"sport.example\"><display-name>Sport One</display-name></channel>"
                + "<programme start=\"" + t(now - h) + "\" stop=\"" + t(now + h) + "\" channel=\"news.example\"><title>Evening News</title>"
                + "<desc>Headlines.</desc><category>News</category></programme>"
                + "<programme start=\"" + t(now + h) + "\" stop=\"" + t(now + 2 * h) + "\" channel=\"news.example\"><title>Weather</title></programme>"
                + "<programme start=\"" + t(now - h) + "\" stop=\"" + t(now + h) + "\" channel=\"sport.example\"><title>Cup Final</title>"
                + "<sub-title>Reds v Blues</sub-title><category>Football</category></programme>"
                + "<programme start=\"" + t(now + 3 * h) + "\" stop=\"" + t(now + 5 * h) + "\" channel=\"sport.example\"><title>Grand Prix</title>"
                + "<category>Motor Sport</category></programme>"
                + "<programme start=\"" + t(now + 30L * 24 * h) + "\" stop=\"" + t(now + 30L * 24 * h + h) + "\" channel=\"news.example\"><title>Far future</title></programme>"
                + "<programme start=\"garbage\" channel=\"news.example\"><title>Broken</title></programme>"
                + "</tv>";
    }

    private XmltvReader.Stats load(InputStream in) throws Exception {
        long id = guide.addSource(StreamingStore.KIND_URL, "https://guide.example.org/g.xml", "guide.example.org");
        try (GuideData.Writer w = guide.writer(id)) {
            XmltvReader.Stats st = XmltvReader.read(in, w, now);
            w.commit(st.summary(), st.unzoned);
            return st;
        }
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void readsChannelsAndProgrammesInTheWindow() throws Exception {
        XmltvReader.Stats st = load(bytes(xmltv(now)));
        assertEquals(2, st.channels);
        assertEquals(4, st.programmes);
        assertEquals(1, st.outsideWindow);
        assertEquals(1, st.invalid);
        assertFalse(st.unzoned);
        List<GuideData.Programme> onNow = guide.programmes("news.example", now, now + 1, 2);
        assertEquals("Evening News", onNow.get(0).title);
        assertEquals("Headlines.", onNow.get(0).description);
        assertEquals("news.example", guide.index().match(null, null, "World News FHD"));
    }

    @Test public void readsGzipTransparently() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(raw)) {
            gz.write(xmltv(now).getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(4, load(new ByteArrayInputStream(raw.toByteArray())).programmes);
    }

    @Test public void externalEntitiesAreNeverResolved() throws Exception {
        File secret = new File(c.getFilesDir(), "secret.txt");
        java.nio.file.Files.write(secret.toPath(), "TOP-SECRET".getBytes(StandardCharsets.UTF_8));
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE tv [<!ENTITY xxe SYSTEM \"file://" + secret.getAbsolutePath()
                + "\">]><tv><channel id=\"x\"><display-name>&xxe;</display-name></channel></tv>";
        try {
            load(bytes(xxe));
        } catch (XmltvReader.GuideFormatException expected) {
            return;   // rejected outright: fine
        }
        for (String n : new String[]{"TOP-SECRET"}) assertNull(guide.index().match(null, null, n));
    }

    @Test public void entityExpansionBombIsRejected() {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\"?><!DOCTYPE tv [<!ENTITY a \"aaaaaaaaaa\">");
        for (int i = 1; i < 10; i++) b.append("<!ENTITY a").append(i).append(" \"&a").append(i == 1 ? "" : String.valueOf(i - 1))
                .append(";&a").append(i == 1 ? "" : String.valueOf(i - 1)).append(";&a").append(i == 1 ? "" : String.valueOf(i - 1)).append(";\">");
        b.append("]><tv><channel id=\"x\"><display-name>&a9;</display-name></channel></tv>");
        try {
            load(bytes(b.toString()));
            fail("expected the guide to be rejected");
        } catch (Exception expected) {
            assertTrue(expected instanceof XmltvReader.GuideFormatException);
        }
    }

    @Test public void notAGuideIsRejectedAndOldDataStays() throws Exception {
        load(bytes(xmltv(now)));
        long id = guide.sources().get(0).id;
        try (GuideData.Writer w = guide.writer(id)) {
            XmltvReader.read(bytes("<html><body>error</body></html>"), w, now);
            fail();
        } catch (XmltvReader.GuideFormatException expected) {
            // writer closed without commit: rolled back
        }
        assertEquals("Evening News", guide.programmes("news.example", now, now + 1, 1).get(0).title);
    }

    @Test public void timesWithoutZoneAreFlagged() throws Exception {
        String unzoned = xmltv(now).replace(" +0000", "");
        XmltvReader.Stats st = load(bytes(unzoned));
        assertTrue(st.unzoned);
        assertTrue(guide.sources().get(0).unzoned);
    }

    @Test public void importerAddsAGuideFromHttpsAndRefusesHttp() {
        final byte[] body = xmltv(now).getBytes(StandardCharsets.UTF_8);
        tv.calmhome.launcher.core.HttpsFetch.Opener fake = url -> new tv.calmhome.launcher.core.HttpsFetch.Response() {
            @Override public int status() { return url.toString().endsWith("/g.xml") ? 200 : 404; }
            @Override public String header(String n) { return null; }
            @Override public InputStream body() { return new ByteArrayInputStream(body); }
            @Override public void close() { }
        };
        SourceImporter imp = new SourceImporter(store, c.getContentResolver(), fake);
        assertFalse(imp.addGuideUrl("http://guide.example.org/g.xml").ok);
        SourceImporter.Outcome missing = imp.addGuideUrl("https://guide.example.org/missing.xml");
        assertFalse(missing.ok);
        assertTrue("a guide that never loaded isn't kept", guide.sources().isEmpty());
        SourceImporter.Outcome o = imp.addGuideUrl("https://guide.example.org/g.xml");
        assertTrue(o.message, o.ok);
        assertTrue(o.message.contains("4 programmes"));
        assertEquals(1, guide.sources().size());
    }

    @Test public void playlistHeaderGuideIsRecordedNotFetched() {
        final String list = "#EXTM3U x-tvg-url=\"http://plain.example.org/g.xml,https://guide.example.org/g.xml\"\n"
                + "#EXTINF:-1,A\nhttps://s.example.org/a.m3u8\n";
        final int[] opened = {0};
        tv.calmhome.launcher.core.HttpsFetch.Opener fake = url -> {
            opened[0]++;
            return new tv.calmhome.launcher.core.HttpsFetch.Response() {
                @Override public int status() { return 200; }
                @Override public String header(String n) { return null; }
                @Override public InputStream body() { return new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)); }
                @Override public void close() { }
            };
        };
        assertTrue(new SourceImporter(store, c.getContentResolver(), fake).addUrl("https://lists.example.org/tv.m3u").ok);
        assertEquals(1, opened[0]);
        assertEquals("https://guide.example.org/g.xml", store.sources().get(0).guideUrl);
        assertTrue(guide.sources().isEmpty());
    }

    @Test public void eventFavourites() throws Exception {
        load(bytes(xmltv(now)));
        GuideData.Programme cup = guide.programmes("sport.example", now, now + 1, 1).get(0);
        guide.setFavouriteEvent(cup.key(), true);
        assertTrue(guide.isFavouriteEvent(cup.key()));
        guide.setFavouriteEvent(cup.key(), false);
        assertFalse(guide.isFavouriteEvent(cup.key()));
    }

    /** A database written by schema version 1 (Milestone B) upgrades in place, and survives a downgrade cycle. */
    @Test public void versionOneDatabaseUpgradesAdditively() {
        store.close();
        c.deleteDatabase(ModuleData.STREAMING_DB);
        File path = c.getDatabasePath(ModuleData.STREAMING_DB);
        path.getParentFile().mkdirs();
        SQLiteDatabase v1 = SQLiteDatabase.openOrCreateDatabase(path, null);
        v1.execSQL("CREATE TABLE sources (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, location TEXT NOT NULL UNIQUE, "
                + "title TEXT NOT NULL, added INTEGER NOT NULL, refreshed INTEGER NOT NULL DEFAULT 0, status TEXT)");
        v1.execSQL("CREATE TABLE channels (id INTEGER PRIMARY KEY, source_id INTEGER NOT NULL REFERENCES sources(id) "
                + "ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, tvg_id TEXT, "
                + "tvg_name TEXT, logo TEXT, grp TEXT, UNIQUE (source_id, url))");
        v1.execSQL("CREATE TABLE favourites (url TEXT PRIMARY KEY, added INTEGER NOT NULL)");
        v1.execSQL("CREATE TABLE recents (url TEXT PRIMARY KEY, name TEXT NOT NULL, watched INTEGER NOT NULL)");
        v1.execSQL("CREATE TABLE progress (url TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated INTEGER NOT NULL)");
        ContentValues s = new ContentValues();
        s.put("kind", "url");
        s.put("location", "https://lists.example.org/tv.m3u");
        s.put("title", "lists.example.org");
        s.put("added", 1);
        long src = v1.insert("sources", null, s);
        ContentValues ch = new ContentValues();
        ch.put("source_id", src);
        ch.put("position", 0);
        ch.put("name", "Alpha");
        ch.put("url", "https://s.example.org/a.m3u8");
        v1.insert("channels", null, ch);
        v1.execSQL("INSERT INTO favourites VALUES ('https://s.example.org/a.m3u8', 1)");
        v1.setVersion(1);
        v1.close();

        store = new StreamingStore(c);
        guide = new GuideData(store);
        assertEquals(1, store.channels().size());
        assertTrue(store.isFavourite("https://s.example.org/a.m3u8"));
        assertNull(store.sources().get(0).guideUrl);
        assertTrue(guide.sources().isEmpty());
        store.close();

        // Simulate an older build opening the file (version back to 1), then this build again.
        SQLiteDatabase db = SQLiteDatabase.openDatabase(path.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        db.setVersion(1);
        db.close();
        store = new StreamingStore(c);
        guide = new GuideData(store);
        assertEquals(1, store.channels().size());
        assertTrue(guide.sources().isEmpty());
    }
}
