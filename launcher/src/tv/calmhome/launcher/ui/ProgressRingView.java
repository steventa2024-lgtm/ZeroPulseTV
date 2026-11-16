package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * A slim progress ring for a real download: the arc follows the real percentage (eased from one sample to the next), or, when the
 * source did not say how big the file is, a short arc turns slowly: an honest "working", never an invented number. The turning runs
 * only while the ring is shown and stops when it is hidden or detached; reduced motion shows a still arc.
 */
public final class ProgressRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final boolean reducedMotion;
    private float shown = -1f;          // the drawn fraction, 0..1, or -1 for indeterminate
    private float target = -1f;
    private float spin;
    private ValueAnimator ease, turn;

    public ProgressRingView(Context c, float strokePx, boolean reducedMotion) {
        super(c);
        this.reducedMotion = reducedMotion;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (Paint p : new Paint[]{track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(strokePx);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x33E9EEF3);
        arc.setColor(0xFFE9F4FB);
    }

    /** {@code percent} 0..100, or -1 when unknown (indeterminate). */
    public void setPercent(int percent) {
        float to = percent < 0 ? -1f : Math.min(100, percent) / 100f;
        if (to == target) return;
        target = to;
        if (ease != null) ease.cancel();
        if (to < 0f || shown < 0f || reducedMotion) {
            shown = to;
            syncTurn();
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, to);
        a.setDuration(800);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(u -> { shown = (Float) u.getAnimatedValue(); invalidate(); });
        ease = a;
        a.start();
    }

    public float fraction() {
        return target;
    }

    public boolean isTurning() {
        return turn != null && turn.isStarted();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        syncTurn();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncTurn();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (ease != null) ease.cancel();
        ease = null;
        stopTurn();
        super.onDetachedFromWindow();
    }

    private void syncTurn() {
        boolean want = target < 0f && !reducedMotion && isAttachedToWindow() && isShown();
        if (want && turn == null) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 360f);
            a.setDuration(1600);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new LinearInterpolator());
            a.addUpdateListener(u -> { spin = (Float) u.getAnimatedValue(); invalidate(); });
            turn = a;
            a.start();
        } else if (!want) {
            stopTurn();
        }
    }

    private void stopTurn() {
        if (turn != null) turn.cancel();
        turn = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = arc.getStrokeWidth() / 2f + 1f;
        box.set(s, s, getWidth() - s, getHeight() - s);
        canvas.drawOval(box, track);
        if (shown < 0f) canvas.drawArc(box, -90f + spin, 70f, false, arc);
        else if (shown > 0f) canvas.drawArc(box, -90f, 360f * shown, false, arc);
    }
}
