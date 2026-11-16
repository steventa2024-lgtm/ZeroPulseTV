package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import tv.calmhome.launcher.core.GlassMaterial;

/**
 * The finish on a piece of approved glass: the optical details the renderer's material leaves to the surface it is used on,
 * painted above the glass and never changing it. All of it is faint and directional, lit from the upper left, and none of it is
 * an outline:
 * <ul>
 * <li>a soft internal illumination, brighter near the lit corner, that lifts as the control takes focus;</li>
 * <li>a thin specular lip along the top edge that is brightest near the lit corner and fades away, with a much weaker
 *     counter-lip along the bottom edge on the opposite side, as light passing through a pane would leave;</li>
 * <li>a dark inner hairline all round, so the edge stays defined over a bright wallpaper without a white border;</li>
 * <li>a little depth: the lower part of the pane is a touch darker, as if it had thickness.</li>
 * </ul>
 *
 * <p><b>It stays on the glass.</b> A tile's artwork is cut out of the finish ({@link #setCenteredHole}): the illumination and the
 * depth shading are decoration for the frame, and laid over a banner they lifted its blacks (about 11 of 255 on the TCL) and
 * tinted its colours. The artwork's pixels are now exactly the app's own.
 *
 * <p><b>It costs one bitmap per frame.</b> The finish is painted once, off the draw path, into two pictures (idle and focused)
 * that every control of the same size, shape and strength shares from a small bounded cache; drawing is one bitmap, or two while
 * the focus level moves between them. (3005 painted the vector finish, five gradient fills and three clipped strokes, for each of
 * thirteen controls on every frame of every focus animation.) It is inert (no focus, no clicks, hidden from accessibility), so it
 * can sit above the content without changing how the control behaves.
 */
final class GlassFinishView extends View {

    /** The two pictures of one finish: idle, and focused. */
    static final class Pictures {
        final Bitmap idle, focus;

        Pictures(Bitmap idle, Bitmap focus) {
            this.idle = idle;
            this.focus = focus;
        }
    }

    private static final int CACHE_LIMIT = 8;
    private static final Map<Key, Pictures> CACHE = new LinkedHashMap<Key, Pictures>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Pictures> eldest) {
            return size() > CACHE_LIMIT;               // never recycled: a view may still draw an evicted picture; the GC takes it later
        }
    };

    private static final class Key {
        final int w, h, holeW, holeH;
        final boolean capsule;
        final int strengthBits, radiusBits;

        Key(int w, int h, boolean capsule, float strength, int holeW, int holeH, float holeRadius) {
            this.w = w;
            this.h = h;
            this.capsule = capsule;
            this.strengthBits = Float.floatToIntBits(strength);
            this.holeW = holeW;
            this.holeH = holeH;
            this.radiusBits = Float.floatToIntBits(holeRadius);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return w == k.w && h == k.h && capsule == k.capsule && strengthBits == k.strengthBits && holeW == k.holeW && holeH == k.holeH
                    && radiusBits == k.radiusBits;
        }

        @Override public int hashCode() {
            return Objects.hash(w, h, capsule, strengthBits, holeW, holeH, radiusBits);
        }
    }

    /** Pictures built since the process began (a test checks that a row of identical tiles builds one pair, not one pair each). */
    private static int built;

    static synchronized int picturesBuilt() {
        return built;
    }

    static synchronized void clearCacheForTests() {
        CACHE.clear();
        built = 0;
    }

    private final boolean capsule;
    private final float strength;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int holeW, holeH;
    private float holeRadius;
    private Pictures pictures;
    private float level;
    private float presence = 1f;

    /** {@code strength} scales every alpha: 1 for a tile, less for the slim quick actions, which stay restrained. */
    GlassFinishView(Context c, boolean capsule, float strength) {
        super(c);
        this.capsule = capsule;
        this.strength = Math.max(0f, strength);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Leaves a rectangle {@code w x h} pixels, centred on the control, with corners of {@code radius}, out of the finish: the
     * artwork's own place. Nothing (0 x 0) puts the finish over the whole control.
     */
    void setCenteredHole(int w, int h, float radius) {
        if (w == holeW && h == holeH && radius == holeRadius) return;
        holeW = Math.max(0, w);
        holeH = Math.max(0, h);
        holeRadius = Math.max(0f, radius);
        build();
    }

    void setLevel(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == level) return;
        level = v;
        invalidate();
    }

    /** How much of the finish shows, 0..1 (a list row's finish, like its glass, belongs to focus). */
    void setPresence(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == presence) return;
        presence = v;
        invalidate();
    }

    /** The pictures this view draws (for tests): null until the view has a size. */
    Pictures pictures() {
        return pictures;
    }

    /** Takes the size it is given and asks for none of its own (see GraphiteGlassSurface#onMeasure). */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(0, widthSpec), resolveSize(0, heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        build();
    }

    private void build() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            pictures = null;
            return;
        }
        int hw = holeW > 0 && holeH > 0 && holeW < w && holeH < h ? holeW : 0, hh = hw == 0 ? 0 : holeH;
        Key key = new Key(w, h, capsule, strength, hw, hh, holeRadius);
        Pictures p;
        synchronized (GlassFinishView.class) {
            p = CACHE.get(key);
            if (p == null) {
                p = new Pictures(render(w, h, capsule, strength, 0f, hw, hh, holeRadius), render(w, h, capsule, strength, 1f, hw, hh, holeRadius));
                CACHE.put(key, p);
                built++;
            }
        }
        pictures = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Pictures p = pictures;
        if (p == null || presence <= 0.001f) return;
        float whole = 255f * presence;
        if (level <= 0.001f) {
            paint.setAlpha(Math.round(whole));
            canvas.drawBitmap(p.idle, 0f, 0f, paint);
        } else if (level >= 0.999f) {
            paint.setAlpha(Math.round(whole));
            canvas.drawBitmap(p.focus, 0f, 0f, paint);
        } else {                                               // between the two: each fades with the level (the endpoints are exact)
            paint.setAlpha(Math.round(whole * (1f - level)));
            canvas.drawBitmap(p.idle, 0f, 0f, paint);
            paint.setAlpha(Math.round(whole * level));
            canvas.drawBitmap(p.focus, 0f, 0f, paint);
        }
        paint.setAlpha(255);
    }

    // ------------------------------------------------------------------ the picture (painted off the draw path)

    /** One finished picture of the finish at a focus level, {@code w x h}, with an optional hole for the artwork. */
    static Bitmap render(int w, int h, boolean capsule, float strength, float level, int holeW, int holeH, float holeRadius) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        paintFinish(new Canvas(out), w, h, capsule, strength, level, holeW, holeH, holeRadius);
        return out;
    }

    /** The vector finish itself: the one definition of how it looks, at a level (0 idle, 1 focus). */
    static void paintFinish(Canvas canvas, int w, int h, boolean capsule, float s, float level, int holeW, int holeH, float holeRadius) {
        float r = GlassMaterial.radius(h, capsule);
        Path shape = new Path();
        shape.addRoundRect(new RectF(1f, 1f, w - 1f, h - 1f), r, r, Path.Direction.CW);
        float lip = Math.max(1f, h * 0.012f);
        float ir = Math.max(0f, r - lip);
        Path inset = new Path();
        inset.addRoundRect(new RectF(lip, lip, w - lip, h - lip), ir, ir, Path.Direction.CW);
        Shader illumination = new RadialGradient(w * 0.30f, h * 0.10f, Math.max(1f, w * 0.80f), 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        Shader lipTop = new LinearGradient(0, 0, w, 0, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x66FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.20f, 0.55f, 0.95f}, Shader.TileMode.CLAMP);
        Shader lipBottom = new LinearGradient(0, 0, w, 0, new int[]{0x00FFFFFF, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP);
        Shader depth = new LinearGradient(0, h * 0.50f, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);

        canvas.save();
        canvas.clipPath(shape);
        if (holeW > 0 && holeH > 0) {                                           // the artwork's own place: the finish is the frame's, not the picture's
            Path hole = new Path();
            float left = (w - holeW) / 2f, top = (h - holeH) / 2f;
            hole.addRoundRect(new RectF(left, top, left + holeW, top + holeH), holeRadius, holeRadius, Path.Direction.CW);
            canvas.clipOutPath(hole);
        }

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(illumination);                                          // internal illumination
        fill.setAlpha(Math.round(255f * s * (0.060f + 0.090f * level)));
        canvas.drawRect(0, 0, w, h, fill);
        fill.setShader(depth);                                                 // depth: the lower pane is a touch darker
        fill.setAlpha(Math.round(255f * s * 0.16f));
        canvas.drawRect(0, h * 0.50f, w, h, fill);
        fill.setShader(null);

        stroke.setShader(null);                                                // a dark inner hairline: edge definition, not a white border
        stroke.setColor(0xFF000000);
        stroke.setAlpha(Math.round(255f * s * 0.20f));
        stroke.setStrokeWidth(Math.max(1f, h * 0.008f));
        canvas.drawPath(shape, stroke);

        stroke.setStrokeWidth(Math.max(1f, h * 0.011f));
        canvas.save();                                                         // the top specular lip, bright at the lit corner
        canvas.clipRect(0, 0, w, h * 0.42f);
        stroke.setShader(lipTop);
        stroke.setAlpha(Math.round(255f * s * (0.55f + 0.30f * level)));
        canvas.drawPath(inset, stroke);
        canvas.restore();
        canvas.save();                                                         // the faint counter-lip along the bottom, far corner
        canvas.clipRect(0, h * 0.72f, w, h);
        stroke.setShader(lipBottom);
        stroke.setAlpha(Math.round(255f * s * (0.20f + 0.16f * level)));
        canvas.drawPath(inset, stroke);
        canvas.restore();
        canvas.restore();
    }
}
