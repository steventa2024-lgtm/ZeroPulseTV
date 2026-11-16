package tv.calmhome.launcher.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import tv.calmhome.launcher.core.TileArt;

/**
 * The artwork pipeline for Home's app tiles. Each installed app is shown by what it ships itself: its TV banner if it has
 * one, else its launcher icon (at the highest density its package offers), else, only when it truly declares neither, a
 * plain monogram. Nothing is ever drawn from memory of what a brand looks like.
 *
 * The artwork is rendered once into a bitmap of exactly the size a tile will blit it at (so it is never scaled again, and
 * stays sharp: only the glass behind it is blurred), cropped to the glass's concentric corner, and kept in a small
 * least-recently-used cache keyed by package and tile size. A package change invalidates just that package. The layout
 * rules (what fills the frame, what is letter-boxed, how big an icon is) are in {@link TileArt}.
 */
public final class AppArt {

    /** One tile's artwork, ready to blit. */
    public static final class Art {
        public final TileArt.Kind kind;
        public final Bitmap bitmap;
        /** Where it came from: an {@link Apps.Source} origin, "drawable" for artwork a caller supplied, or "monogram". */
        public final String origin;
        /** Size of the artwork as the app shipped it (0 for a monogram). */
        public final int sourceWidth, sourceHeight;
        /** The focus glow's colour: the artwork's own dominant colour (extracted once, here), or 0 when it has none. */
        public final int accent;

        Art(TileArt.Kind kind, Bitmap bitmap, String origin, int sourceWidth, int sourceHeight) {
            this.kind = kind;
            this.bitmap = bitmap;
            this.origin = origin;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.accent = kind == TileArt.Kind.MONOGRAM ? 0 : accentOf(bitmap);
        }

        /** The dominant colour of a small copy of the artwork (24 x 14 samples, once per artwork, never on a focus move). */
        private static int accentOf(Bitmap b) {
            if (b == null || b.getWidth() < 2 || b.getHeight() < 2) return 0;
            Bitmap small = Bitmap.createScaledBitmap(b, 24, 14, true);
            int[] px = new int[24 * 14];
            small.getPixels(px, 0, 24, 0, 0, 24, 14);
            if (small != b) small.recycle();
            return tv.calmhome.launcher.core.BrandColor.extract(px, px.length);
        }

        public boolean isGenuine() {
            return kind != TileArt.Kind.MONOGRAM;
        }
    }

    /** Cache and render counters, for the diagnostics line and the tests. */
    public static final class Stats {
        private static final AtomicInteger HITS = new AtomicInteger(), MISSES = new AtomicInteger();
        private static final AtomicLong NANOS = new AtomicLong(), LONGEST = new AtomicLong();

        private Stats() { }

        static void hit() {
            HITS.incrementAndGet();
        }

        static void miss(long nanos) {
            MISSES.incrementAndGet();
            NANOS.addAndGet(nanos);
            long prev;
            while (nanos > (prev = LONGEST.get()) && !LONGEST.compareAndSet(prev, nanos)) { /* keep the longest */ }
        }

        public static int hits() {
            return HITS.get();
        }

        public static int renders() {
            return MISSES.get();
        }

        public static double averageMillis() {
            int n = MISSES.get();
            return n == 0 ? 0 : NANOS.get() / 1e6 / n;
        }

        public static double longestMillis() {
            return LONGEST.get() / 1e6;
        }

        public static void reset() {
            HITS.set(0);
            MISSES.set(0);
            NANOS.set(0);
            LONGEST.set(0);
        }
    }

    private static final int CACHE_BYTES = 8 * 1024 * 1024;

    private final Apps apps;
    private final LruCache<String, Art> cache = new LruCache<String, Art>(CACHE_BYTES) {
        @Override protected int sizeOf(String key, Art art) {
            return art.bitmap.getByteCount();
        }
    };

    public AppArt(Context c, Apps apps) {
        this.apps = apps;
    }

    /** The artwork for an installed app in a tile of this size (pixels), from the cache when it has been rendered before. */
    public Art get(String pkg, int tileW, int tileH) {
        String key = pkg + "|" + tileW + "x" + tileH;
        Art hit = cache.get(key);
        if (hit != null) {
            Stats.hit();
            return hit;
        }
        long t0 = System.nanoTime();
        Art art = build(pkg, tileW, tileH);
        cache.put(key, art);
        Stats.miss(System.nanoTime() - t0);
        return art;
    }

    /** Forgets one package's artwork (after it was updated, removed or reinstalled). */
    public void invalidate(String pkg) {
        Iterator<String> keys = cache.snapshot().keySet().iterator();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.startsWith(pkg + "|")) cache.remove(k);
        }
    }

    public void clear() {
        cache.evictAll();
    }

    /** Bytes of rendered artwork held (for diagnostics). */
    public int cachedBytes() {
        return cache.size();
    }

    public int cachedCount() {
        return cache.snapshot().size();
    }

    private Art build(String pkg, int tileW, int tileH) {
        Apps.Source banner = apps.bannerSource(pkg);
        if (banner != null) {
            Art art = fromBanner(banner.drawable, banner.origin, tileW, tileH);
            if (art != null) return art;
        }
        Apps.Source icon = apps.iconSource(pkg);
        if (icon != null) {
            Art art = fromIcon(icon.drawable, icon.origin, tileW, tileH);
            if (art != null) return art;
        }
        return monogram(apps.label(pkg), tileW, tileH);
    }

    // ------------------------------------------------------------------ rendering (also used for artwork a caller supplies)

    /** A banner rendered for a tile: filling the glass frame when its shape is close, else letter-boxed, a bare logo held inside. */
    public static Art fromBanner(Drawable banner, String origin, int tileW, int tileH) {
        if (banner == null) return null;
        int srcW = banner.getIntrinsicWidth(), srcH = banner.getIntrinsicHeight();
        int[] box = TileArt.artBox(tileW, tileH);
        if (srcW <= 0 || srcH <= 0) {
            srcW = box[0];
            srcH = box[1];                                           // a vector with no intrinsic size is drawn at the frame's shape
        }
        boolean transparent = probeTransparent(banner);
        int[] size = TileArt.bannerSize(srcW, srcH, box[0], box[1], transparent);
        boolean fills = !transparent && size[0] == box[0] && size[1] == box[1];
        Bitmap out = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        if (fills) {
            float s = Math.max(size[0] / (float) srcW, size[1] / (float) srcH);       // fill the frame, cropping a sliver
            int dw = Math.round(srcW * s), dh = Math.round(srcH * s);
            draw(banner, canvas, (size[0] - dw) / 2, (size[1] - dh) / 2, dw, dh);
            out = rounded(out, TileArt.bannerRadius(tileH));
        } else {
            draw(banner, canvas, 0, 0, size[0], size[1]);
            if (!transparent) {
                // A banner of another shape keeps its own proportions; when its backdrop is flat or smoothly shaded it is carried on to
                // the edges of the tile, so the tile is still filled (nothing is stretched or blurred).
                Bitmap wide = extendToFill(out, box[0], box[1], TileArt.bannerRadius(tileH));
                if (wide != null) return new Art(TileArt.Kind.FILLED_ICON, wide, origin, srcW, srcH);
                out = rounded(out, TileArt.bannerRadius(tileH));
            }
        }
        return new Art(transparent ? TileArt.Kind.LOGO : TileArt.Kind.BANNER, out, origin, srcW, srcH);
    }

    /**
     * An icon rendered to fill the tile. An adaptive icon's background layer covers the whole tile and its foreground sits in the
     * middle at tile height; a plain icon keeps its proportions and its own flat or smoothly shaded backdrop is carried on to the
     * edges. Only an icon with no backdrop to carry (a bare glyph, a logo running off its edge) is shown as a large square on the glass.
     */
    public static Art fromIcon(Drawable icon, String origin, int tileW, int tileH) {
        if (icon == null) return null;
        int m = TileArt.margin(tileH);
        int boxW = Math.max(1, tileW - 2 * m), boxH = Math.max(1, tileH - 2 * m);
        float radius = TileArt.bannerRadius(tileH);
        int srcW = icon.getIntrinsicWidth(), srcH = icon.getIntrinsicHeight();
        if (android.os.Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
            Drawable back = ((AdaptiveIconDrawable) icon).getBackground(), front = ((AdaptiveIconDrawable) icon).getForeground();
            if (back != null) {
                Bitmap out = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                int cover = Math.max(boxW, boxH);                                      // the background is a plain square layer: cover the frame
                draw(back, canvas, (boxW - cover) / 2, (boxH - cover) / 2, cover, cover);
                if (front != null) {
                    int f = Math.round(Math.min(boxW, boxH) * TileArt.ADAPTIVE_FOREGROUND);
                    draw(front, canvas, (boxW - f) / 2, (boxH - f) / 2, f, f);
                }
                return new Art(TileArt.Kind.FILLED_ICON, rounded(out, radius), origin, Math.max(0, srcW), Math.max(0, srcH));
            }
        }
        int ownSide = Math.min(boxW, boxH);
        if (ownSide >= 8) {
            Bitmap square = Bitmap.createBitmap(ownSide, ownSide, Bitmap.Config.ARGB_8888);
            int dw = ownSide, dh = ownSide;
            if (srcW > 0 && srcH > 0 && srcW != srcH) {
                float s = Math.min(ownSide / (float) srcW, ownSide / (float) srcH);          // never stretch a non-square icon
                dw = Math.max(1, Math.round(srcW * s));
                dh = Math.max(1, Math.round(srcH * s));
            }
            draw(icon, new Canvas(square), (ownSide - dw) / 2, (ownSide - dh) / 2, dw, dh);
            Bitmap wide = extendToFill(square, boxW, boxH, radius);
            if (wide != null) return new Art(TileArt.Kind.FILLED_ICON, wide, origin, Math.max(0, srcW), Math.max(0, srcH));
        }
        return glyphIcon(icon, origin, tileW, tileH);
    }

    /**
     * {@code picture} (never stretched) centred in a {@code boxW x boxH} frame, its own backdrop carried on to the edges of the frame, with
     * rounded corners; or null when the picture has no backdrop that can be carried (see {@link TileArt#extendsToEdges}). The picture
     * is recycled when it is used.
     */
    private static Bitmap extendToFill(Bitmap picture, int boxW, int boxH, float radius) {
        int w = picture.getWidth(), h = picture.getHeight();
        if (w >= boxW && h >= boxH) return null;                                     // already fills: nothing to extend
        if (w < boxW && h < boxH) return null;                                       // it fits inside on both axes: not a case for an edge extension
        boolean horizontal = w < boxW;
        int[] px = new int[w * h];
        picture.getPixels(px, 0, w, 0, 0, w, h);
        if (!TileArt.extendsToEdges(px, w, h, horizontal)) return null;
        TileArt.squareOff(px, w, h, horizontal);
        // The picture's outermost pixels can be an outline or a shadow; carry on the colour just inside them.
        int inset = Math.max(1, Math.min(w, h) / 64);
        int cw = horizontal ? w - 2 * inset : w, ch = horizontal ? h : h - 2 * inset;
        Bitmap core = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap source = horizontal ? Bitmap.createBitmap(core, inset, 0, cw, ch) : Bitmap.createBitmap(core, 0, inset, cw, ch);
        Bitmap out = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();                                                       // no smoothing: whole-pixel placement keeps the picture exactly as drawn
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix move = new android.graphics.Matrix();
        move.setTranslate((boxW - cw) / 2, (boxH - ch) / 2);                         // integer division: never half a pixel
        shader.setLocalMatrix(move);
        p.setShader(shader);
        new Canvas(out).drawRect(0, 0, boxW, boxH, p);                               // the picture at its own pixels, its edge colours carried beyond it
        if (source != core) core.recycle();
        source.recycle();
        picture.recycle();
        return rounded(out, radius);
    }

    /** A bare glyph icon (no backdrop of its own): large and square on the glass; a plain opaque square gets rounded corners. */
    private static Art glyphIcon(Drawable icon, String origin, int tileW, int tileH) {
        int side = TileArt.iconSide(tileW, tileH);
        int srcW = icon.getIntrinsicWidth(), srcH = icon.getIntrinsicHeight();
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        int dw = side, dh = side;
        boolean adaptive = android.os.Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;
        if (!adaptive && srcW > 0 && srcH > 0 && srcW != srcH) {
            float s = Math.min(side / (float) srcW, side / (float) srcH);          // never stretch a non-square icon
            dw = Math.max(1, Math.round(srcW * s));
            dh = Math.max(1, Math.round(srcH * s));
        }
        draw(icon, canvas, (side - dw) / 2, (side - dh) / 2, dw, dh);
        if (!adaptive) {
            int[] px = new int[side * side];
            out.getPixels(px, 0, side, 0, 0, side, side);
            if (TileArt.opaqueCorners(px, side, side)) out = rounded(out, TileArt.iconRadius(side));
        }
        return new Art(TileArt.Kind.ICON, out, origin, Math.max(0, srcW), Math.max(0, srcH));
    }

    /**
     * What an app with no artwork of its own gets: its initials on a plain graphite square. It carries no colour, mark or
     * lettering that could be taken for a brand, and the tile shows the app's name beside it.
     */
    public static Art monogram(String label, int tileW, int tileH) {
        int side = TileArt.iconSide(tileW, tileH);
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = TileArt.iconRadius(side);
        p.setShader(new LinearGradient(0, 0, side, side, 0xFF3B434D, 0xFF1B2128, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, side, side), r, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, side * 0.015f));
        p.setColor(0x40FFFFFF);
        canvas.drawRoundRect(new RectF(p.getStrokeWidth(), p.getStrokeWidth(), side - p.getStrokeWidth(), side - p.getStrokeWidth()), r, r, p);
        String letters = TileArt.monogram(label);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE9EEF3);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(side * (letters.length() > 1 ? 0.40f : 0.50f));
        Paint.FontMetrics fm = p.getFontMetrics();
        canvas.drawText(letters, side / 2f, side / 2f - (fm.ascent + fm.descent) / 2f, p);
        return new Art(TileArt.Kind.MONOGRAM, out, "monogram", 0, 0);
    }

    private static void draw(Drawable source, Canvas canvas, int x, int y, int w, int h) {
        if (source instanceof BitmapDrawable && ((BitmapDrawable) source).getBitmap() != null) {
            Bitmap bmp = ((BitmapDrawable) source).getBitmap();
            // Halve a much larger picture in steps first: one bilinear step from more than twice the size aliases.
            while (bmp.getWidth() >= 2 * w && bmp.getHeight() >= 2 * h && bmp.getWidth() > 1 && bmp.getHeight() > 1) {
                bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, bmp.getWidth() / 2), Math.max(1, bmp.getHeight() / 2), true);
            }
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            canvas.drawBitmap(bmp, null, new Rect(x, y, x + w, y + h), p);
            return;
        }
        Drawable.ConstantState state = source.getConstantState();
        Drawable d = state == null ? source : state.newDrawable().mutate();       // never change the bounds of a shared drawable
        d.setBounds(x, y, x + w, y + h);
        d.draw(canvas);
    }

    /** Renders the drawable small and says whether enough of it is see-through that it has no backdrop of its own. */
    private static boolean probeTransparent(Drawable d) {
        int w = 48, h = 27;
        Bitmap probe = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        draw(d, new Canvas(probe), 0, 0, w, h);
        int[] px = new int[w * h];
        probe.getPixels(px, 0, w, 0, 0, w, h);
        probe.recycle();
        return TileArt.mostlyTransparent(px, px.length);
    }

    /** A copy of the bitmap with rounded corners (the glass shows through them), cut with an antialiased shader so the edge is clean. */
    private static Bitmap rounded(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), radius, radius, p);
        src.recycle();
        return out;
    }
}
