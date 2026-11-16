package tv.calmhome.launcher.core;

/**
 * The approved graphite glass material, in numbers, and how it scales to a surface of any size. The optics themselves live
 * in {@code GraphiteGlassRenderer} (refraction 11, frost 2, smoke 9 %, graphite tint RGB(19,23,29), focus lift 3.5 %);
 * this class only turns "a card of this height" into the renderer's two size inputs, so every surface (a Home tile, a
 * quick action) uses the same formulas. Pure Java: unit-tested off-device.
 *
 * The reference card is 300 x 152 with a corner radius of 32 and a bevel of 23. A surface of height H uses
 * {@code k = H / 152} as its material unit (display pixels per reference unit): the refraction shift is 11 units, the frost
 * 2 units, the bevel 23 units but never more than 22 % of the height, the corner radius 32 units (a capsule uses H / 2).
 */
public final class GlassMaterial {
    private GlassMaterial() { }

    public static final float REF_WIDTH = 300f, REF_HEIGHT = 152f, REF_RADIUS = 32f, REF_BEVEL = 23f;
    public static final float REFRACTION_UNITS = 11f, FROST_UNITS = 2f;
    /** The graphite smoke share the renderer mixes in (9 %), and the focus lift (3.5 %): documented here, applied there. */
    public static final float SMOKE = 0.09f, FOCUS_LIFT = 0.035f;
    /** The renderer refuses a frost sigma beyond this many cache pixels. */
    public static final float MAX_FROST_SIGMA = 24f;
    /** Never let the bevel eat more than this share of the surface's height. */
    public static final float MAX_BEVEL_SHARE = 0.22f;

    /** Material unit (display pixels per reference unit) for a surface that is {@code heightPx} tall. */
    public static float unit(float heightPx) {
        return heightPx <= 0f ? 1f : heightPx / REF_HEIGHT;
    }

    /** Bevel width in display pixels for a surface {@code heightPx} tall. */
    public static float bevel(float heightPx) {
        float h = Math.max(1f, heightPx);
        return Math.max(1f, Math.min(REF_BEVEL * unit(h), MAX_BEVEL_SHARE * h));
    }

    /** Corner radius in display pixels: a capsule's is half its height, a card's is the reference radius scaled. */
    public static float radius(float heightPx, boolean capsule) {
        float h = Math.max(1f, heightPx);
        return capsule ? h / 2f : Math.min(REF_RADIUS * unit(h), h / 2f);
    }

    /** Frost blur sigma in backdrop-cache pixels for a material unit and a cache scale (cache pixels per display pixel). */
    public static float frostSigma(float unitPx, float cacheScale) {
        return Math.min(MAX_FROST_SIGMA, Math.max(0f, FROST_UNITS * unitPx * cacheScale));
    }

    /** Surfaces whose frost sigmas agree to 1/8 cache pixel share one blurred copy of the wallpaper: its key. */
    public static int frostKey(float sigma) {
        return Math.round(sigma * 8f);
    }

    /** The sigma a frost key stands for. */
    public static float sigmaOf(int key) {
        return key / 8f;
    }

    /**
     * Size of a Home tile row: how wide and tall each of {@code columns} tiles is, in dp, for a screen that is
     * {@code screenDp} wide with {@code marginDp} kept free on each side and {@code gapDp} between tiles.
     */
    public static int[] tileSizeDp(float screenDp, float marginDp, int columns, float gapDp) {
        int cols = Math.max(1, columns);
        int w = Math.round((screenDp - 2f * marginDp - (cols - 1) * gapDp) / cols);
        return new int[]{w, tileHeightDp(w)};
    }

    /** A Home tile's width : height. A little taller than 16:9, so a 16:9 banner sits in an even frame of glass. */
    public static final float TILE_ASPECT = 1.6f;

    public static int tileHeightDp(int widthDp) {
        return Math.max(1, Math.round(widthDp / TILE_ASPECT));
    }

    /**
     * The same row in whole pixels, so that the tiles and the gaps add up to the available width exactly (rounding each tile
     * in dp left the row a few pixels short of the quick actions below it): {tileWidth, extraPixels}, where the first
     * {@code extraPixels} tiles are one pixel wider than {@code tileWidth}.
     */
    public static int[] rowPx(int availablePx, int columns, int gapPx) {
        int cols = Math.max(1, columns);
        int free = Math.max(cols, availablePx - (cols - 1) * Math.max(0, gapPx));
        return new int[]{free / cols, free % cols};
    }
}
