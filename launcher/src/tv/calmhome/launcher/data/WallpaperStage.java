package tv.calmhome.launcher.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.WallpaperCatalog;

/**
 * Shows bundled wallpapers on two stacked ImageViews and crossfades between them.
 * Memory: exactly two full-screen bitmaps exist; each new photo is decoded into whichever one is not
 * on screen (BitmapFactory inBitmap), so rotation allocates nothing. The timer only runs while started
 * (Home visible), and decoding happens off the UI thread.
 */
public final class WallpaperStage {

    public interface Listener {
        void onWallpaper(WallpaperCatalog.Entry e);
    }

    public interface ThumbCallback {
        void onThumb(String file, Bitmap thumb);
    }

    private final Context c;
    private final Prefs prefs;
    private final ImageView front, back;   // swapped on every change
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final WallpaperCatalog catalog;
    private ImageView showing;
    private Bitmap onScreen, spare;
    private WallpaperCatalog.Entry current;
    private boolean started, busy;
    private int fadeMs = 1500;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            next();
            schedule();
        }
    };

    public WallpaperStage(Context c, Prefs prefs, ImageView a, ImageView b, Listener l) {
        this.c = c;
        this.prefs = prefs;
        this.front = a;
        this.back = b;
        this.listener = l;
        this.catalog = WallpaperCatalog.parse(readLines("wallpapers/credits.tsv"));
        a.setAlpha(0f);
        b.setAlpha(0f);
    }

    public WallpaperCatalog catalog() {
        return catalog;
    }

    public WallpaperCatalog.Entry current() {
        return current;
    }

    public void setFadeMs(int ms) {
        fadeMs = ms;
    }

    /** Begins (or resumes) showing wallpapers; call from onStart. */
    public void start() {
        started = true;
        if (current == null) {
            WallpaperCatalog.Entry first = catalog.byFile(prefs.wallpaperPicked() == null ? "" : prefs.wallpaperPicked());
            List<WallpaperCatalog.Entry> pool = rotation();
            if (first == null || !pool.contains(first)) first = pool.isEmpty() ? null : pool.get(new Random().nextInt(pool.size()));
            if (first != null) show(first, false);
        }
        schedule();
    }

    /** Stops the rotation timer; call from onStop. Bitmaps stay so returning Home is instant. */
    public void stop() {
        started = false;
        ui.removeCallbacks(tick);
    }

    public void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** Re-reads interval/collection preferences and restarts the timer. */
    public void reload() {
        if (current != null && !rotation().contains(current)) next();
        schedule();
    }

    private void schedule() {
        ui.removeCallbacks(tick);
        int s = prefs.wallpaperInterval();
        if (started && s > 0) ui.postDelayed(tick, s * 1000L);
    }

    public List<WallpaperCatalog.Entry> rotation() {
        return catalog.inCategory(prefs.wallpaperCollection());
    }

    public void next() {
        List<WallpaperCatalog.Entry> pool = rotation();
        if (pool.isEmpty()) return;
        int i = current == null ? -1 : pool.indexOf(current);
        show(pool.get((i + 1) % pool.size()), true);
    }

    /** Shows a specific wallpaper now. */
    public void show(final WallpaperCatalog.Entry e, final boolean animate) {
        if (e == null || busy) return;
        busy = true;
        final Bitmap reuse = spare;
        spare = null;
        io.execute(() -> {
            Bitmap bmp = decode("wallpapers/" + e.file, reuse, 1);
            ui.post(() -> {
                busy = false;
                if (bmp == null) {
                    spare = reuse;
                    return;
                }
                crossfade(bmp, e, animate);
            });
        });
    }

    private void crossfade(Bitmap bmp, WallpaperCatalog.Entry e, boolean animate) {
        final ImageView in = showing == front ? back : front;
        final ImageView out = showing;
        final Bitmap old = onScreen;
        in.setImageBitmap(bmp);
        onScreen = bmp;
        showing = in;
        current = e;
        int ms = animate ? fadeMs : 300;
        in.animate().alpha(1f).setDuration(ms).withLayer().start();
        if (out != null) {
            out.animate().alpha(0f).setDuration(ms).withLayer().withEndAction(() -> {
                out.setImageDrawable(null);
                spare = old;                 // free for the next decode
            }).start();
        }
        listener.onWallpaper(e);
    }

    /** Small preview for the Wallpapers screen (1/8 size, ~240x135), decoded off the UI thread. */
    public void thumbnail(final String file, final ThumbCallback cb) {
        io.execute(() -> {
            final Bitmap b = decode("wallpapers/" + file, null, 8);
            if (b != null) ui.post(() -> cb.onThumb(file, b));
        });
    }

    private Bitmap decode(String asset, Bitmap reuse, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inMutable = true;
        if (reuse != null && sample == 1) o.inBitmap = reuse;
        try (InputStream in = c.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IllegalArgumentException wrongSize) {
            // inBitmap didn't fit (a differently sized image): decode into a fresh bitmap instead
            o.inBitmap = null;
            try (InputStream in = c.getAssets().open(asset)) {
                return BitmapFactory.decodeStream(in, null, o);
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readLines(String asset) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = c.getAssets().open(asset);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } catch (Exception ignored) { }
        return lines;
    }
}
