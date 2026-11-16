package tv.calmhome.launcher.core;

/**
 * Turns an app's TV banner (a logo on a plain or smoothly graded background, which is what nearly all of them are) into
 * glass: the background is keyed out so the tile can be clear glass in the brand's colour with only the logo on top.
 *
 * The background is estimated from the four corners (bilinear, so a gradient works); a pixel's opacity grows with how far
 * it is from that estimate, and its colour is un-mixed from the background so soft edges have no halo. A banner whose
 * edges or corners are not plain (a photo, a busy pattern) returns null and is drawn as a translucent picture instead.
 * Pure Java on int arrays, so it is unit-tested without Android.
 */
public final class LiquidKey {
    private LiquidKey() { }

    /** Distance (0..1, RGB) below which a pixel is background and above which it is logo; smooth in between. */
    public static final float KEY_LOW = 0.11f, KEY_HIGH = 0.32f;
    /** A pixel counts toward the logo's bounding box from this opacity. */
    private static final float BOX_ALPHA = 0.35f;
    /** More than this share of the banner's border away from the background means it is not a plain backdrop. */
    private static final float MAX_BUSY_BORDER = 0.12f, MAX_CORNER_SPREAD = 0.10f, MIN_COVERAGE = 0.015f, MAX_COVERAGE = 0.80f;

    /** A banner split into its logo (transparent everywhere else) and the colours of the background it sat on. */
    public static final class Logo {
        /** Straight (non-premultiplied) ARGB: the logo opaque, the background transparent. */
        public final int[] pixels;
        public final int width, height;
        /** The banner's background colour at the top and at the bottom (ARGB, opaque). */
        public final int topTint, bottomTint;
        public final float bgLuminance;
        /** Mean luminance of the logo itself, weighted by how opaque each pixel is. */
        public final float logoLuminance;
        /** Share of the banner covered by the logo (0..1). */
        public final float coverage;
        /** The logo's bounding box as fractions of the banner: left, top, right, bottom. */
        public final float[] box;

        Logo(int[] pixels, int width, int height, int topTint, int bottomTint, float bgLuminance, float logoLuminance,
             float coverage, float[] box) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.topTint = topTint;
            this.bottomTint = bottomTint;
            this.bgLuminance = bgLuminance;
            this.logoLuminance = logoLuminance;
            this.coverage = coverage;
            this.box = box;
        }

        /** Banners on a light backdrop (Netflix, YouTube) become light frosted glass; dark ones dark glass. */
        public boolean isLight() {
            return bgLuminance > 0.5f;
        }
    }

    public static Logo extract(int[] argb, int w, int h) {
        if (w < 8 || h < 8 || argb == null || argb.length < w * h) return null;
        int p = Math.max(2, Math.min(w, h) / 14);
        float[] tl = patch(argb, w, 0, 0, p);
        float[] tr = patch(argb, w, w - p, 0, p);
        float[] bl = patch(argb, w, 0, h - p, p);
        float[] br = patch(argb, w, w - p, h - p, p);
        if (tl == null || tr == null || bl == null || br == null) return null;
        for (float[] corner : new float[][]{tl, tr, bl, br}) if (corner[3] > MAX_CORNER_SPREAD) return null;   // not one plain colour

        float[] bg = new float[3];
        // The border must be background too: otherwise this is a picture, not a logo on a backdrop.
        int border = 0, busy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x > 1 && x < w - 2 && y > 1 && y < h - 2) continue;
                border++;
                bgAt(tl, tr, bl, br, w, h, x, y, bg);
                if (distance(argb[y * w + x], bg) > 0.14f) busy++;
            }
        }
        if (busy > border * MAX_BUSY_BORDER) return null;

        int[] out = new int[w * h];
        double alphaSum = 0, lumSum = 0;
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = argb[y * w + x];
                bgAt(tl, tr, bl, br, w, h, x, y, bg);
                float srcA = (px >>> 24) / 255f;
                float a = smooth(KEY_LOW, KEY_HIGH, distance(px, bg)) * srcA;
                if (a < 0.02f) continue;
                float r = unmix(((px >> 16) & 255) / 255f, bg[0], a);
                float g = unmix(((px >> 8) & 255) / 255f, bg[1], a);
                float b = unmix((px & 255) / 255f, bg[2], a);
                out[y * w + x] = (Math.round(a * 255f) << 24) | (to255(r) << 16) | (to255(g) << 8) | to255(b);
                alphaSum += a;
                lumSum += a * luminance(r, g, b);
                if (a >= BOX_ALPHA) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        float coverage = (float) (alphaSum / (w * h));
        if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) return null;

        float[] top = new float[3], bottom = new float[3], mean = new float[3];
        for (int c = 0; c < 3; c++) {
            top[c] = (tl[c] + tr[c]) / 2f;
            bottom[c] = (bl[c] + br[c]) / 2f;
            mean[c] = (top[c] + bottom[c]) / 2f;
        }
        float[] box = maxX >= minX && maxY >= minY
                ? new float[]{minX / (float) w, minY / (float) h, (maxX + 1) / (float) w, (maxY + 1) / (float) h}
                : new float[]{0f, 0f, 1f, 1f};
        return new Logo(out, w, h, rgb(top), rgb(bottom), luminance(mean[0], mean[1], mean[2]),
                (float) (lumSum / alphaSum), coverage, box);
    }

    /** Bilinear background estimate at (x, y) from the four corner colours. */
    private static void bgAt(float[] tl, float[] tr, float[] bl, float[] br, int w, int h, int x, int y, float[] bg) {
        float fx = x / (w - 1f), fy = y / (h - 1f);
        for (int c = 0; c < 3; c++) {
            float top = tl[c] + (tr[c] - tl[c]) * fx;
            float bottom = bl[c] + (br[c] - bl[c]) * fx;
            bg[c] = top + (bottom - top) * fy;
        }
    }

    /** Mean colour of a p x p patch (r, g, b in 0..1) and, as the fourth value, how far its pixels stray from that mean. */
    private static float[] patch(int[] argb, int w, int x0, int y0, int p) {
        float r = 0, g = 0, b = 0;
        for (int y = y0; y < y0 + p; y++) {
            for (int x = x0; x < x0 + p; x++) {
                int px = argb[y * w + x];
                if ((px >>> 24) < 200) return null;                                  // a transparent corner: not a backdrop
                r += ((px >> 16) & 255) / 255f;
                g += ((px >> 8) & 255) / 255f;
                b += (px & 255) / 255f;
            }
        }
        float n = p * p;
        float[] mean = {r / n, g / n, b / n, 0f};
        float spread = 0;
        for (int y = y0; y < y0 + p; y++) for (int x = x0; x < x0 + p; x++) spread = Math.max(spread, distance(argb[y * w + x], mean));
        mean[3] = spread;
        return mean;
    }

    private static float distance(int px, float[] bg) {
        float dr = ((px >> 16) & 255) / 255f - bg[0];
        float dg = ((px >> 8) & 255) / 255f - bg[1];
        float db = (px & 255) / 255f - bg[2];
        return (float) Math.sqrt((dr * dr + dg * dg + db * db) / 3f);
    }

    /** The logo colour that, at opacity {@code a}, over {@code bg} gives {@code p}. */
    private static float unmix(float p, float bg, float a) {
        return Math.max(0f, Math.min(1f, bg + (p - bg) / a));
    }

    private static float smooth(float lo, float hi, float x) {
        float t = Math.max(0f, Math.min(1f, (x - lo) / (hi - lo)));
        return t * t * (3f - 2f * t);
    }

    private static int to255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static int rgb(float[] c) {
        return (0xFF << 24) | (to255(c[0]) << 16) | (to255(c[1]) << 8) | to255(c[2]);
    }

    public static float luminance(float r, float g, float b) {
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b);
    }

    private static float lin(float v) {
        return v <= 0.03928f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4);
    }
}
