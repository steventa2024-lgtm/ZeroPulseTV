package tv.calmhome.launcher.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * A full-screen launcher screen (All Apps, Inputs, Wallpapers, Settings, About) or a modal menu, shown
 * over the Home wallpaper by {@link Host}. Panels stack; Back closes the top one (or lets it handle Back).
 */
public abstract class Panel {

    /** What a panel can ask of the activity hosting it. */
    public interface Host {
        Context context();
        Theme theme();
        Widgets widgets();
        Prefs prefs();
        Apps apps();
        WallpaperStage stage();
        void open(Panel p);
        void close(Panel p);
        void closeAll();
        /** Re-reads preferences that affect Home (dock, greeting, clock, dim, wallpapers). */
        void refreshHome();
        /** Rebuilds the whole activity (UI scale / focus / accent changes), reopening {@code reopen}. */
        void rebuild(String reopen);
        void openAssistant();
        void toast(String text);
    }

    protected final Host host;
    protected final Context c;
    protected final Theme t;
    protected final Widgets w;
    View root;

    protected Panel(Host host) {
        this.host = host;
        this.c = host.context();
        this.t = host.theme();
        this.w = host.widgets();
    }

    /** Creates the panel's view tree (called once when opened). */
    protected abstract View build();

    /** Requests the initial focus once the panel is on screen. */
    public abstract void focusFirst();

    /** Back pressed while this panel is on top. Return true if handled inside the panel. */
    public boolean onBack() {
        return false;
    }

    /** Called when a panel above this one closes. */
    public void onUncovered() { }

    /** Modal panels (menus) keep the screen behind visible; full panels dim it more. */
    public boolean isModal() {
        return false;
    }

    public void onClosed() { }

    private View returnFocus;

    public void setReturnFocus(View v) {
        returnFocus = v;
    }

    /** View that had focus before this panel opened; focus goes back there on close. */
    public View returnFocus() {
        return returnFocus;
    }

    public View view() {
        if (root == null) root = build();
        return root;
    }

    // ------------------------------------------------------------------ shared frame

    /** Standard screen: dark gradient over the wallpaper, header (logo, title, clock), then {@code content}. */
    protected FrameLayout frame(String title, View content) {
        return frame(title, content, 1f);
    }

    /** {@code shade} 1 = standard dark screen; lower lets more of the wallpaper show (Wallpapers screen). */
    protected FrameLayout frame(String title, View content, float shade) {
        FrameLayout f = new FrameLayout(c);
        f.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                Theme.withAlpha(Theme.DEEP_BLACK, 0.94f * shade), Theme.withAlpha(Theme.MIDNIGHT_NAVY, 0.88f * shade * shade),
                Theme.withAlpha(Theme.DEEP_BLACK, 0.95f)});
        f.setBackground(bg);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(t.safeX(), t.safeY(), t.safeX(), 0);

        FrameLayout header = new FrameLayout(c);
        LinearLayout left = new LinearLayout(c);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(new PulseLogo(c, t.dp(26)));
        View sep = new View(c);
        sep.setBackgroundColor(Theme.withAlpha(Theme.SOFT_WHITE, 0.22f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.max(1, t.dp(1)), t.dp(22));
        sl.leftMargin = t.dp(14);
        sl.rightMargin = t.dp(16);
        left.addView(sep, sl);
        TextView h = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        left.addView(h);
        header.addView(left, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        t.style(clock, 26, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        clock.setGravity(Gravity.END);
        clock.setSingleLine(true);
        // Fixed width: content-sized clocks were clipped on this TV after the time changed.
        header.addView(clock, new FrameLayout.LayoutParams(t.dp(140), Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        clp.topMargin = t.dp(22);
        col.addView(content, clp);
        f.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        return f;
    }

    protected TextView sectionLabel(String s) {
        TextView v = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        v.setText(s.toUpperCase());
        v.setLetterSpacing(0.08f);
        return v;
    }

    protected static ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(Widgets.MATCH, Widgets.MATCH);
    }
}
