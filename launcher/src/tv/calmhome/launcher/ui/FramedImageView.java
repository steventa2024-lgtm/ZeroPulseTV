package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import tv.calmhome.launcher.core.WallpaperFraming;

/**
 * Shows a picture filling the view the way a wallpaper should: the largest screen-shaped window of the picture, centred on
 * a focal point (the centre unless the picture or the viewer says otherwise) and optionally zoomed, so a 16:9 screen gets a
 * 16:9 crop of any picture and one of another shape gets the part that matters. The maths is {@link WallpaperFraming}.
 */
public final class FramedImageView extends ImageView {
    private float fx = WallpaperFraming.CENTER, fy = WallpaperFraming.CENTER, zoom = 1f;
    private final Matrix matrix = new Matrix();

    public FramedImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
    }

    /** The focal point (0..1 of the picture's width and height) and zoom (1 or more) the view is framed with. */
    public void setFrame(float x, float y, float zoomFactor) {
        fx = WallpaperFraming.clamp01(x);
        fy = WallpaperFraming.clamp01(y);
        zoom = WallpaperFraming.clampZoom(zoomFactor);
        update();
    }

    public float focusX() {
        return fx;
    }

    public float focusY() {
        return fy;
    }

    public float zoom() {
        return zoom;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        update();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        update();
    }

    private void update() {
        Drawable d = getDrawable();
        if (d == null || getWidth() <= 0 || getHeight() <= 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return;
        float[] m = WallpaperFraming.matrix(d.getIntrinsicWidth(), d.getIntrinsicHeight(), getWidth(), getHeight(), fx, fy, zoom);
        matrix.setScale(m[0], m[0]);
        matrix.postTranslate(m[1], m[2]);
        setImageMatrix(matrix);
    }
}
