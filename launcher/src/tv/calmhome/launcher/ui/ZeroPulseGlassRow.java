package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

/**
 * A control on the Wallpapers and Settings screens, built from the same approved graphite glass, finish and focus language as
 * Home's tiles and quick actions: a capsule or card of glass under the control's own content, one crossfade to the focus material,
 * no outline, no second background. It replaces the 3005 panel controls (the old clear-glass painter with a 3 px specular rim,
 * plus a 2 dp outline on focus, plus a separate wrapper card).
 *
 * <p>Two kinds, by {@code idleGlass}: a <b>pill or card</b> (a collection tab, a framing control) shows its glass all the time, as
 * Home's quick actions do; a <b>list row</b> shows none until it has focus, so a long Settings list is plain text with one lit pane
 * moving down it, and only the rows the viewer reaches are ever rendered.
 *
 * <p>The optics are pinned to one material height (the quick action's), so every row, tab and pill on every screen shares the
 * frosted copy of the wallpaper that Home's actions already use: no new copy is prepared for a screen's controls.
 */
public final class ZeroPulseGlassRow extends ZeroPulseGlassControl {

    /** What a control says about itself beyond focus: nothing, the page dot of the rail (a chosen collection), or a quiet pane (the open category). */
    public enum Mark { NONE, DOT, PLATE }

    /** The material height the optics are pinned to, in pixels: the quick action's, so the frosted copy is shared with Home. */
    public static final int MATERIAL_HEIGHT_PX = 72;
    private static final float QUIET_PLATE = 0.35f;

    private final boolean idleGlass;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dotRadius, dotInset;
    private Mark mark = Mark.NONE;

    public ZeroPulseGlassRow(Context c, GlassKit kit, GraphiteGlassSurface.Shape shape, int focusMs, boolean idleGlass) {
        this(c, kit, shape, focusMs, idleGlass, 1f);
    }

    /**
     * As above; {@code focusedContentScale} above 1 lets the content (a preview, an icon, never the glass) swell slightly on focus, the
     * way a Home tile's artwork does. The glass itself is never scaled (the renderer drops a scaled surface's glass).
     */
    public ZeroPulseGlassRow(Context c, GlassKit kit, GraphiteGlassSurface.Shape shape, int focusMs, boolean idleGlass, float focusedContentScale) {
        super(c, kit, shape, MATERIAL_HEIGHT_PX, focusMs, focusedContentScale, 0.55f);
        this.idleGlass = idleGlass;
        float density = c.getResources().getDisplayMetrics().density;
        dotRadius = 1.6f * density;
        dotInset = 7f * density;
        dot.setColor(0xFFF2F6FA);
        glass().pinMaterialHeight(MATERIAL_HEIGHT_PX);
        contentFrame().setPadding(0, 0, 0, 0);
        setWillNotDraw(false);
        refreshPresence();
    }

    /** The frame a control puts its own views in (above the glass). */
    public FrameLayout content() {
        return contentFrame();
    }

    public boolean idleGlass() {
        return idleGlass;
    }

    public void setMark(Mark value) {
        if (mark == value) return;
        mark = value;
        refreshPresence();
        invalidate();
    }

    public Mark mark() {
        return mark;
    }

    private void refreshPresence() {
        float p = idleGlass ? 1f : focusLevel();
        if (mark == Mark.PLATE) p = Math.max(p, QUIET_PLATE);
        setPresence(p);
    }

    @Override
    protected void onLevel(float level) {
        refreshPresence();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mark == Mark.DOT) canvas.drawCircle(getWidth() / 2f, getHeight() - dotInset, dotRadius, dot);
    }
}
