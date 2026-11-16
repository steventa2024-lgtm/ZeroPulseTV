package tv.calmhome.launcher.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.core.GlassMaterial;

/**
 * Proof that a tile's and an action's glass is the supplied renderer's output and nothing else: a surface is drawn, then
 * the renderer is called directly with the same backdrop, the same silhouette and the same material numbers, and the two
 * must agree pixel for pixel, at idle and at focus. Needs Robolectric with native graphics (see docs/LIQUID_GLASS.md).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class GlassRendererParityTest {

    private static Bitmap busyWallpaper(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = 40 + (x * 150 / w) + (y * 40 / h);
                int ripple = ((x / 6 + y / 6) % 2) * 22;
                b.setPixel(x, y, Color.rgb(Math.min(255, base + ripple), Math.min(255, 70 + y * 120 / h + ripple), Math.min(255, 120 + x * 60 / w)));
            }
        }
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    private interface Task<T> { T run() throws Exception; }

    /** The renderer insists on a worker thread, as it does in the app. */
    private static <T> T onWorker(final Task<T> task) throws Exception {
        final AtomicReference<T> out = new AtomicReference<>();
        final AtomicReference<Throwable> failed = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                out.set(task.run());
            } catch (Throwable e) {
                failed.set(e);
            }
        });
        t.start();
        t.join(60000);
        if (failed.get() != null) throw new AssertionError(failed.get());
        return out.get();
    }

    private static Bitmap drawn(View v) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    private static int maxChannelDifference(Bitmap a, Bitmap b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        int worst = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                worst = Math.max(worst, Math.abs((p >>> 24) - (q >>> 24)));
                worst = Math.max(worst, Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)));
                worst = Math.max(worst, Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)));
                worst = Math.max(worst, Math.abs((p & 255) - (q & 255)));
            }
        }
        return worst;
    }

    private void check(GraphiteGlassSurface.Shape shape, final int widthPx, final int heightPx, final int left, final int top) throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        final GraphiteGlassSurface surface = new GraphiteGlassSurface(activity, shape, heightPx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        lp.leftMargin = left;
        lp.topMargin = top;
        root.addView(surface, lp);
        activity.setContentView(root);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        assertEquals(heightPx, surface.getHeight());
        assertEquals("the surface takes its material unit from its height", GlassMaterial.unit(heightPx), surface.unit(), 1e-6f);

        // The same backdrop the engine would give it: the scene at 1/4 size and the frosted copy for this unit.
        final int cacheW = 480, cacheH = 270;
        final float sigma = GlassMaterial.frostSigma(surface.unit(), 0.25f);
        final Bitmap wallpaper = busyWallpaper(cacheW, cacheH);
        final GraphiteGlassRenderer.Backdrop frosted = onWorker(() -> GraphiteGlassRenderer.prepareBackdrop(wallpaper, cacheW, cacheH).withFrost(sigma));
        surface.bind(frosted, root);

        for (int i = 0; i < 120 && !surface.hasMaterial(); i++) {
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
            try {
                android.view.ViewTreeObserver.class.getMethod("dispatchOnPreDraw").invoke(decor.getViewTreeObserver());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            Thread.sleep(40);
        }
        assertTrue("the surface rendered", surface.hasMaterial());

        // The renderer called directly: the same silhouette (a rounded rectangle, 1 px in), the same numbers.
        final float radius = GlassMaterial.radius(heightPx, shape == GraphiteGlassSurface.Shape.CAPSULE);
        final float unit = GlassMaterial.unit(heightPx), bevel = GlassMaterial.bevel(heightPx);
        final float sx = cacheW / 1920f, sy = cacheH / 1080f;
        final GraphiteGlassRenderer.Frames direct = onWorker(() -> {
            Path path = new Path();
            path.addRoundRect(new RectF(1, 1, widthPx - 1, heightPx - 1), radius, radius, Path.Direction.CW);
            Bitmap mask = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            new Canvas(mask).drawPath(path, paint);
            return GraphiteGlassRenderer.render(frosted, mask, left, top, sx, sy, unit, bevel);
        });

        surface.setLevel(0f);
        assertEquals("idle glass is the renderer's idle frame", 0, maxChannelDifference(drawn(surface), direct.idle));
        surface.setLevel(1f);
        assertEquals("focused glass is the renderer's focus frame", 0, maxChannelDifference(drawn(surface), direct.emphasized));
        surface.setLevel(0.5f);
        Bitmap half = drawn(surface);
        assertTrue("half way is a real blend: not the idle frame", maxChannelDifference(half, direct.idle) > 0);
        assertTrue("and not the focus frame", maxChannelDifference(half, direct.emphasized) > 0);
        assertNotNull(half);
        assertFalse(half.isRecycled());
    }

    @Test public void aTileIsTheRenderersCardGlass() throws Exception {
        check(GraphiteGlassSurface.Shape.CARD, 222, 125, 300, 700);
    }

    @Test public void aQuickActionIsTheRenderersCapsuleGlass() throws Exception {
        check(GraphiteGlassSurface.Shape.CAPSULE, 340, 72, 120, 880);
    }

    @Test public void theReferenceCardAtItsOwnSizeUsesTheApprovedUnitOne() throws Exception {
        check(GraphiteGlassSurface.Shape.CARD, 300, 152, 500, 300);
    }
}
