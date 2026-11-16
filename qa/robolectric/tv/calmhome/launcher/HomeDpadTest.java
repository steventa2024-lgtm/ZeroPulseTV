package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.screens.WallpapersPanel;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;

/**
 * The D-pad defects found on the TCL with 3005, as tests: Left and Right stay within the current horizontal region (Left on Home
 * did not jump to Netflix, Right on Settings did not jump into the favorites, Right on the last quick action did not jump to the
 * eighth favorite); Up and Down are predictable between regions; the Wallpapers screen is navigable from the side, and a value
 * changes only after OK; focus comes back to the tile that launched an app; the page dot does not follow the remote.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class HomeDpadTest {

    private static final int LEFT = View.FOCUS_LEFT, RIGHT = View.FOCUS_RIGHT, UP = View.FOCUS_UP, DOWN = View.FOCUS_DOWN;
    private static final String[] RAIL = {"Home", "Apps", "Downloads", "Inputs", "Settings"};
    private static final String[] ACTIONS = {"All Apps", "Downloads", "Wallpapers", "Inputs"};
    private static final String EIGHTH = "com.extra.eighth";

    private boolean installed;

    /** Home with the seven default favorites, or those and an eighth (the row then scrolls). */
    private MainActivity home(boolean eight) throws Exception {
        if (!installed) {
            for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
            MockTvApps.install(EIGHTH, "Eighth", true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
            installed = true;
        }
        List<String> dock = new ArrayList<>();
        for (String[] app : MockTvApps.DEFAULTS) dock.add(app[0]);
        if (eight) dock.add(EIGHTH);
        new Prefs(HomeTestSupport.app()).setDock(dock);
        MainActivity home = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(home, 2);
        return home;
    }

    private static GlassTopNavView rail(MainActivity home) {
        return HomeTestSupport.all(home, GlassTopNavView.class).get(0);
    }

    private static String label(MainActivity home) {
        View f = HomeTestSupport.focus(home);
        if (f == null) return null;
        return f.getContentDescription() == null ? f.getClass().getSimpleName() : f.getContentDescription().toString();
    }

    private static boolean inRail(MainActivity home) {
        for (View v = HomeTestSupport.focus(home); v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v instanceof GlassTopNavView) return true;
        return false;
    }

    private static String tag(MainActivity home) {
        View f = HomeTestSupport.focus(home);
        return f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
    }

    // ------------------------------------------------------------------ Left and Right stay in the region

    @Test public void leftOnTheFirstDestinationAndRightOnTheLastStayOnTheRail() throws Exception {
        MainActivity home = home(true);
        assertTrue(rail(home).requestFocusOn("Home"));
        assertEquals("Home", label(home));
        assertFalse("Left on Home goes nowhere (3005 jumped to Netflix)", HomeTestSupport.dpad(home, LEFT));
        assertEquals("Home", label(home));
        for (int i = 1; i < RAIL.length; i++) {
            assertTrue("Right to " + RAIL[i], HomeTestSupport.dpad(home, RIGHT));
            assertEquals(RAIL[i], label(home));
        }
        assertFalse("Right on Settings goes nowhere (3005 jumped into the favorites)", HomeTestSupport.dpad(home, RIGHT));
        assertEquals("Settings", label(home));
        for (int i = RAIL.length - 2; i >= 0; i--) {
            assertTrue(HomeTestSupport.dpad(home, LEFT));
            assertEquals(RAIL[i], label(home));
        }
    }

    @Test public void theRailHasExactlyFiveDestinationsAndInputsIsNextToSettings() throws Exception {
        MainActivity home = home(false);
        for (String tab : RAIL) assertTrue(tab, rail(home).requestFocusOn(tab));
        assertFalse("no Assistant destination", rail(home).requestFocusOn("Assistant"));
        assertTrue(rail(home).requestFocusOn("Inputs"));
        assertTrue(HomeTestSupport.dpad(home, RIGHT));
        assertEquals("the rail is Home, Apps, Downloads, Inputs, Settings", "Settings", label(home));
        assertFalse(HomeTestSupport.dpad(home, RIGHT));
        assertEquals("Settings", label(home));
    }

    @Test public void leftOnTheFirstQuickActionAndRightOnTheLastStayOnTheActionsWhateverTheRowBeside() throws Exception {
        for (boolean eight : new boolean[]{false, true}) {
            MainActivity home = home(eight);
            List<ZeroPulseGlassQuickAction> actions = HomeTestSupport.glassActions(home);
            assertEquals("four quick actions: All Apps, Downloads, Wallpapers, Inputs", 4, actions.size());
            assertTrue(actions.get(0).requestFocus());
            assertFalse("Left on the first action goes nowhere (" + (eight ? "eight" : "seven") + " favorites)", HomeTestSupport.dpad(home, LEFT));
            assertEquals(ACTIONS[0], label(home));
            for (int i = 1; i < ACTIONS.length; i++) {
                assertTrue(HomeTestSupport.dpad(home, RIGHT));
                assertEquals(ACTIONS[i], label(home));
            }
            assertFalse("Right on the last action goes nowhere, not to a favorite (3005 jumped to the eighth)", HomeTestSupport.dpad(home, RIGHT));
            assertEquals(ACTIONS[ACTIONS.length - 1], label(home));
            assertTrue("it is still an action, not a tile", HomeTestSupport.focus(home) instanceof ZeroPulseGlassQuickAction);
        }
    }

    @Test public void theFourQuickActionsShareTheRowEvenly() throws Exception {
        MainActivity home = home(false);
        List<ZeroPulseGlassQuickAction> actions = HomeTestSupport.glassActions(home);
        assertEquals(4, actions.size());
        int w = actions.get(0).getWidth();
        assertTrue(w > 0);
        for (ZeroPulseGlassQuickAction a : actions) {
            assertEquals("every action is shown", View.VISIBLE, a.getVisibility());
            assertTrue("equal widths (within a pixel of rounding)", Math.abs(a.getWidth() - w) <= 1);
        }
        ZeroPulseGlassQuickAction last = actions.get(3);
        assertEquals("Inputs", String.valueOf(last.getContentDescription()));
        assertTrue(last.requestFocus());
        assertFalse(HomeTestSupport.dpad(home, RIGHT));
        assertSame(last, HomeTestSupport.focus(home));
    }

    @Test public void theTileRowStopsAtItsEndsWithSevenAndWithEightFavorites() throws Exception {
        for (boolean eight : new boolean[]{false, true}) {
            MainActivity home = home(eight);
            int count = eight ? 8 : 7;
            List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
            assertEquals(count, tiles.size());
            assertEquals("the row starts on its first tile", MockTvApps.DEFAULTS[0][0], tag(home));
            assertFalse("Left on the first tile goes nowhere", HomeTestSupport.dpad(home, LEFT));
            for (int i = 1; i < count; i++) assertTrue(HomeTestSupport.dpad(home, RIGHT));
            assertEquals("the last tile", eight ? EIGHTH : MockTvApps.DEFAULTS[6][0], tag(home));
            assertFalse("Right on the last tile goes nowhere", HomeTestSupport.dpad(home, RIGHT));
            for (int i = 1; i < count; i++) assertTrue(HomeTestSupport.dpad(home, LEFT));
            assertEquals(MockTvApps.DEFAULTS[0][0], tag(home));
        }
    }

    // ------------------------------------------------------------------ Up and Down between regions

    @Test public void upAndDownBetweenTheRailAndTheTilesAreByPositionAndComeBack() throws Exception {
        MainActivity home = home(false);
        for (String destination : RAIL) {
            assertTrue(rail(home).requestFocusOn(destination));
            assertTrue("Down from " + destination + " reaches the tiles", HomeTestSupport.dpad(home, DOWN));
            assertTrue(HomeTestSupport.focus(home) instanceof ZeroPulseGlassAppTile);
            String landed = tag(home);
            assertTrue("and Up returns to the rail", HomeTestSupport.dpad(home, UP));
            assertTrue("on one of the rail's buttons", inRail(home));
            assertTrue(rail(home).requestFocusOn(destination));
            assertTrue(HomeTestSupport.dpad(home, DOWN));
            assertEquals("the same press from the same place lands on the same tile every time", landed, tag(home));
        }
    }

    @Test public void downFromATileAndUpFromAnActionMeetOnTheSameColumn() throws Exception {
        MainActivity home = home(false);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        for (ZeroPulseGlassAppTile tile : tiles) {
            assertTrue(tile.requestFocus());
            assertTrue(HomeTestSupport.dpad(home, DOWN));
            View action = HomeTestSupport.focus(home);
            assertTrue("a tile's Down is a quick action", action instanceof ZeroPulseGlassQuickAction);
            int[] t = new int[2], a = new int[2];
            tile.getLocationOnScreen(t);
            action.getLocationOnScreen(a);
            float tileCenter = t[0] + tile.getWidth() / 2f, actionCenter = a[0] + action.getWidth() / 2f;
            float nearest = Float.MAX_VALUE;
            for (ZeroPulseGlassQuickAction other : HomeTestSupport.glassActions(home)) {
                int[] o = new int[2];
                other.getLocationOnScreen(o);
                nearest = Math.min(nearest, Math.abs(o[0] + other.getWidth() / 2f - tileCenter));
            }
            assertTrue("Down lands on the action nearest the tile's centre (" + Math.abs(actionCenter - tileCenter) + " against " + nearest + ")",
                    Math.abs(actionCenter - tileCenter) <= nearest + action.getWidth() * 0.6f);
        }
    }

    // ------------------------------------------------------------------ the page dot is not the focus

    @Test public void theSelectedPageDotStaysWhereItIsWhileFocusMovesAcrossTheRail() throws Exception {
        MainActivity home = home(false);
        GlassTopNavView rail = rail(home);
        assertEquals("Home is the page that is open", "Home", rail.getSelectedId());
        for (String destination : RAIL) {
            assertTrue(rail.requestFocusOn(destination));
            assertEquals("focus on " + destination + " does not move the dot", "Home", rail.getSelectedId());
        }
        assertTrue(HomeTestSupport.dpad(home, DOWN));
        assertEquals("nor does leaving the rail", "Home", rail.getSelectedId());
    }

    // ------------------------------------------------------------------ coming back from an app

    @Test public void focusComesBackToTheTileThatLaunchedTheApp() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        new Prefs(HomeTestSupport.app()).setLiquidGlass(true);
        new Prefs(HomeTestSupport.app()).setLiquidStarting(false);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, new Intent(HomeTestSupport.app(), MainActivity.class)).setup();
        MainActivity home = controller.get();
        HomeTestSupport.leaveTouchMode(home.getWindow().getDecorView());
        controller.pause().resume();
        HomeTestSupport.layoutTv(home);
        ShadowLooper.idleMainLooper();
        for (int i = 0; i < 3; i++) assertTrue(HomeTestSupport.dpad(home, RIGHT));
        assertEquals(MockTvApps.DEFAULTS[3][0], tag(home));
        HomeTestSupport.press(home, KeyEvent.KEYCODE_DPAD_CENTER);
        assertNotNull("OK launched the app", org.robolectric.Shadows.shadowOf(HomeTestSupport.app()).getNextStartedActivity());

        controller.pause().stop();                                       // the app is in front
        controller.restart().resume();                                   // Back: Home returns
        HomeTestSupport.layoutTv(home);
        ShadowLooper.idleMainLooper();
        assertEquals("focus is on the same tile, not the first", MockTvApps.DEFAULTS[3][0], tag(home));
    }

    // ------------------------------------------------------------------ the Wallpapers screen

    private static View row(MainActivity home, String startsWith) {
        View v = HomeTestSupport.withDescription(home, startsWith);
        assertNotNull(startsWith, v);
        return v;
    }

    private static String value(View row) {
        String d = String.valueOf(row.getContentDescription());
        return d.substring(d.indexOf(", ") + 2).replace(", changing: Left and Right change it, OK keeps it", "");
    }

    private static boolean changing(View row) {
        return String.valueOf(row.getContentDescription()).contains("changing");
    }

    private MainActivity wallpapers() throws Exception {
        MainActivity home = home(false);
        home.open(new WallpapersPanel(home));
        HomeTestSupport.pump(home, 4);
        return home;
    }

    private static KeyEvent key(int action, int code) {
        return new KeyEvent(action, code);
    }

    @Test public void leftAndRightOnAControlNeverChangeItsValueAndOkStartsAChange() throws Exception {
        MainActivity home = wallpapers();
        Prefs prefs = new Prefs(HomeTestSupport.app());
        View dim = row(home, "Dim overlay");
        assertTrue(dim.requestFocus());
        int before = prefs.dimPercent();
        String shown = value(dim);

        assertFalse("not changing: Right is navigation, the row does not take it", dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertFalse(dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertEquals(before, prefs.dimPercent());
        assertTrue("Right moves focus to the next control", HomeTestSupport.dpad(home, RIGHT));
        assertFalse(HomeTestSupport.focus(home) == dim);
        assertTrue("and Left comes back", HomeTestSupport.dpad(home, LEFT));
        assertSame(dim, HomeTestSupport.focus(home));
        assertEquals("the value never moved while focus went across and back", shown, value(dim));
        assertEquals(before, prefs.dimPercent());

        dim.performClick();                                              // OK
        assertTrue("OK starts a change", changing(dim));
        assertTrue("now Right steps the value", dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertEquals(before + 10, prefs.dimPercent());
        assertTrue(dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertTrue(dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertEquals(before - 10, prefs.dimPercent());
        dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
        assertFalse("Down keeps the value and ends the change", changing(dim));
        assertEquals(before - 10, prefs.dimPercent());
        assertFalse("and the next Right is navigation again", dim.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertEquals(before - 10, prefs.dimPercent());
    }

    @Test public void backEndsAChangeBeforeItClosesAnything() throws Exception {
        MainActivity home = wallpapers();
        View zoom = row(home, "Zoom");
        assertTrue(zoom.requestFocus());
        zoom.performClick();
        assertTrue(changing(zoom));
        home.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertFalse("Back ended the change", changing(zoom));
        assertTrue("and the Wallpapers screen is still open", HomeTestSupport.hasText(home, "Framing for"));
        home.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertFalse("a second Back closes the screen", HomeTestSupport.hasText(home, "Framing for"));
    }

    @Test public void movingAwayFromARowEndsItsChangeAndKeepsTheValue() throws Exception {
        MainActivity home = wallpapers();
        Prefs prefs = new Prefs(HomeTestSupport.app());
        View interval = row(home, "Rotation interval");
        assertTrue(interval.requestFocus());
        interval.performClick();
        int before = prefs.wallpaperInterval();
        interval.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        int changed = prefs.wallpaperInterval();
        assertTrue("the step applied", changed != before);
        row(home, "Dim overlay").requestFocus();
        assertFalse("focus moved on: the change is over", changing(interval));
        assertEquals("and it kept its value", changed, prefs.wallpaperInterval());
    }

    @Test public void everyWallpaperControlIsReachableFromTheSideWithoutGoingThroughThePreviews() throws Exception {
        MainActivity home = wallpapers();
        String[] controls = {"Rotation interval", "Dim overlay", "Information", "Zoom", "Focus across", "Focus down"};
        View start = row(home, "Rotation interval");
        assertTrue(start.requestFocus());
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        reached.add("Rotation interval");
        queue.add("Rotation interval");
        int[] directions = {RIGHT, LEFT, DOWN, UP};
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (int direction : directions) {
                assertTrue(row(home, at).requestFocus());
                if (!HomeTestSupport.dpad(home, direction)) continue;
                View now = HomeTestSupport.focus(home);
                String name = null;
                for (String control : controls) if (String.valueOf(now.getContentDescription()).startsWith(control)) name = control;
                if (name != null && reached.add(name)) queue.add(name);                 // only moves between the controls count: a move into a preview or tab is not followed
            }
        }
        assertEquals("all six controls are reached by Left, Right, Up and Down among themselves: " + reached, new LinkedHashSet<>(Arrays.asList(controls)), reached);
    }
}
