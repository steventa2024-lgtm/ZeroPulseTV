package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import tv.calmhome.launcher.core.MotionTokens;
import tv.calmhome.launcher.core.TileArt;

/**
 * A Home app tile on the approved graphite glass: the glass ({@link ZeroPulseGlassTileBackground}) under the app's real
 * logo, banner or icon. The content is separate views above the glass, so it stays crisp; the tile is the focusable,
 * clickable card, and the glass refracts only the registered wallpaper. See {@link ZeroPulseGlassControl} for the focus
 * language.
 *
 * Focus lift (premium interactions): the glass may not be scaled or moved (the renderer drops a scaled surface's glass), so the lift is
 * built around it: the artwork swells to {@link MotionTokens#FOCUS_SCALE} and rises {@link MotionTokens#LIFT_DP}, the glass crossfades
 * to its focus frame (its brighter rim is the edge highlight), and a {@link TileHaloView} behind it adds a soft shadow and a restrained
 * glow in the app's own colour. All of it is transforms and alpha over pre-rendered pictures; nothing moves the neighbours.
 *
 * States: default, focused, pressed (the artwork dips), launching (a second OK within {@link MotionTokens#LAUNCH_GUARD_MS} is ignored),
 * and update available (a dot in the top-right corner, only when a real update source says so).
 */
public final class ZeroPulseGlassAppTile extends ZeroPulseGlassControl {

    /** How far the content swells on focus. The glass itself may not scale (the renderer's contract). */
    static final float FOCUSED_CONTENT_SCALE = MotionTokens.FOCUS_SCALE;

    private final int heightPx;
    private final TileHaloView halo;
    private final UpdateBadgeView badge;
    private final float liftPx;
    private final boolean reducedMotion;
    private String baseDescription;
    private boolean updateAvailable;
    private long launchedAt = Long.MIN_VALUE;

    public ZeroPulseGlassAppTile(Context c, GlassKit kit, int heightPx, int focusMs) {
        super(c, kit, GraphiteGlassSurface.Shape.CARD, heightPx, focusMs, FOCUSED_CONTENT_SCALE, 1f);
        this.heightPx = heightPx;
        this.reducedMotion = focusMs <= 0;
        liftPx = MotionTokens.LIFT_DP * c.getResources().getDisplayMetrics().density;
        // Both are drawn in this tile's own draw pass, so the tile keeps exactly its approved children (glass, content, finish).
        halo = new TileHaloView();
        badge = new UpdateBadgeView(this, reducedMotion);
        setWillNotDraw(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        halo.draw(canvas, getWidth(), getHeight());                  // behind the glass, and outside the tile's bounds
        super.dispatchDraw(canvas);
        float r = Math.max(3f, heightPx * 0.045f), inset = heightPx * 0.07f + r;
        badge.draw(canvas, getWidth() - inset, inset, r);            // on the artwork's corner, above everything
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        badge.sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        badge.sync();
    }

    /** The app's own artwork, when the tile shows some (null for a notice or a module tile). */
    public TileArtView artView() {
        View v = contentFrame().getChildCount() == 0 ? null : contentFrame().getChildAt(0);
        return v instanceof TileArtView ? (TileArtView) v : null;
    }

    /**
     * Replaces the tile's content (the logo, the banner, or the icon and name); it is drawn above the glass. A tile's artwork
     * is kept out of the glass finish: the finish decorates the frame, and never lifts or tints the app's own pixels.
     */
    public void setContent(View content) {
        contentFrame().removeAllViews();
        contentFrame().addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (content instanceof TileArtView) {
            Bitmap b = ((TileArtView) content).art().bitmap;
            float radius;
            switch (((TileArtView) content).kind()) {
                case BANNER:
                case FILLED_ICON: radius = TileArt.bannerRadius(heightPx); break;
                case ICON: radius = TileArt.iconRadius(Math.min(b.getWidth(), b.getHeight())); break;
                default: radius = Math.max(2f, Math.min(b.getWidth(), b.getHeight()) * 0.08f); break;
            }
            setContentInset(0);                                  // artwork is placed by its own bitmap: it fills the tile inside the hairline rim
            setFinishHole(b.getWidth(), b.getHeight(), radius);
            halo.setColor(((TileArtView) content).art().accent);
        } else {
            setContentInset(-1);                                 // text content keeps the glass's padding
            setFinishHole(0, 0, 0f);
            halo.setColor(0);
        }
    }

    // ------------------------------------------------------------------------------------------------ focus lift

    @Override
    protected void onLevel(float level) {
        if (halo.setLevel(level)) invalidate();                     // redraws the tile's own pass: blits, no glass re-render
        contentFrame().setTranslationY(-liftPx * level);
    }

    /** The glow's colour from a known brand (overrides the colour extracted from the artwork). */
    public void setGlowColor(int argb) {
        if (argb != 0) { halo.setColor(argb); invalidate(); }
    }

    /** Turns the focus glow and shadow on or off (Settings: Focus glow). The lift and the glass focus frame stay. */
    public void setGlowEnabled(boolean on) {
        halo.setHaloEnabled(on);
        invalidate();
    }

    public int glowColor() {
        return halo.color();
    }

    public float haloLevel() {
        return halo.level();
    }

    public boolean glowEnabled() {
        return halo.haloEnabled();
    }

    // ------------------------------------------------------------------------------------------------ press and launch

    /**
     * OK was pressed: acknowledges it at once (the artwork dips and returns) and says whether to launch. A second OK on the same tile
     * within {@link MotionTokens#LAUNCH_GUARD_MS} (a double press, a held key) is acknowledged but not launched again.
     */
    public boolean acknowledgeLaunch() {
        long now = SystemClock.uptimeMillis();
        boolean fresh = launchedAt == Long.MIN_VALUE || now - launchedAt >= MotionTokens.LAUNCH_GUARD_MS;
        View art = contentFrame().getChildCount() == 0 ? null : contentFrame().getChildAt(0);
        if (art != null && !reducedMotion) {
            art.animate().cancel();
            art.setScaleX(MotionTokens.PRESS_SCALE);
            art.setScaleY(MotionTokens.PRESS_SCALE);
            art.animate().scaleX(1f).scaleY(1f).setDuration(MotionTokens.PRESS_MS * 2).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        if (fresh) launchedAt = now;
        return fresh;
    }

    /** True while a launch from this tile is in flight (the guard window). */
    public boolean isLaunching() {
        return launchedAt != Long.MIN_VALUE && SystemClock.uptimeMillis() - launchedAt < MotionTokens.LAUNCH_GUARD_MS;
    }

    // ------------------------------------------------------------------------------------------------ update available

    /** Shows the update dot. Only call with true when a real update source reported an update for this app. */
    public void setUpdateAvailable(boolean available) {
        updateAvailable = available;
        badge.setShown(available);
        applyDescription();
    }

    public boolean updateAvailable() {
        return updateAvailable;
    }

    /** True while the update dot is drawn. */
    public boolean updateBadgeShown() {
        return badge.shown();
    }

    public boolean badgePulsing() {
        return badge.isPulsing();
    }

    @Override
    public void setContentDescription(CharSequence d) {
        baseDescription = d == null ? null : d.toString();
        applyDescription();
    }

    private void applyDescription() {
        String d = baseDescription;
        if (d != null && updateAvailable) d = d + ", update available";
        super.setContentDescription(d);
    }
}
