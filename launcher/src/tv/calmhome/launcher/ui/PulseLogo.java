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
 * the original ECG-style pulse, pearl wordmark and silver suffix.
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
    /** The outer halo of the luminous treatment: a wide, very faint stroke under the nearer glow. */
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF markBox = new RectF();
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private String name = "ZeroPulse", suffix = "TV";
    private float heightPx;
    private boolean glowOn = true;
    private boolean luminous;

    /** Measured on the approved concept (see {@link #luminous()}). */
    static final int LUMINOUS_CORE = 0xFFDBFDFF, LUMINOUS_HALO = 0xFF5EC8FF, LUMINOUS_WORD = 0xFFCCF3FF, LUMINOUS_TV = 0xFF6FB2E4;

    public PulseLogo(Context c, float heightPx) {
        super(c);
        this.heightPx = heightPx;
        word.setColor(Theme.SOFT_WHITE);
        word.setTypeface(Typeface.create(Theme.FONT_MEDIUM, Typeface.NORMAL));
        suffixPaint.setColor(Theme.SOFT_WHITE);
        suffixPaint.setTypeface(Typeface.create(Theme.FONT_MEDIUM, Typeface.NORMAL));
        for (Paint p : new Paint[]{line, glow, halo}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        line.setColor(Theme.PULSE_CYAN);
        glow.setColor(Theme.withAlpha(Theme.PULSE_CYAN, 0.16f));
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

    /**
     * The luminous treatment of the approved TV concept, for Liquid Glass's header: the same pulse (the branding master's own
     * geometry, nothing redrawn, no badge) with a white-cyan core and a soft cyan halo, "ZeroPulse" in ice white and "TV" in a
     * clear blue. Measured on the concept: core (219, 253, 255), wordmark (204, 243, 255), "TV" (111, 178, 228).
     */
    public PulseLogo luminous() {
        luminous = true;
        line.setColor(LUMINOUS_CORE);
        glow.setColor(LUMINOUS_HALO);
        halo.setColor(LUMINOUS_HALO);
        word.setColor(LUMINOUS_WORD);
        suffixPaint.setColor(LUMINOUS_TV);
        applySize();
        return this;
    }

    public boolean isLuminous() {
        return luminous;
    }

    private void applySize() {
        float text = heightPx * 0.62f;
        word.setTextSize(text);
        suffixPaint.setTextSize(text);
        word.setLetterSpacing(0.01f);
        suffixPaint.setLetterSpacing(0.04f);
        line.setStrokeWidth(Math.max(1.5f, heightPx * 0.075f));
        glow.setStrokeWidth(Math.max(3f, luminous ? heightPx * 0.14f : heightPx * 0.16f));
        halo.setStrokeWidth(Math.max(5f, heightPx * 0.30f));
        glow.setAlpha(luminous ? 0x52 : glow.getAlpha());
        halo.setAlpha(0x1C);
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
        if (glowOn) {
            if (luminous) c.drawPath(path, halo);
            c.drawPath(path, glow);
        }
        c.drawPath(path, line);
        if (name.isEmpty()) return;

        word.getFontMetrics(metrics);
        float baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f;
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
