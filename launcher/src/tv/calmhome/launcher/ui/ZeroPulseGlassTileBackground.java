package tv.calmhome.launcher.ui;

import android.content.Context;

/**
 * The glass behind an app tile or a quick action: a {@link GraphiteGlassSurface} that registers itself with the shared
 * {@link GlassKit} while it is on screen, so it refracts the same selected wallpaper as the header and the clock, with the
 * same alignment, through the one {@code GraphiteGlassRenderer}. Not interactive and not a focus target.
 */
public final class ZeroPulseGlassTileBackground extends GraphiteGlassSurface {
    private final GlassKit kit;

    public ZeroPulseGlassTileBackground(Context c, GlassKit kit, Shape shape, float designHeightPx) {
        super(c, shape, designHeightPx);
        this.kit = kit;
        setOnUnitChanged(() -> {
            if (isAttachedToWindow()) kit.rebindSurface(this);
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        kit.bindSurface(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        kit.unbindSurface(this);
        super.onDetachedFromWindow();
    }
}
