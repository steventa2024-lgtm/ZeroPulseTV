package tv.calmhome.launcher.screens;

import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tv.calmhome.launcher.BuildConfig;
import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.ModuleData;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * ZeroPulse settings: categories on the left, rows on the right. Moving focus over a category previews
 * it; OK or Right enters it; Back (or Left on a non-choice row) returns to the categories.
 */
public class SettingsPanel extends Panel {

    /** The categories, in order. (Assistant was the fifth until the Assistant was removed in 3.0.0-beta.7.) */
    static final String[] CATEGORIES = {"Appearance", "Wallpapers", "Dock & Apps", "Features", "System Shortcuts", "About"};
    static final Glyph.Kind[] ICONS = {Glyph.Kind.GEAR, Glyph.Kind.IMAGE, Glyph.Kind.DOCK, Glyph.Kind.PLAY,
            Glyph.Kind.SHORTCUT, Glyph.Kind.INFO};

    private final Prefs prefs;
    private final Apps apps;
    private final Rows rows;
    private final String startCategory;
    private LinearLayout categoryList, rowList;
    private TextView rowTitle;
    private ScrollView rowScroll;
    private int selected = 0;
    private final List<View> categoryViews = new ArrayList<>();

    public SettingsPanel(Host host, String startCategory) {
        super(host);
        prefs = host.prefs();
        apps = host.apps();
        rows = new Rows(c, t);
        this.startCategory = startCategory;
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setClipChildren(false);
        body.setPadding(0, 0, 0, t.safeY());

        categoryList = new LinearLayout(c);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        categoryList.setPadding(t.dp(10), t.dp(12), t.dp(10), t.dp(12));
        categoryList.setBackground(t.glass(Theme.R_PANEL, 0.85f));
        for (int i = 0; i < CATEGORIES.length; i++) categoryList.addView(categoryItem(i), Rows.full(t));
        body.addView(categoryList, new LinearLayout.LayoutParams(t.dp(250), Widgets.WRAP));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(t.dp(22), t.dp(18), t.dp(22), t.dp(12));
        right.setBackground(t.glass(Theme.R_PANEL, 0.85f));
        rowTitle = t.text(c, Theme.T_TITLE + 3, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        right.addView(rowTitle);
        rowList = new LinearLayout(c);
        rowList.setOrientation(LinearLayout.VERTICAL);
        // Focus scrolling keeps the focused row whole with its neighbours in view (about one and a half rows of context either side),
        // and the list has a little room of its own at top and bottom, so a row's highlight is never cut by the scroller's edge.
        rowScroll = new tv.calmhome.launcher.ui.ContextScrollView(c, t.dp(54));
        rowScroll.setPadding(0, t.dp(4), 0, t.dp(4));
        rowScroll.setVerticalScrollBarEnabled(false);
        rowScroll.addView(rowList);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        sl.topMargin = t.dp(10);
        right.addView(rowScroll, sl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f);
        rl.leftMargin = t.dp(20);
        body.addView(right, rl);

        // A category that no longer exists (an old "settings:assistant" link) opens the first one, Appearance.
        if (startCategory != null) {
            for (int i = 0; i < CATEGORIES.length; i++) if (key(CATEGORIES[i]).equals(startCategory)) selected = i;
        }
        showCategory(selected);
        return frame("Settings", body);
    }

    private static String key(String category) {
        return category.toLowerCase().replaceAll("[^a-z]", "");
    }

    private View categoryItem(final int i) {
        final boolean glass = t.liquid && t.kit != null;
        final LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(t.dp(14), t.dp(11), t.dp(14), t.dp(11));
        final Glyph g = new Glyph(ICONS[i], Theme.SOFT_WHITE);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(g);
        line.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView l = t.text(c, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        l.setText(CATEGORIES[i]);
        LinearLayout.LayoutParams ll = w.wrap();
        ll.leftMargin = t.dp(12);
        line.addView(l, ll);
        final View item;
        if (glass) {
            // The approved glass takes focus here, as it does on Home; the open category keeps a quiet pane.
            ZeroPulseGlassRow row = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CARD, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, false);
            row.content().addView(line, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.CENTER_VERTICAL));
            item = row;
        } else {
            item = line;
            item.setFocusable(true);
            item.setClickable(true);
        }
        item.setContentDescription(CATEGORIES[i]);
        item.setOnFocusChangeListener((v, has) -> {
            if (has && selected != i && !rebuilding) showCategory(i);
            paintCategories();
        });
        item.setOnClickListener(v -> focusRows());
        item.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusRows();
                return true;
            }
            return false;
        });
        categoryViews.add(item);
        return item;
    }

    private void paintCategories() {
        for (int i = 0; i < categoryViews.size(); i++) {
            View v = categoryViews.get(i);
            if (v instanceof ZeroPulseGlassRow) {                                  // glass: focus is the glass itself; the open category is a quiet pane
                ((ZeroPulseGlassRow) v).setMark(i == selected ? ZeroPulseGlassRow.Mark.PLATE : ZeroPulseGlassRow.Mark.NONE);
                continue;
            }
            if (v.isFocused()) v.setBackground(t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.3f), Theme.ELECTRIC_BLUE, 1.5f, 10));
            else if (i == selected) v.setBackground(t.rounded(t.accentBlue(0.22f), 10));
            else v.setBackground(null);
        }
    }

    private void focusRows() {
        for (int i = 0; i < rowList.getChildCount(); i++) {
            if (rowList.getChildAt(i).isFocusable()) {
                rowList.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    private void showCategory(int i) {
        selected = i;
        rowTitle.setText(CATEGORIES[i]);
        // Removing a focused row makes Android focus the first category, whose listener would start building another
        // category in the middle of this one (seen on the TV: Appearance rows mixed into Features). Park focus on
        // this category first, and ignore category focus changes while the rows are rebuilt.
        View f = root != null ? root.findFocus() : null;
        if (f != null && f.getParent() == rowList) categoryViews.get(i).requestFocus();
        rebuilding = true;
        try {
            rowList.removeAllViews();
            switch (i) {
                case 0: appearance(); break;
                case 1: wallpapers(); break;
                case 2: dock(); break;
                case 3: features(); break;
                case 4: shortcuts(); break;
                default: about(); break;
            }
        } finally {
            rebuilding = false;
        }
        rowScroll.scrollTo(0, 0);
        paintCategories();
    }

    private boolean rebuilding;

    private void add(View row) {
        rowList.addView(row, Rows.full(t));
    }

    private void note(String text) {
        TextView v = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        v.setText(text);
        v.setPadding(t.dp(16), t.dp(10), t.dp(16), t.dp(4));
        rowList.addView(v);
    }

    // ------------------------------------------------------------------ categories

    private void appearance() {
        add(rows.toggle("Liquid Glass Home (beta)", prefs.liquidGlass(), v -> {
            prefs.setLiquidGlass(v);
            prefs.setLiquidStarting(false);
            // Liquid Glass is designed against the dark glass wallpapers supplied for it, so it needs the photo backdrop and brings that
            // collection with it (and the default back when it goes). A collection you chose yourself is left alone.
            if (v) {
                prefs.setPhotoBackdrop(true);
                if (WallpaperCatalog.ALL.equals(prefs.wallpaperCollection())) prefs.setWallpaperCollection(WallpaperCatalog.LIQUID);
            } else if (WallpaperCatalog.LIQUID.equals(prefs.wallpaperCollection())) {
                prefs.setWallpaperCollection(WallpaperCatalog.ALL);
            }
            host.rebuild("settings:appearance");
        }));
        // Liquid Glass always shows the photo, so the plain switch is only offered without it.
        if (!prefs.liquidGlass()) add(rows.toggle("Photo wallpaper on Home", prefs.usePhotoBackdrop(), v -> { prefs.setPhotoBackdrop(v); host.refreshHome(); }));
        add(rows.toggle("Show greeting", prefs.showGreeting(), v -> { prefs.setShowGreeting(v); host.refreshHome(); }));
        add(rows.toggle("Show clock", prefs.showClock(), v -> { prefs.setShowClock(v); host.refreshHome(); }));
        add(rows.toggle("Show date below clock", prefs.showDate(), v -> { prefs.setShowDate(v); host.refreshHome(); }));
        String[] dims = {"0%", "10%", "20%", "30%", "40%", "50%", "60%"};
        add(rows.choice("Wallpaper dimming", dims, prefs.dimPercent() / 10, k -> { prefs.setDimPercent(k * 10); host.refreshHome(); }));
        add(rows.action("Wallpaper information", "Credits, licences and framing", false, v -> host.open(new WallpaperInfoPanel(host))));
        // These three rebuild the whole screen, so they apply once when the change ends, not on every step of it.
        add(rows.choice("UI scale", Prefs.UI_SCALE_NAMES, prefs.uiScaleIndex(), true, k -> {
            prefs.setUiScaleIndex(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.choice("Focus emphasis", new String[]{"Low", "Medium", "High"}, prefs.focusIntensity(), true, k -> {
            prefs.setFocusIntensity(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.choice("Accent intensity", new String[]{"Subtle", "Standard", "Vivid"}, prefs.accentIntensity(), true, k -> {
            prefs.setAccentIntensity(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.toggle("Reduced motion", prefs.reducedMotion(), v -> {
            prefs.setReducedMotion(v);
            host.rebuild("settings:appearance");
        }));
        // Premium interactions: each optional effect has its own switch; the defaults keep Home as approved.
        add(rows.toggle("Focus glow", prefs.focusGlow(), v -> { prefs.setFocusGlow(v); host.rebuild("settings:appearance"); }));
        add(rows.toggle("Featured app on Home", prefs.featuredOnHome(), v -> { prefs.setFeaturedOnHome(v); host.refreshHome(); }));
        add(rows.toggle("Time-of-day tint", prefs.timeOfDayTint(), v -> { prefs.setTimeOfDayTint(v); host.refreshHome(); }));
        add(rows.toggle("Wallpaper depth", prefs.wallpaperDepth(), v -> { prefs.setWallpaperDepth(v); host.refreshHome(); }));
        add(rows.toggle("Interface sounds", prefs.uiSounds(), prefs::setUiSounds));
        String[] volumes = {"Quiet", "Medium", "Loud"};
        int vol = prefs.uiSoundVolume() <= 25 ? 0 : prefs.uiSoundVolume() <= 55 ? 1 : 2;
        add(rows.choice("Sound volume", volumes, vol, k -> prefs.setUiSoundVolume(new int[]{20, 35, 70}[k])));
        note("Wallpaper depth moves the wallpaper a few dp with focus; the glass is not re-rendered for it, so its refraction can lag "
                + "slightly while it is on. Interface sounds are ZeroPulse's own quiet tones; your TV may also play its own navigation sounds.");
        note(prefs.liquidGlass()
                ? "Liquid Glass Home is on (the default): your apps, favorites and settings are the same as in graphite. Turn it off for the plain graphite Home. If it ever fails to start, Home switches back to graphite by itself."
                : "Graphite Home (Liquid Glass is off). Turn it on above to get the glass app tiles and buttons; your apps and favorites do not change.");
    }

    private void wallpapers() {
        Map<String, Integer> cats = host.stage().catalog().categories();
        final List<String> names = new ArrayList<>(cats.keySet());
        String[] labels = new String[names.size()];
        for (int k = 0; k < labels.length; k++) labels[k] = names.get(k) + " (" + cats.get(names.get(k)) + ")";
        add(rows.choice("Collection", labels, Math.max(0, names.indexOf(prefs.wallpaperCollection())), k -> {
            prefs.setWallpaperCollection(names.get(k));
            host.stage().reload();
        }));
        String[] intervals = new String[WallpaperCatalog.INTERVALS.length];
        int cur = 0;
        for (int k = 0; k < intervals.length; k++) {
            intervals[k] = WallpaperCatalog.intervalLabel(WallpaperCatalog.INTERVALS[k]);
            if (WallpaperCatalog.INTERVALS[k] == prefs.wallpaperInterval()) cur = k;
        }
        add(rows.choice("Rotation interval", intervals, cur, k -> {
            prefs.setWallpaperInterval(WallpaperCatalog.INTERVALS[k]);
            host.stage().reload();
        }));
        add(rows.action("Choose wallpaper", null, false, v -> host.open(new WallpapersPanel(host))));
        add(rows.action("Photo credits & licenses", null, false, v -> host.open(new AboutPanel(host))));
        WallpaperCatalog.Entry e = host.stage().current();
        if (e != null) note("Now showing: " + e.caption());
        note("Photo credits appear wherever a wallpaper is shown.");
    }

    private void dock() {
        final List<String> visible = DockList.visible(prefs.dock(), apps::usable);
        for (final String pkg : visible) {
            add(rows.action(apps.label(pkg), "Move or remove", false, v -> dockMenu(pkg)));
        }
        if (visible.isEmpty()) note("Your dock is empty.");
        add(rows.choice("App names", new String[]{"On focus", "Always"}, prefs.labelsAlways() ? 1 : 0, k -> {
            prefs.setLabelsAlways(k == 1);
            host.refreshHome();
        }));
        add(rows.action("Add apps", "All Apps", false, v -> host.open(new AppsPanel(host))));
        add(rows.action("Reset dock", "Original 7 apps", true, v -> host.open(new MenuPanel(host, "Reset the dock?",
                "Restores Netflix, Prime Video, Disney+, Hulu, YouTube, YouTube TV and YouTube Music. Apps are not affected.")
                .item("Reset dock", () -> {
                    prefs.resetDock();
                    host.refreshHome();
                    showCategory(selected);
                    host.toast("Dock reset");
                }, true)
                .item("Cancel", null))));
    }

    private void dockMenu(final String pkg) {
        final List<String> visible = DockList.visible(prefs.dock(), apps::usable);
        final int i = visible.indexOf(pkg);
        MenuPanel m = new MenuPanel(host, apps.label(pkg), "Dock app");
        if (i > 0) m.item("Move left", () -> moved(DockList.move(prefs.dock(), visible, pkg, -1)));
        if (i >= 0 && i < visible.size() - 1) m.item("Move right", () -> moved(DockList.move(prefs.dock(), visible, pkg, 1)));
        m.item("Remove from dock", () -> moved(DockList.remove(prefs.dock(), pkg)));
        host.open(m);
    }

    private void moved(List<String> dock) {
        prefs.setDock(dock);
        host.refreshHome();
        showCategory(selected);
        focusRows();
    }

    private void features() {
        final boolean streaming = prefs.enabled(Features.Module.STREAMING);
        add(rows.toggle(Features.Module.STREAMING.title, streaming, v -> {
            prefs.setEnabled(Features.Module.STREAMING, v);
            host.refreshHome();
            refreshFeatures(Features.Module.STREAMING);
        }));
        if (streaming) {
            for (final Features.Section x : Features.Section.values()) {
                add(rows.toggle("   " + x.label, prefs.sectionEnabled(x), v -> prefs.setSectionEnabled(x, v)));
            }
            add(rows.action("Open " + Features.Module.STREAMING.title, null, false,
                    v -> host.openModule(Features.Module.STREAMING)));
        }
        final boolean web = prefs.enabled(Features.Module.WEB);
        add(rows.toggle(Features.Module.WEB.title, web, v -> {
            prefs.setEnabled(Features.Module.WEB, v);
            if (!v) ModuleData.stopWeb(c);
            host.refreshHome();
            refreshFeatures(Features.Module.WEB);
        }));
        if (web) {
            add(rows.action("Open " + Features.Module.WEB.title, null, false, v -> host.openModule(Features.Module.WEB)));
        }
        add(rows.action("Delete Streaming data", "Asks first", true, v -> host.open(new MenuPanel(host,
                "Delete Streaming data?",
                "Deletes saved sources, channel lists, guide data, favourites and watch progress. "
                        + "The dock and launcher settings are not affected.")
                .item("Delete Streaming data", () -> {
                    ModuleData.deleteStreamingData(c);
                    host.toast("Streaming data deleted");
                }, true)
                .item("Cancel", null))));
        add(rows.action("Delete Web data", "Asks first", true, v -> host.open(new MenuPanel(host,
                "Delete Web data?",
                "Closes the browser and deletes tabs, bookmarks, history, cookies, cache and site data. "
                        + "Downloaded files stay. The dock and launcher settings are not affected.")
                .item("Delete Web data", () -> {
                    ModuleData.deleteWebData(c);
                    host.toast("Web data deleted");
                }, true)
                .item("Cancel", null))));
        note("Both are off until you turn them on. Turning one off hides it and stops it; what it saved stays "
                + "until you delete it here.");
        note("These switches change what ZeroPulse does, not Android permissions: the app has the same "
                + "permissions either way. Streaming and Web use HTTPS only in this beta.");
    }

    /** Rebuilds the Features rows (sub-options appear or disappear) and puts focus back on {@code m}'s switch. */
    private void refreshFeatures(Features.Module m) {
        showCategory(selected);
        for (int i = 0; i < rowList.getChildCount(); i++) {
            CharSequence d = rowList.getChildAt(i).getContentDescription();
            if (d != null && d.toString().startsWith(m.title)) {
                rowList.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    private void shortcuts() {
        shortcut("Android TV settings", new Intent(Settings.ACTION_SETTINGS));
        add(rows.action("Inputs", null, false, v -> host.open(new InputsPanel(host))));
        shortcut("Apps", new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        shortcut("Network & Internet", new Intent(Settings.ACTION_WIFI_SETTINGS));
        shortcut("Display", new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        shortcut("Sound", new Intent(Settings.ACTION_SOUND_SETTINGS));
        note("Only shortcuts this TV supports are listed.");
    }

    private void shortcut(String label, final Intent i) {
        if (!apps.resolves(i)) return;
        add(rows.action(label, null, false, v -> {
            if (!apps.start(i)) host.toast("This TV couldn't open " + label);
        }));
    }

    private void about() {
        String version = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        add(rows.info("ZeroPulse TV", version, Theme.SOFT_WHITE));
        add(rows.info("Device", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")", Theme.SOFT_WHITE));
        add(rows.info("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", Theme.SOFT_WHITE));
        add(rows.action("What's new", "The changes in this version", false, v -> host.open(new WhatsNewPanel(host))));
        add(rows.action("About ZeroPulse TV", "Licenses & recovery", false, v -> host.open(new AboutPanel(host))));
    }

    // ------------------------------------------------------------------ navigation

    @Override
    public void focusFirst() {
        categoryViews.get(selected).requestFocus();
    }

    @Override
    public boolean onBack() {
        if (rows.endEditing()) return true;               // Back first ends a change in progress, and keeps what was chosen
        View f = root.findFocus();
        if (f != null && f.getParent() == rowList) {
            categoryViews.get(selected).requestFocus();
            return true;
        }
        return false;
    }

    @Override
    public void onUncovered() {
        if (selected == 2) showCategory(selected);   // dock list may have changed
    }
}
