package tv.calmhome.launcher.core;

/**
 * How a wallpaper of any size fills a 16:9 (or any) screen: the crop, centred on an optional focal point, and the size to
 * decode it at. Pure Java, so the maths is unit-tested without a device.
 *
 * The focal point is the part of the picture that should stay on screen, as a fraction of the picture's width and height
 * (0.5, 0.5 is the centre). The crop is the largest screen-shaped window of the picture, centred on that point and then
 * slid back inside the picture, so a point near an edge never leaves a gap.
 */
public final class WallpaperFraming {
    private WallpaperFraming() { }

    public static final float CENTER = 0.5f;
    /** How far one press of Left or Right moves the focal point in the Wallpapers screen. */
    public static final float STEP = 0.1f;

    public static float clamp01(float v) {
        return Float.isNaN(v) ? CENTER : Math.max(0f, Math.min(1f, v));
    }

    /** One step of the focal point in a direction (-1 or +1), kept in 0..1 and free of float drift (always a multiple of 0.1). */
    public static float nudge(float v, int direction) {
        int tenths = Math.round(clamp01(v) * 10f) + (direction < 0 ? -1 : direction > 0 ? 1 : 0);
        return Math.max(0, Math.min(10, tenths)) / 10f;
    }

    /** "0.3,0.5" for a focal point (what the preferences and the catalog store). */
    public static String format(float x, float y) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f", clamp01(x), clamp01(y));
    }

    /** The focal point in a stored string, {x, y}; the centre when it is missing or unreadable. */
    public static float[] parse(String s) {
        float[] out = {CENTER, CENTER};
        if (s == null) return out;
        String[] p = s.split(",");
        if (p.length != 2) return out;
        try {
            float x = Float.parseFloat(p[0].trim()), y = Float.parseFloat(p[1].trim());
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) return out;
            out[0] = clamp01(x);
            out[1] = clamp01(y);
        } catch (NumberFormatException unreadable) {
            // keep the centre
        }
        return out;
    }

    public static boolean isCentered(float x, float y) {
        return Math.abs(x - CENTER) < 0.005f && Math.abs(y - CENTER) < 0.005f;
    }

    // ---- the viewer's own framing of one wallpaper: focal point and zoom
    /** Zoom steps offered on the Wallpapers screen (1.0 = the plain fill of the screen). */
    public static final float[] ZOOMS = {1.0f, 1.1f, 1.2f, 1.3f, 1.4f};

    public static float clampZoom(float z) {
        return Float.isNaN(z) ? 1f : Math.max(ZOOMS[0], Math.min(ZOOMS[ZOOMS.length - 1], z));
    }

    /** "x,y,zoom" for a stored framing. */
    public static String formatFrame(float x, float y, float zoom) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", clamp01(x), clamp01(y), clampZoom(zoom));
    }

    /** {x, y, zoom} from a stored framing; the plain centred fill when it is missing or unreadable. */
    public static float[] parseFrame(String s, float defaultX, float defaultY) {
        float[] out = {clamp01(defaultX), clamp01(defaultY), 1f};
        if (s == null) return out;
        String[] p = s.split(",");
        if (p.length != 3) return out;
        try {
            float x = Float.parseFloat(p[0].trim()), y = Float.parseFloat(p[1].trim()), z = Float.parseFloat(p[2].trim());
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z) || Float.isInfinite(x + y + z)) return out;
            out[0] = clamp01(x);
            out[1] = clamp01(y);
            out[2] = clampZoom(z);
        } catch (NumberFormatException unreadable) {
            // keep the defaults
        }
        return out;
    }

    /**
     * The window of the picture that fills a screen of {@code dstAspect} (width / height), as {x, y, width, height} in the
     * picture's pixels, centred on the focal point and kept inside the picture. The whole picture's longer side is kept:
     * a wider picture loses width, a taller one loses height; the same aspect loses nothing.
     */
    public static int[] crop(int srcW, int srcH, float dstAspect, float fx, float fy) {
        return crop(srcW, srcH, dstAspect, fx, fy, 1f);
    }

    /** As above, then {@code zoom} (1 or more) shrinks the window around the focal point, so the picture appears larger. */
    public static int[] crop(int srcW, int srcH, float dstAspect, float fx, float fy, float zoom) {
        if (srcW <= 0 || srcH <= 0 || !(dstAspect > 0f)) return new int[]{0, 0, Math.max(0, srcW), Math.max(0, srcH)};
        float srcAspect = srcW / (float) srcH;
        float z = clampZoom(zoom);
        int w = srcW, h = srcH;
        if (srcAspect > dstAspect) w = Math.max(1, Math.min(srcW, Math.round(srcH * dstAspect)));
        else if (srcAspect < dstAspect) h = Math.max(1, Math.min(srcH, Math.round(srcW / dstAspect)));
        w = Math.max(1, Math.round(w / z));
        h = Math.max(1, Math.round(h / z));
        int x = Math.round(clamp01(fx) * srcW - w / 2f), y = Math.round(clamp01(fy) * srcH - h / 2f);
        x = Math.max(0, Math.min(srcW - w, x));
        y = Math.max(0, Math.min(srcH - h, y));
        return new int[]{x, y, w, h};
    }

    /**
     * Scale and translation that draw a picture of {@code srcW x srcH} so the crop for the view's own shape fills a view of
     * {@code viewW x viewH}: {scale, dx, dy}, for {@code ImageView.ScaleType.MATRIX}.
     */
    public static float[] matrix(int srcW, int srcH, int viewW, int viewH, float fx, float fy) {
        return matrix(srcW, srcH, viewW, viewH, fx, fy, 1f);
    }

    public static float[] matrix(int srcW, int srcH, int viewW, int viewH, float fx, float fy, float zoom) {
        if (srcW <= 0 || srcH <= 0 || viewW <= 0 || viewH <= 0) return new float[]{1f, 0f, 0f};
        int[] c = crop(srcW, srcH, viewW / (float) viewH, fx, fy, zoom);
        float scale = Math.max(viewW / (float) c[2], viewH / (float) c[3]);
        return new float[]{scale, -c[0] * scale, -c[1] * scale};
    }

    /**
     * The largest power-of-two {@code inSampleSize} that still leaves the picture at least as large as the screen, so a
     * 4K or 8K original is decoded no bigger than needed and a small one is never reduced further.
     */
    public static int sampleSize(int srcW, int srcH, int screenW, int screenH) {
        if (srcW <= 0 || srcH <= 0 || screenW <= 0 || screenH <= 0) return 1;
        int sample = 1;
        while (srcW / (sample * 2) >= screenW && srcH / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /** True if the picture has fewer pixels than the screen across either side (it will be enlarged on screen). */
    public static boolean isUpscaled(int srcW, int srcH, int screenW, int screenH) {
        return srcW < screenW || srcH < screenH;
    }
}
