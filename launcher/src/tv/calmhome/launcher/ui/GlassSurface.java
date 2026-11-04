package tv.calmhome.launcher.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;

/** A native glass fill with a directional rim. Geometry and shader change only on resize. */
final class GlassSurface extends GradientDrawable {
    private final Paint reflection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF inset = new RectF();
    private final float radius;
    GlassSurface(int[] colors, float radius, int stroke) {
        super(Orientation.TOP_BOTTOM, colors);
        this.radius = radius;
        setCornerRadius(radius);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
    }
    @Override protected void onBoundsChange(Rect box) {
        super.onBoundsChange(box);
        float half = rim.getStrokeWidth() / 2f;
        inset.set(box.left + half, box.top + half, box.right - half, box.bottom - half);
        if (!box.isEmpty()) reflection.setShader(new android.graphics.RadialGradient(
                box.left + radius, box.top, Math.max(radius * 2.5f, box.height() * .72f),
                new int[]{0x3af8f9fa, 0x0ae8eaed, 0x00ffffff}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        if (!box.isEmpty()) rim.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                new int[]{0xe8f2f3f5, 0x738d929a, 0x2c777c83, 0xc8d6d8dc},
                new float[]{0f, .20f, .69f, 1f}, Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        if (!inset.isEmpty()) {
            canvas.drawRoundRect(inset, radius, radius, reflection);
            canvas.drawRoundRect(inset, radius, radius, rim);
        }
    }
}
