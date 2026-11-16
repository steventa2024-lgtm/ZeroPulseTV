package tv.calmhome.launcher.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.core.LiquidMath;
import tv.calmhome.launcher.core.LiquidTokens;

/**
 * The Liquid Glass surface (Apple's "clear" variant). Painted from back to front:
 *
 *  1. a soft bloom around a focused panel (volume: it seems to glow into the scene);
 *  2. the frosted backdrop: a window onto the lightly blurred wallpaper ({@link LiquidBackdrop});
 *  3. the base fill (very light: frost or smoke), the brand tint (with an underglow for Disney+); a focused light panel then
 *     turns milky and pearlescent;
 *  -- the content (the logo) goes here, between the back and the front part --
 *  4. optical lensing: a diagonal gradient of reflected light that is gone by a third of the way across, and radial
 *     highlights in the two top corners (22 % of the width), so the middle of the glass is left clear; then the bead
 *     highlight and a specular spot on the top edge;
 *  5. the bevel: a faint inner line, and a caustic line along the bottom;
 *  6. the specular rim: a vertical gradient (bright top and corner curves, clear sides, softer bottom lip) with diagonal
 *     highlights from both top corners; focused, a double bezel: a 2 dp white outer edge and a 1 dp inner rim 3 dp in.
 *
 * A panel that holds no content of its own uses {@link #all}; a focusable card uses {@link #layers}: the back part as its
 * background and the front part as its foreground, so the logo sits between them. {@link #setFocusLevel} (0..1) drives
 * focus; the caller animates it.
 */
public final class LiquidGlass extends Drawable {

    private enum Part { BACK, FRONT, ALL }

    /** State shared by the parts of one surface. */
    private static final class State {
        float level;
        final List<LiquidGlass> parts = new ArrayList<>();
    }

    private final LiquidSpec spec;
    private final LiquidBackdrop backdrop;
    private final float dp, radiusDp;
    private final Part part;
    private final State state;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private final int[] where = new int[2];
    private float radius;

    private LiquidGlass(LiquidSpec spec, LiquidBackdrop backdrop, float dp, float radiusDp, Part part, State state) {
        this.spec = spec;
        this.backdrop = backdrop;
        this.dp = dp;
        this.radiusDp = radiusDp;
        this.part = part;
        this.state = state;
        state.parts.add(this);
        if (backdrop != null) backdrop.register(this);
    }

    /** A surface drawn all at once, for a panel behind other views. */
    static LiquidGlass all(LiquidSpec spec, LiquidBackdrop backdrop, float pxPerDp, float radiusDp) {
        return new LiquidGlass(spec, backdrop, pxPerDp, radiusDp, Part.ALL, new State());
    }

    /** The back part (background) and the front part (foreground) of one surface, so content can sit between them. */
    static LiquidGlass[] layers(LiquidSpec spec, LiquidBackdrop backdrop, float pxPerDp, float radiusDp) {
        State st = new State();
        return new LiquidGlass[]{
                new LiquidGlass(spec, backdrop, pxPerDp, radiusDp, Part.BACK, st),
                new LiquidGlass(spec, backdrop, pxPerDp, radiusDp, Part.FRONT, st)};
    }

    /** Focus (or selection), 0..1. Redraws every part of this surface. */
    public void setFocusLevel(float f) {
        state.level = Math.max(0f, Math.min(1f, f));
        for (LiquidGlass d : state.parts) d.invalidateSelf();
    }

    public float focusLevel() {
        return state.level;
    }

    @Override protected void onBoundsChange(Rect b) {
        box.set(b);
        radius = Math.min(radiusDp * dp, Math.min(b.width(), b.height()) / 2f);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
    }

    @Override public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, radius > 0 ? radius : radiusDp * dp);
        outline.setAlpha(1f);
    }

    @Override public void draw(Canvas c) {
        if (box.isEmpty()) return;
        float f = state.level;
        if (part != Part.FRONT) {
            if (f > 0.01f && spec.glowStrength > 0f) bloom(c, f);
            c.save();
            c.clipPath(clip);
            if (spec.frostsBackdrop) window(c);
            tint(c, f);
            c.restore();
        }
        if (part != Part.BACK) {
            c.save();
            c.clipPath(clip);
            lens(c, f);
            c.restore();
            bevel(c, f);
            rim(c, f);
        }
    }

    // ---------------------------------------------------------------------------------------------- back

    /** Soft bloom: rings widening outward, each fainter than the last, so the light falls off smoothly from the edge. */
    private void bloom(Canvas c, float f) {
        float extent = spec.glowExtentDp * dp, strength = spec.glowStrength * f;
        if (strength < 0.01f || extent <= 0f) return;
        final int rings = 30;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int k = rings; k >= 1; k--) {
            float t = k / (float) rings;
            float a = strength * 0.15f * (10f / rings) * (float) Math.pow(1f - t * 0.88f, 2);
            paint.setColor(LiquidMath.scaleAlpha(spec.glow | 0xFF000000, Math.max(0f, Math.min(1f, a))));
            paint.setStrokeWidth(2f * extent * t);
            c.drawRoundRect(box, radius, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** The panel's window onto the shared backdrop, or a frosted gradient where there is none yet. */
    private void window(Canvas c) {
        Rect b = getBounds();
        if (backdrop != null && backdrop.ready()) {
            View host = host();
            if (host != null) {
                host.getLocationOnScreen(where);
                c.save();
                c.translate(b.left, b.top);
                backdrop.drawWindow(c, where[0] + b.left, where[1] + b.top);
                c.restore();
                return;
            }
        }
        shade(new LinearGradient(0, box.top, 0, box.bottom, new int[]{0x40DCE6FF, 0x2AB4C3E6, 0x66101A2B},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, paint);
        paint.setShader(null);
    }

    /**
     * Sets a gradient to paint with. A Paint multiplies its shader's colours by its own alpha, so the colour is reset to
     * opaque first: otherwise a gradient drawn after a translucent fill comes out far fainter than it was specified.
     */
    private void shade(Shader shader) {
        paint.setColor(0xFF000000);
        paint.setShader(shader);
    }

    private View host() {
        Callback cb = getCallback();
        return cb instanceof View ? (View) cb : null;
    }

    private void tint(Canvas c, float f) {
        float alpha = spec.tintAlpha + (spec.tintAlphaFocused - spec.tintAlpha) * f;
        if (spec.fill != 0) {
            paint.setColor(spec.fill);
            c.drawRect(box, paint);
        }
        if ((spec.tintTop >>> 24) != 0 || (spec.tintBottom >>> 24) != 0) {
            shade(new LinearGradient(0, box.top, 0, box.bottom, LiquidMath.scaleAlpha(spec.tintTop, alpha),
                    LiquidMath.scaleAlpha(spec.tintBottom, alpha), Shader.TileMode.CLAMP));
            c.drawRect(box, paint);
            paint.setShader(null);
        }
        if (spec.underglow != 0) underglow(c, f);
        if (spec.pearl && f > 0.01f) pearl(c, f);
    }

    /** Light rising from the bottom centre of the glass, stronger when focused. */
    private void underglow(Canvas c, float f) {
        float w = box.width();
        shade(new RadialGradient(box.centerX(), box.top + box.height() * 1.04f, w * 0.62f,
                new int[]{LiquidMath.scaleAlpha(spec.underglow, 0.62f + 0.30f * f), LiquidMath.scaleAlpha(spec.underglow, 0.22f + 0.12f * f), 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, paint);
        paint.setShader(null);
    }

    /** Milky pearlescent glass: a frosted white base, with a diagonal shift from warm rose (bottom-left) to icy cyan (top-right). */
    private void pearl(Canvas c, float f) {
        paint.setColor(LiquidMath.scaleAlpha(LiquidTokens.PEARL_BASE, f));
        c.drawRect(box, paint);
        shade(new LinearGradient(box.left, box.bottom, box.right, box.top,
                new int[]{LiquidMath.scaleAlpha(LiquidTokens.PEARL_WARM, f), LiquidMath.scaleAlpha(0x26DDD3FF, f), LiquidMath.scaleAlpha(LiquidTokens.PEARL_COOL, f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, paint);
        paint.setShader(null);
    }

    // ---------------------------------------------------------------------------------------------- front

    private void lens(Canvas c, float f) {
        shade(new LinearGradient(box.left, box.top, box.right, box.bottom,
                new int[]{LiquidTokens.SHEEN_START, LiquidTokens.SHEEN_END, LiquidTokens.SHEEN_END},
                new float[]{0f, LiquidTokens.SHEEN_FADE_BY, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, paint);
        if (spec.corners > 0f) {
            float reach = LiquidMath.cornerReach(box.width(), box.height());
            float inset = Math.min(box.width(), box.height()) * 0.10f;
            int hot = LiquidMath.scaleAlpha(LiquidTokens.CORNER_GLOW, spec.corners);
            for (float cx : new float[]{box.left + inset, box.right - inset}) {
                shade(new RadialGradient(cx, box.top + inset, reach, hot, LiquidTokens.CORNER_GLOW_END, Shader.TileMode.CLAMP));
                c.drawRect(box, paint);
            }
        }
        paint.setShader(null);
        if (spec.bead) bead(c, f);
        if (spec.specular) specular(c, f);
    }

    /** A convex bead: a bright crescent under the top edge and a lift of light at the bottom. */
    private void bead(Canvas c, float f) {
        float w = box.width(), h = box.height();
        RectF oval = new RectF(box.left + w * 0.10f, box.top + h * 0.08f, box.left + w * 0.90f, box.top + h * 0.54f);
        shade(new LinearGradient(0, box.top + h * 0.08f, 0, box.top + h * 0.54f,
                LiquidMath.scaleAlpha(0xFFFFFFFF, 0.50f + 0.10f * f), LiquidMath.scaleAlpha(0xFFFFFFFF, 0.03f), Shader.TileMode.CLAMP));
        c.drawOval(oval, paint);
        shade(new LinearGradient(0, box.top + h * 0.60f, 0, box.bottom, 0x00FFFFFF,
                LiquidMath.scaleAlpha(0xFFFFFFFF, 0.16f + 0.06f * f), Shader.TileMode.CLAMP));
        c.drawRect(box.left, box.top + h * 0.60f, box.right, box.bottom, paint);
        paint.setShader(null);
    }

    /** A small, bright highlight on the top edge, off to the right, like a window reflected in the glass. */
    private void specular(Canvas c, float f) {
        float cx = box.left + box.width() * 0.72f, cy = box.top + box.height() * 0.05f, r = box.width() * 0.24f;
        c.save();
        c.scale(1f, 0.26f, cx, cy);
        shade(new RadialGradient(cx, cy, r, LiquidMath.scaleAlpha(0xFFFFFFFF, 0.40f + 0.32f * f), 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        c.restore();
    }

    /** The glass's thickness: a faint inner line, and a caustic line along the bottom. */
    private void bevel(Canvas c, float f) {
        float inset = spec.bevelInsetDp * dp, r = Math.max(0f, radius - inset);
        RectF in = new RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset);
        if (in.width() <= 0 || in.height() <= 0) return;
        float fade = spec.doubleBezel ? 1f - f : 1f;      // the double bezel's inner rim takes over when focused
        paint.setStyle(Paint.Style.STROKE);
        if (spec.bevel && fade > 0.01f) {
            paint.setStrokeWidth(Math.max(1f, dp));
            shade(new LinearGradient(in.left, in.top, in.right, in.bottom,
                    new int[]{LiquidMath.scaleAlpha(0xFFFFFFFF, (0.10f + 0.30f * f) * fade), LiquidMath.scaleAlpha(0xFFFFFFFF, (0.02f + 0.04f * f) * fade),
                            LiquidMath.scaleAlpha(0xFFFFFFFF, (0.05f + 0.10f * f) * fade)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(in, r, r, paint);
        }
        if (spec.caustic) {
            paint.setStrokeWidth(Math.max(1f, 1.4f * dp));
            shade(new LinearGradient(0, in.top, 0, in.bottom,
                    new int[]{0x00FFFFFF, 0x00FFFFFF, LiquidMath.scaleAlpha(spec.causticTint | 0xFF000000, 0.26f + 0.40f * f)},
                    new float[]{0f, 0.66f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(in, r, r, paint);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * The specular rim: a vertical gradient (bright top edge, nearly clear sides, a softer bottom lip), with a diagonal
     * highlight from each top corner on top of it. Focus brightens it; a {@code doubleBezel} panel then also gets a 2 dp
     * white outer edge and a crisp 1 dp inner rim 3 dp in, with clear glass between the two.
     */
    private void rim(Canvas c, float f) {
        float width = Math.max(1f, spec.rimWidthDp * dp * (1f + 0.5f * f));
        int top = withAlpha(spec.rimTop, lerp(alpha(spec.rimTop), 1f, f));
        int mid = withAlpha(spec.rimMid, lerp(alpha(spec.rimMid), Math.max(alpha(spec.rimMid), 0.40f), f));
        int bottom = withAlpha(spec.rimBottom, lerp(alpha(spec.rimBottom), Math.max(alpha(spec.rimBottom), 0.70f), f));
        RectF edge = new RectF(box.left + width / 2f, box.top + width / 2f, box.right - width / 2f, box.bottom - width / 2f);
        float er = Math.max(0f, radius - width / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        shade(new LinearGradient(0, box.top, 0, box.bottom, new int[]{top, mid, mid, bottom},
                new float[]{0f, spec.rimMidFrom, spec.rimMidTo, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(edge, er, er, paint);
        int hi = LiquidMath.scaleAlpha(0xFFFFFFFF, 0.30f + 0.25f * f);
        shade(new LinearGradient(box.left, box.top, box.right, box.bottom, new int[]{hi, 0x00FFFFFF},
                new float[]{0f, 0.30f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(edge, er, er, paint);
        shade(new LinearGradient(box.right, box.top, box.left, box.bottom, new int[]{hi, 0x00FFFFFF},
                new float[]{0f, 0.30f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(edge, er, er, paint);
        paint.setShader(null);
        if (spec.doubleBezel && f > 0.01f) {
            float outer = LiquidTokens.BEZEL_OUTER_DP * dp;
            paint.setStrokeWidth(outer);
            paint.setColor(LiquidMath.scaleAlpha(LiquidTokens.BEZEL_OUTER, f));
            RectF o = new RectF(box.left + outer / 2f, box.top + outer / 2f, box.right - outer / 2f, box.bottom - outer / 2f);
            float or = Math.max(0f, radius - outer / 2f);
            c.drawRoundRect(o, or, or, paint);
            float gap = LiquidTokens.BEZEL_INSET_DP * dp, line = Math.max(1f, LiquidTokens.BEZEL_INNER_DP * dp);
            paint.setStrokeWidth(line);
            paint.setColor(LiquidMath.scaleAlpha(LiquidTokens.BEZEL_INNER, f));
            RectF in = new RectF(box.left + gap, box.top + gap, box.right - gap, box.bottom - gap);
            float ir = Math.max(0f, radius - gap);
            c.drawRoundRect(in, ir, ir, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static float alpha(int argb) {
        return (argb >>> 24) / 255f;
    }

    private static int withAlpha(int argb, float a) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, Math.round(a * 255f))) << 24);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter colorFilter) { }

    @SuppressWarnings("deprecation")
    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
