package tv.calmhome.launcher.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.core.SourceEntry.Category;
import tv.calmhome.launcher.data.AppArt;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.downloads.DownloadPolicy;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Native, device-backed Download Center. No sample downloads or preconfigured APK links. */
public final class DownloadsPanel extends DownloadPage {
    private static final String EXTERNAL_DOWNLOADER = "com.esaba.downloader";
    // Editorial picks only. Every tile must also resolve to an installed, launchable package.
    private static final List<String> FEATURED_PACKAGES = Arrays.asList("com.netflix.ninja",
            "com.google.android.youtube.tv", "com.disney.disneyplus", "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android", "tv.pluto.android", "com.tubitv", "org.videolan.vlc", "org.xbmc.kodi", "com.plexapp.android");
    private EditText input;
    private TextView message;
    private ActiveDownloads queue;
    private LinearLayout shelf;
    private List<String> shownPackages;
    private AppArt art;

    public DownloadsPanel(Host host) { super(host); }
    public static Panel detail(Host host, long id) { return new DownloadDetailPanel(host, id); }
    public static Panel library(Host host) { return new DownloadLibraryPanel(host); }

    @Override protected View build() {
        LinearLayout intro = new LinearLayout(c);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.addView(text("Get apps, games, and streaming apps on your TV.", Theme.T_BODY, false),
                new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        // Launch the integrated ZPAPPS curated TV app & shortcode toolbox directly.
        View zpapps = compactButton("ZPAPPS Toolbox", Glyph.Kind.APPS,
                () -> host.open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(host)));
        zpapps.setContentDescription("Open ZPAPPS Toolbox");
        LinearLayout.LayoutParams zpLp = new LinearLayout.LayoutParams(Widgets.WRAP, glass() ? t.dp(30) : Widgets.WRAP);
        zpLp.rightMargin = t.dp(8);
        intro.addView(zpapps, zpLp);
        // The official Downloader by AFTVnews: open it, get it from the Play Store, send it one of your codes.
        View downloader = compactButton("Downloader", Glyph.Kind.DOWNLOAD, () -> host.open(new DownloaderPanel(host)));
        downloader.setContentDescription("Open Downloader");
        intro.addView(downloader, new LinearLayout.LayoutParams(Widgets.WRAP, glass() ? t.dp(30) : Widgets.WRAP));
        body.addView(intro);
        makeInput();
        makeCategories();
        queue = new ActiveDownloads(this);
        body.addView(queue.view, spaced(8));
        makeShelf();
        makeDock();
        return screen("Download Center");
    }

    private void makeInput() {
        if (glass()) {
            // A capsule of the approved glass holding the field and its Go button; the glass lights while either has focus (no ring).
            input = field("Enter a code or HTTPS link...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 8192);
            input.setImeOptions(EditorInfo.IME_ACTION_GO);
            input.setOnEditorActionListener((v, id, e) -> {
                if (id == EditorInfo.IME_ACTION_GO) { go(); return true; }
                return false;
            });
            View goButton = w.glassPillButton(Glyph.Kind.ARROW, null, Theme.T_LABEL, v -> go());
            goButton.setContentDescription("Go");
            LinearLayout trailing = new LinearLayout(c);
            trailing.addView(goButton, new LinearLayout.LayoutParams(t.dp(46), t.dp(30)));
            View bar = w.glassField(input, icon(Glyph.Kind.LINK), trailing);
            body.addView(bar, spaced(5));
            message = text("", Theme.T_LABEL, false);
            message.setVisibility(View.GONE);
            message.setPadding(t.dp(14), t.dp(4), 0, 0);
            body.addView(message);
            return;
        }
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(t.dp(14), t.dp(3), t.dp(4), t.dp(3));
        bar.setBackground(t.glass(Theme.R_PILL, .84f));
        bar.addView(icon(Glyph.Kind.LINK), new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        input = field("Enter a code or HTTPS link...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 8192);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_GO) { go(); return true; }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        ImageView go = icon(Glyph.Kind.ARROW, Theme.DEEP_BLACK);
        go.setPadding(t.dp(6), t.dp(6), t.dp(6), t.dp(6));
        go.setBackground(t.rounded(Theme.SOFT_WHITE, Theme.R_PILL));
        go.setContentDescription("Go");
        w.focusable(go, Theme.R_PILL, null, true);
        go.setOnClickListener(v -> go());
        bar.addView(go, new LinearLayout.LayoutParams(t.dp(30), t.dp(30)));
        body.addView(bar, spaced(5));
        message = text("", Theme.T_LABEL, false);
        message.setVisibility(View.GONE);
        body.addView(message);
    }

    private void makeCategories() {
        LinearLayout cards = strip();
        for (Category category : Category.values()) {
            if (category == Category.ANIME) continue; // retained in All Categories for existing catalogs
            categoryCard(cards, category.title, categoryIcon(category), () -> host.open(new SourceCategoryPanel(host, category)));
        }
        categoryCard(cards, "All Categories", Glyph.Kind.GRID, () -> host.open(new SourcesPanel(host, "All Categories")));
        body.addView(cards, spaced(8));
    }

    private void categoryCard(LinearLayout row, String name, Glyph.Kind symbol, Runnable open) {
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.addView(icon(symbol), new LinearLayout.LayoutParams(t.dp(25), t.dp(25)));
        TextView label = text(name, Theme.T_NAV, true);
        label.setSingleLine();
        label.setGravity(Gravity.CENTER);
        label.setPadding(t.dp(4), t.dp(3), t.dp(4), 0);
        inner.addView(label);
        // Liquid Glass: a card of the approved glass whose icon and name lift a little on focus (the glass itself never scales).
        View glassCard = w.glassButton(inner, tv.calmhome.launcher.ui.GraphiteGlassSurface.Shape.CARD, true, 1.04f);
        final View card;
        if (glassCard != null) {
            card = glassCard;
        } else {
            inner.setBackground(t.glass(Theme.R_PANEL, .82f));
            w.focusable(inner, Theme.R_PANEL, null, true);
            card = inner;
        }
        card.setContentDescription(name + ", browse sources");
        card.setFocusableInTouchMode(true);
        card.setOnClickListener(v -> open.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, t.dp(71), 1);
        if (row.getChildCount() > 0) lp.leftMargin = t.dp(7);
        row.addView(card, lp);
        if (first == null) first = card;
    }

    private Glyph.Kind categoryIcon(Category category) {
        switch (category) {
            case APPS: return Glyph.Kind.APPS;
            case MOVIES: return Glyph.Kind.PLAY;
            case SPORTS: return Glyph.Kind.TV;
            case GAMES: return Glyph.Kind.GAMES;
            case TOOLS: return Glyph.Kind.LAYERS;
            default: return Glyph.Kind.GRID;
        }
    }

    private void makeShelf() {
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Trending Apps", Theme.T_NAV, true));
        TextView installed = text("   ·   Installed on this TV", Theme.T_CAPTION, false);
        header.addView(installed);
        body.addView(header, spaced(6));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(!t.reducedMotion);
        if (!glass()) scroll.setBackground(t.glass(Theme.R_PANEL, .78f));       // Liquid Glass: the glass tiles sit on the scene, as on Home
        scroll.setPadding(t.dp(glass() ? 4 : 9), t.dp(6), t.dp(9), t.dp(6));
        shelf = strip();
        shelf.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(shelf);
        body.addView(scroll, spaced(3));
        refreshShelf();
        scroll.getViewTreeObserver().addOnWindowFocusChangeListener(has -> {
            if (has && shelf.isShown()) {
                refreshShelf();
                if (queue != null) queue.refresh();
            }
        });
    }

    private void refreshShelf() {
        List<String> packages = new ArrayList<>();
        for (String pkg : FEATURED_PACKAGES) if (host.apps().usable(pkg)) packages.add(pkg);
        if (packages.equals(shownPackages)) return;
        Object restore = shelf.findFocus() == null ? null : shelf.findFocus().getTag();
        shownPackages = packages;
        shelf.removeAllViews();
        if (packages.isEmpty()) shelf.addView(text("No featured apps installed on this TV.", Theme.T_LABEL, false));
        int usableWidth = t.screenW() - 2 * t.safeX() - t.dp(33) - t.dp(43);
        int tileWidth = Math.max(t.dp(52), Math.min(t.dp(88), usableWidth / Math.max(1, packages.size()) - t.dp(7)));
        if (art == null) art = new AppArt(c, host.apps());
        art.clear();
        final int tileHeight = t.dp(43);
        for (String pkg : packages) {
            if (glass()) {
                // The same glass tile and artwork as Home's Favorites, at this row's size.
                View.OnClickListener launch = v -> {
                    if (!host.apps().launch(pkg)) { host.toast("This app is no longer available."); refreshShelf(); }
                };
                LinearLayout col = w.glassAppTilePx(pkg, art.get(pkg, tileWidth, tileHeight), host.apps().label(pkg), tileWidth, tileHeight, false,
                        launch, null);
                View card = Widgets.cardOf(col);
                card.setTag(pkg);
                card.setContentDescription("Open " + host.apps().label(pkg));
                col.removeViewAt(1);                                  // no name line under these small tiles: the row is one strip
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileWidth, tileHeight);
                lp.rightMargin = t.dp(8);
                shelf.addView(col, lp);
                continue;
            }
            ImageView tile = new ImageView(c);
            // The app's own banner or icon, filling the tile like Home's tiles do. It is rendered at the tile's own size, so it is never
            // scaled again (sharp, not stretched); the tile's rounded outline clips its corners.
            tile.setScaleType(ImageView.ScaleType.CENTER);
            tile.setImageDrawable(new BitmapDrawable(c.getResources(), art.get(pkg, tileWidth, tileHeight).bitmap));
            tile.setTag(pkg);
            tile.setContentDescription("Open " + host.apps().label(pkg));
            tile.setPadding(0, 0, 0, 0);
            tile.setBackground(t.glass(10, .65f));
            w.focusable(tile, 10, null, true);
            tile.setOnClickListener(v -> {
                if (!host.apps().launch(pkg)) { host.toast("This app is no longer available."); refreshShelf(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileWidth, tileHeight);
            lp.rightMargin = t.dp(7);
            shelf.addView(tile, lp);
        }
        ImageView plusIcon = icon(Glyph.Kind.PLUS);
        plusIcon.setPadding(t.dp(11), t.dp(11), t.dp(11), t.dp(11));
        View glassPlus = w.glassButton(plusIcon, tv.calmhome.launcher.ui.GraphiteGlassSurface.Shape.CARD, true, 1.06f);
        final View plus;
        if (glassPlus != null) {
            plus = glassPlus;
        } else {
            plusIcon.setBackground(t.glass(10, .72f));
            plusIcon.setPadding(t.dp(9), t.dp(9), t.dp(9), t.dp(9));
            w.focusable(plusIcon, 10, null, true);
            plus = plusIcon;
        }
        plus.setTag("all-apps");
        plus.setContentDescription("All installed apps");
        plus.setOnClickListener(v -> host.navigate("Apps"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(43), t.dp(43));
        if (packages.isEmpty()) lp.leftMargin = t.dp(12);
        shelf.addView(plus, lp);
        if (restore != null) {
            View previous = shelf.findViewWithTag(restore);
            (previous == null ? plus : previous).requestFocus();
        }
    }

    private void makeDock() {
        if (glass()) {
            // Four capsules of the approved glass, evenly across the row, like Home's quick actions (no strip with dividers).
            LinearLayout row = strip();
            String[] names = {"Install APK", "File Manager", "App Sources", "Manage Downloads"};
            Glyph.Kind[] icons = {Glyph.Kind.APK, Glyph.Kind.FOLDER, Glyph.Kind.LAYERS, Glyph.Kind.DOWNLOAD};
            Runnable[] actions = {() -> host.open(new ApkFilesPanel(host, true)), () -> host.open(new ApkFilesPanel(host, false)),
                    () -> host.open(new SourcesPanel(host, "App Sources")), () -> host.open(library(host))};
            for (int i = 0; i < names.length; i++) {
                final Runnable a = actions[i];
                View b = w.glassPillButton(icons[i], names[i], Theme.T_LABEL, v -> a.run());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, t.dp(34), 1);
                if (i > 0) lp.leftMargin = t.dp(10);
                row.addView(b, lp);
            }
            body.addView(row, spaced(8));
            return;
        }
        LinearLayout dock = strip();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(t.glass(Theme.R_PILL, .88f));
        dock.setPadding(t.dp(4), t.dp(3), t.dp(4), t.dp(3));
        dockItem(dock, "Install APK", Glyph.Kind.APK, () -> host.open(new ApkFilesPanel(host, true)));
        dockItem(dock, "File Manager", Glyph.Kind.FOLDER, () -> host.open(new ApkFilesPanel(host, false)));
        dockItem(dock, "App Sources", Glyph.Kind.LAYERS, () -> host.open(new SourcesPanel(host, "App Sources")));
        dockItem(dock, "Manage Downloads", Glyph.Kind.DOWNLOAD, () -> host.open(library(host)));
        body.addView(dock, spaced(7));
    }

    private void dockItem(LinearLayout dock, String name, Glyph.Kind symbol, Runnable open) {
        if (dock.getChildCount() > 0) {
            View line = new View(c);
            line.setBackgroundColor(Theme.withAlpha(Theme.SOFT_WHITE, .30f));
            dock.addView(line, new LinearLayout.LayoutParams(t.dp(1), t.dp(15)));
        }
        LinearLayout item = new LinearLayout(c);
        item.setGravity(Gravity.CENTER);
        item.setBackground(t.rounded(android.graphics.Color.TRANSPARENT, Theme.R_PILL));
        item.addView(icon(symbol), new LinearLayout.LayoutParams(t.dp(16), t.dp(16)));
        TextView label = text(name, Theme.T_LABEL, true);
        label.setSingleLine(); label.setPadding(t.dp(5), 0, 0, 0);
        item.addView(label);
        item.setContentDescription(name);
        w.focusable(item, Theme.R_PILL, null, true);
        item.setOnClickListener(v -> open.run());
        dock.addView(item, new LinearLayout.LayoutParams(0, t.dp(31), 1));
    }

    private View compactButton(String label, Glyph.Kind symbol, Runnable open) {
        if (glass()) return w.glassPillButton(symbol, label, Theme.T_LABEL, v -> open.run());
        TextView v = (TextView) action(label, open);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Theme.T_LABEL * t.scale);
        v.setPadding(t.dp(7), t.dp(3), t.dp(7), t.dp(3));
        Glyph icon = new Glyph(symbol, Theme.SOFT_WHITE);
        icon.setBounds(0, 0, t.dp(15), t.dp(15));
        v.setCompoundDrawablesRelative(icon, null, null, null);
        v.setCompoundDrawablePadding(t.dp(4));
        return v;
    }

    private boolean glass() {
        return t.liquid && t.kit != null;
    }

    private LinearLayout strip() {
        LinearLayout row = new LinearLayout(c);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }
    private ImageView icon(Glyph.Kind symbol) { return icon(symbol, Theme.SOFT_WHITE); }
    private ImageView icon(Glyph.Kind symbol, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new Glyph(symbol, color));
        return v;
    }

    @Override public void onUncovered() {
        refreshShelf();
        queue.refresh();
    }
    @Override public void onClosed() { queue.close(); }
    @Override public void onPackagesChanged() { if (shelf != null) refreshShelf(); }

    private void go() {
        String value = input.getText().toString().trim();
        message.setVisibility(View.GONE);
        try {
            if (SourceEntry.isCode(value)) {
                dismissKeyboard();
                SourceEntry entry = SourceEntry.byCode(new SourceCatalog(c).all(), value);
                if (entry != null) host.open(new SourceReviewPanel(host, entry.url, entry.sha256, entry.label));
                else external(value);
            } else {
                String url = DownloadPolicy.validateUrl(value).toASCIIString();
                dismissKeyboard();
                host.open(new SourceReviewPanel(host, url, "", "Direct HTTPS link"));
            }
        } catch (IllegalArgumentException e) {
            message.setVisibility(View.VISIBLE);
            showError(message, e);
            input.requestFocus();
        }
    }

    private void external(String code) {
        boolean installed = host.apps().usable(EXTERNAL_DOWNLOADER);
        MenuPanel menu = new MenuPanel(host, "Unmapped code", "No ZeroPulse source uses " + code +
                ". AFTVnews codes can be entered in its Downloader app." + (installed ? "" : " Downloader is unavailable on this TV."));
        Runnable copy = () -> {
            ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Downloader code", code));
        };
        if (installed) menu.item("Send code to Downloader", () -> {
            tv.calmhome.launcher.aftv.OfficialDownloaderLauncher.Outcome o =
                    tv.calmhome.launcher.aftv.OfficialDownloaderLauncher.sendCode(c, code);
            host.toast(tv.calmhome.launcher.aftv.OfficialDownloaderLauncher.message(o, code));
        });
        if (installed) menu.item("Copy code and open Downloader", () -> {
            copy.run();
            if (!host.apps().launch(EXTERNAL_DOWNLOADER)) host.toast("Code copied. Downloader could not open.");
        });
        menu.item("Copy code", () -> { copy.run(); host.toast("Code copied"); });
        menu.item("Back", null);
        host.open(menu);
    }
}
