package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

import tv.calmhome.launcher.data.Prefs;

/**
 * ZeroPulse TV design system: brand colors, type scale, spacing, radii and focus tuning.
 * All screens size themselves through this class so UI scale, safe areas and focus intensity
 * are consistent everywhere. Sizes are designed on a 960x540 dp canvas (1920x1080 at xhdpi).
 */
public final class Theme {

    // ---- Brand colors
    public static final int DEEP_BLACK = 0xFF06090D;
    public static final int MIDNIGHT_NAVY = 0xFF0A1220;
    public static final int PANEL_NAVY = 0xFF101A2B;
    public static final int ELECTRIC_BLUE = 0xFF2F80FF;
    public static final int PULSE_CYAN = 0xFF35D6FF;
    public static final int HOLO_BLUE = 0xFF5AA9FF;
    public static final int SOFT_WHITE = 0xFFF3F7FF;
    public static final int MUTED_GRAY = 0xFF94A3B8;
    public static final int BORDER = 0xFF22324A;
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

    public Theme(Context c, Prefs p) {
        dm = c.getResources().getDisplayMetrics();
        scale = p.uiScale();
        int intensity = p.focusIntensity();             // 0 low, 1 medium, 2 high
        focusScale = new float[]{1.04f, 1.06f, 1.08f}[intensity];
        glowDp = new float[]{6f, 12f, 18f}[intensity];
        accent = new float[]{0.55f, 0.8f, 1f}[p.accentIntensity()];
        float systemAnim = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reducedMotion = p.reducedMotion() || systemAnim == 0f;
    }

    public int dp(float v) {
        return Math.round(v * dm.density * scale);
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
        return reducedMotion ? Math.min(normal, 250) : normal;
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

    /** Glass panel used over wallpapers: translucent Panel Navy with a hairline border. */
    public GradientDrawable glass(float radiusDp, float opacity) {
        return outlined(withAlpha(PANEL_NAVY, opacity), withAlpha(BORDER, 0.9f), 1, radiusDp);
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
