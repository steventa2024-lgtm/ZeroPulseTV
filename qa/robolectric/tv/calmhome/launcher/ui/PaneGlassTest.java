package tv.calmhome.launcher.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import tv.calmhome.launcher.AllocationMeter;

/**
 * The panes' rim, measured the way the TCL captures were: the luminance (0 to 255) down a column across a pane's top edge, over
 * black. 3005's panel painter drew a band about 3 px wide at about 90 % white (the TCL measured 214 to 229 on its collection pills and
 * control cards, against 84 for a Home quick action and 117 at the peak of a Home tile); the pane is now one hairline, and costs
 * two draw calls that allocate nothing.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PaneGlassTest {

    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    private static int lum(int p) {
        return Math.round(0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p));
    }

    /** {peak luminance, pixels above half the peak} down the top edge at the pane's centre, over the first 12 rows. */
    private static int[] topEdge(Bitmap b) {
        int x = b.getWidth() / 2, peak = 0;
        for (int y = 0; y < 12; y++) peak = Math.max(peak, lum(b.getPixel(x, y)));
        int wide = 0;
        for (int y = 0; y < 12; y++) if (lum(b.getPixel(x, y)) > peak / 2) wide++;
        return new int[]{peak, wide};
    }

    @Test public void thePaneHasOneHairlineRimNotTheOldThreePixelBand() {
        Bitmap pane = render(new PaneGlass(32f, 1f, 0.85f), 600, 400);
        Bitmap old = render(LiquidGlass.all(LiquidSpec.panel(0.85f), null, 2f, 16f), 600, 400);
        int[] now = topEdge(pane), was = topEdge(old);
        assertTrue("the pane's rim peaks at 120 or less and is at most 2 px wide: peak " + now[0] + ", " + now[1] + " px", now[0] <= 120 && now[1] <= 2);
        assertTrue("(3005's painter, measured the same way: peak " + was[0] + ", " + was[1] + " px)", was[0] > now[0] + 40);
        // The pane is translucent graphite: the black behind it is lifted a little, never replaced.
        int inside = lum(pane.getPixel(300, 200));
        assertTrue("a translucent graphite body over black: " + inside, inside > 6 && inside < 60);
    }

    @Test public void theOpacitySettingsKeepTheirMeaning() {
        int faint = lum(render(new PaneGlass(32f, 1f, 0.5f), 400, 300).getPixel(200, 150)), strong = lum(render(new PaneGlass(32f, 1f, 0.9f), 400, 300).getPixel(200, 150));
        assertTrue("a higher opacity shows more graphite over the wallpaper: " + faint + " < " + strong, strong > faint);
        assertEquals(Math.round(255f * (0.34f + 0.40f * 0.85f)), PaneGlass.topAlpha(0.85f));
    }

    @Test public void aPaneDrawsWithoutAllocatingAndItsCornersFollowItsRadius() {
        PaneGlass pane = new PaneGlass(32f, 1f, 0.7f);
        pane.setBounds(0, 0, 600, 400);
        Bitmap target = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        for (int i = 0; i < 30; i++) pane.draw(canvas);
        Bitmap control = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888);
        android.graphics.Paint plain = new android.graphics.Paint();
        android.graphics.RectF box = new android.graphics.RectF(0, 0, 600, 400);
        Canvas cc = new Canvas(control);
        for (int i = 0; i < 30; i++) { cc.drawRoundRect(box, 32f, 32f, plain); cc.drawRoundRect(box, 32f, 32f, plain); }
        long bytes = AllocationMeter.during(() -> { for (int i = 0; i < 100; i++) pane.draw(canvas); });
        long bare = AllocationMeter.during(() -> { for (int i = 0; i < 100; i++) { cc.drawRoundRect(box, 32f, 32f, plain); cc.drawRoundRect(box, 32f, 32f, plain); } });
        assertTrue("two round-rect draws and nothing else: " + bytes + " bytes against the platform calls' " + bare, bytes <= bare + 4096);
        Bitmap shot = render(pane, 600, 400);
        assertEquals("the corner is rounded: the very corner pixel is not painted", 0, lum(shot.getPixel(1, 1)));
    }
}
