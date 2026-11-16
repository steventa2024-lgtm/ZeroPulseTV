package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TabSetTest {

    @Test public void openPlacesAfterActiveAndActivates() {
        TabSet s = new TabSet();
        TabSet.Tab a = s.open("https://a");
        TabSet.Tab b = s.open("https://b");
        s.select(a.id);
        TabSet.Tab c = s.open("https://c");
        assertSame(c, s.active());
        assertEquals("a c b", order(s));
    }

    @Test public void closeActivatesRightNeighbourThenLeft() {
        TabSet s = new TabSet();
        TabSet.Tab a = s.open("a"), b = s.open("b"), c = s.open("c");
        s.select(b.id);
        assertTrue(s.close(b.id));
        assertSame(c, s.active());
        assertTrue(s.close(c.id));
        assertSame(a, s.active());
        assertTrue(s.close(a.id));
        assertNull(s.active());
        assertEquals(-1, s.activeIndex());
        assertFalse(s.close(42));
    }

    @Test public void closingAnInactiveTabKeepsTheActiveOne() {
        TabSet s = new TabSet();
        TabSet.Tab a = s.open("a"), b = s.open("b"), c = s.open("c");
        s.close(a.id);
        assertSame(c, s.active());
        assertEquals(1, s.activeIndex());
        s.select(b.id);
        s.close(c.id);
        assertSame(b, s.active());
    }

    @Test public void moveReordersAndKeepsActive() {
        TabSet s = new TabSet();
        TabSet.Tab a = s.open("a"), b = s.open("b"), c = s.open("c");
        s.select(a.id);
        assertTrue(s.move(c.id, -1));
        assertEquals("a c b", order(s));
        assertSame(a, s.active());
        assertFalse(s.move(a.id, -1));
        assertFalse(s.move(b.id, 1));
    }

    @Test public void tabLimitIsEnforced() {
        TabSet s = new TabSet();
        for (int i = 0; i < TabSet.MAX_TABS; i++) s.open("t" + i);
        assertNull(s.open("one too many"));
        assertEquals(TabSet.MAX_TABS, s.size());
        s.closeAll();
        assertEquals(0, s.size());
        assertNull(s.active());
    }

    private static String order(TabSet s) {
        StringBuilder b = new StringBuilder();
        for (TabSet.Tab t : s.all()) b.append(b.length() > 0 ? " " : "").append(t.url.replace("https://", ""));
        return b.toString();
    }
}
