package tv.calmhome.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import tv.calmhome.launcher.core.LiquidMath;
import tv.calmhome.launcher.core.LiquidTokens;

/**
 * The frosted backdrop every glass panel draws a window of: a small copy (1/4 size) of the current wallpaper, lightly
 * blurred once off the UI thread, with the same navy scrims that sit over the sharp wallpaper baked in, so a panel
 * refracts the scene it really sits over. One instance per activity; glass panels register to be redrawn when it changes.
 */
public final class LiquidBackdrop {
    private final int screenW, screenH;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Set<LiquidGlass> users = Collections.newSetFromMap(new WeakHashMap<LiquidGlass, Boolean>());
    private Bitmap base, composed;
    private int dimPercent;

    public LiquidBackdrop(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
    }

    /** The lightly blurred small copy of the wallpaper now on screen (the caller keeps no reference to it). */
    public void set(Bitmap blurred, int dimPercent) {
        base = blurred;
        this.dimPercent = dimPercent;
        rebuild();
    }

    public void setDim(int percent) {
        if (percent == dimPercent) return;
        dimPercent = percent;
        rebuild();
    }

    boolean ready() {
        return composed != null;
    }

    void register(LiquidGlass user) {
        users.add(user);
    }

    private void rebuild() {
        if (base == null || base.isRecycled()) return;
        Bitmap out = compose(base, base.getWidth(), base.getHeight(), dimPercent);
        composed = out;                       // the old copy is left to the garbage collector: a frame may still be drawing it
        for (LiquidGlass user : users.toArray(new LiquidGlass[0])) user.invalidateSelf();
    }

    /**
     * The scene as it looks on screen: {@code source} centre-cropped to {@code w} x {@code h} (as the wallpaper itself is),
     * under the indigo wash, the user's dim and the navy scrims. A new bitmap; {@code source} is only read.
     */
    static Bitmap compose(Bitmap source, int w, int h, int dimPercent) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float[] r = LiquidMath.coverRect(source.getWidth(), source.getHeight(), w, h);
        c.drawBitmap(source, null, new RectF(r[0], r[1], r[2], r[3]), new Paint(Paint.FILTER_BITMAP_FLAG));
        c.drawColor(LiquidTokens.WASH);
        if (dimPercent > 0) c.drawColor(Color.argb(Math.round(2.55f * dimPercent), 0, 0, 0));
        Paint p = new Paint();
        float topH = h * LiquidTokens.TOP_SCRIM_FRACTION;
        p.setShader(new LinearGradient(0, 0, 0, topH, new int[]{LiquidTokens.TOP_SCRIM_A, LiquidTokens.TOP_SCRIM_B, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, topH, p);
        float botH = h * LiquidTokens.BOTTOM_SCRIM_FRACTION;
        p.setShader(new LinearGradient(0, h - botH, 0, h, new int[]{0x00000000, LiquidTokens.BOTTOM_SCRIM_A, LiquidTokens.BOTTOM_SCRIM_B},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, h - botH, w, h, p);
        return out;
    }

    /**
     * Draws the part of the backdrop that lies behind a panel whose top-left corner is at ({@code screenX}, {@code screenY})
     * on the screen, into the canvas whose origin is that corner. The caller has clipped to the panel's shape.
     */
    void drawWindow(Canvas canvas, float screenX, float screenY) {
        if (composed == null) return;
        float[] r = LiquidMath.coverRect(composed.getWidth(), composed.getHeight(), screenW, screenH);
        canvas.save();
        canvas.translate(-screenX, -screenY);
        canvas.drawBitmap(composed, null, new RectF(r[0], r[1], r[2], r[3]), bitmapPaint);
        canvas.restore();
    }
}
