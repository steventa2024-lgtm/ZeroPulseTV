package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A Home quick action (All Apps, Downloads, Assistant, Wallpapers, Inputs) on the same approved graphite glass as the app
 * tiles: a slim capsule, a small icon and a label above the glass, the label a little dimmer until the capsule has focus.
 * Only the focused action takes the focus material; the others stay quiet, with no outline.
 */
public final class ZeroPulseGlassQuickAction extends ZeroPulseGlassControl {
    private static final float IDLE_TEXT_ALPHA = 0.84f;

    private final TextView label;
    private final Glyph glyph;

    public ZeroPulseGlassQuickAction(Context c, Theme t, GlassKit kit, Glyph.Kind icon, String text, int heightPx, int focusMs) {
        super(c, kit, GraphiteGlassSurface.Shape.CAPSULE, heightPx, focusMs, 1f, 0.55f);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        glyph = new Glyph(icon, Theme.SOFT_WHITE);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(glyph);
        int iconPx = Math.max(1, Math.round(heightPx * 0.46f));
        row.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        label = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        label.setText(text);
        label.setSingleLine(true);
        label.setAlpha(IDLE_TEXT_ALPHA);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        lp.leftMargin = Math.round(heightPx * 0.22f);
        row.addView(label, lp);
        contentFrame().setPadding(0, 0, 0, 0);
        contentFrame().addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentDescription(text);
    }

    /** The action's icon (so a test can look at it). */
    public Drawable icon() {
        return glyph;
    }

    public CharSequence text() {
        return label.getText();
    }

    @Override
    protected void onLevel(float level) {
        label.setAlpha(IDLE_TEXT_ALPHA + (1f - IDLE_TEXT_ALPHA) * level);
    }
}
