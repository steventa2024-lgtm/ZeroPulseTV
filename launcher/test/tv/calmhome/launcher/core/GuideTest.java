package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class GuideTest {

    private static final long T = 1_792_524_600_000L;   // 2026-10-20 19:30:00 UTC

    @Test public void readsXmltvTimesWithOffsets() {
        assertEquals(T, Guide.time("20261020193000 +0000").ms);
        assertEquals(T, Guide.time("20261020203000 +0100").ms);
        assertEquals(T, Guide.time("20261020123000 -0700").ms);
        assertEquals(T, Guide.time("20261020193000 Z").ms);
        assertTrue(Guide.time("20261020203000 +0100").zoned);
        assertEquals(T, Guide.time("202610201930 +0000").ms);
    }

    @Test public void missingOffsetIsReadAsUtcAndFlagged() {
        Guide.Time t = Guide.time("20261020193000");
        assertEquals(T, t.ms);
        assertFalse(t.zoned);
    }

    @Test public void rejectsJunk() {
        assertEquals(-1, Guide.time(null).ms);
        assertEquals(-1, Guide.time("tomorrow").ms);
        assertEquals(-1, Guide.time("20261320193000 +0000").ms);
        assertEquals(-1, Guide.time("20261020253000 +0000").ms);
        assertEquals(-1, Guide.time("20261020193000 +2500").ms);
    }

    @Test public void matchesByIdThenNameNeverGuesses() {
        Guide.Index g = new Guide.Index();
        g.add("BBCOne.uk", Arrays.asList("BBC One", "BBC One HD"));
        g.add("news.example", Arrays.asList("World News"));
        g.add("a.example", Arrays.asList("Sports"));
        g.add("b.example", Arrays.asList("Sports"));
        assertEquals("BBCOne.uk", g.match("bbcone.uk", null, "whatever"));
        assertEquals("BBCOne.uk", g.match(null, null, "BBC One FHD"));
        assertEquals("news.example", g.match("unknown.id", "World-News", null));
        assertNull("two guide channels share the name: not guessed", g.match(null, null, "Sports"));
        assertNull(g.match(null, null, "BBC Two"));
        assertNull(g.match(null, null, null));
    }

    @Test public void nameKeysIgnoreQualityAndPunctuation() {
        assertEquals("bbc one", Guide.key("BBC-One (HD)"));
        assertEquals("canal plus", Guide.key("Canal+ Plus"));
        assertEquals("tele 5", Guide.key("Télé 5 TV"));
    }
}
