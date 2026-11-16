package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;

/**
 * The remote on Liquid Glass Home: Left and Right along the tiles and the quick actions, Up and Down between them and into
 * the navigation, OK launching or opening, Back, focus coming back where it was, and no direction that leaves focus nowhere.
 * D-pad moves are the window's own focus search ({@link HomeTestSupport#dpad}); OK and Back are real key events.
 * Needs Robolectric (see docs/LIQUID_GLASS.md).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class HomeRemoteTest {

    private static final int LEFT = View.FOCUS_LEFT, RIGHT = View.FOCUS_RIGHT, UP = View.FOCUS_UP, DOWN = View.FOCUS_DOWN;
    private static final String[] ACTIONS = {"All Apps", "Downloads", "Wallpapers", "Inputs"};

    private static MainActivity home() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MainActivity home = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(home, 2);
        return home;
    }

    private static String tag(MainActivity home) {
        View f = HomeTestSupport.focus(home);
        return f == null ? null : f.getTag() instanceof String ? (String) f.getTag() : null;
    }

    private static boolean inNav(MainActivity home) {
        for (View v = HomeTestSupport.focus(home); v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v instanceof GlassTopNavView) return true;
        return false;
    }

    private static String actionLabel(MainActivity home) {
        View f = HomeTestSupport.focus(home);
        return f instanceof ZeroPulseGlassQuickAction ? String.valueOf(f.getContentDescription()) : null;
    }

    @Test public void homeStartsWithFocusOnTheFirstTile() throws Exception {
        MainActivity home = home();
        assertTrue(HomeTestSupport.focus(home) instanceof ZeroPulseGlassAppTile);
        assertEquals(MockTvApps.DEFAULTS[0][0], tag(home));
    }

    @Test public void rightAndLeftWalkTheSevenTilesAndStopAtTheEnds() throws Exception {
        MainActivity home = home();
        for (int i = 1; i < 7; i++) {
            assertTrue("Right to tile " + i, HomeTestSupport.dpad(home, RIGHT));
            assertEquals(MockTvApps.DEFAULTS[i][0], tag(home));
        }
        assertFalse("Right at the last tile goes nowhere (no trap, no escape to the buttons)", HomeTestSupport.dpad(home, RIGHT));
        assertEquals(MockTvApps.DEFAULTS[6][0], tag(home));
        for (int i = 5; i >= 0; i--) {
            assertTrue(HomeTestSupport.dpad(home, LEFT));
            assertEquals(MockTvApps.DEFAULTS[i][0], tag(home));
        }
        assertFalse("Left at the first tile goes nowhere", HomeTestSupport.dpad(home, LEFT));
        assertEquals(MockTvApps.DEFAULTS[0][0], tag(home));
    }

    @Test public void downReachesTheQuickActionsWhichWalkLeftAndRightInOrder() throws Exception {
        MainActivity home = home();
        assertTrue(HomeTestSupport.dpad(home, DOWN));
        assertTrue("Down from the tiles lands on a quick action", HomeTestSupport.focus(home) instanceof ZeroPulseGlassQuickAction);
        // From the first tile the nearest action below is the first one.
        assertEquals(ACTIONS[0], actionLabel(home));
        for (int i = 1; i < ACTIONS.length; i++) {
            assertTrue(HomeTestSupport.dpad(home, RIGHT));
            assertEquals(ACTIONS[i], actionLabel(home));
        }
        assertFalse("Right at the last action goes nowhere", HomeTestSupport.dpad(home, RIGHT));
        assertEquals(ACTIONS[ACTIONS.length - 1], actionLabel(home));
        for (int i = ACTIONS.length - 2; i >= 0; i--) {
            assertTrue(HomeTestSupport.dpad(home, LEFT));
            assertEquals(ACTIONS[i], actionLabel(home));
        }
    }

    @Test public void upFromAnActionReturnsToATileAndUpFromTheTilesGoesIntoTheNavigation() throws Exception {
        MainActivity home = home();
        assertTrue(HomeTestSupport.dpad(home, DOWN));
        assertTrue(HomeTestSupport.dpad(home, UP));
        assertTrue("back on a tile", HomeTestSupport.focus(home) instanceof ZeroPulseGlassAppTile);
        assertTrue("Up from the tiles reaches the navigation", HomeTestSupport.dpad(home, UP));
        assertTrue(inNav(home));
        assertTrue("Down from the navigation returns to the tiles", HomeTestSupport.dpad(home, DOWN));
        assertTrue(HomeTestSupport.focus(home) instanceof ZeroPulseGlassAppTile);
    }

    @Test public void okOnATileLaunchesItsAppAndOkOnAnActionOpensItsScreen() throws Exception {
        MainActivity home = home();
        assertTrue(HomeTestSupport.dpad(home, RIGHT));
        HomeTestSupport.press(home, KeyEvent.KEYCODE_DPAD_CENTER);
        Intent started = shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        assertNotNull("OK launched the focused app", started);
        assertEquals(MockTvApps.DEFAULTS[1][0], started.getComponent().getPackageName());

        // Four actions share the row, so the one below the second tile is the first action, All Apps.
        assertTrue(HomeTestSupport.dpad(home, DOWN));
        assertEquals(ACTIONS[0], actionLabel(home));
        ViewGroup content = (ViewGroup) home.findViewById(android.R.id.content);
        int before = ((ViewGroup) content.getChildAt(0)).getChildCount();
        HomeTestSupport.press(home, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue("OK on All Apps opened the apps screen", ((ViewGroup) content.getChildAt(0)).getChildCount() > before);
    }

    @Test public void backClosesAScreenAndFocusReturnsToTheQuickActionThatOpenedIt() throws Exception {
        MainActivity home = home();
        assertTrue(HomeTestSupport.dpad(home, DOWN));
        View opener = HomeTestSupport.focus(home);
        assertEquals(ACTIONS[0], actionLabel(home));
        ViewGroup content = (ViewGroup) home.findViewById(android.R.id.content);
        int before = ((ViewGroup) content.getChildAt(0)).getChildCount();
        HomeTestSupport.press(home, KeyEvent.KEYCODE_DPAD_CENTER);
        ShadowLooper.idleMainLooper();
        assertTrue(((ViewGroup) content.getChildAt(0)).getChildCount() > before);
        home.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertEquals("the screen is closed", before, ((ViewGroup) content.getChildAt(0)).getChildCount());
        assertSame("and focus is back on the button that opened it", opener, HomeTestSupport.focus(home));
        assertFalse("Back on Home itself does nothing", home.isFinishing());
        home.onBackPressed();
        assertFalse(home.isFinishing());
    }

    @Test public void focusFollowsAHomeRebuildWhenAnAppIsInstalled() throws Exception {
        MainActivity home = home();
        for (int i = 0; i < 3; i++) HomeTestSupport.dpad(home, RIGHT);
        assertEquals(MockTvApps.DEFAULTS[3][0], tag(home));
        MockTvApps.install("com.new.app", "Brand New", true);
        MockTvApps.broadcast(Intent.ACTION_PACKAGE_ADDED, "com.new.app");
        assertEquals("the row was rebuilt but focus stayed on the same app", MockTvApps.DEFAULTS[3][0], tag(home));
    }

    @Test public void noKeyEverLeavesFocusNowhere() throws Exception {
        MainActivity home = home();
        int[] walk = {RIGHT, RIGHT, DOWN, RIGHT, UP, UP, LEFT, DOWN, DOWN, LEFT, LEFT, UP, RIGHT, UP, UP, DOWN, DOWN, DOWN, RIGHT, RIGHT,
                RIGHT, RIGHT, UP, LEFT, LEFT, LEFT, LEFT, LEFT, LEFT, LEFT, DOWN, UP, UP, UP, RIGHT, LEFT, DOWN, DOWN};
        List<String> trail = new ArrayList<>();
        for (int direction : walk) {
            HomeTestSupport.dpad(home, direction);
            assertNotNull("focus survived " + Arrays.toString(trail.toArray()), HomeTestSupport.focus(home));
            trail.add(direction + ":" + HomeTestSupport.focusedLabel(home));
        }
        // And from wherever it ended, a few presses bring it back to the first tile: nothing is a dead end.
        for (int i = 0; i < 6; i++) HomeTestSupport.dpad(home, UP);
        for (int i = 0; i < 8; i++) HomeTestSupport.dpad(home, LEFT);
        for (int i = 0; i < 8; i++) HomeTestSupport.dpad(home, DOWN);
        assertNotNull(HomeTestSupport.focus(home));
    }

    @Test public void theGraphiteHomeStillHasTheSameRemoteStructure() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true);
        MainActivity home = HomeTestSupport.startHome(false);
        assertEquals(MockTvApps.DEFAULTS[0][0], tag(home));
        for (int i = 1; i < 7; i++) {
            assertTrue(HomeTestSupport.dpad(home, RIGHT));
            assertEquals(MockTvApps.DEFAULTS[i][0], tag(home));
        }
        assertFalse(HomeTestSupport.dpad(home, RIGHT));
        assertNull("graphite has no glass quick actions", HomeTestSupport.glassActions(home).isEmpty() ? null : "found");
    }
}
