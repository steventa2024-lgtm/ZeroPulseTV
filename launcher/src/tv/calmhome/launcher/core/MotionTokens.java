package tv.calmhome.launcher.core;

/**
 * One place for the motion of the premium interactions (ZeroPulseMotionTokens in the brief). Every duration here is a starting
 * value inside the brief's range, to be tuned against measurements on the TV; {@link #of} turns any of them into 0 under reduced
 * motion, so a component never has to remember to check it. Pure Java: unit-tested off-device.
 *
 * Rules the components follow: animate transforms, alpha and pre-rendered bitmaps, never layout sizes; one animator per component,
 * cancelled and retargeted from the current value (rapid D-pad presses never queue animations); no infinite loop except a badge
 * pulse that runs only while its badge is visible.
 */
public final class MotionTokens {
    private MotionTokens() { }

    /** A focused tile lifts (brief: 150 to 190 ms). */
    public static final int FOCUS_LIFT_MS = 170;
    /** ...and settles back (brief: 120 to 170 ms). */
    public static final int FOCUS_RELEASE_MS = 140;
    /** The navigation focus glides between destinations (brief: 160 to 220 ms). */
    public static final int PILL_GLIDE_MS = 190;
    /** Contextual label or hint crossfade (brief: 150 to 200 ms). */
    public static final int CONTEXT_FADE_MS = 170;
    /** The centre of Home changes what it shows (brief: 220 to 300 ms). */
    public static final int HERO_FADE_MS = 260;
    /** A modal opens or closes (brief: 180 to 260 ms). */
    public static final int MODAL_MS = 220;
    /** The wallpaper drifts with focus (brief: 200 to 350 ms). */
    public static final int PARALLAX_MS = 300;
    /** A completion is acknowledged (brief: 300 to 600 ms). */
    public static final int ACKNOWLEDGE_MS = 450;
    /** A pressed tile dips and returns. */
    public static final int PRESS_MS = 110;
    /** A pending update badge breathes once per this period. */
    public static final int BADGE_PULSE_MS = 1800;
    /** A screen without a header of its own (a dialog, a menu) fades in whole. */
    public static final int SCREEN_FADE_MS = 180;
    /** A dialog's card rises a few dp as it fades in (brief: 180 to 260 ms). */
    public static final int MODAL_RISE_MS = 220;
    /** Changing destination: the content being left fades out quickly... */
    public static final int CONTENT_OUT_MS = 120;
    /** ...and the new content fades in (brief: 180 to 260 ms), after a short overlap delay. The header never moves. */
    public static final int CONTENT_IN_MS = 220, CONTENT_IN_DELAY_MS = 40;
    /** The longest the previous header is kept while the new header's glass renders (normally a few frames). */
    public static final int HEADER_HANDOFF_MAX_MS = 450;
    /** The Favorites row glides to keep the focused tile inside the safe margins (brief: 180 to 260 ms). */
    public static final int CAROUSEL_MS = 220;
    /** A launch request is ignored if the same tile was launched this recently (a double OK, a held key). */
    public static final int LAUNCH_GUARD_MS = 1500;

    /** Focused content swell (brief: 1.04 to 1.06). The glass itself is never scaled: the renderer drops its glass if it is. */
    public static final float FOCUS_SCALE = 1.05f;
    /** Pressed content dips to this before it launches. */
    public static final float PRESS_SCALE = 0.97f;
    /** How far a focused tile's artwork rises, in dp. */
    public static final float LIFT_DP = 3f;
    /** Wallpaper drift in dp: across, and down. */
    public static final float PARALLAX_X_DP = 4f, PARALLAX_Y_DP = 1.5f;

    /** {@code ms}, or 0 under reduced motion (the change then happens at once). */
    public static int of(int ms, boolean reducedMotion) {
        return reducedMotion ? 0 : Math.max(0, ms);
    }
}
