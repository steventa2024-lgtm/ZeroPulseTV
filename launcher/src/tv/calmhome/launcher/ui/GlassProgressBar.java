package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * A download's progress in the Liquid Glass palette: a slim graphite capsule with the panes' hairline rim, filled with the glass's
 * silver light up to the real fraction transferred. It only ever shows what the download reports:
 * <ul>
 *   <li>{@link #setProgress} with a fraction 0..1: the fill eases to it (about 320 ms), so a jump of a few percent glides instead of
 *       snapping; a bright tip marks the moving edge while {@link #setActive} is true.</li>
 *   <li>{@link #setIndeterminate}: the size is not known yet. No number is invented: a soft sheen travels along an empty track
 *       (with reduced motion, a still, faint track), and the screen says "Working" in words.</li>
 *   <li>{@link #setState}: complete fills the track once and stops; failed turns the fill to the error tint and stops.</li>
 * </ul>
 * The only animation that repeats is the indeterminate sheen, and it runs only while the bar is attached and indeterminate. Nothing is
 * allocated while drawing.
 */
public final class GlassProgressBar extends View {
    public enum State { RUNNING, COMPLETE, FAILED }

    private static final int EASE_MS = 320, SHEEN_MS = 1400;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), tip = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), edge = new RectF(), bar = new RectF();
    private final float density;
    private float target, shown;                 // fractions 0..1
    private boolean indeterminate, active = true, reducedMotion;
    private State state = State.RUNNING;
    private ValueAnimator ease, loop;
    private float sheenAt;                       // 0..1 along the track
    private android.graphics.ColorFilter failedTint =
            new android.graphics.PorterDuffColorFilter(0xFFFF5C7A, android.graphics.PorterDuff.Mode.SRC_IN);

    public GlassProgressBar(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);    // the row's own text says the percent
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, density * 0.5f));
        tip.setColor(0xFFFFFFFF);
    }

    public void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
    }

    public void setErrorColor(int color) {
        failedTint = new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        invalidate();
    }

    /** The real fraction transferred, 0..1. */
    public void setProgress(float fraction) {
        float to = Math.max(0f, Math.min(1f, fraction));
        boolean wasIndeterminate = indeterminate;
        indeterminate = false;
        stopLoop();
        if (to == target && !wasIndeterminate) return;
        target = to;
        if (ease != null) ease.cancel();
        ease = null;
        if (reducedMotion || !isAttachedToWindow() || wasIndeterminate) {
            shown = to;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, to);
        a.setDuration(EASE_MS);
        a.setInterpolator(new DecelerateInterpolator(1.5f));
        a.addUpdateListener(u -> {
            shown = (Float) u.getAnimatedValue();
            invalidate();
        });
        ease = a;
        a.start();
    }

    /** The size is not known: no fraction is shown. */
    public void setIndeterminate() {
        if (indeterminate) return;
        indeterminate = true;
        if (ease != null) ease.cancel();
        ease = null;
        startLoopIfNeeded();
        invalidate();
    }

    /** True while bytes are moving: the moving edge has its bright tip. */
    public void setActive(boolean value) {
        if (active == value) return;
        active = value;
        invalidate();
    }

    public void setState(State value) {
        if (state == value) return;
        state = value;
        if (value == State.COMPLETE) setProgress(1f);
        if (value != State.RUNNING) {
            indeterminate = false;
            stopLoop();
            active = false;
        }
        invalidate();
    }

    /** What is shown now (for tests): the fraction, or NaN while indeterminate. */
    public float shownFraction() {
        return indeterminate ? Float.NaN : shown;
    }

    public float targetFraction() {
        return indeterminate ? Float.NaN : target;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public State state() {
        return state;
    }

    /** True while the indeterminate sheen is running (for tests: it must not run otherwise). */
    public boolean isLooping() {
        return loop != null && loop.isRunning();
    }

    private void startLoopIfNeeded() {
        if (!indeterminate || reducedMotion || !isAttachedToWindow() || (loop != null && loop.isRunning())) return;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(SHEEN_MS);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(u -> {
            sheenAt = (Float) u.getAnimatedValue();
            invalidate();
        });
        loop = a;
        a.start();
    }

    private void stopLoop() {
        if (loop != null) loop.cancel();
        loop = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoopIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoop();
        if (ease != null) ease.cancel();
        ease = null;
        shown = target;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE && isShown()) startLoopIfNeeded();
        else stopLoop();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        box.set(0, 0, w, h);
        float half = rim.getStrokeWidth() / 2f;
        edge.set(half, half, w - half, h - half);
        track.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x8C10141A, 0xA60B0E12}, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x4DFFFFFF, 0x1AFFFFFF}, null, Shader.TileMode.CLAMP));
        fill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFF4F7FA, 0xFFC9D0D8}, null, Shader.TileMode.CLAMP));
        float band = Math.max(h * 6f, w * 0.22f);
        sheen.setShader(new LinearGradient(0, 0, band, 0, new int[]{0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (box.isEmpty()) return;
        float r = box.height() / 2f;
        canvas.drawRoundRect(box, r, r, track);
        if (indeterminate) {
            if (!reducedMotion) {
                float band = Math.max(box.height() * 6f, box.width() * 0.22f);
                float x = -band + (box.width() + band) * sheenAt;
                canvas.save();
                canvas.clipRect(box);
                canvas.translate(x, 0);
                canvas.drawRect(0, 0, band, box.height(), sheen);
                canvas.restore();
            }
        } else if (shown > 0.0005f) {
            float right = Math.max(box.height(), box.width() * shown);      // a sliver never draws thinner than its own round ends
            bar.set(0, 0, right, box.height());
            android.graphics.ColorFilter want = state == State.FAILED ? failedTint : null;
            if (fill.getColorFilter() != want) fill.setColorFilter(want);
            canvas.drawRoundRect(bar, r, r, fill);
            if (active && state == State.RUNNING && shown < 0.999f) {
                tip.setAlpha(0xCC);
                canvas.drawCircle(right - r, r, r * 0.9f, tip);
            }
        }
        canvas.drawRoundRect(edge, r - rim.getStrokeWidth() / 2f, r - rim.getStrokeWidth() / 2f, rim);
    }
}
