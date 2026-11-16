package tv.calmhome.launcher.data;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.ContrastProbe;
import tv.calmhome.launcher.core.LiquidBlur;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.core.WallpaperFraming;
import tv.calmhome.launcher.ui.FramedImageView;

/**
 * Shows bundled wallpapers on two stacked views and crossfades between them.
 * Memory: exactly two screen-sized bitmaps exist; each new photo is decoded into whichever one is not on screen
 * (BitmapFactory inBitmap), so rotation allocates nothing, and a picture bigger than the screen is decoded no bigger than the
 * screen needs (an 8K original at a quarter of its size on a 1080p TV). The timer only runs while started (Home visible),
 * and decoding happens off the UI thread.
 *
 * Framing: every picture fills the screen as the largest screen-shaped window centred on its focal point (the catalog's, or
 * the one the viewer chose on the Wallpapers screen, with an optional zoom), see {@link WallpaperFraming}. The small copies
 * the glass engine and the contrast layer measure are cut with the same window, so they match what is on screen.
 */
public final class WallpaperStage {

    public interface Listener {
        void onWallpaper(WallpaperCatalog.Entry e);
    }

    /** Liquid Glass: receives a small, lightly blurred copy of each wallpaper as it goes on screen (on the UI thread). */
    public interface BackdropSink {
        void onBackdrop(Bitmap blurred);
    }

    /**
     * Liquid Glass: receives a half-size copy of each wallpaper, framed as on screen and already under the scrim, for the glass
     * components to refract (UI thread).
     */
    public interface SceneSink {
        void onScene(Bitmap wallpaper);
    }

    /** Liquid Glass: receives the adaptive scrim's four alphas for each wallpaper (UI thread); see {@link ContrastProbe}. */
    public interface ContrastSink {
        void onContrast(float[] plan);
    }

    public interface ThumbCallback {
        void onThumb(String file, Bitmap thumb);
    }

    private final Context c;
    private final Prefs prefs;
    private final FramedImageView front, back;   // swapped on every change
    private final FramedImageView frostedView;
    private final WallpaperFrost filter = new WallpaperFrost();
    private boolean frostRequested, disposed;
    private String cachedFile, pendingFile;
    private final Listener listener;
    private BackdropSink backdropSink;
    private SceneSink sceneSink;
    private ContrastSink contrastSink;
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** Decodes the picture on its way to the screen, and the Wallpapers screen's previews. */
    private final ExecutorService io;
    /** Measures and copies the picture for the glass and the scrim, and softens it for the panels. Stale jobs are skipped, not run. */
    private final ExecutorService optics;
    private final WallpaperCatalog catalog;
    private final Map<String, int[]> sizes = new java.util.concurrent.ConcurrentHashMap<>();
    private final int screenW, screenH;
    private FramedImageView showing;
    private Bitmap onScreen, spare;
    private WallpaperCatalog.Entry current;
    private boolean started, busy;
    private int fadeMs = 1500;
    private volatile int generation;
    private volatile int opticsRun;
    /** The newest request that arrived while a picture was decoding: it runs next, and older ones are dropped (latest wins). */
    private WallpaperCatalog.Entry queued;
    private boolean queuedAnimate;
    /** The crossfade in flight (one animator drives both alphas, on plain views: no offscreen layer, no matter how big). */
    private ValueAnimator fade;
    private FramedImageView fadeIn, fadeOut;
    private Bitmap fadeOld;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            next();
            schedule();
        }
    };

    public WallpaperStage(Context c, Prefs prefs, FramedImageView a, FramedImageView b, FramedImageView frostedView, Listener l) {
        this(c, prefs, a, b, frostedView, l, Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor());
    }

    /** The stage with its two workers supplied (a test drives them by hand, to prove the order in which pictures and copies land). */
    public WallpaperStage(Context c, Prefs prefs, FramedImageView a, FramedImageView b, FramedImageView frostedView, Listener l,
                          ExecutorService decodeWorker, ExecutorService opticsWorker) {
        this.io = decodeWorker;
        this.optics = opticsWorker;
        this.c = c;
        this.prefs = prefs;
        this.front = a;
        this.back = b;
        this.frostedView = frostedView;
        frostedView.setVisibility(FramedImageView.INVISIBLE);
        this.listener = l;
        this.catalog = WallpaperCatalog.parse(readLines("wallpapers/credits.tsv"));
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        this.screenW = Math.max(1, dm.widthPixels);
        this.screenH = Math.max(1, dm.heightPixels);
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

    public void setBackdropSink(BackdropSink sink) {
        backdropSink = sink;
    }

    public void setSceneSink(SceneSink sink) {
        sceneSink = sink;
    }

    public void setContrastSink(ContrastSink sink) {
        contrastSink = sink;
    }

    // ------------------------------------------------------------------ framing

    /** The framing in force for a picture: the viewer's, else the catalog's own focal point, at zoom 1. {x, y, zoom}. */
    public float[] frameOf(WallpaperCatalog.Entry e) {
        return prefs.wallpaperFrame(e.file, e.focusX, e.focusY);
    }

    /** Frames the picture on screen now, keeps it, and re-measures the copies the glass and the contrast layer use. */
    public void setFrame(WallpaperCatalog.Entry e, float x, float y, float zoom) {
        prefs.setWallpaperFrame(e.file, x, y, zoom);
        if (current != null && current.file.equals(e.file)) {
            if (showing != null) showing.setFrame(x, y, zoom);
            frostedView.setFrame(x, y, zoom);
            deliverOptics(e);
        }
    }

    /** Back to the catalog's own framing. */
    public void resetFrame(WallpaperCatalog.Entry e) {
        prefs.resetWallpaperFrame(e.file);
        float[] f = frameOf(e);
        if (current != null && current.file.equals(e.file)) {
            if (showing != null) showing.setFrame(f[0], f[1], f[2]);
            frostedView.setFrame(f[0], f[1], f[2]);
            deliverOptics(e);
        }
    }

    /** Re-measures the current wallpaper (after the dim changed, say). */
    public void refreshOptics() {
        if (current != null) deliverOptics(current);
    }

    /** The picture's own size in pixels, read from the file's header without decoding it; null if it cannot be read. */
    public int[] sourceSize(String file) {
        int[] known = sizes.get(file);
        if (known != null) return known;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getAssets().open("wallpapers/" + file)) {
            BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int[] size = {o.outWidth, o.outHeight};
        sizes.put(file, size);
        return size;
    }

    // ------------------------------------------------------------------ the copies the glass and the contrast layer use

    /**
     * Cuts a half-size copy framed exactly as the screen frames the picture, measures it for the contrast layer, darkens it by
     * the scrim the layer will paint (so the glass refracts what is really behind it), blurs a smaller copy for the panels'
     * backdrop, and hands all three over. Off the UI thread; a result for a wallpaper that has gone is dropped.
     */
    private void deliverOptics(final WallpaperCatalog.Entry e) {
        final BackdropSink sink = backdropSink;
        final SceneSink scene = sceneSink;
        final ContrastSink contrast = contrastSink;
        if ((sink == null && scene == null && contrast == null) || disposed) return;
        final float[] frame = frameOf(e);
        final float dim = prefs.dimPercent() / 100f;
        final int token = ++generation;
        optics.execute(() -> {
            if (token != generation || disposed) return;               // a newer wallpaper, framing or dim was asked for while this waited
            int sceneW = Math.min(960, Math.max(2, screenW / 2)), sceneH = Math.max(2, Math.round(sceneW * (screenH / (float) screenW)));
            Bitmap framed = framedCopy("wallpapers/" + e.file, sceneW, sceneH, frame);
            if (framed == null || disposed) return;
            if (token != generation) {                                  // newer while decoding: do not spend the copies on a picture that has gone
                framed.recycle();
                return;
            }
            opticsRun++;
            int[] px = new int[sceneW * sceneH];
            framed.getPixels(px, 0, sceneW, 0, 0, sceneW, sceneH);
            framed.recycle();
            // measure a 64 x 36 copy: averaging in two steps so a bright speck is not lost to aliasing
            Bitmap measured = Bitmap.createBitmap(px, sceneW, sceneH, Bitmap.Config.ARGB_8888);
            Bitmap mid = Bitmap.createScaledBitmap(measured, 4 * ContrastProbe.GRID_W, 4 * ContrastProbe.GRID_H, true);
            Bitmap tiny = Bitmap.createScaledBitmap(mid, ContrastProbe.GRID_W, ContrastProbe.GRID_H, true);
            int[] grid = new int[ContrastProbe.GRID_W * ContrastProbe.GRID_H];
            tiny.getPixels(grid, 0, ContrastProbe.GRID_W, 0, 0, ContrastProbe.GRID_W, ContrastProbe.GRID_H);
            measured.recycle();
            if (mid != measured) mid.recycle();
            if (tiny != mid) tiny.recycle();
            final float[] plan = ContrastProbe.plan(grid, ContrastProbe.GRID_W, ContrastProbe.GRID_H, dim);
            ContrastProbe.applyTo(px, sceneW, sceneH, plan, LiquidTokens.WASH);
            final Bitmap sceneCopy = scene == null ? null : Bitmap.createBitmap(px, sceneW, sceneH, Bitmap.Config.ARGB_8888);
            Bitmap blurredCopy = null;
            if (sink != null) {
                Bitmap small = Bitmap.createScaledBitmap(Bitmap.createBitmap(px, sceneW, sceneH, Bitmap.Config.ARGB_8888),
                        Math.max(2, sceneW * 2 / LiquidTokens.BACKDROP_SHRINK), Math.max(2, sceneH * 2 / LiquidTokens.BACKDROP_SHRINK), true);
                int w = small.getWidth(), h = small.getHeight();
                int[] sp = new int[w * h];
                small.getPixels(sp, 0, w, 0, 0, w, h);
                small.setPixels(LiquidBlur.blur(sp, w, h, LiquidTokens.BLUR_RADIUS, 3), 0, w, 0, 0, w, h);
                blurredCopy = small;
            }
            final Bitmap blurred = blurredCopy;
            ui.post(() -> {
                if (disposed || token != generation || current == null || !e.file.equals(current.file)) return;
                if (contrast != null) contrast.onContrast(plan);
                if (sink != null && blurred != null) sink.onBackdrop(blurred);
                if (scene != null && sceneCopy != null) scene.onScene(sceneCopy);
            });
        });
    }

    /** A copy of the picture cut to the screen's shape around the focal point and scaled to w x h; null if it will not decode. */
    private Bitmap framedCopy(String asset, int w, int h, float[] frame) {
        int[] size = sizeOfAsset(asset);
        int sample = size == null ? 1 : WallpaperFraming.sampleSize(size[0], size[1], w, h);
        Bitmap src = decode(asset, null, sample);
        if (src == null) return null;
        int[] crop = WallpaperFraming.crop(src.getWidth(), src.getHeight(), w / (float) h, frame[0], frame[1], frame[2]);
        Bitmap cut = Bitmap.createBitmap(src, crop[0], crop[1], crop[2], crop[3]);
        Bitmap out = Bitmap.createScaledBitmap(cut, w, h, true);
        if (cut != src) cut.recycle();
        if (out != src) src.recycle();
        return out;
    }

    /** Keep Home sharp. Panels composite a small prefiltered bitmap without a full-screen GPU effect. */
    public void setFrosted(boolean enabled) {
        frostRequested = enabled;
        updateFrost();
    }

    private void updateFrost() {
        String file = current == null ? null : current.file;
        boolean ready = frostRequested && file != null && file.equals(cachedFile);
        frostedView.setVisibility(ready ? FramedImageView.VISIBLE : FramedImageView.INVISIBLE);
        front.setVisibility(ready ? FramedImageView.INVISIBLE : FramedImageView.VISIBLE);
        back.setVisibility(ready ? FramedImageView.INVISIBLE : FramedImageView.VISIBLE);
        if (disposed || !frostRequested || ready || file == null || file.equals(pendingFile)) return;
        pendingFile = file;
        optics.execute(() -> {
            if (disposed) return;
            int[] size = sizeOfAsset("wallpapers/" + file);
            Bitmap small = decode("wallpapers/" + file, null, size == null ? 8 : Math.max(1, WallpaperFraming.sampleSize(size[0], size[1], screenW / 8, screenH / 8)));
            if (small != null) filter.soften(small);
            ui.post(() -> {
                if (disposed || !file.equals(pendingFile)) return;
                pendingFile = null;
                if (small == null || current == null || !file.equals(current.file)) return;
                cachedFile = file;
                float[] f = frameOf(current);
                frostedView.setFrame(f[0], f[1], f[2]);
                frostedView.setImageBitmap(small);
                updateFrost();
            });
        });
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
        disposed = true;
        stop();
        if (fade != null) {
            fade.removeAllListeners();
            fade.cancel();
            fade = null;
        }
        io.shutdownNow();
        optics.shutdownNow();
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
        if (pool.size() == 1 && current != null) return;      // nothing to rotate to: do not decode the same photo again
        int i = current == null ? -1 : pool.indexOf(current);
        show(pool.get((i + 1) % pool.size()), true);
    }

    /** Shows a specific wallpaper now. */
    public void show(final WallpaperCatalog.Entry e, final boolean animate) {
        if (e == null || disposed) return;
        if (busy) {                                         // a picture is decoding: this request waits for it, and replaces any older waiting one
            queued = e;
            queuedAnimate = animate;
            return;
        }
        busy = true;
        final Bitmap reuse = spare;
        spare = null;
        io.execute(() -> {
            int[] size = sizeOfAsset("wallpapers/" + e.file);
            int sample = size == null ? 1 : WallpaperFraming.sampleSize(size[0], size[1], screenW, screenH);
            Bitmap bmp = decode("wallpapers/" + e.file, reuse, sample);
            ui.post(() -> {
                busy = false;
                if (disposed) return;
                if (bmp == null) {
                    spare = reuse;
                } else if (queued != null) {
                    spare = bmp;                            // a newer pick arrived while this one decoded: it never shows
                } else {
                    crossfade(bmp, e, animate);
                }
                WallpaperCatalog.Entry next = queued;
                boolean nextAnimate = queuedAnimate;
                queued = null;
                if (next != null) show(next, nextAnimate);
            });
        });
    }

    /** True while one picture is fading in over another (for tests and diagnostics). */
    public boolean isFading() {
        return fade != null;
    }

    /** How many optics jobs went on to measure and copy a picture (a job made stale by a newer one is skipped and not counted). */
    public int opticsJobsRun() {
        return opticsRun;
    }

    private void crossfade(Bitmap bmp, WallpaperCatalog.Entry e, boolean animate) {
        finishFade();                                       // a fade still running is completed first, so two never overlap
        final FramedImageView in = showing == front ? back : front;
        final FramedImageView out = showing;
        final Bitmap old = onScreen;
        float[] f = frameOf(e);
        in.setFrame(f[0], f[1], f[2]);
        in.setImageBitmap(bmp);
        in.setAlpha(0f);
        onScreen = bmp;
        showing = in;
        current = e;
        updateFrost();
        int ms = animate ? fadeMs : Math.min(fadeMs, 300);
        if (ms <= 0) {
            in.setAlpha(1f);
            if (out != null) {
                out.setAlpha(0f);
                out.setImageDrawable(null);
                spare = old;
            }
        } else {
            // One animator, plain alpha on image views (which never need an offscreen copy to fade): the 3005 build asked for a
            // hardware layer on each full-screen view, which cost two full-screen render targets (and about 34 MB of GPU
            // scratch memory) for the length of every fade.
            fadeIn = in;
            fadeOut = out;
            fadeOld = old;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(ms);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.addUpdateListener(u -> {
                float t = (Float) u.getAnimatedValue();
                in.setAlpha(t);
                if (out != null) out.setAlpha(1f - t);
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (fade == animation) finishFade();
                }
            });
            fade = a;
            a.start();
        }
        listener.onWallpaper(e);
        deliverOptics(e);
    }

    /** Ends the running crossfade at its end state: the new picture fully shown, the old one released for the next decode. */
    private void finishFade() {
        if (fade == null) return;
        ValueAnimator a = fade;
        fade = null;
        a.removeAllListeners();
        a.cancel();
        if (fadeIn != null) fadeIn.setAlpha(1f);
        if (fadeOut != null) {
            fadeOut.setAlpha(0f);
            fadeOut.setImageDrawable(null);
            spare = fadeOld;
        }
        fadeIn = null;
        fadeOut = null;
        fadeOld = null;
    }

    /** Small preview for the Wallpapers screen (about 400 x 225, enough for a 176 dp card at 2x), decoded off the UI thread. */
    public void thumbnail(final String file, final ThumbCallback cb) {
        io.execute(() -> {
            int[] size = sizeOfAsset("wallpapers/" + file);
            final Bitmap b = decode("wallpapers/" + file, null, size == null ? 4 : WallpaperFraming.sampleSize(size[0], size[1], 400, 225));
            if (b != null) ui.post(() -> cb.onThumb(file, b));
        });
    }

    private int[] sizeOfAsset(String asset) {
        return sourceSize(asset.substring(asset.indexOf('/') + 1));
    }

    private Bitmap decode(String asset, Bitmap reuse, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = Math.max(1, sample);
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
