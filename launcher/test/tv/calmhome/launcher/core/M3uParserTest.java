package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Fixtures use example.org/.test hosts: nothing here points at a real stream. */
public class M3uParserTest {

    private static final String LIST = "\uFEFF#EXTM3U x-tvg-url=\"https://guide.example.org/epg.xml\"\n"
            + "#EXTINF:-1 tvg-id=\"news.example\" tvg-name=\"News One\" tvg-logo=\"https://img.example.org/n.png\" group-title=\"News\",News One HD\r\n"
            + "https://streams.example.org/news/index.m3u8\r\n"
            + "#EXTINF:-1 tvg-id=\"kids.example\" group-title=\"Kids\" tvg-logo=\"http://img.example.org/k.png\",Kids, Fun & Games\n"
            + "#EXTVLCOPT:http-user-agent=Something\n"
            + "https://streams.example.org/kids/master.m3u8?token=secret\n"
            + "#EXTINF:-1 tvg-name=\"Old\",Old HTTP Channel\n"
            + "http://streams.example.org/old.m3u8\n"
            + "#EXTINF:-1,Multicast\n"
            + "udp://@239.0.0.1:1234\n"
            + "#EXTINF:-1 group-title=\"News\",News One again\n"
            + "https://streams.example.org/news/index.m3u8\n"
            + "#EXTINF:-1,\"Quoted, name\"\n"
            + "#EXTGRP:Music\n"
            + "https://streams.example.org/music.m3u8\n"
            + "https://streams.example.org/bare.m3u8\n"
            + "not a url at all\n";

    @Test public void readsChannelListMetadata() {
        M3uParser.Result r = M3uParser.parse(LIST);
        assertEquals(M3uParser.Kind.CHANNEL_LIST, r.kind);
        assertEquals(4, r.entries.size());
        M3uParser.Entry news = r.entries.get(0);
        assertEquals("News One HD", news.name);
        assertEquals("news.example", news.tvgId);
        assertEquals("News One", news.tvgName);
        assertEquals("https://img.example.org/n.png", news.logo);
        assertEquals("News", news.group);
        assertEquals("https://streams.example.org/news/index.m3u8", news.url);

        M3uParser.Entry kids = r.entries.get(1);
        assertEquals("Kids, Fun & Games", kids.name);
        assertEquals("Kids", kids.group);
        assertNull("HTTP artwork is not loaded in this beta", kids.logo);

        M3uParser.Entry music = r.entries.get(2);
        assertEquals("Music", music.group);
        assertEquals("\"Quoted, name\"", music.name);

        M3uParser.Entry bare = r.entries.get(3);
        assertEquals("streams.example.org", bare.name);
        assertNull(bare.group);
        assertNull(bare.tvgId);
    }

    @Test public void countsWhatWasLeftOutAndWhy() {
        M3uParser.Result r = M3uParser.parse(LIST);
        assertEquals(1, r.skippedHttp);
        assertEquals(1, r.skippedUnsupported);
        assertEquals(1, r.duplicates);
        assertEquals(1, r.skippedInvalid);
        assertFalse(r.truncated);
    }

    @Test public void recognisesAnHlsMediaManifest() {
        String media = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg1.ts\n";
        assertEquals(M3uParser.Kind.MEDIA_MANIFEST, M3uParser.parse(media).kind);
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nlow.m3u8\n";
        assertEquals(M3uParser.Kind.MEDIA_MANIFEST, M3uParser.parse(master).kind);
    }

    @Test public void rejectsThingsThatAreNotPlaylists() {
        assertEquals(M3uParser.Kind.NOT_A_PLAYLIST, M3uParser.parse("<html><body>404 Not Found</body></html>").kind);
        assertEquals(M3uParser.Kind.NOT_A_PLAYLIST, M3uParser.parse("").kind);
        assertEquals(M3uParser.Kind.NOT_A_PLAYLIST, M3uParser.parse(null).kind);
        assertEquals(M3uParser.Kind.NOT_A_PLAYLIST, M3uParser.parse("https://example.org/a.m3u8\n").kind);
    }

    @Test public void boundsOversizedInput() {
        StringBuilder b = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < M3uParser.MAX_ENTRIES + 5; i++) b.append("#EXTINF:-1,C").append(i).append("\nhttps://s.example.org/").append(i).append(".m3u8\n");
        M3uParser.Result r = M3uParser.parse(b.toString());
        assertEquals(M3uParser.MAX_ENTRIES, r.entries.size());
        assertTrue(r.truncated);

        StringBuilder longLine = new StringBuilder("#EXTM3U\n#EXTINF:-1,Long\nhttps://s.example.org/");
        for (int i = 0; i < M3uParser.MAX_LINE; i++) longLine.append('a');
        longLine.append("\n#EXTINF:-1,Ok\nhttps://s.example.org/ok.m3u8\n");
        M3uParser.Result l = M3uParser.parse(longLine.toString());
        assertEquals(1, l.entries.size());
        assertEquals("Ok", l.entries.get(0).name);
        assertEquals(1, l.skippedInvalid);
    }

    @Test public void survivesMalformedAttributes() {
        String bad = "#EXTM3U\n#EXTINF:-1 tvg-id=\"unterminated group-title=News,Name\nhttps://s.example.org/a.m3u8\n"
                + "#EXTINF\nhttps://s.example.org/b.m3u8\n#EXTINF:-1 tvg-logo=\"javascript:alert(1)\",Evil logo\nhttps://s.example.org/c.m3u8\n";
        M3uParser.Result r = M3uParser.parse(bad);
        assertEquals(3, r.entries.size());
        assertNull(r.entries.get(2).logo);
        for (M3uParser.Entry e : r.entries) assertTrue(e.url.startsWith("https://"));
    }
}
