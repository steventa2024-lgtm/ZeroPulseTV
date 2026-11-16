package tv.calmhome.launcher.core;

import java.util.Locale;

/**
 * The apps whose glass is tuned by hand to the design: Netflix (pearlescent white), Disney+ (dark smoke with a teal-cyan
 * underglow), Prime Video (neutral smoke, not its banner blue) and Hulu (dark smoke, its green logo glowing softly). Every
 * other app gets glass derived from its own banner. An app is recognised by its package name or its label.
 */
public enum LiquidBrand {
    NONE(0), NETFLIX(0), DISNEY(0), PRIME(0), HULU(LiquidTokens.HULU_GREEN);

    /** Colour of a soft glow in the logo's own shape, or 0 for none. */
    public final int logoGlow;

    LiquidBrand(int logoGlow) {
        this.logoGlow = logoGlow;
    }

    public static LiquidBrand of(String id, String label) {
        String s = ((id == null ? "" : id) + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        if (s.contains("netflix")) return NETFLIX;
        if (s.contains("disney")) return DISNEY;
        if (s.contains("prime video") || s.contains("primevideo") || s.contains("amazonvideo")) return PRIME;
        if (s.contains("hulu")) return HULU;
        return NONE;
    }
}
