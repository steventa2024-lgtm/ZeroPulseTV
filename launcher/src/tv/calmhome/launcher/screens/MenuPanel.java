package tv.calmhome.launcher.screens;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Small modal menu (long-press actions, confirmations). Back dismisses it without doing anything, and focus returns to the control
 * that opened it. Up and Down stay inside the menu (the screen behind cannot take focus while it is open).
 *
 * <p>In Liquid Glass the card is the panes' quiet graphite with one hairline rim, the wallpaper still showing around it, and each
 * choice is a row of the approved glass that lights when it has focus (the same rows as Settings): no white outline, no matte card.
 * The card rises a few dp as it fades in; the glass is never scaled.
 */
public class MenuPanel extends Panel {

    private final String title, subtitle;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Boolean> danger = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    private View first, card;

    public MenuPanel(Host host, String title, String subtitle) {
        super(host);
        this.title = title;
        this.subtitle = subtitle;
    }

    public MenuPanel item(String label, Runnable action) {
        return item(label, action, false);
    }

    public MenuPanel item(String label, Runnable action, boolean isDanger) {
        labels.add(label);
        actions.add(action);
        danger.add(isDanger);
        return this;
    }

    /** The labels of the choices, in order (for tests). */
    public List<String> labels() {
        return new ArrayList<>(labels);
    }

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    public View enterMotionView() {
        return card;
    }

    @Override
    protected View build() {
        final boolean glass = t.liquid && t.kit != null;
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, glass ? 0.42f : 0.55f));     // the wallpaper still shows around the card

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(t.dp(glass ? 18 : 22), t.dp(18), t.dp(glass ? 18 : 22), t.dp(14));
        box.setBackground(glass ? t.glass(Theme.R_PANEL, 0.9f) : t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.BORDER, 1, Theme.R_PANEL));
        box.setClipChildren(false);
        box.setClipToPadding(false);
        TextView h = t.text(c, Theme.T_TITLE + 2, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        h.setPadding(t.dp(glass ? 4 : 0), 0, 0, 0);
        box.addView(h);
        if (subtitle != null) {
            TextView s = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            s.setText(subtitle);
            s.setLineSpacing(0, 1.15f);
            s.setPadding(t.dp(glass ? 4 : 0), 0, 0, 0);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            sl.topMargin = t.dp(6);
            box.addView(s, sl);
        }
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = actions.get(i);
            final int idle = danger.get(i) ? Theme.ERROR : Theme.SOFT_WHITE;
            TextView label = t.text(c, Theme.T_BODY + 1, idle, Theme.FONT_REGULAR);
            label.setText(labels.get(i));
            label.setPadding(t.dp(14), t.dp(11), t.dp(14), t.dp(11));
            final View row;
            if (glass) {
                ZeroPulseGlassRow g = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CARD, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, false);
                g.content().addView(label, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.CENTER_VERTICAL));
                row = g;
            } else {
                row = label;
                row.setFocusable(true);
                row.setClickable(true);
                row.setOnFocusChangeListener((v, has) -> {
                    v.setBackground(has ? t.outlined(0xFF50545A, Theme.ELECTRIC_BLUE, 2, 10) : null);
                    ((TextView) v).setTextColor(has ? Theme.SOFT_WHITE : idle);
                });
            }
            row.setContentDescription(labels.get(i));
            row.setOnClickListener(v -> {
                host.close(this);
                if (action != null) action.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            lp.topMargin = t.dp(i == 0 ? 14 : 2);
            box.addView(row, lp);
            rows.add(row);
            if (first == null) first = row;
        }
        // Up on the first choice and Down on the last stay in the menu (the screen behind is blocked anyway; this keeps it explicit).
        if (!rows.isEmpty()) {
            View top = rows.get(0), bottom = rows.get(rows.size() - 1);
            top.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_UP);
            if (bottom != top) bottom.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN);
            else bottom.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN
                    && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN));
        }
        card = box;
        scrim.addView(box, new FrameLayout.LayoutParams(t.dp(360), Widgets.WRAP, Gravity.CENTER));
        return scrim;
    }

    @Override
    public void focusFirst() {
        if (first != null) first.requestFocus();
    }
}
