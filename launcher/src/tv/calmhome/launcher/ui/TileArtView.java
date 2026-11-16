package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.data.AppArt;

/**
 * An app's own artwork, centred above its tile's glass. The bitmap was rendered at exactly the size it is drawn at, so it is
 * blitted 1:1 and stays sharp; only the glass behind it is soft. The view takes no focus and no clicks: the tile does.
 */
public final class TileArtView extends View {
    private final AppArt.Art art;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Rect drawn = new Rect();

    public TileArtView(Context c, AppArt.Art art) {
        super(c);
        this.art = art;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public AppArt.Art art() {
        return art;
    }

    public TileArt.Kind kind() {
        return art.kind;
    }

    /** Where the artwork is drawn, in this view's pixels: centred. (A new rectangle each call: not for drawing.) */
    public Rect drawnRect() {
        Rect r = new Rect();
        place(r);
        return r;
    }

    private void place(Rect out) {
        int w = art.bitmap.getWidth(), h = art.bitmap.getHeight();
        int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
        out.set(x, y, x + w, y + h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        place(drawn);
        canvas.drawBitmap(art.bitmap, null, drawn, paint);
    }
}
