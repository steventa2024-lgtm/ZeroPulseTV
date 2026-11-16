package tv.calmhome.launcher.streaming;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.HttpsFetch;

/**
 * Channel logos for one screen: HTTPS only, at most {@link #MAX_BYTES} per image, decoded down to the size shown,
 * kept in a small memory cache that is dropped when the screen closes or Android asks for memory. Logos that fail
 * are not retried while the screen is open, and a channel without a logo simply shows its name.
 */
final class Logos {

    static final int MAX_BYTES = 512 * 1024;
    static final int CACHE_BYTES = 6 * 1024 * 1024;

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private final Set<String> failed = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HttpsFetch.Opener opener;
    private final int sizePx;

    Logos(HttpsFetch.Opener opener, int sizePx) {
        this.opener = opener;
        this.sizePx = sizePx;
    }

    void load(final String url, final ImageView into) {
        into.setTag(url);
        into.setImageDrawable(null);
        if (url == null || failed.contains(url)) return;
        Bitmap hit = cache.get(url);
        if (hit != null) {
            into.setImageBitmap(hit);
            return;
        }
        if (pool.isShutdown()) return;
        pool.execute(() -> {
            Bitmap b = null;
            try {
                b = decode(HttpsFetch.bytes(url, MAX_BYTES, opener), sizePx);
            } catch (HttpsFetch.FetchException | RuntimeException e) {
                b = null;
            }
            final Bitmap result = b;
            main.post(() -> {
                if (result == null) failed.add(url);
                else cache.put(url, result);
                if (url.equals(into.getTag()) && result != null) into.setImageBitmap(result);
            });
        });
    }

    static Bitmap decode(byte[] data, int sizePx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 8192 || o.outHeight > 8192) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= sizePx && o.outHeight / (sample * 2) >= sizePx) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    void trim() {
        cache.evictAll();
    }

    void close() {
        pool.shutdownNow();
        cache.evictAll();
    }
}
