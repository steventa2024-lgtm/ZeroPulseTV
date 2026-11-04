package tv.calmhome.launcher.data;

import android.graphics.Bitmap;

/** Filter a sampled wallpaper once on the wallpaper worker. The final image is scaled by ImageView. */
final class WallpaperFrost {
    private int[] source = new int[0], horizontal = new int[0];

    void soften(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        if (source.length < width * height) {
            source = new int[width * height];
            horizontal = new int[width * height];
        }
        bitmap.getPixels(source, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = average(source[row + Math.max(0, x - 1)], source[row + x],
                        source[row + Math.min(width - 1, x + 1)]);
            }
        }
        for (int y = 0; y < height; y++) {
            int above = Math.max(0, y - 1) * width, row = y * width, below = Math.min(height - 1, y + 1) * width;
            for (int x = 0; x < width; x++) {
                source[row + x] = average(horizontal[above + x], horizontal[row + x], horizontal[below + x]);
            }
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height);
    }

    private static int average(int a, int b, int c) {
        int red = ((a >>> 16 & 255) + (b >>> 16 & 255) + (c >>> 16 & 255)) / 3;
        int green = ((a >>> 8 & 255) + (b >>> 8 & 255) + (c >>> 8 & 255)) / 3;
        int blue = ((a & 255) + (b & 255) + (c & 255)) / 3;
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
}
