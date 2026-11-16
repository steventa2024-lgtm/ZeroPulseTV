package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;

/**
 * Glass cost on the real Home, in the terms the TCL measurements showed (UI thread about 1 ms a frame; the frame cost is the render
 * thread's, so what can change is how much is drawn and how much is regenerated):
 *
 * <ul>
 * <li>moving focus between stationary controls renders nothing, prepares no scene, re-measures no wallpaper and never scales or
 *     moves the glass (the renderer's contract, and what keeps the refraction aligned);</li>
 * <li>a surface that is still moving keeps its glass and is rendered once, after it has held still, not once per frame of the move;</li>
 * <li>drawing the glass, the finish and the artwork in the middle of a focus animation allocates nothing.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class GlassPerformanceTest {

    private static MainActivity glassHome() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) {
            MockTvApps.install(app[0], app[1], true, app[1].startsWith("YouTube") ? MockTvApps.pictureBanner()
                    : MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        }
        MainActivity home = HomeTestSupport.startHome(true);
        GlassKit kit = home.theme().kit;
        assertTrue("all the glass rendered", HomeTestSupport.pumpUntil(home, () -> kit.surfaceCount() > 0 && kit.renderedSurfaceCount() == kit.surfaceCount(), 200));
        return home;
    }

    private static long allocated(Runnable r) {
        return AllocationMeter.during(r);
    }

    // ------------------------------------------------------------------ focus is not a render

    @Test public void movingFocusBetweenStationaryControlsRendersAndPreparesNothing() throws Exception {
        MainActivity home = glassHome();
        GlassKit kit = home.theme().kit;
        HomeTestSupport.pump(home, 3);                                  // let the first scene's last jobs finish
        long renders = GraphiteGlassRenderer.Stats.renders(), submitted = GraphiteGlassRenderer.Stats.submitted();
        int scenes = kit.sceneCount(), opticsJobs = home.wallpaperStage().opticsJobsRun();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);

        int[] route = {View.FOCUS_RIGHT, View.FOCUS_RIGHT, View.FOCUS_RIGHT, View.FOCUS_LEFT, View.FOCUS_LEFT, View.FOCUS_RIGHT, View.FOCUS_DOWN,
                View.FOCUS_RIGHT, View.FOCUS_RIGHT, View.FOCUS_LEFT, View.FOCUS_UP, View.FOCUS_LEFT, View.FOCUS_RIGHT};
        int moved = 0;
        for (int direction : route) {
            if (HomeTestSupport.dpad(home, direction)) moved++;
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS);     // the focus animation runs
            HomeTestSupport.firePreDraw(home.getWindow().getDecorView());// and the glass looks at where it is, as it does before each frame
            for (GraphiteGlassSurface s : HomeTestSupport.surfaces(home)) {
                assertTrue("every surface keeps its glass through the move", s.hasMaterial());
                assertEquals("the glass is never scaled, so its refraction stays aligned", 1f, s.getScaleX(), 0f);
                assertEquals(1f, s.getScaleY(), 0f);
                assertEquals(0f, s.getTranslationX(), 0f);
            }
            for (ZeroPulseGlassAppTile t : tiles) {
                assertEquals("nor is the tile", 1f, t.getScaleX(), 0f);
                assertEquals("nor the glass inside the tile: its parent chain is untransformed", 0f, t.getRotation(), 0f);
            }
        }
        assertTrue("the route really moved focus: " + moved, moved >= 8);
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        HomeTestSupport.pump(home, 2);
        assertEquals("no glass was rendered by any focus move", renders, GraphiteGlassRenderer.Stats.renders());
        assertEquals("none was even submitted", submitted, GraphiteGlassRenderer.Stats.submitted());
        assertEquals("no scene or frosted copy was prepared", scenes, kit.sceneCount());
        assertEquals("the wallpaper was not re-measured", opticsJobs, home.wallpaperStage().opticsJobsRun());
    }

    // ------------------------------------------------------------------ a moving surface renders once, when it stops

    @Test public void aSurfaceThatIsStillMovingKeepsItsGlassAndRendersOnceWhenItHasStopped() throws Exception {
        MainActivity home = glassHome();
        HomeTestSupport.pump(home, 3);
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(0);
        ViewGroup column = (ViewGroup) tile.getParent();
        long submitted = GraphiteGlassRenderer.Stats.submitted();
        android.view.View decor = home.getWindow().getDecorView();

        for (int step = 0; step < 6; step++) {                          // six frames of a slide, each a little further, 15 ms apart
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) column.getLayoutParams();
            lp.topMargin += 5;
            column.setLayoutParams(lp);
            HomeTestSupport.layoutTv(home);
            ShadowLooper.idleMainLooper(15, TimeUnit.MILLISECONDS);
            HomeTestSupport.firePreDraw(decor);
            assertTrue("it keeps its old glass while it moves", tile.glass().hasMaterial());
        }
        assertEquals("not one render was started during the move", submitted, GraphiteGlassRenderer.Stats.submitted());

        ShadowLooper.idleMainLooper(GraphiteGlassRenderer.CachedView.SETTLE_MS + 40, TimeUnit.MILLISECONDS);
        HomeTestSupport.firePreDraw(decor);
        long started = GraphiteGlassRenderer.Stats.submitted() - submitted;
        assertTrue("once it held still its glass was rendered for the new place: " + started, started >= 1);
        assertTrue("and once per surface that moved, not once per frame of the move (6 frames, 7 tiles at most): " + started, started <= 7);
        assertTrue(HomeTestSupport.pumpUntil(home, () -> GraphiteGlassRenderer.Stats.submitted() - submitted == started
                && home.theme().kit.renderedSurfaceCount() == home.theme().kit.surfaceCount(), 60));
        assertTrue(tile.glass().hasMaterial());
    }

    // ------------------------------------------------------------------ drawing allocates nothing

    @Test public void drawingTheGlassTheFinishAndTheArtworkMidFocusAllocatesNothing() throws Exception {
        MainActivity home = glassHome();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(home).get(2);
        ZeroPulseGlassQuickAction action = HomeTestSupport.glassActions(home).get(1);
        assertTrue(tile.glass().hasMaterial() && action.glass().hasMaterial());
        Bitmap target = Bitmap.createBitmap(Math.max(tile.getWidth(), action.getWidth()), Math.max(tile.getHeight(), action.getHeight()), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        for (int i = 0; i < 30; i++) { tile.draw(canvas); action.draw(canvas); }          // warm up
        final long[] bytes = new long[1];
        bytes[0] = allocated(() -> {
            for (int i = 0; i < 100; i++) {
                float level = (i % 10) / 10f;                           // a focus animation: every frame at a different crossfade level
                tile.glass().setLevel(level);
                action.glass().setLevel(level);
                tile.draw(canvas);
                action.draw(canvas);
            }
        });
        assertTrue("a focus animation's frames (glass crossfade, finish, artwork, label) allocate almost nothing: " + bytes[0] + " bytes in 100 frames of two controls",
                bytes[0] < 16 * 1024);
    }
}
