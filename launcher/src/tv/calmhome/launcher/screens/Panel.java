package tv.calmhome.launcher.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.ui.Navigation;
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
        void navigate(String tab);
        void pickApk(tv.calmhome.launcher.core.SourceEntry.Category category);
        /** Opens ZeroPulse Streaming or Web; refused (with an explanation) while the module is switched off. */
        void openModule(Features.Module m);
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

    private Navigation navigation;

    /** The panel's own copy of the top navigation, or null (menus, dialogs). */
    public final Navigation navigation() {
        return navigation;
    }

    private final java.util.List<View> motion = new java.util.ArrayList<>();
    private View heading;

    /**
     * The views that move when this screen is reached from another destination: everything under the navigation (the heading, the
     * content, the credit). The navigation itself is not among them: it stays where it is (see MainActivity's tab transition).
     */
    public final java.util.List<View> motionViews() {
        return motion;
    }

    /** The heading (the only part of a screen that also slides a few dp as it fades in; glass never moves for a transition). */
    public final View headingView() {
        return heading;
    }

    /**
     * A modal's card, which rises a few dp as it fades in (it may hold glass: a translation only re-renders the glass once it has
     * settled, and it is never scaled). Null for full screens.
     */
    public View enterMotionView() {
        return null;
    }

    /** Empty lists still need a remote target when Android reports no inputs or launchable apps. */
    public final void focusContentOrTab() {
        focusFirst();
        if (navigation != null && !view().hasFocus()) navigation.focusCurrent();
    }

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

    public void onPackagesChanged() { }

    /** Called when the hosting Activity returns to the foreground (for post-install or permission checks). */
    public void onResume() { }

    /** Small navigation token safe to restore after Android reclaims the launcher. */
    public String restoreRoute() {
        if (this instanceof AppsPanel) return "apps";
        if (this instanceof InputsPanel) return "inputs";
        if (this instanceof WallpapersPanel) return "wallpapers";
        if (this instanceof WallpaperInfoPanel) return "wallpaper-info";
        if (this instanceof SettingsPanel) return "settings";
        if (this instanceof AboutPanel) return "about";
        if (this instanceof DownloadPage) return this instanceof DownloadsPanel ? "downloads" : "downloads:library";
        return null;
    }

    private TextView wallpaperCredit;

    public void updateCredit(String caption) {
        if (wallpaperCredit != null) wallpaperCredit.setText(caption);
    }

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
        if (this instanceof WallpapersPanel) f.setBackground(bg);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(t.safeX(), t.safeY(), t.safeX(), t.safeY());

        String tab;
        if (tv.calmhome.launcher.BuildConfig.STANDALONE_ZPAPPS) {
            tab = this instanceof tv.calmhome.launcher.toolbox.ZpAppsPanel ? "Toolbox"
                    : this instanceof DownloadPage ? "Downloads"
                    : (this instanceof WallpapersPanel || this instanceof WallpaperInfoPanel) ? "Wallpapers"
                    : "Settings";
        } else {
            tab = this instanceof AppsPanel ? "Apps" : this instanceof InputsPanel ? "Inputs" :
                    this instanceof DownloadPage ? "Downloads" : "Settings";
        }
        navigation = new Navigation(host, tab);
        col.addView(navigation, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        TextView h = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setPadding(t.dp(2), t.dp(8), 0, 0);
        if (this instanceof DownloadsPanel) {
            t.style(h, 28, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        }
        h.setText(title);
        col.addView(h);
        heading = h;
        motion.add(h);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        clp.topMargin = t.dp(3);
        col.addView(content, clp);
        motion.add(content);
        wallpaperCredit = t.text(c, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        wallpaperCredit.setPadding(0, t.dp(3), 0, 0);
        if (host.stage().current() != null) updateCredit(host.stage().current().caption());
        if (this instanceof WallpapersPanel) {
            col.addView(wallpaperCredit);
            motion.add(wallpaperCredit);
        }
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
