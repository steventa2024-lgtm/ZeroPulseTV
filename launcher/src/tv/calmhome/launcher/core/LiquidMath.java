package tv.calmhome.launcher.core;

/** Liquid Glass layout and colour arithmetic that needs no Android classes (unit-tested). */
public final class LiquidMath {
    private LiquidMath() { }

    /**
     * Fits a logo into its tile with room to breathe. {@code box} is the logo's bounding box as fractions of the tile
     * (left, top, right, bottom), {@code w} x {@code h} the tile in pixels. Returns the scale (never above 1: only a logo
     * that would come closer than {@code padX} to the sides, or {@code padY} to the top and bottom, is shrunk), and the
     * horizontal and vertical shifts in pixels that centre the box in the tile after that scale about the tile's centre.
     */
    public static float[] logoFit(float[] box, float w, float h, float padX, float padY) {
        float bw = box[2] - box[0], bh = box[3] - box[1];
        if (bw <= 0f || bh <= 0f || w <= 0f || h <= 0f) return new float[]{1f, 0f, 0f};
        float s = Math.max(0.3f, Math.min(1f, Math.min((w - 2f * padX) / (bw * w), (h - 2f * padY) / (bh * h))));
        float cx = (box[0] + box[2]) / 2f, cy = (box[1] + box[3]) / 2f;
        return new float[]{s, (0.5f - cx) * w * s, (0.5f - cy) * h * s};
    }

    /**
     * Uniform scale of the glass kit's backdrop cache: half the screen, and never beyond the kit's 2,097,152-pixel budget.
     * The kit samples this cache through the screen-to-cache ratio, so a smaller cache only costs sampling detail.
     */
    public static float kitCacheScale(int screenW, int screenH) {
        return Math.min(0.5f, (float) Math.sqrt(2097152.0 / ((double) screenW * screenH)));
    }

    /** Radius, in pixels, of the radial highlight in each top corner: 22 % of the width, but never more than 1.2x the height. */
    public static float cornerReach(float w, float h) {
        return Math.min(LiquidTokens.CORNER_REACH * w, 1.2f * h);
    }

    /**
     * Where a wallpaper copy ({@code bmpW} x {@code bmpH}) lies on a {@code screenW} x {@code screenH} screen when it is
     * centre-cropped to fill it, as the wallpaper itself is: left, top, right, bottom in screen pixels (so the rectangle
     * reaches past the screen on the sides that are cropped).
     */
    public static float[] coverRect(int bmpW, int bmpH, float screenW, float screenH) {
        float scale = Math.max(screenW / bmpW, screenH / bmpH);
        float offX = (bmpW * scale - screenW) / 2f, offY = (bmpH * scale - screenH) / 2f;
        return new float[]{-offX, -offY, bmpW * scale - offX, bmpH * scale - offY};
    }

    /** A brand colour softened for glass: pulled toward grey so a loud banner does not become a loud tile. */
    public static int soften(int argb, float amount) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float grey = r * 0.299f + g * 0.587f + b * 0.114f;
        return (argb & 0xFF000000)
                | (Math.round(r + (grey - r) * amount) << 16) | (Math.round(g + (grey - g) * amount) << 8) | Math.round(b + (grey - b) * amount);
    }

    /** Linear blend of two ARGB colours, channel by channel. */
    public static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= Math.max(0, Math.min(255, Math.round(ca + (cb - ca) * t))) << shift;
        }
        return out;
    }

    /** A colour with its alpha multiplied by {@code by}. */
    public static int scaleAlpha(int argb, float by) {
        int a = Math.max(0, Math.min(255, Math.round((argb >>> 24) * by)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }
}
