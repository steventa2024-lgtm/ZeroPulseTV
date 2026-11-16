package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.data.Prefs;

/**
 * ZeroPulse TV design system: brand colors, type scale, spacing, radii and focus tuning.
 * All screens size themselves through this class so UI scale, safe areas and focus intensity
 * are consistent everywhere. Sizes are designed on a 960x540 dp canvas (1920x1080 at xhdpi).
 */
public final class Theme {

    // ---- Brand colors
    public static final int DEEP_BLACK = 0xFF0B0E12;
    public static final int MIDNIGHT_NAVY = 0xFF151A20;
    public static final int PANEL_NAVY = 0xFF252B32;
    public static final int ELECTRIC_BLUE = 0xFFF1F2F4;
    public static final int PULSE_CYAN = 0xFFF6F7F8;
    public static final int HOLO_BLUE = 0xFFCED2D7;
    public static final int SOFT_WHITE = 0xFFF6F7F8;
    public static final int MUTED_GRAY = 0xFFBEC5CD;
    public static final int BORDER = 0xFF797E85;
    public static final int SUCCESS = 0xFF28C76F;
    public static final int WARNING = 0xFFF5A623;
    public static final int ERROR = 0xFFFF5C7A;

    // ---- Type scale (sp at UI scale 1.0)
    public static final float T_CLOCK = 40, T_DATE = 14, T_GREETING = 44, T_TAGLINE = 15, T_HEADING = 26,
            T_TITLE = 17, T_BODY = 14, T_LABEL = 13, T_NAV = 14, T_CAPTION = 11;

    // ---- Spacing / radii (dp)
    public static final int R_TILE = 12, R_PANEL = 16, R_CHIP = 12, R_PILL = 22;
    public static final int GAP = 14;

    public static final String FONT_LIGHT = "sans-serif-light", FONT_REGULAR = "sans-serif",
            FONT_MEDIUM = "sans-serif-medium", FONT_CONDENSED = "sans-serif-condensed";

    private final DisplayMetrics dm;
    public final float scale;          // user UI scale 0.9 / 1.0 / 1.1
    public final float focusScale;     // tile scale on focus
    public final float glowDp;         // translationZ on focus (drives the blue spot shadow)
    public final float accent;         // 0..1 strength of blue accents on non-focused chrome
    public final boolean reducedMotion;
    /** True for the optional Liquid Glass look (Home and its panels): clear glass over the photo wallpaper. */
    public final boolean liquid;
    /** The frosted backdrop glass panels draw a window of; null unless {@link #liquid}. */
    public final LiquidBackdrop backdrop;
    /** The backdrop for the supplied glass clock and navigation; null unless {@link #liquid}. */
    public final GlassKit kit;
    /** Liquid Glass focus: tile scale (1.03 / 1.05 / 1.07x) and lift (8 / 10 / 12 dp) by the focus-emphasis setting. */
    public final float liquidScale, liquidLiftDp;

    public Theme(Context c, Prefs p) {
        this(c, p, false);
    }

    public Theme(Context c, Prefs p, boolean liquid) {
        dm = c.getResources().getDisplayMetrics();
        this.liquid = liquid;
        this.backdrop = liquid ? new LiquidBackdrop(dm.widthPixels, dm.heightPixels) : null;
        this.kit = liquid ? new GlassKit(dm.widthPixels, dm.heightPixels, dm.density) : null;
        scale = p.uiScale();
        int intensity = p.focusIntensity();             // 0 low, 1 medium, 2 high
        focusScale = new float[]{1.015f, 1.025f, 1.035f}[intensity];
        glowDp = new float[]{1f, 1.5f, 2f}[intensity];
        liquidScale = new float[]{LiquidTokens.FOCUS_SCALE - 0.02f, LiquidTokens.FOCUS_SCALE, LiquidTokens.FOCUS_SCALE + 0.02f}[intensity];
        liquidLiftDp = new float[]{LiquidTokens.ELEVATION_LOW_DP, (LiquidTokens.ELEVATION_LOW_DP + LiquidTokens.ELEVATION_HIGH_DP) / 2f,
                LiquidTokens.ELEVATION_HIGH_DP}[intensity];
        accent = new float[]{0.55f, 0.8f, 1f}[p.accentIntensity()];
        float systemAnim = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reducedMotion = p.reducedMotion() || systemAnim == 0f;
        focusGlow = p.focusGlow();
    }

    /** The focused tile's brand glow and lift shadow (Settings > Appearance > Focus glow). */
    public final boolean focusGlow;

    public int dp(float v) {
        return Math.round(v * dm.density * scale);
    }

    /** Pixels per dp including the user's UI scale (as {@link #dp} uses), unrounded. */
    public float pxPerDp() {
        return dm.density * scale;
    }

    /** Raw dp without the user scale (screen geometry). */
    public int rawDp(float v) {
        return Math.round(v * dm.density);
    }

    public int screenW() {
        return dm.widthPixels;
    }

    public int screenH() {
        return dm.heightPixels;
    }

    /** Safe-area margins: 5% of each dimension, so nothing essential sits in TV overscan. */
    public int safeX() {
        return Math.round(dm.widthPixels * 0.05f);
    }

    public int safeY() {
        return Math.round(dm.heightPixels * 0.05f);
    }

    /** Focus animation length; 0 when reduced motion is on. Note the system animator scale (0.5x on this TV) also applies. */
    public int focusMs() {
        return reducedMotion ? 0 : 150;
    }

    public int fadeMs(int normal) {
        return reducedMotion ? 0 : normal;
    }

    public TextView text(Context c, float sp, int color, String family) {
        TextView t = new TextView(c);
        style(t, sp, color, family);
        return t;
    }

    public void style(TextView t, float sp, int color, String family) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
    }

    /** Text shadow for anything drawn directly over a photo. */
    public void overPhoto(TextView t) {
        t.setShadowLayer(dp(8), 0, dp(1), 0x99000000);
    }

    public GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public GradientDrawable outlined(int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = rounded(fill, radiusDp);
        g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    /** Liquid Glass only: the ultra-clear navigation capsule. */
    public Drawable liquidCapsule(float radiusDp) {
        return LiquidGlass.all(LiquidSpec.navCapsule(), backdrop, pxPerDp(), radiusDp);
    }

    /** Liquid Glass only: the back and front parts of a surface, so a card's content can sit between them. */
    LiquidGlass[] liquidLayers(LiquidSpec spec, float radiusDp) {
        return LiquidGlass.layers(spec, backdrop, pxPerDp(), radiusDp);
    }

    /** Pearl reflection, neutral graphite body and a thin silver rim (clear glass in Liquid Glass). */
    public Drawable glass(float radiusDp, float opacity) {
        // Liquid Glass panes are quiet graphite with one hairline rim; their controls are the approved glass (see ZeroPulseGlassRow).
        if (liquid) return new PaneGlass(dp(radiusDp), Math.max(1f, dp(0.5f)), opacity);
        return new GlassSurface(new int[]{withAlpha(0xFF71767D, opacity), withAlpha(0xFF40464D, opacity * .80f),
                withAlpha(0xFF282C32, opacity * .90f), withAlpha(0xFF42464C, opacity)}, dp(radiusDp), Math.max(1, dp(1)));
    }

    public Drawable raised(float radiusDp, boolean focused) {
        if (liquid) {
            LiquidGlass bead = LiquidGlass.all(LiquidSpec.navBead(), backdrop, pxPerDp(), radiusDp);
            bead.setFocusLevel(focused ? 1f : 0.9f);
            return bead;
        }
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xFFF3F4F5, 0xFFC8CBD0, 0xFFABB0B7} : new int[]{0xFF868B92, 0xFF52575F, 0xFF696E75});
        surface.setCornerRadius(dp(radiusDp));
        surface.setStroke(Math.max(1, dp(focused ? 2 : 1)), focused ? SOFT_WHITE : 0xEEDEE0E3);
        return surface;
    }

    public int accentBlue(float base) {
        return withAlpha(ELECTRIC_BLUE, Math.min(1f, base * accent));
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int blend(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
