package tv.calmhome.launcher.core;

/**
 * The frosted backdrop's blur, done once per wallpaper on a small copy (1/4 size) rather than on every frame. Three box
 * passes approximate a Gaussian. Light on purpose: clear glass shows the scenery through the middle of each panel.
 */
public final class LiquidBlur {
    private LiquidBlur() { }

    private static final int[] SHIFTS = {16, 8, 0};

    /** Box-blurs {@code argb} (w x h, edges clamped). The alpha channel is left as it is; the input is not changed. */
    public static int[] blur(int[] argb, int w, int h, int radius, int passes) {
        if (radius <= 0 || w < 2 || h < 2) return argb.clone();
        int[] src = argb.clone();
        int[] dst = new int[argb.length];
        int[] line = new int[Math.max(w, h)];
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < h; y++) {                                                  // horizontal
                for (int c = 0; c < 3; c++) {
                    int shift = SHIFTS[c];
                    for (int x = 0; x < w; x++) line[x] = (src[y * w + x] >> shift) & 255;
                    slide(line, w, radius);
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        dst[i] = (c == 0 ? (src[i] & (255 << 24)) : dst[i]) | (line[x] << shift);
                    }
                }
            }
            int[] t = src; src = dst; dst = t;
            for (int x = 0; x < w; x++) {                                                  // vertical
                for (int c = 0; c < 3; c++) {
                    int shift = SHIFTS[c];
                    for (int y = 0; y < h; y++) line[y] = (src[y * w + x] >> shift) & 255;
                    slide(line, h, radius);
                    for (int y = 0; y < h; y++) {
                        int i = y * w + x;
                        dst[i] = (c == 0 ? (src[i] & (255 << 24)) : dst[i]) | (line[y] << shift);
                    }
                }
            }
            t = src; src = dst; dst = t;
        }
        return src;
    }

    /** In-place sliding-window mean over the first {@code n} values of {@code v}, clamping at both ends. */
    private static void slide(int[] v, int n, int r) {
        int window = 2 * r + 1;
        int sum = 0;
        for (int i = -r; i <= r; i++) sum += v[Math.max(0, Math.min(n - 1, i))];
        int[] copy = java.util.Arrays.copyOf(v, n);
        for (int i = 0; i < n; i++) {
            v[i] = (sum + window / 2) / window;
            sum += copy[Math.min(n - 1, i + 1 + r)] - copy[Math.max(0, i - r)];
        }
    }
}
