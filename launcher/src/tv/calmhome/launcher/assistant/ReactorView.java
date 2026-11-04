package tv.calmhome.launcher.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;

/** Compact monochrome activity mark. Only a real pending action schedules another frame. */
public class ReactorView extends View {
    public enum State { IDLE, WORKING, ERROR }
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path waveform = new Path();
    private final RectF box = new RectF();
    private State state = State.IDLE;
    private boolean animate, running;
    private final Runnable endPulse = () -> setState(State.IDLE);

    public ReactorView(Context c) {
        super(c);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setAnimated(boolean enabled) { animate = enabled; invalidate(); }
    public void setRunning(boolean enabled) { running = enabled; invalidate(); }
    public void setState(State next) {
        removeCallbacks(endPulse);
        state = next;
        invalidate();
    }
    public void pulseWorking(long ms) { setState(State.WORKING); postDelayed(endPulse, ms); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        box.set(w * .12f, h * .22f, w * .88f, h * .78f);
        PulseLogo.buildPulse(waveform, box);
        ink.setStrokeWidth(Math.max(1f, w * .06f));
    }
    @Override protected void onDraw(Canvas canvas) {
        boolean moving = animate && running && state == State.WORKING && isShown();
        ink.setColor(state == State.ERROR ? Theme.WARNING : Theme.SOFT_WHITE);
        if (moving) ink.setAlpha(170 + (int)(72 * Math.cos(SystemClock.uptimeMillis() * .008)));
        canvas.drawPath(waveform, ink);
        if (moving) postInvalidateDelayed(65);
    }
}
