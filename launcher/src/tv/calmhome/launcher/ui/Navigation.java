package tv.calmhome.launcher.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.function.Consumer;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.screens.Panel;

/** Common graphite navigation, also used by the separate Assistant activity. */
public final class Navigation extends LinearLayout {
    private final View assistant;
    private final LinearLayout clockBox;
    private final TextClock date;
    private View current;

    public Navigation(Panel.Host host, String selected) {
        this(host.context(), host.theme(), host.widgets(), host.prefs(), selected, host::navigate);
    }

    public Navigation(Context c, Theme t, Widgets w, Prefs prefs, String selected, Consumer<String> onTab) {
        super(c);
        setGravity(Gravity.CENTER_VERTICAL); setClipChildren(false);
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setClipChildren(false);
        bar.setPadding(t.dp(6), t.dp(3), t.dp(5), t.dp(3));
        bar.setBackground(t.glass(Theme.R_PILL, .83f));
        addView(bar, new LayoutParams(0, Widgets.WRAP, 1));
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(t.raised(20, false));
        badge.addView(new PulseLogo(c, t.dp(19)).name(""), new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.CENTER));
        bar.addView(badge, new LayoutParams(t.dp(34), t.dp(34)));
        LinearLayout brand = new LinearLayout(c);
        brand.setOrientation(VERTICAL); brand.setPadding(t.dp(5), 0, t.dp(6), 0);
        TextView name = t.text(c, 15, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        name.setText("ZeroPulse TV"); name.setSingleLine();
        TextView system = t.text(c, 9, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        system.setText("Android TV"); brand.addView(name); brand.addView(system); bar.addView(brand);
        LinearLayout tabs = new LinearLayout(c);
        tabs.setGravity(Gravity.CENTER); tabs.setClipChildren(false);
        bar.addView(tabs, new LayoutParams(0, Widgets.WRAP, 1));
        String[] labels = {"Home","Apps","Downloads","Inputs","Assistant","Settings"};
        Glyph.Kind[] icons = {Glyph.Kind.HOME,Glyph.Kind.GRID,Glyph.Kind.DOWNLOAD,Glyph.Kind.INPUTS,Glyph.Kind.ASSISTANT,Glyph.Kind.GEAR};
        View assistantTab = null;
        for (int i = 0; i < labels.length; i++) {
            String tab = labels[i];
            TextView item = w.navItem(tab, tab.equals(selected), v -> onTab.accept(tab));
            if (tab.equals(selected)) { current = item; item.setFocusableInTouchMode(true); }
            if (t.screenW() - 2 * t.safeX() >= t.dp(755)) {
                Glyph glyph = new Glyph(icons[i], Theme.SOFT_WHITE);
                glyph.setBounds(0,0,t.dp(14),t.dp(14));
                item.setCompoundDrawablesRelative(glyph,null,null,null); item.setCompoundDrawablePadding(t.dp(4));
            }
            tabs.addView(item);
            if ("Assistant".equals(tab)) assistantTab = item;
        }
        assistant = assistantTab;
        clockBox = new LinearLayout(c);
        clockBox.setOrientation(VERTICAL); clockBox.setGravity(Gravity.CENTER);
        clockBox.setBackground(t.glass(Theme.R_PILL, .84f));
        TextClock time = new TextClock(c); time.setFormat12Hour("h:mm a"); time.setFormat24Hour("HH:mm");
        t.style(time, 15, Theme.SOFT_WHITE, Theme.FONT_MEDIUM); time.setGravity(Gravity.CENTER); clockBox.addView(time);
        date = new TextClock(c); date.setFormat12Hour("EEE, MMM d"); date.setFormat24Hour("EEE, MMM d");
        t.style(date, 9, Theme.MUTED_GRAY, Theme.FONT_REGULAR); date.setGravity(Gravity.CENTER); clockBox.addView(date);
        LayoutParams box = new LayoutParams(t.dp(85),t.dp(39)); box.leftMargin=t.dp(12); addView(clockBox,box);
        update(prefs);
    }
    private void update(Prefs prefs) {
        assistant.setVisibility(prefs.assistantEnabled() ? VISIBLE : GONE);
        clockBox.setVisibility(prefs.showClock() ? VISIBLE : GONE);
        date.setVisibility(prefs.showDate() ? VISIBLE : GONE);
    }
    public void refresh(Panel.Host host) { update(host.prefs()); }
    public void focusCurrent() { if (current != null) current.requestFocus(); }
}
