package tv.calmhome.launcher.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;

/**
 * The Assistant's holographic reactor: concentric rings drawn with plain Canvas primitives.
 * Animates at ~30 fps only while running and visible; with animation off it draws one static frame.
 * The state it shows is set by the Assistant from real activity (idle / working / error).
 */
public class ReactorView extends View {

    public enum State { IDLE, WORKING, ERROR }

    private static final long FRAME_MS = 33;

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ticks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcs = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lines = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path pulsePath = new Path();
    private final float density;

    private boolean animate = true, running;
    private State state = State.IDLE;
    private long workingUntil;
    private float shaderRadius = -1;

    public ReactorView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        thin.setStyle(Paint.Style.STROKE);
        ticks.setStyle(Paint.Style.STROKE);
        ticks.setStrokeCap(Paint.Cap.ROUND);
        arcs.setStyle(Paint.Style.STROKE);
        arcs.setStrokeCap(Paint.Cap.ROUND);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setPathEffect(new DashPathEffect(new float[]{6 * density, 7 * density}, 0));
        coreRing.setStyle(Paint.Style.STROKE);
        pulse.setStyle(Paint.Style.STROKE);
        pulse.setStrokeCap(Paint.Cap.ROUND);
        pulse.setStrokeJoin(Paint.Join.ROUND);
        lines.setStyle(Paint.Style.STROKE);
        text.setColor(Theme.SOFT_WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Theme.FONT_CONDENSED, Typeface.BOLD));
        text.setLetterSpacing(0.22f);
    }

    /** False = draw a still frame (reduced motion, or the user turned the animation off). */
    public void setAnimated(boolean on) {
        animate = on;
        invalidate();
    }

    public void setRunning(boolean on) {
        running = on;
        invalidate();
    }

    public void setState(State s) {
        state = s;
        invalidate();
    }

    /** Shows the WORKING state for a moment while an action is being carried out. */
    public void pulseWorking(long ms) {
        state = State.WORKING;
        workingUntil = SystemClock.uptimeMillis() + ms;
        invalidate();
        postDelayed(() -> {
            if (SystemClock.uptimeMillis() >= workingUntil && state == State.WORKING) setState(State.IDLE);
        }, ms + 20);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.46f;
        boolean moving = animate && running && isShown();
        float t = moving ? SystemClock.uptimeMillis() / 1000f : 0f;
        float speed = state == State.WORKING ? 2.6f : 1f;
        int accent = state == State.ERROR ? Theme.ERROR : Theme.ELECTRIC_BLUE;
        float dp = density;

        // technical guide lines left/right of the reactor
        lines.setStrokeWidth(Math.max(1, dp));
        lines.setColor(Theme.withAlpha(Theme.HOLO_BLUE, 0.22f));
        c.drawLine(0, cy, cx - r * 1.08f, cy, lines);
        c.drawLine(cx + r * 1.08f, cy, w, cy, lines);
        c.drawLine(cx - r * 1.3f, cy - r * 0.62f, cx - r * 1.08f, cy, lines);
        c.drawLine(cx + r * 1.3f, cy + r * 0.62f, cx + r * 1.08f, cy, lines);

        // soft glow (shader rebuilt only when the size changes)
        if (shaderRadius != r) {
            glow.setShader(new RadialGradient(cx, cy, r * 1.25f,
                    new int[]{Theme.withAlpha(Theme.HOLO_BLUE, 0.30f), Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.10f), 0},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            core.setShader(new RadialGradient(cx, cy, r * 0.42f,
                    new int[]{Theme.withAlpha(Theme.MIDNIGHT_NAVY, 0.95f), Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.35f)},
                    new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
            shaderRadius = r;
        }
        c.drawCircle(cx, cy, r * 1.25f, glow);

        // outer hairline + rotating tick ring
        thin.setStrokeWidth(Math.max(1, dp));
        thin.setColor(Theme.withAlpha(Theme.HOLO_BLUE, 0.45f));
        c.drawCircle(cx, cy, r, thin);
        ticks.setStrokeWidth(Math.max(1, 1.4f * dp));
        ticks.setColor(Theme.withAlpha(Theme.HOLO_BLUE, 0.55f));
        c.save();
        c.rotate(t * 6f * speed, cx, cy);
        for (int i = 0; i < 60; i++) {
            float len = i % 5 == 0 ? r * 0.06f : r * 0.03f;
            c.drawLine(cx, cy - r * 0.97f, cx, cy - r * 0.97f + len, ticks);
            c.rotate(6f, cx, cy);
        }
        c.restore();

        // counter-rotating arc segments
        arcs.setStrokeWidth(3.2f * dp);
        arcs.setColor(accent);
        float ra = r * 0.86f;
        box.set(cx - ra, cy - ra, cx + ra, cy + ra);
        float a = -t * 18f * speed;
        for (int i = 0; i < 4; i++) c.drawArc(box, a + i * 90f, 58f, false, arcs);

        // dashed cyan ring
        dashed.setStrokeWidth(1.6f * dp);
        dashed.setColor(Theme.withAlpha(Theme.PULSE_CYAN, 0.7f));
        c.save();
        c.rotate(t * 12f * speed, cx, cy);   // rotate instead of re-creating the dash effect each frame
        c.drawCircle(cx, cy, r * 0.72f, dashed);
        c.restore();

        // inner ring with a sweeping highlight
        coreRing.setStrokeWidth(2f * dp);
        coreRing.setColor(Theme.withAlpha(Theme.HOLO_BLUE, 0.8f));
        c.drawCircle(cx, cy, r * 0.56f, coreRing);
        coreRing.setStrokeWidth(4.5f * dp);
        coreRing.setColor(state == State.ERROR ? Theme.ERROR : Theme.PULSE_CYAN);
        float rs = r * 0.56f;
        box.set(cx - rs, cy - rs, cx + rs, cy + rs);
        c.drawArc(box, t * 40f * speed, 70f, false, coreRing);

        // core + label + pulse
        c.drawCircle(cx, cy, r * 0.42f, core);
        float breathe = moving ? 0.5f + 0.5f * (float) Math.sin(t * 2.2f * speed) : 1f;
        thin.setColor(Theme.withAlpha(Theme.PULSE_CYAN, 0.35f + 0.4f * breathe));
        c.drawCircle(cx, cy, r * 0.42f, thin);
        text.setTextSize(r * 0.17f);
        c.drawText("JARVIS", cx, cy + r * 0.02f, text);
        pulse.setStrokeWidth(Math.max(1.5f, 1.8f * dp));
        pulse.setColor(state == State.ERROR ? Theme.ERROR : Theme.PULSE_CYAN);
        box.set(cx - r * 0.2f, cy + r * 0.1f, cx + r * 0.2f, cy + r * 0.24f);
        PulseLogo.buildPulse(pulsePath, box);
        c.drawPath(pulsePath, pulse);

        if (moving) postInvalidateDelayed(FRAME_MS);
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) invalidate();     // the frame loop stops by itself while hidden (isShown() is false)
    }
}
