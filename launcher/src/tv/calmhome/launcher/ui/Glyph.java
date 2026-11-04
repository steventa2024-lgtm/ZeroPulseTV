package tv.calmhome.launcher.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Original ZeroPulse line icons, drawn on a 24-unit grid with a 1.8-unit stroke. */
public class Glyph extends Drawable {

    public enum Kind { GRID, INPUTS, TV, HDMI, ASSISTANT, IMAGE, GEAR, INFO, HOME, PLAY, DOCK, SHORTCUT, DOWNLOAD, SPORTS, CINEMA, TORII,
        APPS, GAMES, LAYERS, LINK, ARROW, FOLDER, APK, PLUS }

    private final Kind kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public Glyph(Kind kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int color) {
        p.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        p.setStrokeWidth(1.8f);
        path.reset();
        switch (kind) {
            case APPS:
                c.drawLine(6, 19, 14, 4, p);
                c.drawLine(10, 4, 18, 19, p);
                c.drawLine(3, 15, 14, 15, p);
                c.drawLine(17, 15, 21, 15, p);
                break;
            case GAMES:
                path.moveTo(7, 7); path.quadTo(3.5f, 7, 2.5f, 16);
                path.quadTo(2, 20, 5.5f, 17.5f); path.lineTo(8.5f, 15);
                path.lineTo(15.5f, 15); path.lineTo(18.5f, 17.5f);
                path.quadTo(22, 20, 21.5f, 16); path.quadTo(20.5f, 7, 17, 7);
                path.close(); c.drawPath(path, p);
                c.drawLine(6, 11, 10, 11, p); c.drawLine(8, 9, 8, 13, p);
                c.drawCircle(16, 10, .55f, p); c.drawCircle(18, 12, .55f, p);
                break;
            case LAYERS:
                path.moveTo(2, 8); path.lineTo(12, 3); path.lineTo(22, 8); path.lineTo(12, 13); path.close();
                path.moveTo(2, 12); path.lineTo(12, 17); path.lineTo(22, 12);
                path.moveTo(2, 16); path.lineTo(12, 21); path.lineTo(22, 16);
                c.drawPath(path, p);
                break;
            case LINK:
                r.set(2, 8, 13, 16); c.save(); c.rotate(-45, 7.5f, 12); c.drawRoundRect(r, 4, 4, p); c.restore();
                r.set(11, 5, 22, 13); c.save(); c.rotate(-45, 16.5f, 9); c.drawRoundRect(r, 4, 4, p); c.restore();
                c.drawLine(9, 14, 15, 8, p);
                break;
            case ARROW:
                path.moveTo(4, 12); path.lineTo(19, 12); path.moveTo(13, 6); path.lineTo(19, 12); path.lineTo(13, 18);
                c.drawPath(path, p); break;
            case FOLDER:
                path.moveTo(3, 7); path.lineTo(9, 7); path.lineTo(11, 9); path.lineTo(21, 9);
                path.lineTo(21, 19); path.lineTo(3, 19); path.close(); c.drawPath(path, p); break;
            case APK:
                path.moveTo(6, 2); path.lineTo(14, 2); path.lineTo(19, 7); path.lineTo(19, 22);
                path.lineTo(6, 22); path.close(); path.moveTo(14, 2); path.lineTo(14, 7); path.lineTo(19, 7);
                path.moveTo(9, 13); path.lineTo(16, 13); path.moveTo(9, 16); path.lineTo(14, 16);
                c.drawPath(path, p); break;
            case PLUS:
                c.drawLine(4, 12, 20, 12, p); c.drawLine(12, 4, 12, 20, p); break;
            case DOWNLOAD:
                path.moveTo(12, 3); path.lineTo(12, 15); path.lineTo(7, 10);
                path.moveTo(12, 15); path.lineTo(17, 10);
                path.moveTo(4, 15); path.lineTo(4, 20); path.lineTo(20, 20); path.lineTo(20, 15);
                c.drawPath(path, p);
                break;
            case SPORTS:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(12, 7); path.lineTo(17, 10); path.lineTo(15, 15);
                path.lineTo(9, 15); path.lineTo(7, 10); path.close();
                path.moveTo(12, 7); path.lineTo(12, 3); path.moveTo(17, 10); path.lineTo(21, 9);
                path.moveTo(15, 15); path.lineTo(18, 19); path.moveTo(9, 15); path.lineTo(6, 19);
                path.moveTo(7, 10); path.lineTo(3, 9); c.drawPath(path, p);
                break;
            case CINEMA:
                r.set(3, 9, 21, 20); c.drawRoundRect(r, 2, 2, p);
                path.moveTo(3, 9); path.lineTo(3, 6); path.lineTo(20, 3); path.lineTo(21, 6); path.close();
                path.moveTo(12, 12); path.lineTo(16, 14.5f); path.lineTo(12, 17); path.close(); c.drawPath(path, p);
                break;
            case TORII:
                path.moveTo(2, 5); path.quadTo(12, 8, 22, 5);
                path.moveTo(4, 10); path.lineTo(20, 10); path.moveTo(7, 6); path.lineTo(6, 21);
                path.moveTo(17, 6); path.lineTo(18, 21); c.drawPath(path, p);
                break;
            case GRID:
                for (int i = 0; i < 4; i++) {
                    float x = 4 + (i % 2) * 9, y = 4 + (i / 2) * 9;
                    r.set(x, y, x + 7, y + 7);
                    c.drawRoundRect(r, 1.8f, 1.8f, p);
                }
                break;
            case INPUTS:
                r.set(3, 5, 21, 17);
                c.drawRoundRect(r, 2, 2, p);
                c.drawLine(9, 20.5f, 15, 20.5f, p);
                path.moveTo(8, 11); path.lineTo(15, 11); path.moveTo(12.5f, 8.5f); path.lineTo(15, 11); path.lineTo(12.5f, 13.5f);
                c.drawPath(path, p);
                break;
            case TV:
                r.set(3, 7, 21, 19);
                c.drawRoundRect(r, 2, 2, p);
                path.moveTo(8, 3); path.lineTo(12, 7); path.lineTo(16, 3);
                c.drawPath(path, p);
                break;
            case HDMI:
                path.moveTo(4, 9); path.lineTo(20, 9); path.lineTo(20, 13); path.lineTo(17, 16); path.lineTo(7, 16);
                path.lineTo(4, 13); path.close();
                c.drawPath(path, p);
                c.drawLine(8, 12.5f, 16, 12.5f, p);
                break;
            case ASSISTANT:
                path.moveTo(12, 2); path.quadTo(13, 10, 22, 12); path.quadTo(13, 14, 12, 22);
                path.quadTo(10, 14, 2, 12); path.quadTo(10, 10, 12, 2); path.close();
                c.drawPath(path, p);
                break;
            case IMAGE:
                r.set(3, 5, 21, 19);
                c.drawRoundRect(r, 2, 2, p);
                path.moveTo(5, 17); path.lineTo(10, 11); path.lineTo(13.5f, 14.5f); path.lineTo(15.5f, 12.5f); path.lineTo(19, 17);
                c.drawPath(path, p);
                c.drawCircle(16, 9, 1.4f, p);
                break;
            case GEAR:
                c.drawCircle(12, 12, 3.2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine(12 + (float) Math.cos(a) * 6.2f, 12 + (float) Math.sin(a) * 6.2f,
                            12 + (float) Math.cos(a) * 8.8f, 12 + (float) Math.sin(a) * 8.8f, p);
                }
                c.drawCircle(12, 12, 6.2f, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, p);
                c.drawLine(12, 11, 12, 16.5f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 7.6f, 1.2f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case HOME:
                path.moveTo(4, 11); path.lineTo(12, 4); path.lineTo(20, 11); path.moveTo(6.5f, 9.5f); path.lineTo(6.5f, 20);
                path.lineTo(17.5f, 20); path.lineTo(17.5f, 9.5f);
                c.drawPath(path, p);
                break;
            case PLAY:
                path.moveTo(8, 5); path.lineTo(19, 12); path.lineTo(8, 19); path.close();
                c.drawPath(path, p);
                break;
            case DOCK:
                r.set(3, 14, 21, 20);
                c.drawRoundRect(r, 2, 2, p);
                r.set(5, 5, 11, 11);
                c.drawRoundRect(r, 1.5f, 1.5f, p);
                r.set(13, 5, 19, 11);
                c.drawRoundRect(r, 1.5f, 1.5f, p);
                break;
            case SHORTCUT:
                path.moveTo(9, 5); path.lineTo(19, 5); path.lineTo(19, 15); path.moveTo(19, 5); path.lineTo(5, 19);
                c.drawPath(path, p);
                break;
        }
        c.restore();
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @SuppressWarnings("deprecation")   // still abstract (required) on API 31
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
