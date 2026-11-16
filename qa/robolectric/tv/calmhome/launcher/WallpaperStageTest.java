package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.ui.FramedImageView;

/**
 * The wallpaper stage's ordering rules, driven by hand: with the decode and optics workers replaced by queues the test runs, so
 * "which job ran, and which was skipped" is exact instead of a race.
 *
 * <ul>
 * <li>latest wins: pictures asked for while one decodes never show; only the newest is shown (3005 dropped the newest instead);</li>
 * <li>a measuring/copying job made stale by a newer picture, framing or dim is skipped, not run and thrown away;</li>
 * <li>the crossfade is plain alpha on the two image views: never an offscreen layer (3005 asked for two full-screen hardware
 *     layers, about 34 MB of GPU scratch memory and a 29 to 31 ms frame on the TCL), and a second pick during a fade leaves a
 *     clean end state (one picture, fully opaque, the other released).</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class WallpaperStageTest {

    /** A worker whose jobs run when the test says so, on the test's thread. */
    static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean shut;

        @Override public void execute(Runnable r) { queue.add(r); }
        int pending() { return queue.size(); }
        void runNext() { queue.poll().run(); }
        void runAll() { while (!queue.isEmpty()) queue.poll().run(); }
        @Override public void shutdown() { shut = true; }
        @Override public List<Runnable> shutdownNow() { shut = true; return new ArrayList<>(queue); }
        @Override public boolean isShutdown() { return shut; }
        @Override public boolean isTerminated() { return shut && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private ManualExecutor decode, optics;
    private FramedImageView front, back, frost;
    private WallpaperStage stage;
    private final List<String> shown = new ArrayList<>();
    private int scenes, contrasts;

    @Before public void build() {
        decode = new ManualExecutor();
        optics = new ManualExecutor();
        front = new FramedImageView(RuntimeEnvironment.getApplication());
        back = new FramedImageView(RuntimeEnvironment.getApplication());
        frost = new FramedImageView(RuntimeEnvironment.getApplication());
        stage = new WallpaperStage(RuntimeEnvironment.getApplication(), new Prefs(RuntimeEnvironment.getApplication()), front, back, frost,
                e -> shown.add(e.file), decode, optics);
        stage.setSceneSink(bitmap -> scenes++);
        stage.setContrastSink(plan -> contrasts++);
    }

    private WallpaperCatalog.Entry entry(String file) {
        WallpaperCatalog.Entry e = stage.catalog().byFile(file);
        assertNotNull(file, e);
        return e;
    }

    /**
     * Asks for a picture, runs its decode, and runs the one main-thread task that puts it on screen (so a fade it starts has not been
     * stepped yet, and a test sees it running; the animation's frames run when a test advances the clock).
     */
    private void land(WallpaperCatalog.Entry e, boolean animate) {
        stage.show(e, animate);
        decode.runNext();
        org.robolectric.shadows.ShadowLooper looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
        for (int guard = 0; guard < 50 && (stage.current() == null || !stage.current().file.equals(e.file)); guard++) looper.runOneTask();
    }

    // ------------------------------------------------------------------ latest wins

    @Test public void picturesAskedForWhileOneDecodesNeverShowAndTheNewestIsTheOneShown() {
        stage.show(entry("lg01.jpg"), false);
        stage.show(entry("lg02.jpg"), false);
        stage.show(entry("lg03.jpg"), false);
        assertEquals("one decode runs; the later requests wait as one", 1, decode.pending());
        decode.runNext();
        ShadowLooper.idleMainLooper();
        assertTrue("the first picture arrived after a newer one was asked for: it is not shown: " + shown, shown.isEmpty());
        assertEquals("the newest request starts its own decode", 1, decode.pending());
        decode.runNext();
        ShadowLooper.idleMainLooper();
        assertEquals("only the newest pick reached the screen", java.util.Collections.singletonList("lg03.jpg"), shown);
        assertEquals("lg03.jpg", stage.current().file);
        assertEquals("nothing else is waiting", 0, decode.pending());
    }

    @Test public void aPickMadeAfterThePreviousOneLandedStartsAtOnce() {
        land(entry("lg01.jpg"), false);
        stage.show(entry("lg04.jpg"), false);
        assertEquals(1, decode.pending());
        decode.runNext();
        ShadowLooper.idleMainLooper();
        assertEquals(java.util.Arrays.asList("lg01.jpg", "lg04.jpg"), shown);
    }

    // ------------------------------------------------------------------ stale copies are skipped

    @Test public void aCopyJobMadeStaleByAnewerPictureIsSkippedNotRun() {
        land(entry("lg01.jpg"), false);
        land(entry("lg02.jpg"), false);
        assertEquals("both pictures queued their copies behind the (idle) worker", 2, optics.pending());
        optics.runAll();
        ShadowLooper.idleMainLooper();
        assertEquals("the stale job did no work; only the newest picture was measured and copied", 1, stage.opticsJobsRun());
        assertEquals("and the glass and the scrim were fed once, for the picture that is on screen", 1, scenes);
        assertEquals(1, contrasts);
    }

    @Test public void framingStepsCoalesceIntoOneCopyJob() {
        WallpaperCatalog.Entry e = entry("lg03.jpg");
        land(e, false);
        optics.runAll();
        ShadowLooper.idleMainLooper();
        int before = stage.opticsJobsRun();
        stage.setFrame(e, 0.4f, 0.5f, 1.1f);
        stage.setFrame(e, 0.5f, 0.5f, 1.2f);
        stage.setFrame(e, 0.6f, 0.5f, 1.3f);
        assertEquals(3, optics.pending());
        optics.runAll();
        ShadowLooper.idleMainLooper();
        assertEquals("three slider steps cost one measurement, for the last", before + 1, stage.opticsJobsRun());
    }

    // ------------------------------------------------------------------ the crossfade

    @Test public void theCrossfadeUsesNoOffscreenLayerAndEndsWithOnePictureFullyOpaque() {
        land(entry("lg01.jpg"), true);
        assertTrue("the first picture fades in", stage.isFading());
        assertEquals(View.LAYER_TYPE_NONE, front.getLayerType());
        assertEquals(View.LAYER_TYPE_NONE, back.getLayerType());
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS);
        assertFalse(stage.isFading());

        land(entry("lg02.jpg"), true);
        assertTrue(stage.isFading());
        assertEquals("a fade between two full-screen pictures needs no layer: front", View.LAYER_TYPE_NONE, front.getLayerType());
        assertEquals("a fade between two full-screen pictures needs no layer: back", View.LAYER_TYPE_NONE, back.getLayerType());
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS);
        assertFalse(stage.isFading());
        FramedImageView on = front.getDrawable() != null ? front : back, off = on == front ? back : front;
        assertEquals("the new picture is fully shown", 1f, on.getAlpha(), 0f);
        assertEquals("the old one is gone", 0f, off.getAlpha(), 0f);
        assertNull("and released for the next decode", off.getDrawable());
        assertEquals(View.LAYER_TYPE_NONE, on.getLayerType());
    }

    @Test public void aSecondPickDuringAFadeFinishesTheFirstCleanlyAndFadesTheSecondIn() {
        land(entry("lg01.jpg"), true);
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS);
        land(entry("lg02.jpg"), true);
        assertTrue(stage.isFading());
        land(entry("lg03.jpg"), true);                                  // lands while the second is still fading
        assertTrue("the third fades in", stage.isFading());
        ShadowLooper.idleMainLooper(2500, TimeUnit.MILLISECONDS);
        assertFalse(stage.isFading());
        assertEquals("lg03.jpg", stage.current().file);
        int withPicture = (front.getDrawable() != null ? 1 : 0) + (back.getDrawable() != null ? 1 : 0);
        assertEquals("exactly one picture is left on the two views", 1, withPicture);
        FramedImageView on = front.getDrawable() != null ? front : back;
        assertEquals(1f, on.getAlpha(), 0f);
        assertEquals(0f, (on == front ? back : front).getAlpha(), 0f);
    }

    @Test public void reducedMotionShowsThePictureAtOnceWithoutAnAnimator() {
        stage.setFadeMs(0);
        land(entry("lg01.jpg"), true);
        assertFalse(stage.isFading());
        FramedImageView on = front.getDrawable() != null ? front : back;
        assertEquals(1f, on.getAlpha(), 0f);
    }

    @Test public void shutdownStopsTheFadeAndTheWorkers() {
        land(entry("lg01.jpg"), true);
        assertTrue(stage.isFading());
        stage.shutdown();
        assertFalse(stage.isFading());
        assertTrue(decode.isShutdown() && optics.isShutdown());
        stage.show(entry("lg02.jpg"), false);
        assertEquals("nothing is queued after shutdown", 0, decode.pending());
    }
}
