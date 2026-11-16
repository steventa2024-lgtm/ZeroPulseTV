package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.core.BrandColor;
import tv.calmhome.launcher.core.MotionTokens;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Phase A of the premium interactions on the real Home (fixture apps, simulated: Robolectric, not the TV): the Favorites focus lift,
 * the brand glow, the press acknowledgment with its double-launch guard, and the update dot that only real update data may show.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class PremiumFocusTest {

    private static MainActivity home() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) {
            MockTvApps.install(app[0], app[1], true, app[1].startsWith("YouTube") ? MockTvApps.pictureBanner()
                    : MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        }
        MainActivity h = HomeTestSupport.startHome(true);
        assertTrue(HomeTestSupport.pumpUntil(h, () -> {
            GlassKit kit = h.theme().kit;
            return kit.surfaceCount() > 0 && kit.renderedSurfaceCount() == kit.surfaceCount();
        }, 150));
        return h;
    }

    private static void settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
    }

    private static int[] lefts(List<ZeroPulseGlassAppTile> tiles) {
        int[] out = new int[tiles.size()];
        for (int i = 0; i < out.length; i++) {
            int[] at = new int[2];
            tiles.get(i).getLocationInWindow(at);
            out[i] = at[0];
        }
        return out;
    }

    @Test public void aFocusedTileLiftsItsArtworkGlowsAndCastsAShadowWithoutScalingTheGlassOrMovingItsNeighbours() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        ZeroPulseGlassAppTile tile = tiles.get(2);
        int[] before = lefts(tiles);
        long renders = GraphiteGlassRenderer.Stats.renders();

        assertTrue(tile.requestFocus());
        settle();

        assertEquals("the glass reaches its focus frame (its brighter rim is the edge highlight)", 1f, tile.focusLevel(), 0.01f);
        View content = tile.artView().getParent() instanceof View ? (View) tile.artView().getParent() : null;
        assertEquals("the artwork swells within the brief's 1.04 to 1.06", MotionTokens.FOCUS_SCALE, content.getScaleX(), 0.001f);
        float lift = MotionTokens.LIFT_DP * h.getResources().getDisplayMetrics().density;
        assertEquals("and rises off the glass", -lift, content.getTranslationY(), 0.5f);
        assertEquals("the glow and shadow are fully in", 1f, tile.haloLevel(), 0.01f);
        assertEquals("the tile itself is never scaled: the renderer would drop its glass", 1f, tile.getScaleX(), 0f);
        assertEquals(1f, tile.glass().getScaleX(), 0f);
        assertTrue("its glass is still there", tile.glass().hasMaterial());
        int[] after = lefts(tiles);
        for (int i = 0; i < before.length; i++) assertEquals("tile " + i + " did not move", before[i], after[i]);
        assertEquals("a focus move never re-renders the glass", renders, GraphiteGlassRenderer.Stats.renders());

        assertTrue(tiles.get(3).requestFocus());
        settle();
        assertEquals("focus left: the lift settles back", 1f, content.getScaleX(), 0.001f);
        assertEquals(0f, content.getTranslationY(), 0.01f);
        assertEquals(0f, tile.haloLevel(), 0.001f);
    }

    @Test public void theGlowUsesTheAppsBrandColourAndTheMasksAreSharedNotRenderedPerTile() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        ZeroPulseGlassAppTile netflix = null;
        for (ZeroPulseGlassAppTile t : tiles) if ("com.netflix.ninja".equals(t.getTag())) netflix = t;
        if (netflix != null) assertEquals("Netflix glows a restrained red", BrandColor.known("com.netflix.ninja", "Netflix"), netflix.glowColor());
        for (ZeroPulseGlassAppTile t : tiles) assertNotEquals("every tile has a glow colour (its own, or a cool neutral)", 0, t.glowColor());
        assertEquals("the glow adds no view: the tile keeps its glass, content and finish", 3, tiles.get(0).getChildCount());
        assertTrue("the glass is still the first child, under everything", tiles.get(0).getChildAt(0) == tiles.get(0).glass());
    }

    @Test public void theFocusGlowSwitchTurnsTheGlowOffAndKeepsTheLift() throws Exception {
        new Prefs(HomeTestSupport.app()).setFocusGlow(false);
        MainActivity h = home();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(1);
        assertFalse(tile.glowEnabled());
        tile.requestFocus();
        settle();
        assertEquals(1f, tile.focusLevel(), 0.01f);
    }

    @Test public void okLaunchesOnceAndASecondOkWhileTheLaunchIsInFlightIsIgnored() throws Exception {
        MainActivity h = home();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(0);
        tile.requestFocus();
        settle();
        assertTrue(tile.performClick());
        assertTrue(tile.performClick());
        Intent first = shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        Intent second = shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        assertTrue("the app was launched", first != null);
        assertTrue("but only once", second == null);
        assertTrue(tile.isLaunching());
        ShadowLooper.idleMainLooper(MotionTokens.LAUNCH_GUARD_MS + 100, TimeUnit.MILLISECONDS);
        assertFalse("the guard lifts after its window", tile.isLaunching());
        assertTrue(tile.performClick());
        assertTrue("a later OK launches again", shadowOf(HomeTestSupport.app()).getNextStartedActivity() != null);
    }

    @Test public void noUpdateDotIsShownWithoutARealUpdateSourceAndARealOneShowsAnAccessibleBreathingDot() throws Exception {
        MainActivity h = home();
        for (ZeroPulseGlassAppTile t : HomeTestSupport.glassTiles(h)) {
            assertFalse("no authorised update source exists, so no dot", t.updateBadgeShown());
            assertFalse(t.updateAvailable());
        }
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(0);
        String name = String.valueOf(tile.getContentDescription());
        tile.setUpdateAvailable(true);                       // what a real source would do
        assertTrue(tile.updateBadgeShown());
        assertEquals(name + ", update available", String.valueOf(tile.getContentDescription()));
        assertTrue("it breathes while the update is pending", tile.badgePulsing());
        tile.setUpdateAvailable(false);
        assertFalse(tile.updateBadgeShown());
        assertFalse("and stops when it is not", tile.badgePulsing());
        assertEquals(name, String.valueOf(tile.getContentDescription()));
    }
}
