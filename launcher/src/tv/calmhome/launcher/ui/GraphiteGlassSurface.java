package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.core.GlassMaterial;

/**
 * A shape and surface adapter for the supplied {@link GraphiteGlassRenderer}: any rounded card or capsule becomes a piece
 * of the approved graphite glass without a second renderer. The adapter only describes the shape (a rounded rectangle or
 * a capsule with a 1 px transparent border, as the renderer requires) and the material scale for the surface's height
 * ({@link GlassMaterial}); the optics (refraction, frost, smoke, rim light, the idle and focus frames) are the renderer's.
 *
 * The surface is a plain, non-interactive view: it never takes focus, never handles a click and is hidden from
 * accessibility, so it can sit under a control's content without changing how the control behaves. Content (logos, icons,
 * text) is drawn by sibling views above it, so it stays sharp; only the registered wallpaper is refracted.
 *
 * {@link #setLevel} is the focus state, 0 (idle) to 1 (the focus material): the renderer crossfades between its two
 * frames, so focus changes without a re-render.
 *
 * (Not to be confused with {@link GlassSurface}, the graphite drawable that panels use.)
 */
public class GraphiteGlassSurface extends GraphiteGlassRenderer.CachedView {

    public enum Shape { CARD, CAPSULE }

    private final Shape shape;
    /** While the real glass is still being rendered (or after it was invalidated): a translucent graphite pane, not the kit's opaque grey. */
    private final Paint pending = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    private float unit = 1f;
    private GraphiteGlassRenderer.Backdrop boundBackdrop;
    private View boundAnchor;
    private Runnable onUnitChanged;

    public GraphiteGlassSurface(Context c, Shape shape, float designHeightPx) {
        super(c);
        this.shape = shape;
        setFocusable(false);
        setClickable(false);
        setFocusableInTouchMode(false);
        setLongClickable(false);
        setDefaultFocusHighlightEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        pending.setColor(0x6614181E);
        applyMaterial(designHeightPx);
    }

    /**
     * The glass takes the size it is given and asks for none of its own: laid over the content of a control that wraps its content
     * (a list row), the default measure would claim the whole space the parent offers.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(0, widthSpec), resolveSize(0, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isPresent()) return;
        if (!hasMaterial() && showMaterial()) {
            pending.setAlpha(Math.round(0x66 * presence));
            canvas.drawPath(silhouette, pending);
            return;
        }
        super.onDraw(canvas);
    }

    public final Shape shape() {
        return shape;
    }

    /** The material unit in use (display pixels per reference unit): the surface's height / 152. */
    public final float unit() {
        return unit;
    }

    /** The bevel in display pixels. */
    public final float bevel() {
        return bevelPx;
    }

    /** Called (on the UI thread) when layout gives the surface a height whose material unit differs from the design one. */
    final void setOnUnitChanged(Runnable r) {
        onUnitChanged = r;
    }

    private void applyMaterial(float heightPx) {
        float u = GlassMaterial.unit(heightPx);
        unit = u;
        materialUnitPx = u;
        bevelPx = GlassMaterial.bevel(heightPx);
    }

    @Override
    protected void buildSilhouette(Path path, int width, int height) {
        float before = unit;
        applyMaterial(pinnedHeight > 0f ? pinnedHeight : height);     // the real height decides the optics, unless a height was pinned
        float inset = 1f;                                             // the renderer wants a transparent border
        float r = GlassMaterial.radius(height, shape == Shape.CAPSULE);
        path.addRoundRect(new RectF(inset, inset, width - inset, height - inset), r, r, Path.Direction.CW);
        if (Math.abs(before - unit) > 0.01f * Math.max(before, unit) && onUnitChanged != null) post(onUnitChanged);
    }

    private float presence = 1f;
    private float pinnedHeight;

    /** How much of the glass shows, 0..1: a list row's glass is there only while it has focus (nothing is rendered at 0). */
    public final void setPresence(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == presence) return;
        presence = v;
        invalidate();
    }

    public final float presenceLevel() {
        return presence;
    }

    /** True while any of the glass shows (a hidden row's glass is not counted among the surfaces on screen). */
    public final boolean isPresent() {
        return presence > 0.001f;
    }

    @Override
    protected float presence() {
        return presence;
    }

    /**
     * Gives the surface the optics of a surface {@code heightPx} tall whatever its own height: a list row 60 px tall uses the quick
     * action's material (72 px), so every row, tab and pill on Home and on the screens shares one frosted copy of the wallpaper
     * instead of preparing one for each height.
     */
    public final void pinMaterialHeight(float heightPx) {
        pinnedHeight = Math.max(0f, heightPx);
        if (pinnedHeight > 0f) applyMaterial(pinnedHeight);
    }

    /** Focus level 0..1 (a crossfade between the idle and the focus frame). */
    public final void setLevel(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (v == level) return;
        level = v;
        invalidate();
    }

    public final float level() {
        return level;
    }

    @Override
    protected float emphasis() {
        return level;
    }

    /** Binds a backdrop; does nothing (and so re-renders nothing) if this surface already shows that backdrop. */
    final void bind(GraphiteGlassRenderer.Backdrop backdrop, View anchor) {
        if (backdrop == boundBackdrop && anchor == boundAnchor) return;
        boundBackdrop = backdrop;
        boundAnchor = anchor;
        bindBackdrop(backdrop, anchor);
    }

    final boolean isBound() {
        return boundBackdrop != null;
    }

    /** The backdrop this surface currently refracts (tests compare it with the kit's). */
    final GraphiteGlassRenderer.Backdrop boundBackdrop() {
        return boundBackdrop;
    }
}
