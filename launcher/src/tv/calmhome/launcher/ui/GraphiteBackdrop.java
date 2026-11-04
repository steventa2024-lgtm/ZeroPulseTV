package tv.calmhome.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Original still graphite curves. All screens share the same small opaque raster. */
public final class GraphiteBackdrop extends Drawable {
    private static Bitmap artwork;
    private final Paint scale = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    private static Bitmap render() {
        final int width = 960, height = 540;
        Bitmap pic = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(pic);
        Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        brush.setShader(new LinearGradient(70, 0, 650, 540, new int[]{0xff14171b, 0xff404349, 0xff202328},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, brush);
        Path fold = new Path();
        fold.moveTo(-18, 485);
        fold.cubicTo(112, 337, 249, 342, 376, 212);
        fold.cubicTo(555, 24, 789, 43, 985, 117);
        Path shape = new Path(fold);
        shape.lineTo(985, 550); shape.lineTo(-18, 550); shape.close();
        brush.setShader(new LinearGradient(310, 132, 680, 500,
                new int[]{0xff7a7e85, 0xff32353a, 0xff15181d, 0xff3f4349},
                new float[]{0f, .22f, .62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, brush);
        edge(canvas, fold, brush);
        Path base = new Path();
        base.moveTo(280, 570); base.cubicTo(544, 424, 806, 514, 986, 305);
        shape.set(base); shape.lineTo(986, 570); shape.close();
        brush.setStyle(Paint.Style.FILL);
        brush.setShader(new LinearGradient(620, 355, 935, 555,
                new int[]{0xff565a60, 0xff21252a, 0xff121519}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, brush); edge(canvas, base, brush);
        return pic;
    }

    private static void edge(Canvas c, Path path, Paint p) {
        p.setShader(null); p.setStyle(Paint.Style.STROKE);
        p.setColor(0x173f444c); p.setStrokeWidth(7); c.drawPath(path, p);
        p.setColor(0x8bdddfe3); p.setStrokeWidth(1); c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xffffffff);
    }

    @Override public void draw(Canvas canvas) {
        if (artwork == null) artwork = render();
        Rect bounds = getBounds();
        canvas.drawBitmap(artwork, null, bounds, scale);
    }
    @Override public void setAlpha(int alpha) { scale.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { scale.setColorFilter(filter); invalidateSelf(); }
    @SuppressWarnings("deprecation") @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
