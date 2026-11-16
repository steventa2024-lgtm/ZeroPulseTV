package tv.calmhome.launcher.core;

/**
 * Dark logos on graphite glass. Some banners are a dark logo on a light backdrop (a black wordmark, a navy one). Keyed
 * out and laid on the dark graphite material they would nearly vanish, so their ink is lifted toward the glass's own
 * specular colour, just far enough to read, with its hue kept. Logos that are already light or colourful are not touched.
 * Pure Java: unit-tested off-device.
 */
public final class LiquidInk {
    private LiquidInk() { }

    /** A logo whose mean luminance (linear, 0..1) is below this counts as dark ink. */
    public static final float DARK_INK_LUMINANCE = 0.12f;
    /** Pixels darker than this (linear luminance, the same line as a dark logo) are lifted, in proportion to how dark they are; brand reds and blues stay as they are. */
    private static final float LIFT_BELOW = 0.12f;
    /** The most a pixel is mixed toward {@link #TARGET} (a fully black pixel). */
    private static final float MAX_MIX = 0.86f;
    /** The renderer's specular white, RGB(242,246,250). */
    private static final int TARGET = 0xF2F6FA;

    public static boolean needsLift(float logoLuminance) {
        return logoLuminance < DARK_INK_LUMINANCE;
    }

    /** One straight (non-premultiplied) ARGB pixel, lifted if dark. Alpha is kept. */
    public static int lift(int argb) {
        int a = argb >>> 24;
        if (a == 0) return argb;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float lum = LiquidKey.luminance(r / 255f, g / 255f, b / 255f);
        if (lum >= LIFT_BELOW) return argb;
        float t = 1f - lum / LIFT_BELOW;                            // 0 at the threshold, 1 for black
        float mix = MAX_MIX * t * t * (3f - 2f * t);                // smoothstep: mid-darks lift gently, black lifts most
        int tr = (TARGET >> 16) & 255, tg = (TARGET >> 8) & 255, tb = TARGET & 255;
        int nr = Math.round(r + (tr - r) * mix), ng = Math.round(g + (tg - g) * mix), nb = Math.round(b + (tb - b) * mix);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    /** A lifted copy of a logo's pixels (the input is left alone), or the input itself when the logo is not dark ink. */
    public static int[] liftedIfDark(int[] pixels, float logoLuminance) {
        if (!needsLift(logoLuminance)) return pixels;
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) out[i] = lift(pixels[i]);
        return out;
    }
}
