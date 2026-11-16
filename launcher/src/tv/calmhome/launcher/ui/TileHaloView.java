package tv.calmhome.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.LruCache;

import tv.calmhome.launcher.core.GlassMaterial;

/**
 * What lifts a focused tile off the glass without touching the glass: a soft shadow under it and a restrained glow in the app's own
 * colour around it. The tile draws it first in its own draw pass, behind its glass, and outside its bounds (its parents do not clip),
 * so it adds no view, never changes the layout and never moves a neighbour.
 *
 * Both are pre-rendered once per tile size into small alpha-only masks, shared by every tile of that size, with the tile's own area
 * cut out so the glow never tints the glass or the artwork. A focus change only changes the paint's alpha and colour: no blur, no
 * bitmap work and no glass re-render on a focus move.
 */
final class TileHaloView {
    /** How far the glow reaches beyond the tile, as a share of its height. */
    static final float SPREAD = 0.22f;
    /** At full focus: the glow's and the shadow's opacity (restrained: the banner and the glass stay as they are). */
    static final float GLOW_ALPHA = 0.42f, SHADOW_ALPHA = 0.5f;

    private static final LruCache<String, Bitmap[]> MASKS = new LruCache<String, Bitmap[]>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap[] v) {
            return v[0].getByteCount() + v[1].getByteCount();
        }
    };

    private final Paint glow = new Paint(Paint.FILTER_BITMAP_FLAG), shadow = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap[] masks;
    private int maskW, maskH, pad;
    private float level;
    private int color = tv.calmhome.launcher.core.BrandColor.NEUTRAL;
    private boolean enabled = true;

    TileHaloView() {
        shadow.setColor(0xFF000000);
        glow.setColor(color);
    }

    /** The glow's colour (the app's own, softened); 0 keeps a cool neutral. */
    void setColor(int argb) {
        color = argb == 0 ? tv.calmhome.launcher.core.BrandColor.NEUTRAL : argb;
        glow.setColor(color);
    }

    int color() {
        return color;
    }

    void setHaloEnabled(boolean on) {
        enabled = on;
    }

    boolean haloEnabled() {
        return enabled;
    }

    /** 0 idle (nothing drawn) to 1 focused. Returns true when it changed (the tile then redraws). */
    boolean setLevel(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == level) return false;
        level = v;
        return true;
    }

    float level() {
        return level;
    }

    /** Draws the halo for a {@code w x h} tile at the canvas origin (masks come from the shared cache; built once per size). */
    void draw(Canvas canvas, int w, int h) {
        if (!enabled || level <= 0.001f || w <= 0 || h <= 0) return;
        if (masks == null || w != maskW || h != maskH) {
            masks = masksFor(w, h);
            maskW = w;
            maskH = h;
            pad = Math.round(h * SPREAD);
        }
        shadow.setAlpha(Math.round(255 * SHADOW_ALPHA * level));
        glow.setAlpha(Math.round(255 * GLOW_ALPHA * level));
        canvas.drawBitmap(masks[1], -pad, -pad, shadow);
        canvas.drawBitmap(masks[0], -pad, -pad, glow);
    }

    static int cachedSizes() {
        return MASKS.snapshot().size();
    }

    /** {glow, shadow} alpha masks for a {@code w x h} tile, the tile's own rounded area cut out of both. */
    static Bitmap[] masksFor(int w, int h) {
        String key = w + "x" + h;
        Bitmap[] cached = MASKS.get(key);
        if (cached != null) return cached;
        int pad = Math.round(h * SPREAD);
        int bw = w + 2 * pad, bh = h + 2 * pad;
        float r = GlassMaterial.radius(h, false);
        RectF tile = new RectF(pad, pad, pad + w, pad + h);
        Bitmap glowMask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, h * 0.16f), BlurMaskFilter.Blur.NORMAL));
        Canvas c = new Canvas(glowMask);
        c.drawRoundRect(tile, r, r, p);
        Bitmap shadowMask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, h * 0.09f), BlurMaskFilter.Blur.NORMAL));
        Canvas s = new Canvas(shadowMask);
        RectF below = new RectF(tile);
        below.offset(0, h * 0.06f);
        below.inset(w * 0.04f, 0);
        s.drawRoundRect(below, r, r, p);
        // The tile's own area is cut out: the glow and shadow are around the tile, never on its glass or artwork.
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        Path hole = new Path();
        hole.addRoundRect(tile, r, r, Path.Direction.CW);
        c.drawPath(hole, clear);
        s.drawPath(hole, clear);
        Bitmap[] out = {glowMask, shadowMask};
        MASKS.put(key, out);
        return out;
    }
}
