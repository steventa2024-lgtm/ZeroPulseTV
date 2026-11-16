package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import tv.calmhome.launcher.core.ContrastProbe;

/**
 * The adaptive contrast layer over the wallpaper: a navy scrim whose strength follows the screen's four bands (the header, the
 * greeting, the app row, the quick actions), each only as dark as the wallpaper behind it needs for white text to stay readable
 * ({@link ContrastProbe}), with a smooth ramp between bands. A dark picture is barely touched; a bright one is darkened where
 * it is bright. When a new wallpaper arrives the scrim eases to its plan over the wallpaper's own crossfade.
 *
 * It paints nothing interactive: no focus, no clicks, hidden from accessibility.
 *
 * <p>Drawing allocates nothing. The vertical ramp lives in one {@value #STOPS}-pixel column (one pixel per stop, the colour's
 * alpha being the scrim's alpha there) that is stretched over the screen with bilinear filtering, which interpolates between the
 * stops exactly as the gradient it replaces did. Easing to a new plan rewrites the column's pixels in place; the destination
 * rectangle is set once, and only when the view is resized. (3005 built a new gradient on every frame of every fade.) The column
 * is drawn as a bitmap rather than through a bitmap shader because a shader is rebuilt, natively, each time its bitmap changes.
 */
public final class AdaptiveScrimView extends View {
    private static final int STOPS = 33;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private float[] from = ContrastProbe.FLOOR.clone(), to = ContrastProbe.FLOOR.clone(), shown = ContrastProbe.FLOOR.clone();
    private ValueAnimator animator;
    private final int[] colors = new int[STOPS];
    private final float[] positions = new float[STOPS];
    private final Bitmap column = Bitmap.createBitmap(1, STOPS, Bitmap.Config.ARGB_8888);
    private final Rect source = new Rect(0, 0, 1, STOPS);
    private final RectF target = new RectF();
    private boolean dirty = true;

    public AdaptiveScrimView(Context c) {
        super(c);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < STOPS; i++) positions[i] = i / (float) (STOPS - 1);
    }

    /** Eases to a new plan (immediately when {@code ms} is 0). */
    public void setPlan(float[] plan, int ms) {
        if (plan == null || plan.length != ContrastProbe.BANDS) return;
        if (animator != null) animator.cancel();
        from = shown.clone();
        to = plan.clone();
        if (ms <= 0 || !isAttachedToWindow()) {
            shown = to.clone();
            dirty = true;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ms);
        animator.addUpdateListener(u -> {
            float t = (Float) u.getAnimatedValue();
            for (int i = 0; i < ContrastProbe.BANDS; i++) shown[i] = from[i] + (to[i] - from[i]) * t;
            dirty = true;
            invalidate();
        });
        animator.start();
    }

    /** The plan the scrim is easing to (the alpha of each band). */
    public float[] plan() {
        return to.clone();
    }

    /** The alphas painted right now. */
    public float[] shown() {
        return shown.clone();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        // Stop i sits at y = i / (STOPS - 1) of the height; a pixel's centre is at i + 0.5, so the stretched column reaches half a stop
        // above the top and below the bottom: pixel i's centre then falls exactly on its stop.
        float step = h / (float) (STOPS - 1);
        target.set(0f, -0.5f * step, w, h + 0.5f * step);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int h = getHeight(), w = getWidth();
        if (h <= 0 || w <= 0) return;
        if (dirty) {
            for (int i = 0; i < STOPS; i++) {
                float a = Math.max(0f, Math.min(1f, ContrastProbe.alphaAt(shown, positions[i])));
                colors[i] = (Math.round(a * 255f) << 24) | (ContrastProbe.NAVY & 0xFFFFFF);
            }
            column.setPixels(colors, 0, 1, 0, 0, 1, STOPS);
            dirty = false;
        }
        canvas.drawBitmap(column, source, target, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }
}
