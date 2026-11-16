package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The Settings on/off switch in the Liquid Glass palette: a small capsule of the panes' graphite with their hairline rim, and a pearl
 * knob that slides across it. On, the track takes the glass's silver light (the same near-white the focus material uses), so on and
 * off read apart without any colour the approved kit does not have. It replaces the flat white-and-grey rectangles of 3005.
 *
 * <p>It is a picture only: it never takes focus or clicks (the row around it does, so a toggle changes only when OK is pressed on its
 * row, never because focus passed over it). {@link #setChecked} with {@code animate} slides the knob (140 ms); without, it jumps there.
 * Nothing is allocated while drawing: the gradients are built when the size changes.
 */
public final class GlassSwitch extends View {
    private static final int SLIDE_MS = 140;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            lit = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG), knobRim = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), edge = new RectF();
    private final float density;
    private boolean checked;
    private float position;              // 0 = off (knob left), 1 = on (knob right)
    private ValueAnimator slide;
    private boolean reducedMotion;

    public GlassSwitch(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);    // the row says "on" or "off"
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, density * 0.5f));
        knobRim.setStyle(Paint.Style.STROKE);
        knobRim.setStrokeWidth(Math.max(1f, density * 0.5f));
        knobRim.setColor(0x59FFFFFF);
        shade.setColor(0x40000000);
    }

    public void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
    }

    public boolean isChecked() {
        return checked;
    }

    /** The knob's place, 0 (off) to 1 (on); between while it slides (for tests). */
    public float position() {
        return position;
    }

    public void setChecked(boolean on, boolean animate) {
        checked = on;
        float to = on ? 1f : 0f;
        if (slide != null) slide.cancel();
        slide = null;
        if (!animate || reducedMotion || !isAttachedToWindow() || position == to) {
            position = to;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(position, to);
        a.setDuration(SLIDE_MS);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(u -> {
            position = (Float) u.getAnimatedValue();
            invalidate();
        });
        slide = a;
        a.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (slide != null) slide.cancel();
        slide = null;
        position = checked ? 1f : 0f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        box.set(0, 0, w, h);
        float half = rim.getStrokeWidth() / 2f;
        edge.set(half, half, w - half, h - half);
        track.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xB81E252E, 0xC8161B22, 0xD80E1218}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x66FFFFFF, 0x1FFFFFFF, 0x33FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        lit.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xE6F2F6FA, 0xC8D3D9E0, 0xB8BCC3CB}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float r = h / 2f - 3f * density;
        knob.setShader(new RadialGradient(r * 0.75f, r * 0.55f, Math.max(1f, r * 1.6f),
                new int[]{0xFFFFFFFF, 0xFFE8ECF0, 0xFFB9C0C8}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (box.isEmpty()) return;
        float h = box.height(), radius = h / 2f;
        canvas.drawRoundRect(box, radius, radius, track);
        if (position > 0.001f) {                                     // the silver light fades in under the knob as it slides on
            lit.setAlpha(Math.round(255 * position));
            canvas.drawRoundRect(box, radius, radius, lit);
        }
        canvas.drawRoundRect(edge, radius - rim.getStrokeWidth() / 2f, radius - rim.getStrokeWidth() / 2f, rim);
        float inset = 3f * density, r = radius - inset;
        float cx = inset + r + (box.width() - 2f * (inset + r)) * position, cy = radius;
        canvas.drawCircle(cx, cy + density * 0.8f, r, shade);         // a soft contact shadow, one draw call
        canvas.save();
        canvas.translate(cx - r, cy - r);                            // the knob's light is set up for a knob at the origin
        canvas.drawCircle(r, r, r, knob);
        canvas.drawCircle(r, r, r - knobRim.getStrokeWidth() / 2f, knobRim);
        canvas.restore();
    }
}
