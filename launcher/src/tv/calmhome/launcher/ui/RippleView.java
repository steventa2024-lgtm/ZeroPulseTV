package tv.calmhome.launcher.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * The secret's ripple: two soft rings that spread across the wallpaper once and fade, then the view removes itself. It is a transient
 * overlay above the wallpaper and under the interface: the glass scene is not rebuilt, nothing takes focus, and there is no loop.
 */
public final class RippleView extends View {
    static final int MS = 1100;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float t;

    public RippleView(Context c) {
        super(c);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFDFF4FF);
    }

    /** Plays once and removes itself from its parent. */
    public void play() {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(MS);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(u -> { t = (Float) u.getAnimatedValue(); invalidate(); });
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator x) {
                if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(RippleView.this);
            }
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float max = (float) Math.hypot(getWidth(), getHeight()) / 2f;
        float cx = getWidth() / 2f, cy = getHeight() * 0.42f;
        for (int k = 0; k < 2; k++) {
            float p = Math.max(0f, t - k * 0.18f);
            if (p <= 0f) continue;
            ring.setStrokeWidth(getResources().getDisplayMetrics().density * (6f - 4f * p));
            ring.setAlpha(Math.round(70 * (1f - p)));
            canvas.drawCircle(cx, cy, max * p, ring);
        }
    }
}
