package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.animation.PathInterpolator;
import android.widget.HorizontalScrollView;

/**
 * A horizontal scroller whose focus scrolling keeps the focused control inside its own padding, and (as Home's Favorites carousel)
 * glides there.
 *
 * <p>Home's app row is a scroller padded by the TV safe margin on both sides. Android's focus scroll treats the whole width of the
 * scroller as visible, so moving focus to the last of eight favorites scrolled only until that tile touched the edge of the
 * screen: its right edge (and its label) sat at x = 1918 of 1920, outside the 96 px safe margin. Here the visible window is the
 * scroller's width less its padding, so a focused control settles inside the margin, as the padding says it should.
 *
 * <p><b>Carousel</b> ({@link #setCarousel}): when focus moves to a tile that is not wholly inside the margins, the row scrolls just far
 * enough, and only then, with one eased glide (fast out, slow in, about 220 ms, no bounce). The framework's own focus scroll is either
 * an instant jump or a fixed-length smooth scroll that a second press restarts from a stale position; here every request retargets the
 * one glide from wherever the row is now, so rapid presses never snap, queue or lose focus, and the row never scrolls past its first or
 * last tile (no blank space, no wrap-around, the list stays finite). The tiles themselves never change size or place in the row.
 */
public final class SafeMarginScrollView extends HorizontalScrollView {
    private static final PathInterpolator GLIDE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private boolean carousel;
    private int glideMs;
    private ValueAnimator glide;
    private int glideTarget = Integer.MIN_VALUE;

    public SafeMarginScrollView(Context c) {
        super(c);
    }

    /** Turns the eased carousel scroll on ({@code ms} > 0) or off (instant focus scrolling, as with reduced motion). */
    public void setCarousel(boolean on, int ms) {
        carousel = on && ms > 0;
        glideMs = Math.max(0, ms);
        setSmoothScrollingEnabled(!carousel && on);
    }

    /** True while the row is gliding (for tests and diagnostics). */
    public boolean isGliding() {
        return glide != null && glide.isRunning();
    }

    /** Where the current glide ends (for tests), or the current scroll when it is not gliding. */
    public int glideTarget() {
        return isGliding() ? glideTarget : getScrollX();
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        int delta = safeDelta(rect);
        if (!carousel || delta == 0 || !isLaidOut() || !isAttachedToWindow()) return delta;
        glideTo(getScrollX() + delta);
        return 0;                                    // the glide moves the row; the framework's own scroll has nothing left to do
    }

    /** Where the row must scroll by so that {@code rect} (in this view's coordinates) sits inside the padding. */
    private int safeDelta(Rect rect) {
        if (getChildCount() == 0) return 0;
        int windowLeft = getScrollX() + getPaddingLeft();
        int windowRight = getScrollX() + getWidth() - getPaddingRight();
        int window = windowRight - windowLeft;
        int delta = 0;
        if (rect.right > windowRight && rect.left > windowLeft) {
            // off to the right: scroll just far enough that its right edge meets the margin (never so far that its left edge leaves it)
            delta = Math.min(rect.width() > window ? rect.left - windowLeft : rect.right - windowRight, rect.right - windowRight);
        } else if (rect.left < windowLeft && rect.right < windowRight) {
            delta = -Math.min(rect.width() > window ? windowRight - rect.right : windowLeft - rect.left, windowLeft - rect.left);
        }
        // never beyond what the content has (the content's right end plus its right padding is the last thing to show)
        int contentRight = getChildAt(0).getRight() + getPaddingRight();
        delta = Math.min(delta, contentRight - (getScrollX() + getWidth()));
        return Math.max(delta, -getScrollX());
    }

    private int maxScroll() {
        if (getChildCount() == 0) return 0;
        return Math.max(0, getChildAt(0).getRight() + getPaddingRight() - getWidth());
    }

    private void glideTo(int target) {
        final int to = Math.max(0, Math.min(maxScroll(), target));
        if (isGliding() && to == glideTarget) return;                // already on its way there
        if (glide != null) glide.cancel();
        glideTarget = to;
        final int from = getScrollX();
        if (from == to) {
            glide = null;
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(glideMs);
        a.setInterpolator(GLIDE);
        a.addUpdateListener(u -> scrollTo((Integer) u.getAnimatedValue(), getScrollY()));
        glide = a;
        a.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (glide != null) {
            glide.cancel();
            if (glideTarget != Integer.MIN_VALUE) scrollTo(glideTarget, getScrollY());   // end where it was going
        }
        glide = null;
        super.onDetachedFromWindow();
    }
}
