package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.data.Prefs;

/**
 * Phase B on the real Home (simulated: Robolectric, not the TV): the navigation focus glides between destinations, the active page
 * stays where it is until a destination is chosen, rapid presses retarget instead of queueing, and reduced motion switches at once.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PremiumNavigationTest {

    private static GlassTopNavView nav(MainActivity home) {
        return HomeTestSupport.all(home, GlassTopNavView.class).get(0);
    }

    private static View button(GlassTopNavView nav, String label) {
        ArrayList<View> found = new ArrayList<>();
        nav.findViewsWithText(found, label, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return found.isEmpty() ? null : found.get(0);
    }

    @Test public void focusGlidesFromOneDestinationToTheNextAndThePageStaysUntilChosen() throws Exception {
        MainActivity home = HomeTestSupport.startHome(true);
        GlassTopNavView nav = nav(home);
        View homeTab = button(nav, "Home"), apps = button(nav, "Apps"), downloads = button(nav, "Downloads");
        assertNotNull(apps);
        assertTrue(homeTab.requestFocus());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals("arriving from outside the rail does not glide", -1f, nav.glideProgress(), 0f);
        long renders = GraphiteGlassRenderer.Stats.renders();

        assertTrue(apps.requestFocus());                         // Right on the remote
        float started = nav.glideProgress();
        assertTrue("a direct move between destinations glides: " + started, started >= 0f && started < 1f);
        assertEquals("the active page is still Home", "Home", nav.getSelectedId());
        assertTrue(homeTab.isSelected());

        assertTrue(downloads.requestFocus());                    // a rapid second press
        assertTrue("it retargets from where the capsule is, never queues", nav.glideProgress() >= 0f);
        ShadowLooper.idleMainLooper(GlassTopNavView.GLIDE_MS + 200, TimeUnit.MILLISECONDS);
        assertEquals("then it settles: nothing extra is drawn", -1f, nav.glideProgress(), 0f);
        assertEquals("the page has still not changed", "Home", nav.getSelectedId());
        assertEquals("gliding draws, it never re-renders glass", renders, GraphiteGlassRenderer.Stats.renders());

        assertTrue(downloads.performClick());
        assertEquals("choosing a destination makes it the active page", "Downloads", nav.getSelectedId());
    }

    @Test public void reducedMotionMovesTheFocusPillAtOnce() throws Exception {
        new Prefs(HomeTestSupport.app()).setReducedMotion(true);
        MainActivity home = HomeTestSupport.startHome(true);
        GlassTopNavView nav = nav(home);
        assertTrue(button(nav, "Home").requestFocus());
        assertTrue(button(nav, "Apps").requestFocus());
        assertEquals(-1f, nav.glideProgress(), 0f);
    }

    @Test public void leftOnTheFirstAndRightOnTheLastDestinationStayOnTheRail() throws Exception {
        MainActivity home = HomeTestSupport.startHome(true);
        GlassTopNavView nav = nav(home);
        View first = button(nav, "Home"), last = button(nav, "Settings");
        assertEquals("the 3006 boundary correction is kept", first.getId(), first.getNextFocusLeftId());
        assertEquals(last.getId(), last.getNextFocusRightId());
    }
}
