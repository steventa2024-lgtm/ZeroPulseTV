package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.screens.SettingsPanel;
import tv.calmhome.launcher.screens.WallpapersPanel;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Safe margins and clipping on the TCL's 1920 x 1080 (96 px at the sides, 54 px above and below):
 * with eight favorites the focused eighth tile, its highlight and its label settle inside the margin (3005 left them flush with the
 * screen edge at x = 1918); Settings scrolls so the focused row is whole with its neighbours in view; and the Wallpapers
 * screen keeps its framing caption clear of the Zoom card and everything inside the bottom margin.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class HomeSafeMarginsTest {

    private static final int SAFE_X = 96, SAFE_Y = 54, W = 1920, H = 1080, SLACK = 2;
    private static final String EIGHTH = "com.extra.eighth";

    private static void installEight() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MockTvApps.install(EIGHTH, "Eighth", true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        List<String> dock = new ArrayList<>();
        for (String[] app : MockTvApps.DEFAULTS) dock.add(app[0]);
        dock.add(EIGHTH);
        new Prefs(HomeTestSupport.app()).setDock(dock);
    }

    private static MainActivity home() throws Exception {
        installEight();
        MainActivity home = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(home, 2);
        return home;
    }

    /** Lets a focus scroll finish: the scroller's animation is stepped as a frame would. */
    private static void settle(View scroller) {
        for (int i = 0; i < 20; i++) {
            ShadowLooper.idleMainLooper(40, TimeUnit.MILLISECONDS);
            if (scroller instanceof ScrollView) ((ScrollView) scroller).computeScroll();
            else if (scroller instanceof HorizontalScrollView) ((HorizontalScrollView) scroller).computeScroll();
        }
    }

    private static HorizontalScrollView dockScroll(MainActivity home) {
        for (HorizontalScrollView s : HomeTestSupport.all(home, HorizontalScrollView.class)) if (s.getChildAt(0) instanceof android.widget.LinearLayout
                && ((ViewGroup) s.getChildAt(0)).getChildAt(0) instanceof ViewGroup && HomeTestSupport.glassTiles(home).contains(
                        ((ViewGroup) ((ViewGroup) s.getChildAt(0)).getChildAt(0)).getChildAt(0))) return s;
        throw new AssertionError("Home's app row");
    }

    private static int[] at(View v) {
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        return p;
    }

    // ------------------------------------------------------------------ the eighth favorite

    @Test public void theEighthTileItsHighlightAndItsLabelSettleInsideTheSafeMargins() throws Exception {
        MainActivity home = home();
        HorizontalScrollView scroll = dockScroll(home);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        assertEquals(8, tiles.size());
        ZeroPulseGlassAppTile eighth = tiles.get(7);
        assertTrue(eighth.requestFocus());
        settle(scroll);
        int[] p = at(eighth);
        assertTrue("the highlight's right edge is inside the margin: " + (p[0] + eighth.getWidth()), p[0] + eighth.getWidth() <= W - SAFE_X + SLACK);
        assertTrue("and its left edge is still on screen: " + p[0], p[0] >= SAFE_X - SLACK);
        TextView label = (TextView) ((ViewGroup) eighth.getParent()).getChildAt(1);
        int[] l = at(label);
        assertTrue("the label's right edge: " + (l[0] + label.getWidth()), l[0] + label.getWidth() <= W - SAFE_X + SLACK);
        assertTrue("the label is above the bottom margin", l[1] + label.getHeight() <= H - SAFE_Y + SLACK);
        // The swell is on the art inside the glass, so the glass itself (the highlight) is what the margin is measured on.
        assertEquals(1f, eighth.getScaleX(), 0f);
    }

    @Test public void everyTileSettlesInsideTheMarginWhenFocusedAndTheRowComesBackToTheStart() throws Exception {
        MainActivity home = home();
        HorizontalScrollView scroll = dockScroll(home);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        for (ZeroPulseGlassAppTile tile : tiles) {
            assertTrue(tile.requestFocus());
            settle(scroll);
            int[] p = at(tile);
            assertTrue(tile.getContentDescription() + " left " + p[0], p[0] >= SAFE_X - SLACK);
            assertTrue(tile.getContentDescription() + " right " + (p[0] + tile.getWidth()), p[0] + tile.getWidth() <= W - SAFE_X + SLACK);
        }
        assertTrue(tiles.get(0).requestFocus());
        settle(scroll);
        assertEquals("back at the first tile the row is at its start", 0, scroll.getScrollX());
        assertTrue(at(tiles.get(0))[0] >= SAFE_X - SLACK);
    }

    // ------------------------------------------------------------------ Settings scrolls to the focused row

    @Test public void settingsKeepsTheFocusedRowWholeAndItsNeighboursInViewAsFocusMovesDown() throws Exception {
        installEight();
        MainActivity home = HomeTestSupport.startHome(true);
        home.open(new SettingsPanel(home, "appearance"));
        HomeTestSupport.pump(home, 3);
        ScrollView rows = null;
        for (ScrollView s : HomeTestSupport.all(home, ScrollView.class)) rows = s;
        assertNotNull(rows);
        View first = HomeTestSupport.withDescription(home, "Liquid Glass Home");
        assertNotNull(first);
        assertTrue(first.requestFocus());
        int visited = 0;
        for (int i = 0; i < 16; i++) {
            settle(rows);
            View f = HomeTestSupport.focus(home);
            if (f == null || f.getParent() != ((ViewGroup) rows).getChildAt(0)) break;
            Rect visible = new Rect();
            assertTrue("a focused row is on screen", f.getGlobalVisibleRect(visible));
            assertEquals("row " + i + " (" + f.getContentDescription() + ") is not cut by the scroller: " + visible.height() + " of " + f.getHeight(),
                    f.getHeight(), visible.height());
            visited++;
            if (!HomeTestSupport.dpad(home, View.FOCUS_DOWN)) break;
        }
        assertTrue("the walk covered the list: " + visited, visited >= 8);
        settle(rows);
        View last = HomeTestSupport.focus(home);
        Rect pane = new Rect();
        rows.getGlobalVisibleRect(pane);
        Rect r = new Rect();
        last.getGlobalVisibleRect(r);
        assertTrue("the last row is inside the pane with room below it: " + r + " in " + pane, r.bottom <= pane.bottom);
    }

    // ------------------------------------------------------------------ the Wallpapers screen

    @Test public void theFramingCaptionIsClearOfTheZoomCardAndEverythingIsInsideTheBottomMargin() throws Exception {
        installEight();
        MainActivity home = HomeTestSupport.startHome(true);
        home.open(new WallpapersPanel(home));
        HomeTestSupport.pump(home, 4);
        View zoomRow = HomeTestSupport.withDescription(home, "Zoom");
        assertNotNull(zoomRow);
        View zoomCard = (View) zoomRow.getParent();
        TextView caption = null;
        for (TextView tv : HomeTestSupport.all(home, TextView.class)) if (tv.getText().toString().startsWith("Framing for")) caption = tv;
        assertNotNull(caption);
        int[] c = at(caption), z = at(zoomCard);
        int gap = z[1] - (c[1] + caption.getHeight());
        assertTrue("the caption has room above the Zoom card (3005: it sat against it): " + gap + " px", gap >= 16);
        for (View row : new View[]{HomeTestSupport.withDescription(home, "Zoom"), HomeTestSupport.withDescription(home, "Focus across"),
                HomeTestSupport.withDescription(home, "Focus down")}) {
            int[] p = at((View) row.getParent());
            assertTrue(row.getContentDescription() + " card ends at " + (p[1] + ((View) row.getParent()).getHeight()),
                    p[1] + ((View) row.getParent()).getHeight() <= H - SAFE_Y + SLACK);
        }
    }
}
