package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import tv.calmhome.launcher.core.LiquidBlur;
import tv.calmhome.launcher.core.LiquidInk;
import tv.calmhome.launcher.core.LiquidKey;
import tv.calmhome.launcher.core.LiquidMath;

/**
 * An app's logo, keyed out of its banner (only the logo; the backdrop it sat on is gone), drawn on a glass tile. It is
 * only ever scaled uniformly (fit), centred, and shrunk just as far as needed to keep {@code padX} free left and right and
 * {@code padY} above and below, so nothing is stretched or touches the glass's edge. An optional soft glow in the logo's
 * own shape (Hulu's green) sits under it.
 */
public final class LiquidLogoView extends View {
    private static final float GLOW_GAIN = 0.9f, GLOW_MAX = 125f;

    private final Bitmap logo;
    private final Bitmap glow;
    private final float[] box;
    private final float padX, padY;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    public LiquidLogoView(Context c, LiquidKey.Logo key, int glowColor, float padXPx, float padYPx) {
        this(c, key, glowColor, padXPx, padYPx, false);
    }

    /** {@code liftDarkInk}: on the dark graphite glass a dark wordmark is lifted to read (see {@link LiquidInk}); on light glass it is left as it is. */
    public LiquidLogoView(Context c, LiquidKey.Logo key, int glowColor, float padXPx, float padYPx, boolean liftDarkInk) {
        super(c);
        int[] ink = liftDarkInk ? LiquidInk.liftedIfDark(key.pixels, key.logoLuminance) : key.pixels;
        logo = Bitmap.createBitmap(ink, key.width, key.height, Bitmap.Config.ARGB_8888);
        glow = glowColor == 0 ? null : makeGlow(key, glowColor);
        box = key.box;
        padX = padXPx;
        padY = padYPx;
    }

    /** The logo's outline blurred and tinted: a soft glow of {@code color} in the logo's own shape, same size as the logo. */
    static Bitmap makeGlow(LiquidKey.Logo key, int color) {
        int w = key.width, h = key.height;
        int[] shape = new int[w * h];
        for (int i = 0; i < shape.length; i++) {
            int a = key.pixels[i] >>> 24;
            shape[i] = 0xFF000000 | (a << 16) | (a << 8) | a;
        }
        int[] blurred = LiquidBlur.blur(shape, w, h, Math.max(2, w / 26), 3);
        int rgb = color & 0xFFFFFF;
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) out[i] = (Math.round(Math.min(GLOW_MAX, (blurred[i] & 255) * GLOW_GAIN)) << 24) | rgb;
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float[] fit = LiquidMath.logoFit(box, w, h, padX, padY);
        float k = Math.min(w / (float) logo.getWidth(), h / (float) logo.getHeight());     // ContentScale.Fit
        float dw = logo.getWidth() * k, dh = logo.getHeight() * k;
        dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
        canvas.save();
        canvas.translate(w / 2f + fit[1], h / 2f + fit[2]);
        canvas.scale(fit[0], fit[0]);
        canvas.translate(-w / 2f, -h / 2f);
        if (glow != null) canvas.drawBitmap(glow, null, dst, paint);
        canvas.drawBitmap(logo, null, dst, paint);
        canvas.restore();
    }
}
