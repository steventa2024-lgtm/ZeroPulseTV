package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import tv.calmhome.launcher.core.MotionTokens;

/**
 * The small dot in a tile's top-right corner that says an update is pending. The tile draws it in its own draw pass (no extra view,
 * no layout space). It is shown only when a real update source says so (see {@code AppUpdates}); while shown it breathes softly, and
 * the breathing stops whenever the dot is hidden, the tile is detached, or reduced motion is on.
 */
final class UpdateBadgeView {
    static final int COLOR = 0xFF5FD3F3;

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final View host;
    private final boolean reducedMotion;
    private boolean shown;
    private float alpha = 1f;
    private ValueAnimator pulse;

    UpdateBadgeView(View host, boolean reducedMotion) {
        this.host = host;
        this.reducedMotion = reducedMotion;
        dot.setColor(COLOR);
        ring.setColor(0xCC0A1420);
    }

    void setShown(boolean value) {
        shown = value;
        sync();
        host.invalidate();
    }

    boolean shown() {
        return shown;
    }

    boolean isPulsing() {
        return pulse != null && pulse.isStarted();
    }

    /** Starts or stops the breathing to match whether the dot can be seen. */
    void sync() {
        boolean want = shown && host.isAttachedToWindow() && !reducedMotion;
        if (want && pulse == null) {
            ValueAnimator a = ValueAnimator.ofFloat(1f, 0.55f);
            a.setDuration(MotionTokens.BADGE_PULSE_MS / 2);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.addUpdateListener(u -> { alpha = (Float) u.getAnimatedValue(); host.invalidate(); });
            pulse = a;
            a.start();
        } else if (!want && pulse != null) {
            pulse.cancel();
            pulse = null;
            alpha = 1f;
        }
    }

    /** Draws the dot centred at ({@code cx}, {@code cy}) with radius {@code r}, if shown. */
    void draw(Canvas canvas, float cx, float cy, float r) {
        if (!shown) return;
        ring.setAlpha(Math.round(0xCC * alpha));
        dot.setAlpha(Math.round(255 * alpha));
        canvas.drawCircle(cx, cy, r, ring);
        canvas.drawCircle(cx, cy, r * 0.68f, dot);
    }
}
