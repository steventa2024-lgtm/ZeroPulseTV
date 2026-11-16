package tv.calmhome.launcher.core;

/**
 * What an app tile shows and where, in numbers: the pure decisions behind the artwork pipeline (an installed app's own TV
 * banner, else its own icon, else a plain monogram). Nothing here is drawn or looked up; {@code AppArt} (Android) fetches the
 * artwork from the PackageManager and {@code TileArtView} paints it using these rules, so the rules are unit-tested off-device.
 *
 * A tile is a piece of approved glass and the artwork fills it, edge to edge, inside a hairline rim of glass that keeps the
 * tile's own edge readable. The artwork is never blurred (only the glass behind it is), never stretched (a banner is cropped a
 * sliver; an icon is scaled uniformly and its own backdrop is carried on to the edges of the tile) and never ours: the monogram
 * is the only thing ZeroPulse draws, and only when an app genuinely has no artwork.
 */
public final class TileArt {
    private TileArt() { }

    /**
     * What the tile ended up showing; also what diagnostics and tests report. {@code FILLED_ICON} is an icon whose own backdrop
     * (an adaptive icon's background layer, or the flat or smoothly shaded backdrop of a plain icon) fills the whole tile; an
     * {@code ICON} is a shape with no backdrop (a bare glyph), shown large on the glass.
     */
    public enum Kind { BANNER, LOGO, ICON, FILLED_ICON, MONOGRAM }

    /** A banner within this share of the box's aspect ratio fills the box (a sliver is cropped); a stranger one is letter-boxed. */
    public static final float COVER_TOLERANCE = 0.12f;
    /** The hairline of glass left around the artwork, as a share of the tile height (never under {@link #RIM_MIN_PX} pixels). */
    public static final float RIM_SHARE = 0.015f;
    public static final int RIM_MIN_PX = 2;
    /** A bare glyph icon's side as a share of the tile height. */
    public static final float ICON_SHARE = 0.70f;
    /** An adaptive icon's foreground layer is drawn this many tile heights tall, so its 72/108 safe zone fills 85 % of the tile. */
    public static final float ADAPTIVE_FOREGROUND = 1.28f;
    /** A backdrop can be carried to the edges only if at least this share of the edge pixels are opaque. */
    public static final float EDGE_OPAQUE_SHARE = 0.9f;
    /** ...and only if the edge changes by no more than this (0..255, mean over the channels) from one pixel to the next on average,
     *  and never by more than {@link #EDGE_MAX_JUMP} at once (a logo running off the edge would smear). */
    public static final float EDGE_MAX_STEP = 12f;
    public static final int EDGE_MAX_JUMP = 96;
    /** A banner with a transparent backdrop (a bare logo) is drawn at most this share of the box's width, centred on the glass. */
    public static final float LOGO_SHARE = 0.78f;
    /** More than this share of see-through pixels means the banner has no backdrop of its own. */
    public static final float TRANSPARENT_SHARE = 0.06f;
    /** Corner radius of an icon that is a plain opaque square, as a share of its side. */
    public static final float ICON_CORNER = 0.22f;
    /** Tiles on Home are a little taller than 16:9, so a 16:9 banner sits in an even frame of glass. */
    public static final float TILE_ASPECT = GlassMaterial.TILE_ASPECT;

    /** Pixels between a tile's edge and its artwork: only the hairline rim, so the artwork fills the glass. */
    public static int margin(int tileH) {
        return Math.max(RIM_MIN_PX, Math.round(RIM_SHARE * tileH));
    }

    /** The box the banner or logo may fill, {width, height}, for a tile of this size. */
    public static int[] artBox(int tileW, int tileH) {
        int m = margin(tileH);
        return new int[]{Math.max(1, tileW - 2 * m), Math.max(1, tileH - 2 * m)};
    }

    /** Fill the box when the banner's shape is close to it, else letter-box. */
    public static boolean covers(int srcW, int srcH, int boxW, int boxH) {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) return false;
        float a = srcW / (float) srcH, b = boxW / (float) boxH;
        return Math.abs(a / b - 1f) <= COVER_TOLERANCE;
    }

    /**
     * The size a banner is drawn at inside the box, {width, height}: the whole box when it covers, else the largest
     * uniform scale that fits (a logo with no backdrop is held to {@link #LOGO_SHARE} of the width).
     */
    public static int[] bannerSize(int srcW, int srcH, int boxW, int boxH, boolean transparent) {
        if (srcW <= 0 || srcH <= 0) return new int[]{boxW, boxH};
        if (!transparent && covers(srcW, srcH, boxW, boxH)) return new int[]{boxW, boxH};
        float s = Math.min(boxW / (float) srcW, boxH / (float) srcH);
        if (transparent) s = Math.min(s, boxW * LOGO_SHARE / srcW);
        return new int[]{Math.max(1, Math.round(srcW * s)), Math.max(1, Math.round(srcH * s))};
    }

    /** Corner radius of a banner drawn inside the glass: concentric with the glass's own corner. */
    public static float bannerRadius(int tileH) {
        return Math.max(2f, GlassMaterial.radius(tileH, false) - margin(tileH));
    }

    /** An icon's side in pixels for a tile. */
    public static int iconSide(int tileW, int tileH) {
        return Math.max(1, Math.min(Math.round(tileH * ICON_SHARE), Math.min(tileW, tileH) - 2 * margin(tileH)));
    }

    public static float iconRadius(int side) {
        return side * ICON_CORNER;
    }

    /** One or two capital letters for an app with no artwork of its own: initials of its first two words, else its first letter. */
    public static String monogram(String label) {
        if (label == null) return "?";
        StringBuilder out = new StringBuilder();
        for (String word : label.trim().split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            out.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
            if (out.length() >= 2) break;
        }
        return out.length() == 0 ? "?" : out.toString();
    }

    /** True if enough of the pixels (ARGB, row-major) are see-through that the banner has no backdrop of its own. */
    public static boolean mostlyTransparent(int[] argb, int count) {
        if (argb == null || count <= 0) return false;
        int seeThrough = 0;
        for (int i = 0; i < count; i++) if ((argb[i] >>> 24) < 250) seeThrough++;
        return seeThrough / (float) count > TRANSPARENT_SHARE;
    }

    /**
     * True if the picture has a backdrop of its own that can be carried on to the edges of a wider (or taller) frame: the two edges
     * that are extended ({@code horizontal}: the left and right columns, else the top and bottom rows) are opaque over the middle
     * half of their length (a rounded corner's cut-out is left out) and change smoothly. A bare glyph on nothing, or a logo that runs
     * off the edge, is refused: stretching its edge pixels would only smear it.
     */
    public static boolean extendsToEdges(int[] argb, int w, int h, boolean horizontal) {
        if (argb == null || w < 8 || h < 8 || argb.length < w * h) return false;
        int len = horizontal ? h : w;
        int from = Math.round(len * 0.25f), to = len - from;      // a corner may be cut away up to a quarter of the side (a rounded square's is about 22 %)
        if (to - from < 4) return false;
        for (int edge = 0; edge < 2; edge++) {
            int opaque = 0, steps = 0, jump = 0;
            long sum = 0;
            for (int i = from; i < to; i++) {
                int p = edgePixel(argb, w, h, horizontal, edge, i);
                if ((p >>> 24) >= 250) opaque++;
                if (i + 1 < to) {
                    int q = edgePixel(argb, w, h, horizontal, edge, i + 1);
                    int d = (Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255)));
                    sum += d / 3;
                    jump = Math.max(jump, Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)),
                            Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255)))));
                    steps++;
                }
            }
            if (opaque / (float) (to - from) < EDGE_OPAQUE_SHARE) return false;
            if (steps > 0 && sum / (float) steps > EDGE_MAX_STEP) return false;
            if (jump > EDGE_MAX_JUMP) return false;
        }
        return true;
    }

    private static int edgePixel(int[] argb, int w, int h, boolean horizontal, int edge, int i) {
        return horizontal ? argb[i * w + (edge == 0 ? 0 : w - 1)] : argb[(edge == 0 ? 0 : h - 1) * w + i];
    }

    /**
     * Gives a rounded-cornered icon a square backdrop: in every row (or column, for a vertical extension) the see-through pixels
     * outside the first and last opaque ones take that pixel's colour, so the corner cut-outs do not smear when the edges are carried on.
     */
    public static void squareOff(int[] argb, int w, int h, boolean horizontal) {
        if (argb == null || argb.length < w * h) return;
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            int first = -1, last = -1;
            for (int i = 0; i < len; i++) {
                int p = horizontal ? argb[line * w + i] : argb[i * w + line];
                if ((p >>> 24) >= 128) { if (first < 0) first = i; last = i; }
            }
            if (first < 0) continue;
            for (int i = 0; i < len; i++) {
                if (i >= first && i <= last) continue;
                int src = (i < first ? first : last);
                int p = horizontal ? argb[line * w + src] : argb[src * w + line];
                int o = 0xFF000000 | (p & 0xFFFFFF);
                if (horizontal) argb[line * w + i] = o; else argb[i * w + line] = o;
            }
        }
    }

    /**
     * True if all four corners of an icon are opaque (a plain square, with no shape of its own), so it should be given
     * rounded corners; an icon that already has a shape (a circle, a glyph on nothing) is left as it is.
     */
    public static boolean opaqueCorners(int[] argb, int w, int h) {
        if (argb == null || w < 4 || h < 4 || argb.length < w * h) return false;
        int cx = Math.max(1, w / 12), cy = Math.max(1, h / 12);
        int[][] corners = {{cx, cy}, {w - 1 - cx, cy}, {cx, h - 1 - cy}, {w - 1 - cx, h - 1 - cy}};
        for (int[] c : corners) if ((argb[c[1] * w + c[0]] >>> 24) < 250) return false;
        return true;
    }
}
