package tv.calmhome.launcher.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.GlassMaterial;
import tv.calmhome.launcher.core.LiquidMath;
import tv.calmhome.glasscomponents.GlassClockView;
import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.glasscomponents.GraphiteGlassRenderer;

/**
 * The one glass engine of Liquid Glass Home. It feeds the supplied glass clock and navigation (package
 * {@code tv.calmhome.glasscomponents}) and every app tile and quick action ({@link GraphiteGlassSurface}) the backdrop they
 * refract: the scene that is really on screen, registered to the screen. The scene is the selected wallpaper centre-cropped
 * to the screen, with the same dim and scrims that sit over the sharp wallpaper, so a different wallpaper (or dimming)
 * changes all the glass at once. All preparation happens on a worker; results are discarded if a newer wallpaper has
 * arrived. There is one sharp scene, never a screen capture and never one buffer per component.
 *
 * The renderer wants an immutable software bitmap, a frosted copy of it per material scale, and an anchor view that covers
 * the screen. The anchor here is the activity's root view, whose bounds are the screen. The clock, the rail and the focus
 * pill each have their own scale; surfaces share one frosted copy per distinct scale (a row of tiles of one size needs one,
 * the row of quick actions another), keyed by {@link GlassMaterial#frostKey}.
 */
public final class GlassKit {
    /** Height of the clock's digits in dp. The kit's example draws 100 dp; a header clock is smaller, and its material scales with it. */
    public static final float CLOCK_DIGIT_DP = 28f;

    private static final class Variants {
        final GraphiteGlassRenderer.Backdrop sharp, clock, rail, focus;
        /** Frosted copies for the app surfaces, by {@link GlassMaterial#frostKey}. Added to on the UI thread, read on it. */
        final Map<Integer, GraphiteGlassRenderer.Backdrop> frost = new ConcurrentHashMap<>();
        final Set<Integer> requested = new HashSet<>();
        Variants(GraphiteGlassRenderer.Backdrop sharp, GraphiteGlassRenderer.Backdrop clock, GraphiteGlassRenderer.Backdrop rail,
                 GraphiteGlassRenderer.Backdrop focus) {
            this.sharp = sharp;
            this.clock = clock;
            this.rail = rail;
            this.focus = focus;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "glass-kit");
        t.setDaemon(true);
        return t;
    });
    private final Set<GlassTopNavView> navs = Collections.newSetFromMap(new WeakHashMap<GlassTopNavView, Boolean>());
    private final Set<GlassClockView> clocks = Collections.newSetFromMap(new WeakHashMap<GlassClockView, Boolean>());
    private final Set<GraphiteGlassSurface> surfaces = Collections.newSetFromMap(new WeakHashMap<GraphiteGlassSurface, Boolean>());
    private final Set<Integer> surfaceKeys = new HashSet<>();
    private final int cacheW, cacheH;
    private final float density, cacheScale;
    private View anchor;
    private Bitmap wallpaper;
    private int dim, scenes;
    private volatile int generation;
    private volatile Variants variants;

    public GlassKit(int screenW, int screenH, float density) {
        this.density = density;
        this.cacheScale = LiquidMath.kitCacheScale(screenW, screenH);
        this.cacheW = Math.max(2, Math.round(screenW * cacheScale));
        this.cacheH = Math.max(2, Math.round(screenH * cacheScale));
    }

    /** The view that covers the screen (the activity's root): where the components' positions are measured from. */
    public void setAnchor(View screen) {
        anchor = screen;
        rebind();
    }

    /** UI thread. A new wallpaper (a small copy that this class only reads, never changes or recycles) and the dim over it. */
    public void setWallpaper(Bitmap small, int dimPercent) {
        wallpaper = small;
        dim = dimPercent;
        prepare();
    }

    public void setDim(int dimPercent) {
        if (dimPercent == dim) return;
        dim = dimPercent;
        prepare();
    }

    public void bindNav(GlassTopNavView nav) {
        navs.add(nav);
        Variants v = variants;
        if (v != null && anchor != null) nav.bindBackdrops(v.rail, v.focus, anchor);
    }

    public void bindClock(GlassClockView clock) {
        clocks.add(clock);
        Variants v = variants;
        if (v != null && anchor != null) clock.bindBackdrop(v.clock, anchor);
    }

    /** UI thread. An app tile's or quick action's glass joins the engine (called when the surface is attached). */
    void bindSurface(GraphiteGlassSurface surface) {
        surfaces.add(surface);
        surfaceKeys.add(keyOf(surface));
        rebindSurface(surface);
    }

    void unbindSurface(GraphiteGlassSurface surface) {
        surfaces.remove(surface);
    }

    /** UI thread. Gives one surface the frosted copy that matches its material scale, preparing that copy if it is new. */
    void rebindSurface(GraphiteGlassSurface surface) {
        Variants v = variants;
        if (v == null || anchor == null) return;
        int key = keyOf(surface);
        surfaceKeys.add(key);
        GraphiteGlassRenderer.Backdrop frosted = v.frost.get(key);
        if (frosted != null) surface.bind(frosted, anchor);
        else requestVariant(v, key);
    }

    private int keyOf(GraphiteGlassSurface surface) {
        return GlassMaterial.frostKey(GlassMaterial.frostSigma(surface.unit(), cacheScale));
    }

    /** True once a backdrop has been prepared and the anchor is set (the components then show real glass). */
    public boolean isReady() {
        return variants != null && anchor != null;
    }

    public void shutdown() {
        generation++;
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------ diagnostics

    /** Surfaces (app tiles and quick actions) currently registered with the engine. */
    public int surfaceCount() {
        int n = 0;
        for (GraphiteGlassSurface s : new ArrayList<>(surfaces)) if (s.isPresent()) n++;     // a hidden row's glass is not on screen yet
        return n;
    }

    /** Registered surfaces that are showing real glass pixels (not the plain placeholder silhouette). */
    public int renderedSurfaceCount() {
        int n = 0;
        for (GraphiteGlassSurface s : new ArrayList<>(surfaces)) if (s.isPresent() && s.hasMaterial()) n++;
        return n;
    }

    /** Distinct frosted copies in use for the app surfaces (one per material scale). */
    public int surfaceVariantCount() {
        Variants v = variants;
        return v == null ? 0 : v.frost.size();
    }

    /** Wallpaper scenes prepared so far (each wallpaper or dim change prepares one). */
    public int sceneCount() {
        return scenes;
    }

    /**
     * One line saying how the glass is rendering: the mode, whether a scene exists, how many of the surfaces show real
     * glass, how many frosted copies back them, and the renderer's own timings on this device.
     */
    public String diagnostics() {
        return "Liquid Glass: shared graphite renderer, scene " + (isReady() ? "ready" : "preparing")
                + ", " + cacheW + "x" + cacheH + " backdrop, " + surfaceCount() + " tiles and actions (" + renderedSurfaceCount()
                + " rendered), " + surfaceVariantCount() + " frost copies, " + GraphiteGlassRenderer.Stats.renders() + " renders ("
                + String.format(java.util.Locale.ROOT, "%.0f", GraphiteGlassRenderer.Stats.averageMillis()) + " ms average, "
                + String.format(java.util.Locale.ROOT, "%.0f", GraphiteGlassRenderer.Stats.maxMillis()) + " ms longest)";
    }

    // ------------------------------------------------------------------ engine

    private void rebind() {
        Variants v = variants;
        if (v == null || anchor == null) return;
        for (GlassTopNavView nav : new ArrayList<>(navs)) nav.bindBackdrops(v.rail, v.focus, anchor);
        for (GlassClockView clock : new ArrayList<>(clocks)) clock.bindBackdrop(v.clock, anchor);
        for (GraphiteGlassSurface surface : new ArrayList<>(surfaces)) rebindSurface(surface);
    }

    private void prepare() {
        if (wallpaper == null) return;
        final int token = ++generation, dimNow = dim;
        final Bitmap source = wallpaper;
        final List<Integer> keys = new ArrayList<>(surfaceKeys);
        try {
            worker.execute(() -> {
                if (token != generation) return;                       // a newer wallpaper or dim was asked for while this waited: skip it
                try {
                    Bitmap scene = LiquidBackdrop.compose(source, cacheW, cacheH, dimNow);
                    Bitmap immutable = scene.copy(Bitmap.Config.ARGB_8888, false);
                    scene.recycle();                                   // an unpublished temporary
                    if (token != generation) {
                        immutable.recycle();                           // never published
                        return;
                    }
                    GraphiteGlassRenderer.Backdrop sharp = GraphiteGlassRenderer.prepareBackdrop(immutable, cacheW, cacheH);
                    float clockUnitPx = CLOCK_DIGIT_DP * density / 100f;
                    final Variants made = new Variants(sharp,
                            sharp.withFrost(2f * clockUnitPx * cacheScale),
                            sharp.withFrost(2f * 0.65f * density * cacheScale),
                            sharp.withFrost(2f * 0.5f * density * cacheScale));
                    for (int key : keys) {
                        if (token != generation) return;               // each frost copy is a full blur: stop at once when a newer scene supersedes this one
                        made.frost.put(key, sharp.withFrost(GlassMaterial.sigmaOf(key)));
                        made.requested.add(key);
                    }
                    ui.post(() -> {
                        if (token != generation) return;               // a newer wallpaper is already being prepared
                        variants = made;
                        scenes++;
                        rebind();
                    });
                } catch (RuntimeException ignored) {
                    // Keep the previous backdrop: the glass keeps its last good look rather than failing Home.
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // Home is closing.
        }
    }

    /** A surface needs a frosted copy that this scene does not have yet (a new size, or one that joined after the scene). */
    private void requestVariant(final Variants v, final int key) {
        if (!v.requested.add(key)) return;
        try {
            worker.execute(() -> {
                try {
                    final GraphiteGlassRenderer.Backdrop made = v.sharp.withFrost(GlassMaterial.sigmaOf(key));
                    ui.post(() -> {
                        if (variants != v) return;                      // the scene was replaced; its successor prepares its own
                        v.frost.put(key, made);
                        for (GraphiteGlassSurface surface : new ArrayList<>(surfaces)) if (keyOf(surface) == key) surface.bind(made, anchor);
                    });
                } catch (RuntimeException ignored) {
                    ui.post(() -> v.requested.remove(key));            // so a later bind can ask again
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // Home is closing.
        }
    }
}
