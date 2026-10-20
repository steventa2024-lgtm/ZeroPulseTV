package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * ZeroPulse "Wordmark + Pulse" logo, drawn as vectors so it is crisp at any size:
 * a blue ECG-style pulse line, "ZeroPulse" in Soft White and a suffix ("TV") in Electric Blue.
 * The same geometry is used for the SVG masters in branding/.
 */
public class PulseLogo extends View {

    /** Pulse polyline in a unit box (x 0..1, y 0..1, y down). Shared with branding/*.svg. */
    static final float[] PULSE = {
            0f, .5f, .20f, .5f, .27f, .36f, .34f, .5f, .43f, .5f, .51f, .04f,
            .60f, .96f, .68f, .5f, .77f, .5f, .83f, .38f, .89f, .5f, 1f, .5f};

    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suffixPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF markBox = new RectF();
    private String name = "ZeroPulse", suffix = "TV";
    private float heightPx;
    private boolean glowOn = true;

    public PulseLogo(Context c, float heightPx) {
        super(c);
        this.heightPx = heightPx;
        word.setColor(Theme.SOFT_WHITE);
        word.setTypeface(Typeface.create(Theme.FONT_MEDIUM, Typeface.NORMAL));
        suffixPaint.setColor(Theme.ELECTRIC_BLUE);
        suffixPaint.setTypeface(Typeface.create(Theme.FONT_MEDIUM, Typeface.NORMAL));
        for (Paint p : new Paint[]{line, glow}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        line.setColor(Theme.ELECTRIC_BLUE);
        glow.setColor(Theme.withAlpha(Theme.PULSE_CYAN, 0.28f));
        applySize();
    }

    public PulseLogo suffix(String s, int color) {
        suffix = s == null ? "" : s;
        suffixPaint.setColor(color);
        applySize();
        return this;
    }

    /** Text before the suffix; "" draws the pulse mark alone. */
    public PulseLogo name(String s) {
        name = s == null ? "" : s;
        if (name.isEmpty()) suffix = "";
        applySize();
        return this;
    }

    public PulseLogo glow(boolean on) {
        glowOn = on;
        invalidate();
        return this;
    }

    private void applySize() {
        float text = heightPx * 0.62f;
        word.setTextSize(text);
        suffixPaint.setTextSize(text);
        word.setLetterSpacing(0.01f);
        suffixPaint.setLetterSpacing(0.04f);
        line.setStrokeWidth(Math.max(1.5f, heightPx * 0.075f));
        glow.setStrokeWidth(Math.max(3f, heightPx * 0.24f));
        requestLayout();
        invalidate();
    }

    private float markWidth() {
        return heightPx * 1.35f;
    }

    private float gap() {
        return heightPx * 0.28f;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float w = markWidth() + (name.isEmpty() ? 0 : gap() + word.measureText(name))
                + (suffix.isEmpty() ? 0 : heightPx * 0.22f + suffixPaint.measureText(suffix));
        setMeasuredDimension((int) Math.ceil(w + heightPx * 0.2f), (int) Math.ceil(heightPx));
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = heightPx;
        float pad = h * 0.1f;
        markBox.set(pad, h * 0.18f, pad + markWidth(), h * 0.82f);
        buildPulse(path, markBox);
        if (glowOn) c.drawPath(path, glow);
        c.drawPath(path, line);
        if (name.isEmpty()) return;

        Paint.FontMetrics fm = word.getFontMetrics();
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        float x = markBox.right + gap();
        c.drawText(name, x, baseline, word);
        if (!suffix.isEmpty()) {
            x += word.measureText(name) + h * 0.22f;
            c.drawText(suffix, x, baseline, suffixPaint);
        }
    }

    /** Builds the pulse line inside {@code box}. */
    public static void buildPulse(Path p, RectF box) {
        p.reset();
        for (int i = 0; i < PULSE.length; i += 2) {
            float x = box.left + PULSE[i] * box.width();
            float y = box.top + PULSE[i + 1] * box.height();
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
    }
}
