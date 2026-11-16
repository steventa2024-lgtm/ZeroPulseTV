package tv.calmhome.launcher.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The large pane behind a screen's content (a Settings column, a list, a card): translucent graphite with the glass's own light, and
 * one hairline rim. It is the quiet partner of the approved glass that the controls on the pane are made of ({@link
 * ZeroPulseGlassRow}), in the same graphite and with the same rim weight as Home's tiles, so a screen no longer has three
 * thicknesses of white edge.
 *
 * <p>It replaces the 3005 panel painter (the old clear-glass surface: a frosted window onto the wallpaper, ten gradient fills under
 * a clip, a 1.5 dp specular rim that measured 3 px at 90 % white on the TCL against Home's 1 px at about 33 %, and a 30-ring bloom
 * under a focused pane), which cost a frame about 27 ms of GPU on the TCL. A pane is two draw calls, with no clip, no layer and no
 * allocation: the gradients are built when the bounds change.
 */
public final class PaneGlass extends Drawable {
    private final float radius, rimWidth, opacity;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), edge = new RectF();
    private float edgeRadius;

    /**
     * @param radiusPx corner radius in pixels
     * @param rimPx    the rim's width in pixels (1 matches Home's hairline at 1080p)
     * @param opacity  0..1, how much of the graphite shows: the panes' own 0.5 to 0.9 settings keep their meaning
     */
    public PaneGlass(float radiusPx, float rimPx, float opacity) {
        this.radius = Math.max(0f, radiusPx);
        this.rimWidth = Math.max(1f, rimPx);
        this.opacity = Math.max(0f, Math.min(1f, opacity));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
    }

    /** The graphite's alpha at the top of a pane (a little lighter) for an opacity setting: 0.5 gives about 0.57, 0.85 about 0.70. */
    static int topAlpha(float opacity) {
        return Math.round(255f * (0.34f + 0.40f * opacity));
    }

    @Override
    protected void onBoundsChange(Rect b) {
        box.set(b);
        float half = rimWidth / 2f;
        edge.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        edgeRadius = Math.max(0f, Math.min(radius, Math.min(b.width(), b.height()) / 2f) - half);
        if (b.width() <= 0 || b.height() <= 0) return;
        int top = topAlpha(opacity), bottom = Math.min(255, top + 22);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                new int[]{(top << 24) | 0x1E252E, ((top + bottom) / 2 << 24) | 0x161B22, (bottom << 24) | 0x0E1218},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                new int[]{0x4DFFFFFF, 0x1AFFFFFF, 0x12FFFFFF, 0x26FFFFFF}, new float[]{0f, 0.18f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        if (box.isEmpty()) return;
        float r = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
        canvas.drawRoundRect(box, r, r, fill);
        canvas.drawRoundRect(edge, edgeRadius, edgeRadius, rim);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(1f);
    }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter colorFilter) { }

    @SuppressWarnings("deprecation")
    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
