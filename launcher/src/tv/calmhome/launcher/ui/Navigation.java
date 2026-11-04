package tv.calmhome.launcher.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import tv.calmhome.launcher.screens.Panel;

/** Shared, overscan-safe top navigation on the wallpaper plane. */
public final class Navigation extends LinearLayout {
    private final View assistant;
    private final TextClock clock;
    private View current;

    public Navigation(Panel.Host host, String selected) {
        super(host.context());
        Theme t = host.theme();
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setBackground(t.glass(Theme.R_PILL, .86f));
        setPadding(t.dp(10), t.dp(4), t.dp(8), t.dp(4));
        addView(new PulseLogo(getContext(), t.dp(23)));
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setClipChildren(false);
        tabs.setGravity(Gravity.CENTER);
        addView(tabs, new LayoutParams(0, Widgets.WRAP, 1));
        View assistantTab = null;
        String[] labels = {"Home", "Apps", "Downloads", "Inputs", "Assistant", "Settings"};
        Glyph.Kind[] icons = {Glyph.Kind.HOME, Glyph.Kind.GRID, Glyph.Kind.DOWNLOAD, Glyph.Kind.INPUTS,
                Glyph.Kind.ASSISTANT, Glyph.Kind.GEAR};
        for (int i = 0; i < labels.length; i++) {
            String tab = labels[i];
            TextView item = host.widgets().navItem(tab, tab.equals(selected), v -> host.navigate(tab));
            if (tab.equals(selected)) {
                current = item;
                item.setFocusableInTouchMode(true);
            }
            if (t.screenW() - 2 * t.safeX() >= t.dp(780)) {
                Glyph icon = new Glyph(icons[i], Theme.SOFT_WHITE);
                icon.setBounds(0, 0, t.dp(15), t.dp(15));
                item.setCompoundDrawablesRelative(icon, null, null, null);
                item.setCompoundDrawablePadding(t.dp(4));
            }
            tabs.addView(item);
            if ("Assistant".equals(tab)) assistantTab = item;
        }
        assistant = assistantTab;
        clock = new TextClock(getContext());
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER);
        t.style(clock, 15, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        clock.setBackground(t.glass(Theme.R_PILL, .70f));
        addView(clock, new LayoutParams(t.dp(82), t.dp(32)));
        refresh(host);
    }

    public void refresh(Panel.Host host) {
        assistant.setVisibility(host.prefs().assistantEnabled() ? VISIBLE : GONE);
        clock.setVisibility(host.prefs().showClock() ? VISIBLE : GONE);
    }

    public void focusCurrent() { if (current != null) current.requestFocus(); }
}
