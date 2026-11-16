package tv.calmhome.launcher.core;

import java.util.Locale;

/**
 * The colour of a focused tile's ambient glow: restrained, and taken from the app itself. A few apps whose brand colour is well known
 * and whose banner does not carry it reliably (Prime Video's banner is not its glow) are in a small map; every other app's colour is
 * extracted once from its own rendered artwork and cached with it (never on a focus move). Pure Java: unit-tested off-device.
 */
public final class BrandColor {
    private BrandColor() { }

    /** No brand colour found: the glow falls back to a cool neutral. */
    public static final int NEUTRAL = 0xFFB8D4E8;

    /** A known app's glow colour, or 0 when the app is not in the map. */
    public static int known(String pkg, String label) {
        String s = ((pkg == null ? "" : pkg) + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        if (s.contains("netflix")) return 0xFFD83A3A;
        if (s.contains("hulu")) return 0xFF3CCB7F;
        if (s.contains("disney")) return 0xFF39B6D8;
        if (s.contains("prime video") || s.contains("primevideo") || s.contains("amazonvideo")) return 0xFF4A86C8;
        if (s.contains("youtube")) return 0xFFD84A4A;
        return 0;
    }

    /**
     * The dominant saturated colour of {@code argb} (row-major pixels of a small copy of the artwork), toned down for a glow; 0 when
     * the artwork has too little colour (white, black or grey artwork glows neutral).
     */
    public static int extract(int[] argb, int count) {
        if (argb == null || count <= 0) return 0;
        final int buckets = 12;
        float[] weight = new float[buckets], r = new float[buckets], g = new float[buckets], b = new float[buckets];
        float[] hsv = new float[3];
        int considered = 0;
        for (int i = 0; i < count; i++) {
            int p = argb[i];
            if ((p >>> 24) < 200) continue;
            considered++;
            int pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
            toHsv(pr, pg, pb, hsv);
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
            int k = Math.min(buckets - 1, (int) (hsv[0] / 360f * buckets));
            float wgt = hsv[1] * hsv[2];
            weight[k] += wgt;
            r[k] += pr * wgt;
            g[k] += pg * wgt;
            b[k] += pb * wgt;
        }
        if (considered == 0) return 0;
        int best = 0;
        for (int k = 1; k < buckets; k++) if (weight[k] > weight[best]) best = k;
        if (weight[best] < considered * 0.03f) return 0;              // a few coloured pixels in a white logo are not a brand colour
        int cr = Math.round(r[best] / weight[best]), cg = Math.round(g[best] / weight[best]), cb = Math.round(b[best] / weight[best]);
        return soften(0xFF000000 | (cr << 16) | (cg << 8) | cb);
    }

    /** Caps saturation and brightness so the glow stays restrained over the glass. */
    public static int soften(int argb) {
        float[] hsv = new float[3];
        toHsv((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, hsv);
        hsv[1] = Math.min(hsv[1], 0.75f);
        hsv[2] = Math.max(0.55f, Math.min(hsv[2], 0.9f));
        return fromHsv(hsv);
    }

    static void toHsv(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf)), d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == rf) h = 60f * (((gf - bf) / d) % 6f);
        else if (max == gf) h = 60f * ((bf - rf) / d + 2f);
        else h = 60f * ((rf - gf) / d + 4f);
        if (h < 0f) h += 360f;
        out[0] = h;
        out[1] = max == 0f ? 0f : d / max;
        out[2] = max;
    }

    static int fromHsv(float[] hsv) {
        float h = hsv[0], s = hsv[1], v = hsv[2];
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}
