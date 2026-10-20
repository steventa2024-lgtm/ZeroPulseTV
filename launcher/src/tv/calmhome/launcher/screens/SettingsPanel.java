package tv.calmhome.launcher.screens;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tv.calmhome.launcher.assistant.PairingInfo;
import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse settings: categories on the left, rows on the right. Moving focus over a category previews
 * it; OK or Right enters it; Back (or Left on a non-choice row) returns to the categories.
 */
public class SettingsPanel extends Panel {

    static final String[] CATEGORIES = {"Appearance", "Wallpapers", "Dock & Apps", "Assistant", "System Shortcuts", "About"};
    static final Glyph.Kind[] ICONS = {Glyph.Kind.GEAR, Glyph.Kind.IMAGE, Glyph.Kind.DOCK, Glyph.Kind.ASSISTANT,
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
        rowScroll = new ScrollView(c);
        rowScroll.setVerticalScrollBarEnabled(false);
        rowScroll.addView(rowList);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        sl.topMargin = t.dp(10);
        right.addView(rowScroll, sl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f);
        rl.leftMargin = t.dp(20);
        body.addView(right, rl);

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
        final LinearLayout item = new LinearLayout(c);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(t.dp(14), t.dp(11), t.dp(14), t.dp(11));
        item.setFocusable(true);
        item.setClickable(true);
        item.setContentDescription(CATEGORIES[i]);
        final Glyph g = new Glyph(ICONS[i], Theme.SOFT_WHITE);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(g);
        item.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView l = t.text(c, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        l.setText(CATEGORIES[i]);
        LinearLayout.LayoutParams ll = w.wrap();
        ll.leftMargin = t.dp(12);
        item.addView(l, ll);
        item.setOnFocusChangeListener((v, has) -> {
            if (has && selected != i) showCategory(i);
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
        rowList.removeAllViews();
        switch (i) {
            case 0: appearance(); break;
            case 1: wallpapers(); break;
            case 2: dock(); break;
            case 3: assistant(); break;
            case 4: shortcuts(); break;
            default: about(); break;
        }
        rowScroll.scrollTo(0, 0);
        paintCategories();
    }

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
        add(rows.toggle("Show greeting", prefs.showGreeting(), v -> { prefs.setShowGreeting(v); host.refreshHome(); }));
        add(rows.toggle("Show clock", prefs.showClock(), v -> { prefs.setShowClock(v); host.refreshHome(); }));
        String[] dims = {"0%", "10%", "20%", "30%", "40%", "50%", "60%"};
        add(rows.choice("Wallpaper dimming", dims, prefs.dimPercent() / 10, k -> { prefs.setDimPercent(k * 10); host.refreshHome(); }));
        add(rows.choice("UI scale", Prefs.UI_SCALE_NAMES, prefs.uiScaleIndex(), k -> {
            prefs.setUiScaleIndex(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.choice("Focus glow", new String[]{"Low", "Medium", "High"}, prefs.focusIntensity(), k -> {
            prefs.setFocusIntensity(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.choice("Accent intensity", new String[]{"Subtle", "Standard", "Vivid"}, prefs.accentIntensity(), k -> {
            prefs.setAccentIntensity(k);
            host.rebuild("settings:appearance");
        }));
        add(rows.toggle("Reduced motion", prefs.reducedMotion(), v -> {
            prefs.setReducedMotion(v);
            host.rebuild("settings:appearance");
        }));
        note("Theme: Midnight Blue (the only theme in this version).");
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
        note("Photo credits always stay on Home: the Creative Commons licenses require attribution.");
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

    private void assistant() {
        add(rows.toggle("Assistant", prefs.assistantEnabled(), v -> { prefs.setAssistantEnabled(v); host.refreshHome(); }));
        add(rows.toggle("Reactor animation", prefs.reactorAnimation(), prefs::setReactorAnimation));
        String pc = PairingInfo.pairedPc(host.context());
        add(rows.info("JARVIS PC", pc != null ? "Paired · " + pc : "Not paired", pc != null ? Theme.SUCCESS : Theme.MUTED_GRAY));
        add(rows.info("OpenPulse TV link", pc != null ? "TLS 1.3, certificate pinned" : "Off", pc != null ? Theme.SOFT_WHITE : Theme.MUTED_GRAY));
        add(rows.info("Connection", "Only while the Assistant is open", Theme.SOFT_WHITE));
        add(rows.info("Offline behaviour", "Local TV commands always work", Theme.SOFT_WHITE));
        add(rows.action("Open Assistant", null, false, v -> host.openAssistant()));
        note("Pair, disconnect or forget the PC in the Assistant's JARVIS panel. On the PC, run  openpulse tv invite  "
                + "for a pairing code; the TV is approved on the PC.");
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
        String version = "?";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Exception ignored) { }
        add(rows.info("ZeroPulse TV", version, Theme.SOFT_WHITE));
        add(rows.info("Device", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")", Theme.SOFT_WHITE));
        add(rows.info("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", Theme.SOFT_WHITE));
        add(rows.action("About ZeroPulse TV", "Licenses & recovery", false, v -> host.open(new AboutPanel(host))));
    }

    // ------------------------------------------------------------------ navigation

    @Override
    public void focusFirst() {
        categoryViews.get(selected).requestFocus();
    }

    @Override
    public boolean onBack() {
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
