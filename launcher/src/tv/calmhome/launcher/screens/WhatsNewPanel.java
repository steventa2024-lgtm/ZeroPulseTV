package tv.calmhome.launcher.screens;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import tv.calmhome.launcher.BuildConfig;
import tv.calmhome.launcher.core.MotionTokens;
import tv.calmhome.launcher.core.WhatsNew;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * What's New for this version, as a modal over the current screen: the version, a title, the real highlights, fixes and performance
 * notes of this build (see {@link WhatsNew}), and Close. It is reached from Settings > About and About, never from a seventh
 * destination. Back or Close dismisses it; focus stays inside it while it is open and returns where it was. Opening it records the
 * version as seen, so Home's discreet dot on Settings goes away.
 */
public final class WhatsNewPanel extends Panel {
    private View close;

    public WhatsNewPanel(Host host) {
        super(host);
    }

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    protected View build() {
        host.prefs().setWhatsNewSeen(BuildConfig.VERSION_NAME);
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, t.liquid ? 0.5f : 0.6f));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(26), t.dp(22), t.dp(26), t.dp(18));
        card.setBackground(pane());
        card.setClipChildren(false);

        TextView version = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        version.setText("What's new in ZeroPulse TV " + BuildConfig.VERSION_NAME);
        card.addView(version);
        WhatsNew.Notes notes = WhatsNew.current();
        TextView title = t.text(c, Theme.T_TITLE + 3, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        title.setText(notes.title);
        LinearLayout.LayoutParams tl = w.wrap();
        tl.topMargin = t.dp(4);
        card.addView(title, tl);
        section(card, "Highlights", notes.highlights);
        section(card, "Fixes", notes.fixes);
        section(card, "Performance", notes.performance);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setGravity(Gravity.START);
        close = w.chip(Glyph.Kind.HOME, "Close", false, v -> host.close(this));
        w.glassPill(close);
        buttons.addView(close, new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(38)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        bl.topMargin = t.dp(16);
        card.addView(buttons, bl);

        scrim.addView(card, new FrameLayout.LayoutParams(t.dp(560), Widgets.WRAP, Gravity.CENTER));
        // A soft entrance: the card settles in from 97 % (a transform of an ordinary pane; no glass surface is scaled).
        int ms = MotionTokens.of(MotionTokens.MODAL_MS, t.reducedMotion);
        if (ms > 0) {
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.animate().scaleX(1f).scaleY(1f).setDuration(ms).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        return scrim;
    }

    private void section(LinearLayout card, String heading, List<String> lines) {
        if (lines.isEmpty()) return;
        TextView h = t.text(c, Theme.T_LABEL, Theme.withAlpha(Theme.SOFT_WHITE, 0.7f), Theme.FONT_MEDIUM);
        h.setText(heading.toUpperCase(java.util.Locale.ROOT));
        h.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams hl = w.wrap();
        hl.topMargin = t.dp(14);
        card.addView(h, hl);
        for (String line : lines) {
            TextView v = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
            v.setText("·  " + line);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            vl.topMargin = t.dp(4);
            card.addView(v, vl);
        }
    }

    /** The approved panel glass: Liquid Glass's pane over a dark base (so text behind never shows through), graphite's own otherwise. */
    private Drawable pane() {
        if (!t.liquid) return t.glass(Theme.R_PANEL, 0.97f);
        return new LayerDrawable(new Drawable[]{t.rounded(Theme.withAlpha(0xFF070B11, 0.9f), Theme.R_PANEL), t.glass(Theme.R_PANEL, 0.88f)});
    }

    @Override
    public void focusFirst() {
        if (close != null) close.requestFocus();
    }
}
