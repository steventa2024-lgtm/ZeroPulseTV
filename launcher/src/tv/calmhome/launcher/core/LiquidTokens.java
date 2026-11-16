package tv.calmhome.launcher.core;

/**
 * Liquid Glass design tokens: Apple's "clear" variant, for components over a visually rich background. The wallpaper has
 * to show through the middle of every card and capsule, so the fills are very light and the light gathers at the rims and
 * the two top corners. Plain ints (ARGB) and floats (dp unless noted), so the numbers can be unit-tested without Android.
 */
public final class LiquidTokens {
    private LiquidTokens() { }

    // ---- Clear fills
    /** Frost for light cards, smoke for dark cards and the bottom capsules. */
    public static final int FILL_FROST = 0x12FFFFFF, FILL_SMOKE = 0x28060A10;
    /** A frosted white tint on top for light banners (YouTube, YouTube Music), so their dark logos stay legible. */
    public static final int FROST_TINT = 0x45FFFFFF;
    /** Netflix at rest: pearlescent white glass. */
    public static final int PEARL_IDLE = 0xB3F0F4F8;

    // ---- Optical lensing
    /** The inner gradient of reflected light, top-left to bottom-right, gone by 35 % of the way across. */
    public static final int SHEEN_START = 0x30FFFFFF, SHEEN_END = 0x00FFFFFF;
    public static final float SHEEN_FADE_BY = 0.35f;
    /** Radial highlights in the two top corners, each reaching 22 % of the width; the middle stays clear. */
    public static final int CORNER_GLOW = 0x40FFFFFF, CORNER_GLOW_END = 0x00FFFFFF;
    public static final float CORNER_REACH = 0.22f;

    // ---- Specular rim (1.5 dp): bright top lip and corner curves, almost clear sides, a softer bottom lip
    public static final int RIM_TOP = 0xE6FFFFFF, RIM_MID = 0x18FFFFFF, RIM_BOTTOM = 0x66FFFFFF;
    public static final float RIM_MID_FROM = 0.4f, RIM_MID_TO = 0.6f, RIM_WIDTH_DP = 1.5f;

    // ---- Focus: a double bezel, a 2 dp white outer edge and a crisp 1 dp inner rim 3 dp in
    public static final int BEZEL_OUTER = 0xFFFFFFFF, BEZEL_INNER = 0xB3FFFFFF;
    public static final float BEZEL_OUTER_DP = 2f, BEZEL_INNER_DP = 1f, BEZEL_INSET_DP = 3f;
    public static final float FOCUS_SCALE = 1.05f;
    public static final int FOCUS_MS = 180;
    public static final float ELEVATION_LOW_DP = 8f, ELEVATION_HIGH_DP = 12f;
    /** Shadow of lifted cards: deep navy rather than black, so it belongs to the scene. */
    public static final int SHADOW_TINT = 0xFF020814;

    // ---- A focused light card (Netflix): milky white with a warm-rose to icy-cyan pearlescent shift
    public static final int PEARL_BASE = 0xD9F4F7FA, PEARL_WARM = 0x33FFB5C2, PEARL_COOL = 0x33B5F2FF;

    // ---- Navigation: an ultra-clear capsule; only the active pill is milky
    public static final int NAV_CAPSULE_TINT = 0x14A8C8E8, NAV_RIM = 0x45FFFFFF;
    public static final float NAV_RIM_DP = 1f;
    public static final int NAV_BEAD = 0x38FFFFFF, NAV_BEAD_RIM_MID = 0x33FFFFFF, NAV_BEAD_RIM_BOTTOM = 0xE6FFFFFF;
    public static final float NAV_BEAD_RIM_DP = 1.8f;

    // ---- Brand glass
    public static final int DISNEY_SMOKE = 0x1A052638, DISNEY_UNDERGLOW = 0x8000C8E8, DISNEY_LIP = 0xFF5FE6FF,
            DISNEY_RIM_BOTTOM = 0x995FE6FF, HULU_GREEN = 0xFF1CE783;

    // ---- Logos keep this much room left and right, and above and below, inside their tile
    public static final float LOGO_PAD_X_DP = 20f, LOGO_PAD_Y_DP = 18f;

    // ---- Text
    /** The clock is slightly translucent, as if etched into the glass. */
    public static final int CLOCK_TEXT = 0xD9FFFFFF, DATE_TEXT = 0xE6FFFFFF;

    // ---- Backdrop: a small copy of the wallpaper, lightly blurred, that each panel draws a window of
    public static final int BACKDROP_SHRINK = 4, BLUR_RADIUS = 2;
    /** Scrims over the scene as fractions of the screen height; navy rather than neutral black. */
    public static final float TOP_SCRIM_FRACTION = 170f / 540f, BOTTOM_SCRIM_FRACTION = 330f / 540f;
    public static final int WASH = 0x1A0B1F44, TOP_SCRIM_A = 0xCC050F24, TOP_SCRIM_B = 0x66071630,
            BOTTOM_SCRIM_A = 0x99071327, BOTTOM_SCRIM_B = 0xF2030A18;
}
