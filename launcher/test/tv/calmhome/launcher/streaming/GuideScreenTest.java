package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.M3uParser;
import tv.calmhome.launcher.data.Prefs;

@RunWith(RobolectricTestRunner.class)
public class GuideScreenTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private StreamingActivity setUp(boolean withGuide) throws Exception {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingStore s = new StreamingStore(c);
        long src = s.addSource(StreamingStore.KIND_URL, "https://lists.example.org/tv.m3u", "lists.example.org");
        s.replaceChannels(src, M3uParser.parse("#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"news.example\",World News\nhttps://s.example.org/news.m3u8\n"
                + "#EXTINF:-1,Sport One HD\nhttps://s.example.org/sport.m3u8\n"
                + "#EXTINF:-1,No Guide Channel\nhttps://s.example.org/other.m3u8\n").entries, "3 streams");
        if (withGuide) {
            GuideData g = new GuideData(s);
            long id = g.addSource(StreamingStore.KIND_URL, "https://guide.example.org/g.xml", "guide.example.org");
            try (GuideData.Writer w = g.writer(id)) {
                XmltvReader.Stats st = XmltvReader.read(new ByteArrayInputStream(
                        GuideDataTest.xmltv(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)), w, System.currentTimeMillis());
                w.commit(st.summary(), st.unzoned);
            }
        }
        s.close();
        return Robolectric.buildActivity(StreamingActivity.class).setup().get();
    }

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    private static View find(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return out.isEmpty() ? null : out.get(0);
    }

    private static ListView list(View v) {
        if (v instanceof ListView) return (ListView) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            ListView l = list(((ViewGroup) v).getChildAt(i));
            if (l != null) return l;
        }
        return null;
    }

    /** Renders adapter rows (a ListView in a test has no real layout pass). */
    private static String rows(ListView l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.getAdapter().getCount(); i++) b.append(allText(l.getAdapter().getView(i, null, l)));
        return b.toString();
    }

    @Test public void liveTvCardsShowWhatIsOnNow() throws Exception {
        StreamingActivity a = setUp(true);
        android.widget.GridView grid = null;
        ArrayList<View> all = new ArrayList<>();
        a.getWindow().getDecorView().findViewsWithText(all, "World News", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        for (View v : all) if (v.getParent() instanceof android.widget.GridView) grid = (android.widget.GridView) v.getParent();
        View root = a.getWindow().getDecorView();
        if (grid == null) {
            // Find the grid directly.
            java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
            q.add(root);
            while (!q.isEmpty()) {
                View v = q.poll();
                if (v instanceof android.widget.GridView) grid = (android.widget.GridView) v;
                if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) q.add(((ViewGroup) v).getChildAt(i));
            }
        }
        assertNotNull(grid);
        String cards = allText(grid.getAdapter().getView(0, null, grid)) + allText(grid.getAdapter().getView(1, null, grid));
        assertTrue(cards, cards.contains("Now: Evening News"));
        assertTrue("matched by name when tvg-id is missing", cards.contains("Now: Cup Final"));
    }

    @Test public void guideListsMatchedChannelsAndSaysWhatIsMissing() throws Exception {
        StreamingActivity a = setUp(true);
        View root = a.getWindow().getDecorView();
        find(root, "Guide").performClick();
        String text = allText(root);
        assertTrue(text.contains("1 of your channels have no guide data"));
        ListView l = list(root);
        assertEquals(2, l.getAdapter().getCount());
        String r = rows(l);
        assertTrue(r.contains("Evening News"));
        assertTrue(r.contains("Next"));
    }

    /** A day chip rebuilds the chip row; the guide must stay on screen with focus on the chosen day (device bug). */
    @Test public void dayChipKeepsTheGuideAndItsFocus() throws Exception {
        StreamingActivity a = setUp(true);
        View root = a.getWindow().getDecorView();
        find(root, "Live TV").requestFocus();
        find(root, "Guide").requestFocus();                 // one step down the rail: Guide page
        for (int i = 0; i < 2; i++) {
            View today = find(root, "Today");
            assertNotNull(today);
            assertTrue(today.requestFocus());
            today.performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(allText(root), allText(root).contains("Showing "));
            View f = root.findFocus();
            assertNotNull(f);
            assertEquals("Today", String.valueOf(f.getContentDescription()));
            assertTrue("the old chip is gone, the new one has focus", f.isAttachedToWindow());
        }
    }

    /** Coming back from the player rebuilds the page; it must stay the page the viewer left (device bug). */
    @Test public void returningFromThePlayerKeepsTheGuide() throws Exception {
        setUp(true);
        org.robolectric.android.controller.ActivityController<StreamingActivity> ac =
                Robolectric.buildActivity(StreamingActivity.class).setup();
        StreamingActivity a = ac.get();
        View root = a.getWindow().getDecorView();
        find(root, "Live TV").requestFocus();
        find(root, "Guide").requestFocus();
        ListView l = list(root);
        assertTrue(l.requestFocus());
        l.performItemClick(null, 0, 0);                     // details card for the channel on now, Watch focused
        View watch = root.findFocus();
        assertNotNull(watch);
        assertTrue(String.valueOf(watch.getContentDescription()), String.valueOf(watch.getContentDescription()).startsWith("Watch"));
        ac.pause().stop();
        ac.restart().start().resume();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(allText(root), allText(root).contains("Showing "));
        assertTrue("focus waits on the Guide rail item", find(root, "Guide").isFocused());
    }

    /** A Sports category chip filters the list and keeps focus on itself (device bug: focus went to the rail). */
    @Test public void sportsChipKeepsItsFocus() throws Exception {
        StreamingActivity a = setUp(true);
        View root = a.getWindow().getDecorView();
        find(root, "Sports").performClick();
        View soccer = find(root, "Soccer");
        assertNotNull(soccer);
        assertTrue(soccer.requestFocus());
        soccer.performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        View f = root.findFocus();
        assertNotNull(f);
        assertEquals("Soccer", String.valueOf(f.getContentDescription()));
        assertTrue(f.isAttachedToWindow());
        String r = rows(list(root));
        assertTrue(r, r.contains("Cup Final"));
        assertTrue(r, !r.contains("Grand Prix"));
    }

    @Test public void guideWithoutDataExplainsHowToAddOne() throws Exception {
        StreamingActivity a = setUp(false);
        View root = a.getWindow().getDecorView();
        find(root, "Guide").performClick();
        assertTrue(allText(root).contains("No guide yet"));
    }

    @Test public void sportsComesFromGuideCategoriesAndLiveEventsOpenTheChannel() throws Exception {
        StreamingActivity a = setUp(true);
        View root = a.getWindow().getDecorView();
        find(root, "Sports").performClick();
        ListView l = list(root);
        assertEquals("news is not sport; football and motor sport are", 2, l.getAdapter().getCount());
        String r = rows(l);
        assertTrue(r.contains("● Live"));
        assertTrue(r.contains("Cup Final · Reds v Blues"));
        assertTrue(r.contains("Motorsport"));
        assertTrue(allText(root).contains("Soccer"));
        l.performItemClick(null, 0, 0);
        assertEquals(PlayerActivity.class.getName(), shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }

    @Test public void sportsWithoutGuideSaysNothingIsInvented() throws Exception {
        StreamingActivity a = setUp(false);
        View root = a.getWindow().getDecorView();
        find(root, "Sports").performClick();
        String text = allText(root);
        assertTrue(text.contains("No sports schedule"));
        assertTrue(text.contains("doesn't add schedules"));
    }
}
