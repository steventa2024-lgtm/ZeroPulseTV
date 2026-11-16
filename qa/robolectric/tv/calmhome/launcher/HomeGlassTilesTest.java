package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.core.GlassMaterial;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.LiquidGlass;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;
import tv.calmhome.launcher.ui.ZeroPulseGlassTileBackground;

/**
 * The seven Home tiles and four quick actions are real glass from the one shared renderer: how they are built, that they
 * render, how they behave idle, focused and pressed, when the wallpaper changes, when they move, and over their lifetime.
 * Native graphics, so the pixels are real. Needs Robolectric (see docs/LIQUID_GLASS.md).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class HomeGlassTilesTest {

    private static void installDefaults() {
        for (String[] app : MockTvApps.DEFAULTS) {
            MockTvApps.install(app[0], app[1], true, app[1].startsWith("YouTube") ? MockTvApps.pictureBanner()
                    : MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        }
    }

    private static MainActivity glassHome() {
        installDefaults();
        return HomeTestSupport.startHome(true);
    }

    private static boolean allRendered(MainActivity home) {
        GlassKit kit = home.theme().kit;
        return kit.surfaceCount() > 0 && kit.renderedSurfaceCount() == kit.surfaceCount();
    }

    private static boolean anyOldPainter(View v) {
        if (v.getBackground() instanceof LiquidGlass || v.getForeground() instanceof LiquidGlass) return true;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (anyOldPainter(((ViewGroup) v).getChildAt(i))) return true;
        return false;
    }

    // ------------------------------------------------------------------ shared renderer

    @Test public void allSevenTilesAndFourActionsAreGlassFromTheSharedRenderer() throws Exception {
        MainActivity home = glassHome();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        List<ZeroPulseGlassQuickAction> actions = HomeTestSupport.glassActions(home);
        assertEquals(7, tiles.size());
        assertEquals(4, actions.size());
        List<GraphiteGlassSurface> surfaces = HomeTestSupport.surfaces(home);
        assertEquals(11, surfaces.size());
        for (GraphiteGlassSurface s : surfaces) {
            assertTrue("every surface is the supplied renderer's CachedView", s instanceof GraphiteGlassRenderer.CachedView);
            assertTrue(s instanceof ZeroPulseGlassTileBackground);
        }
        for (ZeroPulseGlassAppTile t : tiles) assertEquals(GraphiteGlassSurface.Shape.CARD, t.glass().shape());
        for (ZeroPulseGlassQuickAction a : actions) assertEquals(GraphiteGlassSurface.Shape.CAPSULE, a.glass().shape());
        assertEquals("they all registered with the one engine", 11, home.theme().kit.surfaceCount());
        assertFalse("no tile or action is drawn by the old painter", anyOldPainter(home.getWindow().getDecorView().findViewById(android.R.id.content)));

        assertTrue("the scene is prepared and all 11 render", HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        assertEquals(11, home.theme().kit.renderedSurfaceCount());
        assertEquals("two frost copies: one for the tiles' size, one for the actions'", 2, home.theme().kit.surfaceVariantCount());
        assertTrue(home.theme().kit.diagnostics().contains("11 tiles and actions (11 rendered)"));
    }

    @Test public void theMaterialScalesWithEachSurfaceUsingTheApprovedFormulas() throws Exception {
        MainActivity home = glassHome();
        HomeTestSupport.pump(home, 2);
        for (ZeroPulseGlassAppTile t : HomeTestSupport.glassTiles(home)) {
            float h = t.glass().getHeight();
            assertTrue("laid out", h > 0);
            assertEquals(GlassMaterial.unit(h), t.glass().unit(), 1e-4f);
            assertEquals(GlassMaterial.bevel(h), t.glass().bevel(), 1e-3f);
        }
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(0);
        ZeroPulseGlassQuickAction action = HomeTestSupport.glassActions(home).get(0);
        assertTrue("a Home tile at 1080p is about 139 px tall (222 px wide at 16:10, was 125 at 16:9): " + tile.glass().getHeight(),
                Math.abs(tile.glass().getHeight() - 139) <= 2);
        assertEquals("a quick action is 36 dp = 72 px, slimmer than the old 84 px capsule", 72, action.glass().getHeight());
        assertTrue("the actions' material is finer than the tiles'", action.glass().unit() < tile.glass().unit());
    }

    @Test public void oneRowOfSevenTilesFitsInsideTheSafeMargins() throws Exception {
        MainActivity home = glassHome();
        HomeTestSupport.pump(home, 1);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        int[] at = new int[2];
        int firstLeft = Integer.MAX_VALUE, lastRight = 0, top = -1;
        for (ZeroPulseGlassAppTile t : tiles) {
            t.getLocationOnScreen(at);
            firstLeft = Math.min(firstLeft, at[0]);
            lastRight = Math.max(lastRight, at[0] + t.getWidth());
            if (top < 0) top = at[1];
            assertEquals("one horizontal row", top, at[1]);
        }
        assertTrue("inside the 5 % safe margin on the left: " + firstLeft, firstLeft >= 96 - 2);
        assertTrue("and on the right: " + lastRight, lastRight <= 1920 - 96 + 2);
        assertEquals("tiles are 16:10, a little taller than 16:9 so a banner sits in an even frame of glass", 1.6f,
                tiles.get(0).getWidth() / (float) tiles.get(0).getHeight(), 0.03f);
    }

    // ------------------------------------------------------------------ the glass layer is inert

    @Test public void theGlassLayerNeverTakesFocusOrClicksAndNothingStrokesTheTile() throws Exception {
        MainActivity home = glassHome();
        HomeTestSupport.pump(home, 2);
        List<View> controls = new ArrayList<>();
        controls.addAll(HomeTestSupport.glassTiles(home));
        controls.addAll(HomeTestSupport.glassActions(home));
        for (View control : controls) {
            GraphiteGlassSurface glass = (GraphiteGlassSurface) ((ViewGroup) control).getChildAt(0);
            assertFalse(glass.isFocusable() || glass.isClickable() || glass.isLongClickable() || glass.isFocusableInTouchMode());
            assertFalse(glass.hasOnClickListeners());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, glass.getImportantForAccessibility());
            assertTrue("the control, not its glass, is the focus target", control.isFocusable() && control.isClickable());
            assertNull("no outline or ring is drawn over the control", control.getForeground());
            assertNull("and no flat background under the glass", control.getBackground());
            assertEquals("the control itself is never scaled (the renderer forbids it)", 1f, control.getScaleX(), 0f);
            assertEquals(0f, control.getTranslationX(), 0f);
        }
    }

    @Test public void aTouchOnTheGlassReachesTheTileAndLaunchesTheApp() throws Exception {
        MainActivity home = glassHome();
        HomeTestSupport.pump(home, 2);
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(0);
        int[] at = new int[2];
        tile.getLocationOnScreen(at);
        long now = android.os.SystemClock.uptimeMillis();
        float x = at[0] + tile.getWidth() / 2f, y = at[1] + tile.getHeight() / 2f;
        // The window dispatches a touch to the tile at that point; the tile then hands it to its children (the glass among them)
        // and takes it itself because the glass does not.
        float lx = tile.getWidth() / 2f, ly = tile.getHeight() / 2f;
        tile.dispatchTouchEvent(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, lx, ly, 0));
        tile.dispatchTouchEvent(android.view.MotionEvent.obtain(now, now + 40, android.view.MotionEvent.ACTION_UP, lx, ly, 0));
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        android.content.Intent started = org.robolectric.Shadows.shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        assertNotNull("the click went through the glass to the tile", started);
        assertEquals(MockTvApps.DEFAULTS[0][0], started.getComponent().getPackageName());
    }

    // ------------------------------------------------------------------ idle, focused, pressed

    @Test public void idleFocusedAndPressedAreDistinctAndOnlyTheFocusedControlTakesTheFocusMaterial() throws Exception {
        MainActivity home = glassHome();
        assertTrue(HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        List<ZeroPulseGlassQuickAction> actions = HomeTestSupport.glassActions(home);
        for (ZeroPulseGlassAppTile t : tiles) if (!t.isFocused()) assertEquals(0f, t.focusLevel(), 0f);

        ZeroPulseGlassQuickAction second = actions.get(1);
        assertTrue(second.requestFocus());
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("the focused action has the focus material", 1f, second.focusLevel(), 0.01f);
        for (ZeroPulseGlassQuickAction a : actions) if (a != second) assertEquals("the others stay quiet", 0f, a.focusLevel(), 0f);
        for (ZeroPulseGlassAppTile t : tiles) assertEquals(0f, t.focusLevel(), 0f);

        ZeroPulseGlassAppTile tile = tiles.get(3);
        Bitmap idle = HomeTestSupport.draw(tile.glass());
        tile.requestFocus();
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(1f, tile.focusLevel(), 0.01f);
        assertEquals("focus moved: the action let go", 0f, second.focusLevel(), 0.01f);
        Bitmap focused = HomeTestSupport.draw(tile.glass());
        assertTrue("the focus material is brighter than the idle glass: " + HomeTestSupport.meanBrightness(idle) + " -> " + HomeTestSupport.meanBrightness(focused),
                HomeTestSupport.meanBrightness(focused) > HomeTestSupport.meanBrightness(idle) + 0.5);
        assertEquals("and the tile still does not scale", 1f, tile.getScaleX(), 0f);

        ZeroPulseGlassAppTile other = tiles.get(5);
        other.setPressed(true);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("pressed shows the focus material too", 1f, other.focusLevel(), 0.01f);
        other.setPressed(false);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("released and not focused: idle again", 0f, other.focusLevel(), 0.01f);
    }

    @Test public void focusFadesInInsteadOfSnapping() throws Exception {
        MainActivity home = glassHome();
        assertTrue(HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        assertFalse("animations are on in this test", home.theme().reducedMotion);
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(4);
        tile.requestFocus();
        // The level is animated (a crossfade between two cached frames), so it has not already jumped to 1 when focus lands.
        // (Robolectric's clock does not step a ValueAnimator through its middle, so the mid-transition value is not sampled.)
        assertTrue("focus lands as the start of a fade, not as a snap: " + tile.focusLevel(), tile.focusLevel() < 1f);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(1f, tile.focusLevel(), 0.01f);
        long rendersAfterFocus = GraphiteGlassRenderer.Stats.renders();
        HomeTestSupport.glassTiles(home).get(5).requestFocus();
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("moving focus never re-renders the glass, it only crossfades the cached frames", rendersAfterFocus, GraphiteGlassRenderer.Stats.renders());
    }

    @Test public void reducedMotionSnapsTheFocusMaterialWithoutAnimating() throws Exception {
        new Prefs(HomeTestSupport.app()).setReducedMotion(true);
        MainActivity home = glassHome();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(2);
        tile.requestFocus();
        assertEquals(1f, tile.focusLevel(), 0f);
    }

    // ------------------------------------------------------------------ wallpaper change

    private static Bitmap wallpaper(int left, int right) {
        Bitmap b = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new android.graphics.LinearGradient(0, 0, 192, 108, left, right, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 192, 108, p);
        return b;
    }

    @Test public void aNewWallpaperRefreshesEveryTileAndActionWithoutAGreyFlash() throws Exception {
        MainActivity home = glassHome();
        assertTrue(HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        GlassKit kit = home.theme().kit;
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(1);
        Bitmap before = HomeTestSupport.draw(tile.glass());
        int scenes = kit.sceneCount();

        kit.setWallpaper(wallpaper(Color.rgb(250, 200, 80), Color.rgb(20, 120, 220)), 0);
        for (GraphiteGlassSurface s : HomeTestSupport.surfaces(home)) assertTrue("the old glass stays until the new one is ready", s.hasMaterial());

        assertTrue("the new scene arrives", HomeTestSupport.pumpUntil(home, () -> kit.sceneCount() > scenes, 150));
        assertTrue("and all twelve re-render", HomeTestSupport.pumpUntil(home, () -> {
            Bitmap now = HomeTestSupport.draw(tile.glass());
            return HomeTestSupport.meanDifference(before, now) > 3.0;
        }, 150));
        assertEquals(11, kit.renderedSurfaceCount());
    }

    @Test public void twoWallpapersGiveTwoDifferentGlassesAtTheSamePlace() throws Exception {
        MainActivity home = glassHome();
        assertTrue(HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        GlassKit kit = home.theme().kit;
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(0);
        final int first = kit.sceneCount();
        kit.setWallpaper(wallpaper(Color.rgb(240, 240, 240), Color.rgb(200, 200, 210)), 0);
        assertTrue(HomeTestSupport.pumpUntil(home, () -> kit.sceneCount() > first, 150));
        HomeTestSupport.pumpUntil(home, () -> HomeTestSupport.meanBrightness(HomeTestSupport.draw(tile.glass())) > 120, 150);
        double light = HomeTestSupport.meanBrightness(HomeTestSupport.draw(tile.glass()));
        final int next = kit.sceneCount();
        kit.setWallpaper(wallpaper(Color.rgb(10, 12, 18), Color.rgb(24, 28, 40)), 0);
        assertTrue(HomeTestSupport.pumpUntil(home, () -> kit.sceneCount() > next, 150));
        HomeTestSupport.pumpUntil(home, () -> HomeTestSupport.meanBrightness(HomeTestSupport.draw(tile.glass())) < 80, 150);
        double dark = HomeTestSupport.meanBrightness(HomeTestSupport.draw(tile.glass()));
        assertTrue("the glass follows the wallpaper behind it: light " + light + " vs dark " + dark, light > dark + 30);
    }

    // ------------------------------------------------------------------ layout movement

    @Test public void movingATileKeepsItsGlassAndRendersItAgainAtTheNewPlace() throws Exception {
        MainActivity home = glassHome();
        assertTrue(HomeTestSupport.pumpUntil(home, () -> allRendered(home), 150));
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(0);
        long rendersBefore = GraphiteGlassRenderer.Stats.renders();
        ViewGroup column = (ViewGroup) tile.getParent();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) column.getLayoutParams();
        lp.topMargin += 30;
        column.setLayoutParams(lp);
        HomeTestSupport.layoutTv(home);
        assertTrue("it never drops to the placeholder silhouette while it waits", tile.glass().hasMaterial());
        assertTrue("it is rendered again for the new place", HomeTestSupport.pumpUntil(home,
                () -> GraphiteGlassRenderer.Stats.renders() > rendersBefore, 150));
        assertTrue(tile.glass().hasMaterial());
    }

    // ------------------------------------------------------------------ lifecycle

    @Test public void surfacesJoinAndLeaveTheEngineWithTheirTilesAndTheEngineStopsWithTheActivity() throws Exception {
        installDefaults();
        new Prefs(HomeTestSupport.app()).setLiquidGlass(true);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class,
                new android.content.Intent(HomeTestSupport.app(), MainActivity.class)).setup();
        MainActivity home = controller.get();
        GlassKit kit = home.theme().kit;
        assertEquals(11, kit.surfaceCount());
        MockTvApps.uninstall(MockTvApps.DEFAULTS[6][0]);
        MockTvApps.broadcast(android.content.Intent.ACTION_PACKAGE_REMOVED, MockTvApps.DEFAULTS[6][0]);
        assertEquals("the removed app's tile left the engine", 10, kit.surfaceCount());
        controller.pause().stop().destroy();
        HomeTestSupport.pump(home, 3);                                  // a late result from the worker must not crash a dead Home
        assertFalse(new Prefs(HomeTestSupport.app()).liquidStarting());
    }

    @Test public void beforeTheSceneIsReadyTheGlassSaysSoInsteadOfFallingBackToTheOldPainter() throws Exception {
        MainActivity home = glassHome();
        GlassKit kit = home.theme().kit;
        if (!kit.isReady()) {
            assertTrue(kit.diagnostics().contains("preparing"));
            for (GraphiteGlassSurface s : HomeTestSupport.surfaces(home)) assertFalse("a plain placeholder, not painted glass", s.hasMaterial());
        }
        assertFalse(anyOldPainter(home.getWindow().getDecorView().findViewById(android.R.id.content)));
        assertTrue(kit.diagnostics().startsWith("Liquid Glass: shared graphite renderer"));
        assertTrue(HomeTestSupport.pumpUntil(home, () -> kit.isReady(), 150));
        assertTrue(kit.diagnostics().contains("scene ready"));
    }

    // ------------------------------------------------------------------ rendering mode is visible

    @Test public void aboutNamesTheRenderingModeAndGraphiteSaysItIsGraphite() throws Exception {
        installDefaults();                                              // nothing chosen: the default is on
        android.content.Intent open = new android.content.Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "about");
        MainActivity liquid = Robolectric.buildActivity(MainActivity.class, open).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue(HomeTestSupport.hasText(liquid, "Liquid Glass (on by default)"));
        assertTrue(HomeTestSupport.hasText(liquid, "Shared graphite glass"));
        assertTrue(HomeTestSupport.hasText(liquid, "Wallpaper scene") || HomeTestSupport.hasText(liquid, "Preparing the wallpaper scene"));

        new Prefs(HomeTestSupport.app()).setLiquidGlass(false);
        MainActivity graphite = Robolectric.buildActivity(MainActivity.class,
                new android.content.Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "about")).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue(HomeTestSupport.hasText(graphite, "Graphite (Liquid Glass is off)"));
    }

    // ------------------------------------------------------------------ the artwork credit

    @Test public void homeShowsNoCreditLineForAnyWallpaperAndAboutAndTheInformationScreenListThem() throws Exception {
        installDefaults();
        Prefs prefs = new Prefs(HomeTestSupport.app());
        prefs.setLiquidGlass(true);
        prefs.setWallpaperCollection("Twilight");
        MainActivity own = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(own, 25);
        assertFalse("no credit line for the original twilight artwork", HomeTestSupport.hasText(own, "Twilight Coast"));
        assertFalse(HomeTestSupport.hasText(own, "Original artwork"));

        prefs.setWallpaperCollection("Nature");
        prefs.setWallpaperPicked("wp04.jpg");
        MainActivity photo = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(photo, 60);
        assertEquals("the CC BY-SA photograph is the wallpaper on screen", "wp04.jpg", photo.currentWallpaperFile());
        assertFalse("and Home carries no credit line over it", HomeTestSupport.hasText(photo, "Piedras Rojas"));
        assertFalse(HomeTestSupport.hasText(photo, "Diego Delso"));

        android.content.Intent about = new android.content.Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "about");
        MainActivity aboutHome = Robolectric.buildActivity(MainActivity.class, about).setup().get();
        ShadowLooper.idleMainLooper();
        assertTrue("About lists the original artwork's credit", HomeTestSupport.hasText(aboutHome, "Original artwork: ZeroPulse TV"));
        assertTrue("and the photographer's", HomeTestSupport.hasText(aboutHome, "Diego Delso"));
    }
}
