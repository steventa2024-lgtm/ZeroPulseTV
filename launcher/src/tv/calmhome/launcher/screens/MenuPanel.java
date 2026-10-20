package tv.calmhome.launcher.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Small modal menu (long-press actions, confirmations). Back dismisses it without doing anything. */
public class MenuPanel extends Panel {

    private final String title, subtitle;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Boolean> danger = new ArrayList<>();
    private View first;

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

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    protected View build() {
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.55f));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(22), t.dp(20), t.dp(22), t.dp(16));
        card.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.BORDER, 1, Theme.R_PANEL));
        card.setClipChildren(false);
        TextView h = t.text(c, Theme.T_TITLE + 2, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        card.addView(h);
        if (subtitle != null) {
            TextView s = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            s.setText(subtitle);
            LinearLayout.LayoutParams sl = w.wrap();
            sl.topMargin = t.dp(6);
            card.addView(s, sl);
        }
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = actions.get(i);
            TextView row = t.text(c, Theme.T_BODY + 1, danger.get(i) ? Theme.ERROR : Theme.SOFT_WHITE, Theme.FONT_REGULAR);
            row.setText(labels.get(i));
            row.setPadding(t.dp(14), t.dp(11), t.dp(14), t.dp(11));
            row.setFocusable(true);
            row.setClickable(true);
            row.setContentDescription(labels.get(i));
            final int idle = danger.get(i) ? Theme.ERROR : Theme.SOFT_WHITE;
            row.setOnFocusChangeListener((v, has) -> {
                v.setBackground(has ? t.rounded(Theme.ELECTRIC_BLUE, 10) : null);
                ((TextView) v).setTextColor(has ? Theme.SOFT_WHITE : idle);
            });
            row.setOnClickListener(v -> {
                host.close(this);
                if (action != null) action.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            lp.topMargin = t.dp(i == 0 ? 14 : 2);
            card.addView(row, lp);
            if (first == null) first = row;
        }
        scrim.addView(card, new FrameLayout.LayoutParams(t.dp(340), Widgets.WRAP, Gravity.CENTER));
        return scrim;
    }

    @Override
    public void focusFirst() {
        if (first != null) first.requestFocus();
    }
}
