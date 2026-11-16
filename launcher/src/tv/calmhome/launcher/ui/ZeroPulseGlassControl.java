package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import tv.calmhome.launcher.core.GlassMaterial;

/**
 * The focusable card that shares one focus language between Home's app tiles and quick actions: approved graphite glass
 * underneath ({@link ZeroPulseGlassTileBackground}), the control's content above it, and a focus level that crossfades
 * the glass between its idle and focus frames.
 *
 * The glass layer may not be scaled, rotated or animated by the renderer's contract, so the control itself never scales
 * or moves; only its content (the logo or the icon and text, which are separate views above the glass) swells slightly
 * on focus. There is no outline or stroke around the control: the focus cue is the glass's own brighter rim and body.
 */
abstract class ZeroPulseGlassControl extends FrameLayout {
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    private final ZeroPulseGlassTileBackground glass;
    private final FrameLayout content;
    private final GlassFinishView finish;
    private final int focusMs;
    private int defaultInset;
    private final float focusedContentScale;
    private ValueAnimator animator;
    private boolean followsChildFocus;

    ZeroPulseGlassControl(Context c, GlassKit kit, GraphiteGlassSurface.Shape shape, float designHeightPx, int focusMs, float focusedContentScale,
                          float finishStrength) {
        super(c);
        this.focusMs = focusMs;
        this.focusedContentScale = focusedContentScale;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);
        setDefaultFocusHighlightEnabled(false);
        glass = new ZeroPulseGlassTileBackground(c, kit, shape, designHeightPx);
        addView(glass, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        content = new FrameLayout(c);
        content.setClipChildren(false);
        int inset = Math.round(GlassMaterial.bevel(designHeightPx) * 0.5f);
        defaultInset = inset;
        content.setPadding(inset, inset, inset, inset);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        finish = new GlassFinishView(c, shape == GraphiteGlassSurface.Shape.CAPSULE, finishStrength);
        addView(finish, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** The finish layer above the content (for tests). */
    public final View finishLayer() {
        return finish;
    }

    /** How much of the glass and its finish show, 0..1 (a list row or hidden drawer tile has none while hidden). */
    protected final void setPresence(float value) {
        glass.setPresence(value);
        finish.setPresence(value);
    }

    /** Adjusts whether the glass surface and finish participate in rendering (e.g. off-screen drawer tiles). */
    public final void setSurfacePresence(float value) {
        setPresence(value);
    }

    /** Keeps the finish off a centred rectangle {@code w x h} (the artwork's place, corners {@code radius}); 0 x 0 puts it over everything. */
    protected final void setFinishHole(int w, int h, float radius) {
        finish.setCenteredHole(w, h, radius);
    }

    /** The finish's pictures (for tests). */
    final GlassFinishView.Pictures finishPictures() {
        return finish.pictures();
    }

    /** The glass layer (for tests and diagnostics). */
    public final ZeroPulseGlassTileBackground glass() {
        return glass;
    }

    /** Where the control's own content goes (above the glass). */
    protected final FrameLayout contentFrame() {
        return content;
    }

    /** Padding of the content frame in pixels; a negative value restores the glass's own padding (half the bevel). */
    protected final void setContentInset(int px) {
        int v = px < 0 ? defaultInset : px;
        content.setPadding(v, v, v, v);
    }

    /** The glass's current focus level, 0 (idle) to 1 (the focus material). */
    public final float focusLevel() {
        return glass.level();
    }

    /** Called with each new focus level, 0..1, so a subclass can brighten its own content with the glass. */
    protected void onLevel(float level) { }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        retarget();
    }

    /**
     * A control that holds its own focusable child (a text field in a glass capsule): the control itself does not take focus, and
     * its glass takes the focus material while the child has focus, so the field is lit exactly as a focused button is, with no ring.
     */
    public final void followChildFocus() {
        followsChildFocus = true;
        setFocusable(false);
        setClickable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (followsChildFocus) retarget();
    }

    @Override
    public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        if (followsChildFocus) retarget();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        content.setAlpha(pressed ? 0.8f : 1f);
        retarget();
    }

    private void retarget() {
        float to = isFocused() || isPressed() || (followsChildFocus && hasFocus()) ? 1f : 0f;
        if (animator != null) animator.cancel();
        animator = null;
        if (focusMs <= 0 || !isAttachedToWindow() || glass.level() == to) {
            apply(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(glass.level(), to);
        // Lift in, settle out a little faster (MotionTokens: 170 ms in, 140 ms out, scaled with this control's own focus time).
        a.setDuration(to >= 1f ? focusMs : Math.round(focusMs * (tv.calmhome.launcher.core.MotionTokens.FOCUS_RELEASE_MS
                / (float) tv.calmhome.launcher.core.MotionTokens.FOCUS_LIFT_MS)));
        a.setInterpolator(EASE);
        a.addUpdateListener(u -> apply((Float) u.getAnimatedValue()));
        animator = a;
        a.start();
    }

    private void apply(float level) {
        glass.setLevel(level);
        finish.setLevel(level);
        float s = 1f + (focusedContentScale - 1f) * level;
        content.setScaleX(s);
        content.setScaleY(s);
        onLevel(level);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }

    /** Test hook: the view that is the glass layer is not focusable and takes no clicks. */
    final boolean glassIsInert() {
        return !glass.isFocusable() && !glass.isClickable() && glass.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO;
    }
}
