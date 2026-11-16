package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.LiquidGlass;

/**
 * Home in graphite and in the optional Liquid Glass, and the guard that protects the TV's only Home. Needs Robolectric
 * (see docs/LIQUID_GLASS.md); the plain JVM suites in qa/tests do not run it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class LiquidGlassActivityTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private ActivityController<MainActivity> launch(String open) {
        Intent i = new Intent(c, MainActivity.class);
        if (open != null) i.putExtra(MainActivity.EXTRA_OPEN, open);
        return Robolectric.buildActivity(MainActivity.class, i).setup();
    }

    private static int glassViews(View v) {
        int n = v.getBackground() instanceof LiquidGlass || v.getForeground() instanceof LiquidGlass ? 1 : 0;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) n += glassViews(((ViewGroup) v).getChildAt(i));
        return n;
    }

    @Test public void liquidGlassIsOnByDefaultWithTheLiquidGlassWallpapersAndItIsExplicitAboutIt() {
        Prefs prefs = new Prefs(c);
        assertFalse("nothing has been chosen yet", prefs.liquidGlassIsExplicit());
        assertTrue("so the approved look is the default", prefs.liquidGlass());
        assertEquals("with the wallpapers supplied for it", WallpaperCatalog.LIQUID, prefs.wallpaperCollection());
        MainActivity home = launch(null).get();
        assertTrue(home.theme().liquid);
        assertFalse("starting did not make a choice for the viewer", new Prefs(c).liquidGlassIsExplicit());
    }

    @Test public void anExplicitOffIsKeptAndShowsTheGraphiteHomeWithoutTouchingOtherSettings() {
        Prefs prefs = new Prefs(c);
        prefs.setDock(java.util.Arrays.asList("a.app", "b.app"));
        prefs.setDimPercent(30);
        prefs.setLabelsAlways(true);
        prefs.setLiquidGlass(false);
        MainActivity home = launch(null).get();
        assertFalse(home.theme().liquid);
        assertFalse(new Prefs(c).liquidStarting());
        assertEquals("graphite has no Liquid Glass surface", 0, glassViews(home.getWindow().getDecorView()));
        assertEquals(0, HomeTestSupport.surfaces(home).size());
        assertEquals(WallpaperCatalog.ALL, new Prefs(c).wallpaperCollection());
        assertEquals(java.util.Arrays.asList("a.app", "b.app"), new Prefs(c).dock());
        assertEquals(30, new Prefs(c).dimPercent());
        assertTrue(new Prefs(c).labelsAlways());
    }

    @Test public void liquidGlassBuildsGlassSurfacesAndArmsTheGuard() {
        new Prefs(c).setLiquidGlass(true);
        MainActivity home = launch(null).get();
        assertTrue(home.theme().liquid);
        assertTrue("armed until Home has stayed up", new Prefs(c).liquidStarting());
        assertEquals("the empty-state card and the bottom peek handle are glass (the header is the supplied glass kit)",
                2, HomeTestSupport.surfaces(home).size());
        assertEquals("duplicate quick actions were removed from Home", 0, HomeTestSupport.glassActions(home).size());
        assertEquals("no tile or action uses the old painter", 0, glassViews(home.getWindow().getDecorView()));
    }

    @Test public void leavingHomeDisarmsTheGuard() {
        new Prefs(c).setLiquidGlass(true);
        ActivityController<MainActivity> controller = launch(null);
        assertTrue(new Prefs(c).liquidStarting());
        controller.pause().stop();
        assertFalse(new Prefs(c).liquidStarting());
        assertTrue("and Liquid Glass stays on", new Prefs(c).liquidGlass());
        controller.destroy();
        assertFalse(new Prefs(c).liquidStarting());
    }

    @Test public void aStartThatNeverFinishedFallsBackToGraphiteAndSaysSo() {
        Prefs prefs = new Prefs(c);
        prefs.setLiquidGlass(true);
        prefs.setLiquidStarting(true);                     // what a crash during the last Liquid Glass start leaves behind
        MainActivity home = launch(null).get();
        assertFalse(home.theme().liquid);
        assertFalse("Liquid Glass is off until the viewer turns it on again", new Prefs(c).liquidGlass());
        assertFalse(new Prefs(c).liquidStarting());
        assertEquals("Liquid Glass was turned off because it did not start last time", ShadowToast.getTextOfLatestToast());
    }

    @Test public void homeStillIgnoresBackInBothLooks() {
        for (boolean liquid : new boolean[]{false, true}) {
            new Prefs(c).setLiquidGlass(liquid);
            new Prefs(c).setLiquidStarting(false);
            MainActivity home = launch(null).get();
            home.onBackPressed();
            assertFalse("liquid=" + liquid + ": Back must not finish Home", home.isFinishing());
        }
    }

    @Test public void settingsOffersTheSwitchAndItBringsTheLiquidGlassCollectionAndTakesItAway() {
        new Prefs(c).setLiquidGlass(false);
        ActivityController<MainActivity> controller = launch("settings:appearance");
        ShadowLooper.idleMainLooper();
        assertEquals(WallpaperCatalog.ALL, new Prefs(c).wallpaperCollection());
        View off = row(controller.get(), "Liquid Glass Home (beta), off");
        assertNotNull("the row is in Settings > Appearance", off);
        off.performClick();
        assertTrue(new Prefs(c).liquidGlass());
        assertFalse("turning it on never leaves the guard armed from Settings", new Prefs(c).liquidStarting());
        assertEquals("the dark glass wallpapers Liquid Glass is designed for", WallpaperCatalog.LIQUID, new Prefs(c).wallpaperCollection());
        assertTrue(new Prefs(c).usePhotoBackdrop());
    }

    @Test public void aCollectionTheViewerChoseIsNeverSwitched() {
        new Prefs(c).setLiquidGlass(false);
        new Prefs(c).setWallpaperCollection("Ocean");
        ActivityController<MainActivity> controller = launch("settings:appearance");
        ShadowLooper.idleMainLooper();
        row(controller.get(), "Liquid Glass Home (beta), off").performClick();
        assertTrue(new Prefs(c).liquidGlass());
        assertEquals("Ocean", new Prefs(c).wallpaperCollection());
    }

    @Test public void theGraphiteHomeStillOffersThePhotoSwitchAndLiquidGlassHidesIt() {
        new Prefs(c).setLiquidGlass(false);
        ActivityController<MainActivity> controller = launch("settings:appearance");
        ShadowLooper.idleMainLooper();
        assertNotNull(row(controller.get(), "Photo wallpaper on Home, off"));
        new Prefs(c).setLiquidGlass(true);
        ActivityController<MainActivity> liquid = launch("settings:appearance");
        ShadowLooper.idleMainLooper();
        assertNull("Liquid Glass always shows the photo", row(liquid.get(), "Photo wallpaper on Home, off"));
        assertNull(row(liquid.get(), "Photo wallpaper on Home, on"));
    }

    private static View row(MainActivity home, String description) {
        ArrayList<View> found = new ArrayList<>();
        home.getWindow().getDecorView().findViewsWithText(found, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return found.isEmpty() ? null : found.get(0);
    }
}
