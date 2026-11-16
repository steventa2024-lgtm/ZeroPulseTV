package tv.calmhome.launcher.ui;

import tv.calmhome.launcher.core.LiquidBrand;
import tv.calmhome.launcher.core.LiquidKey;
import tv.calmhome.launcher.core.LiquidMath;
import tv.calmhome.launcher.core.LiquidTokens;

/**
 * How one kind of glass object is built: its fills, tints and highlights, and which of the glass layers it has. The layers
 * themselves, in drawing order, are described on {@link LiquidGlass}.
 */
final class LiquidSpec {
    /** A tint (vertical gradient, top to bottom) and its strength at rest and when focused; alpha in the colours is kept. */
    int tintTop, tintBottom;
    float tintAlpha = 1f, tintAlphaFocused = 1f;
    /** A uniform base fill under the tint: frost for light cards, smoke for dark ones. 0 = none. */
    int fill;
    /** Focused: milky pearlescent glass with a warm-to-cool shift (for logos on a light banner, e.g. Netflix). */
    boolean pearl;
    /** Focused: a 2 dp white outer edge plus a crisp inner rim 3 dp in (thick, bevelled glass). */
    boolean doubleBezel;
    /** The specular rim: bright at the top edge, nearly clear down the sides, a softer lip at the bottom. */
    int rimTop = LiquidTokens.RIM_TOP, rimMid = LiquidTokens.RIM_MID, rimBottom = LiquidTokens.RIM_BOTTOM;
    float rimMidFrom = LiquidTokens.RIM_MID_FROM, rimMidTo = LiquidTokens.RIM_MID_TO, rimWidthDp = LiquidTokens.RIM_WIDTH_DP;
    /** Strength of the optical-lensing highlights in the two top corners (0 = none). */
    float corners = 1f;
    /** The faint inner bevel line, where the glass has thickness. */
    boolean bevel = true;
    /** A glow rising from the bottom centre of the glass (Disney+). 0 = none. */
    int underglow;
    /** The bloom around a focused panel. */
    int glow = 0xFFFFFFFF;
    float glowExtentDp = 20f, glowStrength = 1f;
    /** False for a panel that sits on another glass panel: the one below already frosts the wallpaper. */
    boolean frostsBackdrop = true;
    /** A convex glass bead (the active navigation pill): bright crescent on top, lift at the bottom. */
    boolean bead;
    /** A small bright highlight on the top edge. */
    boolean specular = true;
    /** A bright line along the bottom inner edge, where the glass bends the light. */
    boolean caustic = true;
    int causticTint = 0xFFFFFFFF;
    float bevelInsetDp = 3.5f;

    /** The navigation capsule: ultra-clear icy glass with a crisp 1 dp rim and nothing else (no over-layering). */
    static LiquidSpec navCapsule() {
        LiquidSpec s = new LiquidSpec();
        s.tintTop = s.tintBottom = LiquidTokens.NAV_CAPSULE_TINT;
        s.glowStrength = 0f;
        s.specular = false;
        s.caustic = false;
        s.bevel = false;
        s.corners = 0.4f;
        s.rimTop = s.rimMid = s.rimBottom = LiquidTokens.NAV_RIM;
        s.rimWidthDp = LiquidTokens.NAV_RIM_DP;
        return s;
    }

    /** The active navigation pill: a convex bead, milky inside, with a bright 1.8 dp rim along the top and the bottom. */
    static LiquidSpec navBead() {
        LiquidSpec s = new LiquidSpec();
        s.tintTop = s.tintBottom = LiquidTokens.NAV_BEAD;
        s.tintAlphaFocused = 1.25f;
        s.frostsBackdrop = false;
        s.bead = true;
        s.specular = false;
        s.bevelInsetDp = 3f;
        s.glow = 0xFFFFFFFF;
        s.glowExtentDp = 16f;
        s.corners = 0f;
        s.rimTop = 0xFFFFFFFF;
        s.rimMid = LiquidTokens.NAV_BEAD_RIM_MID;
        s.rimBottom = LiquidTokens.NAV_BEAD_RIM_BOTTOM;
        s.rimMidFrom = 0.30f;
        s.rimMidTo = 0.70f;
        s.rimWidthDp = LiquidTokens.NAV_BEAD_RIM_DP;
        return s;
    }

    /** Quick actions: dark smoked clear glass; the wallpaper shows through the middle, the light gathers at the corners and rim. */
    static LiquidSpec chip() {
        LiquidSpec s = new LiquidSpec();
        s.fill = LiquidTokens.FILL_SMOKE;
        s.specular = false;
        s.bevelInsetDp = 3f;
        s.glow = 0xFFBFD8FF;
        s.glowExtentDp = 14f;
        return s;
    }

    /** A panel behind text or rows: smoke, a little denser the more {@code opacity} the caller asked for. */
    static LiquidSpec panel(float opacity) {
        LiquidSpec s = new LiquidSpec();
        float a = Math.max(0.12f, Math.min(0.5f, 0.16f + (opacity - 0.5f) * 0.5f));
        s.fill = (Math.round(a * 255f) << 24) | 0x060A10;
        s.specular = false;
        s.caustic = false;
        s.glowStrength = 0f;
        s.corners = 0.6f;
        return s;
    }

    /**
     * An app tile, in Apple's clear variant: very light glass over the lightly frosted wallpaper, which shows through the
     * middle, with only the logo on top. Netflix is pearlescent white; YouTube and YouTube Music (any light banner) get a
     * frosted white tint so their dark logos stay legible; Disney+, Prime Video, Hulu and YouTube TV (any dark banner) are
     * smoked glass, Disney+ with a teal-cyan underglow. Focused, a light tile turns milky and pearlescent and every tile
     * gets the double bezel, a glow and a lift. {@code g} is null for an app whose banner is not a plain logo.
     */
    static LiquidSpec tile(LiquidKey.Logo g, LiquidBrand brand) {
        LiquidSpec s = new LiquidSpec();
        s.doubleBezel = true;
        s.glowExtentDp = 26f;
        if (g == null) {
            s.tintTop = 0xFF2A4666;
            s.tintBottom = 0xFF0E1E30;
            s.tintAlpha = 0.30f;
            s.tintAlphaFocused = 0.42f;
            s.fill = LiquidTokens.FILL_SMOKE;
            s.glow = 0xFFBFD8FF;
            s.glowExtentDp = 24f;
            return s;
        }
        switch (brand) {
            case NETFLIX:
                s.tintTop = s.tintBottom = LiquidTokens.PEARL_IDLE;
                s.pearl = true;
                s.glow = 0xFFCFE0FF;
                return s;
            case DISNEY:
                s.tintTop = LiquidTokens.DISNEY_SMOKE;
                s.tintBottom = LiquidTokens.DISNEY_UNDERGLOW;
                s.tintAlphaFocused = 1.15f;
                s.underglow = LiquidTokens.DISNEY_UNDERGLOW;
                s.rimBottom = LiquidTokens.DISNEY_RIM_BOTTOM;
                s.glow = 0xFF8FEFFF;
                s.causticTint = LiquidTokens.DISNEY_LIP;
                return s;
            case PRIME:
            case HULU:
                s.fill = LiquidTokens.FILL_SMOKE;
                s.glow = 0xFFF3F7FF;
                return s;
            default:
                break;
        }
        int top = LiquidMath.soften(g.topTint, 0.45f), bottom = LiquidMath.soften(g.bottomTint, 0.45f);
        if (g.isLight()) {
            s.tintTop = s.tintBottom = LiquidTokens.FROST_TINT;
            s.fill = LiquidTokens.FILL_FROST;
            s.pearl = true;
            s.glow = 0xFFCFE0FF;
            s.causticTint = 0xFFFFFFFF;
            return s;
        }
        // Dark and mid banners: smoked glass with only a hint of the banner's own colour (more for a colourful one).
        float hint = g.bgLuminance < 0.25f ? 0.14f : 0.30f;
        s.tintTop = top;
        s.tintBottom = bottom;
        s.tintAlpha = hint;
        s.tintAlphaFocused = hint + 0.08f;
        s.fill = LiquidTokens.FILL_SMOKE;
        s.glow = LiquidMath.mix(top, 0xFFFFFFFF, 0.6f) | 0xFF000000;
        s.causticTint = LiquidMath.mix(bottom, 0xFFFFFFFF, 0.7f) | 0xFF000000;
        return s;
    }
}
