package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Reusable ZeroPulse components with one shared focus language:
 * Electric Blue ring + blue spot-shadow glow + gentle scale, 150 ms decelerate (0 ms with reduced motion).
 */
public final class Widgets {
    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context c;
    private final Theme t;
    private final DecelerateInterpolator ease = new DecelerateInterpolator(2f);

    public Widgets(Context c, Theme t) {
        this.c = c;
        this.t = t;
    }

    // ------------------------------------------------------------------ focus

    /** Card outline: hairline when idle, Electric Blue ring when focused. */
    public Drawable ring(boolean focused, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0);
        g.setCornerRadius(t.dp(radiusDp));
        if (focused) g.setStroke(t.dp(2.5f), Theme.ELECTRIC_BLUE);
        else g.setStroke(Math.max(1, t.dp(1)), Theme.withAlpha(Theme.SOFT_WHITE, 0.10f));
        return g;
    }

    /** Applies the shared focus look to a card; {@code label} (optional) fades in when focused. */
    public void focusable(final View card, final float radiusDp, final TextView label, final boolean labelAlways) {
        card.setFocusable(true);
        card.setClickable(true);
        card.setForeground(ring(false, radiusDp));
        card.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(Theme.ELECTRIC_BLUE);
            card.setOutlineAmbientShadowColor(Theme.ELECTRIC_BLUE);
        }
        if (label != null) label.setAlpha(labelAlways ? 0.85f : 0f);
        card.setOnFocusChangeListener((v, has) -> {
            v.setForeground(ring(has, radiusDp));
            float s = has && !t.reducedMotion ? t.focusScale : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? t.dp(t.glowDp) : 0)
                    .setInterpolator(ease).setDuration(t.focusMs()).start();
            if (label != null) {
                label.animate().alpha(has ? 1f : (labelAlways ? 0.85f : 0f)).setDuration(t.focusMs()).start();
                label.setTextColor(has ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.85f));
            }
        });
    }

    // ------------------------------------------------------------------ app tile

    /** A 16:9 app card with its TV banner (or icon + name), and a label underneath. */
    public LinearLayout appTile(Drawable banner, Drawable icon, String name, int wDp, int hDp, boolean labelAlways,
                                View.OnClickListener click, View.OnLongClickListener longClick) {
        FrameLayout card = new FrameLayout(c);
        card.setBackground(t.rounded(Theme.PANEL_NAVY, Theme.R_TILE));
        if (banner != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            inner.addView(iv, new LinearLayout.LayoutParams(t.dp(hDp * 0.42f), t.dp(hDp * 0.42f)));
            TextView tv = t.text(c, Theme.T_CAPTION, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            tv.setText(name);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(t.dp(6), t.dp(5), t.dp(6), 0);
            inner.addView(tv);
            card.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        card.setContentDescription(name);
        card.setOnClickListener(click);
        if (longClick != null) {
            card.setLongClickable(true);
            card.setOnLongClickListener(longClick);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.addView(card, new LinearLayout.LayoutParams(t.dp(wDp), t.dp(hDp)));
        TextView label = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        t.overPhoto(label);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(wDp), WRAP);
        lp.topMargin = t.dp(10);
        col.addView(label, lp);
        focusable(card, Theme.R_TILE, label, labelAlways);
        return col;
    }

    /** The focusable card inside a tile made by {@link #appTile}. */
    public static View cardOf(View tile) {
        return ((ViewGroup) tile).getChildAt(0);
    }

    // ------------------------------------------------------------------ chips and nav

    /** Quick-action chip: icon + label on a glass pill. {@code accent} tints the icon cyan (Assistant). */
    public LinearLayout chip(Glyph.Kind icon, String text, boolean accent, View.OnClickListener click) {
        final LinearLayout chip = new LinearLayout(c);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(t.dp(16), 0, t.dp(16), 0);
        chip.setBackground(t.glass(Theme.R_CHIP, 0.62f));
        final Glyph g = new Glyph(icon, accent ? Theme.PULSE_CYAN : Theme.SOFT_WHITE);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(g);
        chip.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView tv = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(text);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.leftMargin = t.dp(9);
        chip.addView(tv, tlp);
        chip.setContentDescription(text);
        if (accent) chip.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.62f), t.accentBlue(0.7f), 1, Theme.R_CHIP));
        final Drawable idle = chip.getBackground();
        chip.setOnClickListener(click);
        chip.setFocusable(true);
        chip.setClickable(true);
        if (Build.VERSION.SDK_INT >= 28) {
            chip.setOutlineSpotShadowColor(Theme.ELECTRIC_BLUE);
            chip.setOutlineAmbientShadowColor(Theme.ELECTRIC_BLUE);
        }
        chip.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.28f), Theme.ELECTRIC_BLUE, 2, Theme.R_CHIP) : idle);
            v.animate().scaleX(has && !t.reducedMotion ? 1.05f : 1f).scaleY(has && !t.reducedMotion ? 1.05f : 1f)
                    .translationZ(has ? t.dp(t.glowDp * 0.6f) : 0).setDuration(t.focusMs()).start();
        });
        return chip;
    }

    /** Top-navigation item. {@code current} marks the screen you are on. */
    public TextView navItem(String text, boolean current, View.OnClickListener click) {
        final TextView v = t.text(c, Theme.T_NAV, current ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.82f),
                current ? Theme.FONT_MEDIUM : Theme.FONT_REGULAR);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setPadding(t.dp(16), t.dp(8), t.dp(16), t.dp(8));
        v.setFocusable(true);
        v.setClickable(true);
        v.setContentDescription(text);
        final Drawable idle = current ? t.rounded(Theme.withAlpha(Theme.SOFT_WHITE, 0.16f), Theme.R_PILL) : null;
        v.setBackground(idle);
        v.setOnClickListener(click);
        v.setOnFocusChangeListener((view, has) -> {
            view.setBackground(has ? t.rounded(Theme.ELECTRIC_BLUE, Theme.R_PILL) : idle);
            v.setTextColor(has || current ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.82f));
        });
        return v;
    }

    public LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(WRAP, WRAP);
    }
}
