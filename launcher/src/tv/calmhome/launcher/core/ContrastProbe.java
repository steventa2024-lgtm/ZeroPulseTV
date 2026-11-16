package tv.calmhome.launcher.core;

import java.util.Arrays;

/**
 * The adaptive contrast layer, in numbers. Home puts white text and glass over any wallpaper, bright or dark, so a fixed
 * dark scrim has to be heavy enough for the brightest picture and is then far too heavy for the rest. Instead the
 * wallpaper is measured once (a small copy, 64 x 36) and each band of the screen gets exactly the darkening its own
 * brightest pixels need to keep white text at a target contrast, never less than a gentle floor and never more than a cap,
 * so a beautiful picture is not buried. The result is four alphas, one per band, that a scrim view paints as a smooth
 * vertical gradient and that the glass engine folds into the scene it refracts (so the glass sees what is really behind it).
 *
 * Pure Java: the maths is unit-tested over the bundled wallpapers without a device.
 */
public final class ContrastProbe {
    private ContrastProbe() { }

    public static final int HEADER = 0, GREETING = 1, ROW = 2, ACTIONS = 3, BANDS = 4;

    /** Each band's top and bottom as a share of the screen height (the nav and clock, the greeting, the app row and its labels, the actions). */
    public static final float[][] BAND = {{0f, 0.22f}, {0.28f, 0.52f}, {0.60f, 0.85f}, {0.89f, 1f}};
    /** The contrast ratio white text must reach in each band (WCAG: 3 for large text, 4.5 for small; the header and greeting are large). */
    public static final float[] TARGET = {3.6f, 3.6f, 4.5f, 4.5f};
    /** The darkening every band always gets, so a very light wallpaper starts from something and a dark one is barely touched. */
    public static final float[] FLOOR = {0.14f, 0.08f, 0.16f, 0.22f};
    /** No band is ever darkened more than this much, however bright the picture. */
    public static final float CAP = 0.46f;
    /** The brightest 8 % of a band decides: a bright cloud behind a word matters more than the average. */
    public static final float PERCENTILE = 0.92f;
    /** The scrim's colour: a deep navy, so the darkening belongs to the scene rather than being a flat black. */
    public static final int NAVY = 0xFF050F24;
    public static final int GRID_W = 64, GRID_H = 36;

    /** Relative luminance (WCAG) of an ARGB pixel, 0 (black) to 1 (white). */
    public static float luminance(int argb) {
        return 0.2126f * lin((argb >> 16) & 255) + 0.7152f * lin((argb >> 8) & 255) + 0.0722f * lin(argb & 255);
    }

    private static float lin(int c8) {
        float c = c8 / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    /** Contrast ratio of white text over a background of this relative luminance. */
    public static float whiteContrast(float luminance) {
        return 1.05f / (Math.max(0f, luminance) + 0.05f);
    }

    /** Relative luminance a background may have for white text to reach {@code contrast}. */
    public static float maxLuminance(float contrast) {
        return Math.max(0f, 1.05f / contrast - 0.05f);
    }

    /** Luminance of a background once a navy scrim of {@code alpha} and a user dim of {@code dim} (both 0..1) are over it. */
    public static float afterScrim(float luminance, float alpha, float dim) {
        float keep = (1f - clamp(alpha)) * (1f - clamp(dim));
        return luminance * (float) Math.pow(keep, 2.2);
    }

    /** The scrim alpha, 0..CAP, that brings a band whose bright pixels have {@code luminance} down to {@code contrast}. */
    public static float alphaFor(float luminance, float contrast, float dim) {
        float lit = afterScrim(luminance, 0f, dim);
        float allowed = maxLuminance(contrast);
        if (lit <= allowed || lit <= 0f) return 0f;
        return Math.min(CAP, 1f - (float) Math.pow(allowed / lit, 1.0 / 2.2));
    }

    /** The luminance below which {@code percentile} of the values lie. */
    public static float percentile(float[] values, int count, float percentile) {
        if (count <= 0) return 0f;
        float[] copy = Arrays.copyOf(values, count);
        Arrays.sort(copy);
        int i = Math.max(0, Math.min(count - 1, (int) Math.ceil(percentile * count) - 1));
        return copy[i];
    }

    /**
     * The four scrim alphas for a wallpaper (a downsampled ARGB grid of {@code w x h}, already framed as the screen shows it)
     * and the user's dim (0..1): each band gets {@code max(FLOOR, what its brightest pixels need)}, capped.
     */
    public static float[] plan(int[] argb, int w, int h, float dim) {
        float[] out = FLOOR.clone();
        if (argb == null || w <= 0 || h <= 0 || argb.length < w * h) return out;
        float[] cell = new float[w * h];
        for (int band = 0; band < BANDS; band++) {
            int y0 = Math.max(0, Math.min(h - 1, Math.round(BAND[band][0] * h))), y1 = Math.max(y0 + 1, Math.min(h, Math.round(BAND[band][1] * h)));
            int n = 0;
            for (int y = y0; y < y1; y++) for (int x = 0; x < w; x++) cell[n++] = luminance(argb[y * w + x]);
            float bright = percentile(cell, n, PERCENTILE);
            out[band] = Math.max(FLOOR[band], Math.min(CAP, Math.max(alphaFor(bright, TARGET[band], dim), 0f)));
        }
        return out;
    }

    /**
     * The scrim alpha at a height (0 top, 1 bottom): each band's own alpha across the band, and a smooth ramp between one
     * band and the next (above the first band and below the last it carries on at the band's value).
     */
    public static float alphaAt(float[] plan, float y) {
        float v = clamp(y);
        if (v <= BAND[0][1]) return plan[0];
        for (int b = 0; b < BANDS - 1; b++) {
            float from = BAND[b][1], to = BAND[b + 1][0];
            if (v <= to) {
                float t = to > from ? (v - from) / (to - from) : 1f;
                float s = t * t * (3f - 2f * t);                        // smoothstep, so there is no visible seam
                return plan[b] + (plan[b + 1] - plan[b]) * s;
            }
            if (v <= BAND[b + 1][1]) return plan[b + 1];
        }
        return plan[BANDS - 1];
    }

    /**
     * Darkens an opaque ARGB picture in place exactly as the scrim view paints over the wallpaper: first the flat wash, then
     * the plan's navy scrim by height. The glass engine refracts this copy, so the glass shows what is really behind it.
     */
    public static void applyTo(int[] argb, int w, int h, float[] plan, int washArgb) {
        if (argb == null || w <= 0 || h <= 0 || argb.length < w * h) return;
        float wash = (washArgb >>> 24) / 255f;
        for (int y = 0; y < h; y++) {
            float a = alphaAt(plan, (y + 0.5f) / h);
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                r = mix(r, (washArgb >> 16) & 255, wash);
                g = mix(g, (washArgb >> 8) & 255, wash);
                b = mix(b, washArgb & 255, wash);
                r = mix(r, (NAVY >> 16) & 255, a);
                g = mix(g, (NAVY >> 8) & 255, a);
                b = mix(b, NAVY & 255, a);
                argb[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int mix(int from, int to, float amount) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * amount)));
    }

    /** White-text contrast the plan leaves over a band whose bright pixels are {@code luminance}. */
    public static float contrastWith(float[] plan, int band, float luminance, float dim) {
        return whiteContrast(afterScrim(luminance, plan[band], dim));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
