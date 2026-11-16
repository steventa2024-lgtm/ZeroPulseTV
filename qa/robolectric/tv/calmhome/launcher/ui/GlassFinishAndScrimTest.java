package tv.calmhome.launcher.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import tv.calmhome.launcher.core.ContrastProbe;
import tv.calmhome.launcher.data.AppArt;

/**
 * The cheap parts of the glass finish and the scrim, proved against what they replaced: the scrim's column draws the same
 * gradient (within rounding) and allocates nothing when it draws; the finish is two shared pictures per size, keeps off the
 * artwork (a black banner stays exactly black), and agrees with the vector finish at both ends and closely in between.
 * Native graphics, so the pixels are real.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class GlassFinishAndScrimTest {

    private static final int TILE_W = 222, TILE_H = 139;

    private Context c;

    @Before public void setUp() {
        c = RuntimeEnvironment.getApplication();
        GlassFinishView.clearCacheForTests();
    }

    private static void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    private static Bitmap draw(View v, int background) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(background);
        v.draw(canvas);
        return b;
    }

    /** Bytes the current thread allocated while {@code r} ran. */
    private static long allocated(Runnable r) {
        return tv.calmhome.launcher.AllocationMeter.during(r);
    }

    // ------------------------------------------------------------------ the scrim

    @Test public void theScrimColumnPaintsTheSameRampTheGradientDid() {
        AdaptiveScrimView scrim = new AdaptiveScrimView(c);
        layout(scrim, 192, 108);
        float[] plan = {0.14f, 0.30f, 0.42f, 0.22f};
        scrim.setPlan(plan, 0);
        Bitmap got = draw(scrim, Color.rgb(128, 140, 150));

        int stops = 33;
        int[] colors = new int[stops];
        float[] positions = new float[stops];
        for (int i = 0; i < stops; i++) {
            positions[i] = i / (float) (stops - 1);
            float a = Math.max(0f, Math.min(1f, ContrastProbe.alphaAt(plan, positions[i])));
            colors[i] = (Math.round(a * 255f) << 24) | (ContrastProbe.NAVY & 0xFFFFFF);
        }
        Bitmap want = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888);
        Canvas wc = new Canvas(want);
        wc.drawColor(Color.rgb(128, 140, 150));
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, 108, colors, positions, Shader.TileMode.CLAMP));
        wc.drawRect(0, 0, 192, 108, p);

        int worst = 0;
        long sum = 0;
        for (int y = 0; y < 108; y += 3) {
            for (int x = 0; x < 192; x += 16) {
                int a = got.getPixel(x, y), b = want.getPixel(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    int d = Math.abs(((a >> shift) & 255) - ((b >> shift) & 255));
                    worst = Math.max(worst, d);
                    sum += d;
                }
            }
        }
        double mean = sum / (double) (36 * 12 * 3);
        assertTrue("the ramp matches the gradient it replaced: worst " + worst + " mean " + mean, worst <= 2 && mean < 0.7);
    }

    /** The 3005 scrim's draw, kept here as the measuring stick: a new gradient (and its arrays) on every draw. */
    private static final class LegacyScrim extends View {
        private final Paint paint = new Paint();
        private final int[] colors = new int[33];
        private final float[] positions = new float[33];
        private float[] shown = ContrastProbe.FLOOR.clone();

        LegacyScrim(Context c) {
            super(c);
            for (int i = 0; i < 33; i++) positions[i] = i / 32f;
        }

        @Override protected void onDraw(Canvas canvas) {
            for (int i = 0; i < 33; i++) {
                float a = Math.max(0f, Math.min(1f, ContrastProbe.alphaAt(shown, positions[i])));
                colors[i] = (Math.round(a * 255f) << 24) | (ContrastProbe.NAVY & 0xFFFFFF);
            }
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), colors, positions, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

    @Test public void theScrimAllocatesNothingWhenItDrawsEvenWhileItEases() {
        AdaptiveScrimView scrim = new AdaptiveScrimView(c);
        layout(scrim, 192, 108);
        LegacyScrim legacy = new LegacyScrim(c);
        layout(legacy, 192, 108);
        Bitmap target = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        scrim.setPlan(new float[]{0.2f, 0.2f, 0.2f, 0.2f}, 0);
        for (int i = 0; i < 30; i++) { scrim.draw(canvas); legacy.draw(canvas); }          // warm up
        long steady = allocated(() -> { for (int i = 0; i < 100; i++) scrim.draw(canvas); });
        // Robolectric's own bridging allocates a little on every platform call (a canvas draw, a bitmap write), so the measure is
        // relative: a control that makes just those platform calls and nothing else, and the 3005 draw that built a gradient each time.
        Bitmap column = Bitmap.createBitmap(1, 33, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[33];
        Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.Rect from = new android.graphics.Rect(0, 0, 1, 33);
        android.graphics.RectF to = new android.graphics.RectF(0, -1.7f, 192, 109.7f);
        for (int i = 0; i < 30; i++) { column.setPixels(pixels, 0, 1, 0, 0, 1, 33); canvas.drawBitmap(column, from, to, plain); }
        long[] eased = new long[100], was = new long[100], control = new long[100];
        for (int i = 0; i < 100; i++) {
            float a = 0.14f + 0.002f * i;
            scrim.setPlan(new float[]{a, a, a, a}, 0);                    // a new plan marks the ramp dirty (outside the measured draw)
            legacy.shown = new float[]{a, a, a, a};
            eased[i] = allocated(() -> scrim.draw(canvas));
            was[i] = allocated(() -> legacy.draw(canvas));
            control[i] = allocated(() -> { column.setPixels(pixels, 0, 1, 0, 0, 1, 33); canvas.drawBitmap(column, from, to, plain); });
        }
        // (A median, because the JVM's per-thread counter shows an occasional one-off jump unrelated to the code under test.)
        long easedMedian = median(eased), wasMedian = median(was), controlMedian = median(control);
        assertTrue("a steady scrim allocates nothing of its own: " + steady + " bytes in 100 draws", steady < 4096);
        assertTrue("while easing a draw costs what its bitmap write and canvas call cost, nothing of its own: median " + easedMedian
                + " bytes against " + controlMedian + " for the bare calls", easedMedian <= controlMedian + 16);
        assertTrue("3005's draw cost a median of " + wasMedian + " bytes (the scrim's " + easedMedian + ")", easedMedian * 4 < wasMedian);
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    // ------------------------------------------------------------------ the finish

    private ZeroPulseGlassAppTile tile(int bannerColor) {
        Bitmap banner = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        banner.eraseColor(bannerColor);
        AppArt.Art art = AppArt.fromBanner(new BitmapDrawable(c.getResources(), banner), "test", TILE_W, TILE_H);
        assertNotNull(art);
        GlassKit kit = new GlassKit(1920, 1080, 2f);
        ZeroPulseGlassAppTile t = new ZeroPulseGlassAppTile(c, kit, TILE_H, 0);
        t.setContent(new TileArtView(c, art));
        layout(t, TILE_W, TILE_H);
        return t;
    }

    @Test public void aRowOfIdenticalTilesSharesOnePairOfFinishPictures() {
        ZeroPulseGlassAppTile first = tile(Color.BLACK);
        assertEquals("the first tile paints idle and focus once", 1, GlassFinishView.picturesBuilt());
        for (int i = 0; i < 7; i++) {
            ZeroPulseGlassAppTile next = tile(Color.BLACK);
            assertSame("every tile of the same size, shape and artwork box shares the pictures", first.finishPictures(), next.finishPictures());
        }
        assertEquals("eight tiles, one pair", 1, GlassFinishView.picturesBuilt());
        ZeroPulseGlassQuickAction action = new ZeroPulseGlassQuickAction(c, new Theme(c, new tv.calmhome.launcher.data.Prefs(c), true),
                new GlassKit(1920, 1080, 2f), tv.calmhome.launcher.ui.Glyph.Kind.GRID, "All Apps", 72, 0);
        layout(action, 360, 72);
        assertEquals("a different shape (the capsule) paints its own", 2, GlassFinishView.picturesBuilt());
    }

    @Test public void theFinishStaysOnTheFrameAndLeavesTheArtworkAlone() {
        ZeroPulseGlassAppTile t = tile(Color.BLACK);
        GlassFinishView.Pictures p = t.finishPictures();
        assertNotNull(p);
        TileArtView art = t.artView();
        android.graphics.Rect drawn = art.drawnRect();
        int pad = 0;                                    // the artwork fills the tile inside a hairline rim: its view starts at the tile's own corner
        int left = pad + drawn.left, top = pad + drawn.top, right = pad + drawn.right, bottom = pad + drawn.bottom;
        int r = Math.round(tv.calmhome.launcher.core.TileArt.bannerRadius(TILE_H)) + 1;      // the banner's own corners are rounded: the hole's are too
        for (Bitmap picture : new Bitmap[]{p.idle, p.focus}) {
            long insideAlpha = 0;
            for (int y = top + r; y < bottom - r; y++) for (int x = left + 3; x < right - 3; x++) insideAlpha += Color.alpha(picture.getPixel(x, y));
            for (int y = top + 3; y < bottom - 3; y++) for (int x = left + r; x < right - r; x++) insideAlpha += Color.alpha(picture.getPixel(x, y));
            assertEquals("not one finish pixel over the artwork's rectangle", 0L, insideAlpha);
            int frameAlpha = 0;
            for (int y = 1; y <= 3; y++) for (int x = 20; x < TILE_W - 20; x++) frameAlpha = Math.max(frameAlpha, Color.alpha(picture.getPixel(x, y)));
            assertTrue("but the frame itself carries the finish (the top lip): " + frameAlpha, frameAlpha > 20);
        }
        // What 3005 did: the same finish over the whole tile lifts a black banner.
        Bitmap lifted = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(lifted);
        lc.drawColor(Color.BLACK);
        GlassFinishView.paintFinish(lc, TILE_W, TILE_H, false, 1f, 0f, 0, 0, 0f);
        int cx = TILE_W / 2, cy = TILE_H / 2;
        assertTrue("(3005: a black banner under the full-tile finish reads " + Color.red(lifted.getPixel(cx, cy)) + " of 255)", Color.red(lifted.getPixel(cx, cy)) >= 8);
    }

    @Test public void aBlackBannerStaysExactlyBlackUnderTheWholeControlIdleAndFocused() {
        ZeroPulseGlassAppTile t = tile(Color.BLACK);
        for (float level : new float[]{0f, 1f}) {
            ((GlassFinishView) t.finishLayer()).setLevel(level);          // the finish at idle, then at the focus material
            Bitmap shot = draw(t, Color.rgb(90, 100, 110));
            int[][] points = {{TILE_W / 2, TILE_H / 2}, {TILE_W / 2 - 40, TILE_H / 2 - 20}, {TILE_W / 2 + 40, TILE_H / 2 + 20}};
            for (int[] at : points) {
                int px = shot.getPixel(at[0], at[1]);
                assertEquals("black banner pixel at " + at[0] + "," + at[1] + " (level " + level + ")", 0, Color.red(px) + Color.green(px) + Color.blue(px));
                assertEquals(255, Color.alpha(px));
            }
        }
    }

    @Test public void theFinishPicturesAgreeWithTheVectorFinishAtBothEndsAndCloselyBetween() {
        GlassFinishView view = new GlassFinishView(c, false, 1f);
        layout(view, TILE_W, TILE_H);
        GlassFinishView.Pictures p = view.pictures();
        assertNotNull(p);
        Bitmap direct0 = GlassFinishView.render(TILE_W, TILE_H, false, 1f, 0f, 0, 0, 0f);
        Bitmap direct1 = GlassFinishView.render(TILE_W, TILE_H, false, 1f, 1f, 0, 0, 0f);
        assertTrue("idle is the vector finish at level 0", p.idle.sameAs(direct0));
        assertTrue("focus is the vector finish at level 1", p.focus.sameAs(direct1));

        view.setLevel(0.5f);
        Bitmap mixed = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(mixed));
        Bitmap exact = GlassFinishView.render(TILE_W, TILE_H, false, 1f, 0.5f, 0, 0, 0f);
        long sum = 0;
        int worst = 0;
        for (int y = 0; y < TILE_H; y++) {
            for (int x = 0; x < TILE_W; x++) {
                int d = Math.abs(Color.alpha(mixed.getPixel(x, y)) - Color.alpha(exact.getPixel(x, y)));
                worst = Math.max(worst, d);
                sum += d;
            }
        }
        double mean = sum / (double) (TILE_W * TILE_H);
        assertTrue("half way between idle and focus the pictures differ from the exact finish by under 1.5 of 255 on average (worst " + worst + "): " + mean,
                mean < 1.5 && worst < 60);
    }

    @Test public void theFinishAndTheArtworkAllocateNothingWhenTheyDraw() {
        ZeroPulseGlassAppTile t = tile(Color.rgb(20, 20, 24));
        Bitmap target = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        View finish = t.finishLayer();
        View art = t.artView();
        for (int i = 0; i < 20; i++) { finish.draw(canvas); art.draw(canvas); }
        long bytes = allocated(() -> {
            for (int i = 0; i < 100; i++) {
                ((GlassFinishView) finish).setLevel((i % 10) / 10f);      // a focus animation in progress: every frame draws at a new level
                finish.draw(canvas);
                art.draw(canvas);
            }
        });
        // 3005 painted the vector finish on every frame: shaders, paths and paints (about a hundred objects per frame). Measured in the same
        // harness, whose own call bridging allocates a little per canvas call, the cached pictures must cost a small fraction.
        long was = allocated(() -> {
            for (int i = 0; i < 100; i++) {
                GlassFinishView.paintFinish(canvas, TILE_W, TILE_H, false, 1f, (i % 10) / 10f, 0, 0, 0f);
                art.draw(canvas);
            }
        });
        assertTrue("the cached finish and the tile art cost under a fifth of the vector finish: " + bytes + " bytes against " + was + " in 100 frames",
                bytes * 5 < was);
    }

    @Test public void aControlWithNoArtworkPutsTheFinishOverItsWholeFace() {
        GlassKit kit = new GlassKit(1920, 1080, 2f);
        ZeroPulseGlassAppTile notice = new ZeroPulseGlassAppTile(c, kit, TILE_H, 0);
        layout(notice, TILE_W, TILE_H);
        GlassFinishView.Pictures p = notice.finishPictures();
        assertNotNull(p);
        int alpha = Color.alpha(p.idle.getPixel(TILE_W / 2, TILE_H - 8));
        assertTrue("with nothing to protect, the depth shading covers the face: " + alpha, alpha > 10);
        assertTrue(((FrameLayout) notice).getChildCount() == 3);
    }
}
