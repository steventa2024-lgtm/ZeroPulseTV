package tv.calmhome.launcher.core;

/**
 * A very small time-of-day shift of the Liquid Glass wash (the thin layer between the wallpaper and the glass): a little brighter and
 * cooler in the morning, neutral in the afternoon, a touch deeper in the evening and at night. It only nudges the existing wash's
 * colour and opacity by a few percent, so the wallpaper keeps its own colours and the glass material is unchanged. No purple, no
 * gradients. Pure Java: unit-tested off-device.
 */
public final class TimeTint {
    private TimeTint() { }

    public enum Band { MORNING, AFTERNOON, EVENING, NIGHT }

    public static Band band(int hour) {
        if (hour >= 5 && hour < 12) return Band.MORNING;
        if (hour >= 12 && hour < 17) return Band.AFTERNOON;
        if (hour >= 17 && hour < 22) return Band.EVENING;
        return Band.NIGHT;
    }

    /** The wash colour for {@code hour}, derived from the approved {@code base} wash. */
    public static int wash(int base, int hour) {
        int a = base >>> 24, r = (base >> 16) & 255, g = (base >> 8) & 255, b = base & 255;
        switch (band(hour)) {
            case MORNING:   return argb(Math.max(0, a - 6), r + 6, g + 10, b + 8);       // lighter, a hint of cyan
            case AFTERNOON: return base;                                               // the approved wash itself
            case EVENING:   return argb(a + 6, r, g + 2, b + 6);                         // cool, a little deeper
            default:        return argb(a + 12, Math.max(0, r - 2), g, b + 10);          // deep blue-graphite
        }
    }

    private static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
