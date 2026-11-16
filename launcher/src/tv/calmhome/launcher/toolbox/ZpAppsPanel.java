package tv.calmhome.launcher.toolbox;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.data.AppArt;
import tv.calmhome.launcher.screens.MenuPanel;
import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.ui.ContextScrollView;
import tv.calmhome.launcher.ui.GlassProgressBar;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.SafeMarginScrollView;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.TileArtView;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Native pure-Java ZPAPPS TV Toolbox inside ZeroPulse TV, rendered strictly with
 * unscaled Liquid Glass surfaces ({@link GraphiteGlassSurface}, {@link ZeroPulseGlassRow},
 * {@link ZeroPulseGlassAppTile}) and supporting Home shelves, 20-app grid & category filters,
 * live TV telemetry & APK cache purge, 3x4 D-pad shortcode keypad, Essentials Trio batch install,
 * Web Directory extraction, App Manage menu, and post-install APK cleanup dialog.
 */
public final class ZpAppsPanel extends Panel {

    private static final int GRID_COLUMNS = 4;

    private final ToolboxEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ToolboxCatalog.Catalog catalog;
    private final Map<String, Boolean> installedMap = new HashMap<>();

    private boolean gridMode = false;
    private String selectedCategory = "All";
    private String searchQuery = "";

    // Post-install cleanup + Essentials Trio batch state
    private File pendingCleanupFile;
    private String pendingCleanupAppName;
    private final List<ToolboxCatalog.AppEntry> essentialsQueue = new ArrayList<>();
    private int essentialsTotal = 0;

    // UI references
    private LinearLayout toolbarRow, filterRow, progressCard, contentArea;
    private TextView telemetryText, progressTitle, gridSubtitle;
    private GlassProgressBar progressBar;
    private EditText searchInput;
    private GridLayout gridLayout;
    private ScrollView mainScroll;
    private View discoverBtn, gridBtn, purgeBtn, firstFocusable;
    private boolean onboardingPrompted = false;

    public ZpAppsPanel(Host host) {
        super(host);
        this.engine = ToolboxEngine.get(c);
        this.catalog = ToolboxCatalog.load(c);
    }

    @Override
    public String restoreRoute() {
        return "zpapps";
    }

    @Override
    protected View build() {
        refreshInstalledMap();

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);

        // Top Toolbox Action Toolbar
        toolbarRow = new LinearLayout(c);
        toolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarRow.setClipChildren(false);
        toolbarRow.setClipToPadding(false);

        discoverBtn = toolbarPill("Discover", true, v -> {
            if (gridMode) {
                gridMode = false;
                syncModePills();
                rebuildContent();
            }
        });
        gridBtn = toolbarPill("All 20 Apps", true, v -> {
            if (!gridMode) {
                gridMode = true;
                syncModePills();
                rebuildContent();
            }
        });
        View codeBtn = toolbarPill("+ Code Keypad", false, v -> openCodeModal());
        View trioBtn = toolbarPill("Essentials (3)", false, v -> startEssentialsTrio());
        View syncBtn = toolbarPill("Sync URL", false, v -> openSyncModal());
        View guideBtn = toolbarPill("Guide", false, v -> host.open(new WelcomeGuideModalPanel(host, false)));

        addToolbarPill(discoverBtn, false);
        addToolbarPill(gridBtn, true);
        addToolbarPill(codeBtn, true);
        addToolbarPill(trioBtn, true);
        addToolbarPill(syncBtn, true);
        addToolbarPill(guideBtn, true);
        firstFocusable = discoverBtn;
        body.addView(toolbarRow);

        // Live Telemetry & Storage Cleaner Bar
        body.addView(buildTelemetryBar(), spacedTop(10));

        // Active Download / Batch Progress Banner (hidden until active)
        progressCard = buildProgressBanner();
        progressCard.setVisibility(View.GONE);
        body.addView(progressCard, spacedTop(10));

        // Main Scrollable Area (Discover Shelves OR All-Apps Grid)
        mainScroll = new ContextScrollView(c, t.dp(40));
        mainScroll.setClipChildren(false);
        mainScroll.setClipToPadding(false);
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.setSmoothScrollingEnabled(!t.reducedMotion);
        mainScroll.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        mainScroll.setClipToOutline(true);

        contentArea = new LinearLayout(c);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setClipChildren(false);
        contentArea.setClipToPadding(false);
        contentArea.setPadding(t.dp(4), t.dp(10), t.dp(4), t.dp(32));
        mainScroll.addView(contentArea);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        slp.topMargin = t.dp(8);
        body.addView(mainScroll, slp);

        syncModePills();
        refreshTelemetry();
        rebuildContent();

        if (!engine.isOnboardingCompleted() && !onboardingPrompted) {
            onboardingPrompted = true;
            main.post(() -> {
                if (view().isAttachedToWindow()) {
                    host.open(new WelcomeGuideModalPanel(host, false));
                }
            });
        }

        return frame("ZPAPPS Toolbox", body);
    }

    private void addToolbarPill(View pill, boolean spaceBefore) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(34));
        if (spaceBefore) lp.leftMargin = t.dp(8);
        toolbarRow.addView(pill, lp);
    }

    private View buildTelemetryBar() {
        boolean glass = t.liquid && t.kit != null;
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(t.dp(14), t.dp(6), t.dp(8), t.dp(6));

        telemetryText = t.text(c, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.90f), Theme.FONT_REGULAR);
        telemetryText.setSingleLine(true);
        telemetryText.setEllipsize(TextUtils.TruncateAt.END);
        inner.addView(telemetryText, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));

        purgeBtn = compactPill("Purge APK Cache", v -> {
            int deleted = engine.purgeCachedApks();
            refreshTelemetry();
            host.toast(deleted > 0
                    ? "Removed " + deleted + " leftover installer file" + (deleted == 1 ? "" : "s") + "."
                    : "APK cache is already clean.");
        });
        inner.addView(purgeBtn, new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(30)));

        inner.setBackground(t.glass(Theme.R_PILL, glass ? 0.72f : 0.84f));
        return inner;
    }

    private LinearLayout buildProgressBanner() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
        box.setBackground(t.glass(Theme.R_PANEL, 0.88f));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        progressTitle = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        top.addView(progressTitle, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        View cancelBtn = compactPill("Cancel", v -> {
            engine.cancelActive();
            essentialsQueue.clear();
            progressCard.setVisibility(View.GONE);
            host.toast("Download canceled.");
        });
        top.addView(cancelBtn, new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(30)));
        box.addView(top);

        progressBar = new GlassProgressBar(c);
        progressBar.setReducedMotion(t.reducedMotion);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(8));
        blp.topMargin = t.dp(8);
        box.addView(progressBar, blp);
        return box;
    }

    private void rebuildContent() {
        contentArea.removeAllViews();
        if (gridMode) {
            buildGridView();
        } else {
            buildDiscoverShelves();
        }
    }

    // ------------------------------------------------------------------ Discover Shelves View

    private void buildDiscoverShelves() {
        ToolboxCatalog.AppEntry spotlight = catalog.byId("ak47sports");
        if (spotlight == null) spotlight = catalog.byId("stremio");
        if (spotlight == null && !catalog.apps.isEmpty()) spotlight = catalog.apps.get(0);

        if (spotlight != null) {
            contentArea.addView(buildHeroSpotlight(spotlight));
        }

        List<ToolboxCatalog.AppEntry> installed = new ArrayList<>();
        for (ToolboxCatalog.AppEntry a : catalog.apps) {
            if (Boolean.TRUE.equals(installedMap.get(a.packageName))) installed.add(a);
        }
        if (!installed.isEmpty()) {
            addHorizontalShelf("Installed on This TV (" + installed.size() + ")", installed);
        }

        for (String category : catalog.categories) {
            if ("All".equalsIgnoreCase(category)) continue;
            List<ToolboxCatalog.AppEntry> slice = new ArrayList<>();
            for (ToolboxCatalog.AppEntry a : catalog.apps) {
                if (category.equalsIgnoreCase(a.category)) slice.add(a);
            }
            if (!slice.isEmpty()) addHorizontalShelf(category, slice);
        }
    }

    private View buildHeroSpotlight(final ToolboxCatalog.AppEntry app) {
        boolean installed = Boolean.TRUE.equals(installedMap.get(app.packageName));
        boolean glass = t.liquid && t.kit != null;

        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(t.dp(20), t.dp(16), t.dp(20), t.dp(16));

        int wPx = t.dp(118);
        int hPx = t.dp(72);
        ZeroPulseGlassAppTile emblemTile = null;
        if (glass) {
            emblemTile = new ZeroPulseGlassAppTile(c, t.kit, hPx, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS);
            emblemTile.setFocusable(false);
            emblemTile.setClickable(false);
            emblemTile.setContent(new TileArtView(c, resolveInitialArt(app, wPx, hPx, installed)));
            inner.addView(emblemTile, new LinearLayout.LayoutParams(wPx, hPx));
        }

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(glass ? t.dp(18) : 0, 0, t.dp(12), 0);

        TextView badge = t.text(c, Theme.T_CAPTION, 0xFF9CD4FF, Theme.FONT_MEDIUM);
        badge.setText("FEATURED SPOTLIGHT   ·   " + app.category.toUpperCase(Locale.ROOT)
                + (app.code.isEmpty() ? "" : "   ·   CODE #" + app.code));
        info.addView(badge);

        TextView name = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        name.setText(app.name);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        nlp.topMargin = t.dp(2);
        info.addView(name, nlp);

        TextView desc = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        desc.setText(app.tagline);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        dlp.topMargin = t.dp(3);
        info.addView(desc, dlp);
        inner.addView(info, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        String primaryText = installed ? "Open " + app.name : ("Install #" + app.code);
        View primaryBtn = toolbarPill(primaryText, false, v -> handleAppClick(app));
        View browseBtn = toolbarPill("Browse All " + catalog.apps.size(), false, v -> {
            gridMode = true;
            syncModePills();
            rebuildContent();
        });
        actions.addView(primaryBtn, new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(36)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(36));
        blp.leftMargin = t.dp(8);
        actions.addView(browseBtn, blp);
        inner.addView(actions);

        if (emblemTile != null && !installed) {
            final ZeroPulseGlassAppTile tileRef = emblemTile;
            engine.loadIconTile(app, wPx, hPx, bmp -> {
                if (tileRef.isAttachedToWindow()) {
                    tileRef.setContent(new TileArtView(c,
                            AppArt.fromIcon(new BitmapDrawable(c.getResources(), bmp), "zpapps", wPx, hPx)));
                }
            });
        }

        inner.setBackground(t.glass(Theme.R_PANEL, glass ? 0.78f : 0.85f));
        return inner;
    }

    private void addHorizontalShelf(String title, List<ToolboxCatalog.AppEntry> entries) {
        TextView head = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        head.setText(title + "   ·   " + entries.size());
        head.setPadding(t.dp(2), t.dp(16), 0, t.dp(6));
        contentArea.addView(head);

        SafeMarginScrollView scroller = new SafeMarginScrollView(c);
        scroller.setCarousel(!t.reducedMotion, tv.calmhome.launcher.core.MotionTokens.CAROUSEL_MS);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(t.dp(4), t.dp(6), t.dp(12), t.dp(6));

        LinearLayout strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);

        int tileWdp = 176;
        int tileHdp = 98;
        for (int i = 0; i < entries.size(); i++) {
            ToolboxCatalog.AppEntry app = entries.get(i);
            View tileCol = buildCatalogTile(app, tileWdp, tileHdp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(tileWdp), Widgets.WRAP);
            if (i > 0) lp.leftMargin = t.dp(14);
            strip.addView(tileCol, lp);
        }
        scroller.addView(strip);
        contentArea.addView(scroller, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
    }

    // ------------------------------------------------------------------ All 20 Apps Grid View

    private void buildGridView() {
        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setClipChildren(false);

        searchInput = new EditText(c);
        t.style(searchInput, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        searchInput.setSingleLine(true);
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setHint("Search all 20 toolbox apps or shortcodes…");
        searchInput.setHintTextColor(Theme.MUTED_GRAY);
        searchInput.setText(searchQuery);
        searchInput.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager ime = c.getSystemService(InputMethodManager.class);
            if (ime != null) ime.hideSoftInputFromWindow(v.getWindowToken(), 0);
            if (gridLayout.getChildCount() > 0) Widgets.cardOf(gridLayout.getChildAt(0)).requestFocus();
            return true;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                populateGrid();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        boolean glass = t.liquid && t.kit != null;
        View fieldWrap = glass ? w.glassField(searchInput, null, null) : null;
        if (fieldWrap == null) {
            searchInput.setBackground(t.glass(Theme.R_PILL, 0.84f));
            fieldWrap = searchInput;
        }
        controls.addView(fieldWrap, new LinearLayout.LayoutParams(Widgets.MATCH, glass ? t.dp(38) : Widgets.WRAP));

        SafeMarginScrollView catScroll = new SafeMarginScrollView(c);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setClipChildren(false);
        catScroll.setClipToPadding(false);
        filterRow = new LinearLayout(c);
        filterRow.setClipChildren(false);
        filterRow.setClipToPadding(false);
        for (int i = 0; i < catalog.categories.size(); i++) {
            final String cat = catalog.categories.get(i);
            View catBtn = toolbarPill(cat, true, v -> {
                selectedCategory = cat;
                syncCategoryPills();
                populateGrid();
            });
            catBtn.setTag(cat);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(32));
            if (i > 0) clp.leftMargin = t.dp(8);
            filterRow.addView(catBtn, clp);
        }
        catScroll.addView(filterRow);
        LinearLayout.LayoutParams fslp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        fslp.topMargin = t.dp(10);
        controls.addView(catScroll, fslp);

        gridSubtitle = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        LinearLayout.LayoutParams gslp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        gslp.topMargin = t.dp(8);
        controls.addView(gridSubtitle, gslp);

        contentArea.addView(controls);

        gridLayout = new GridLayout(c);
        gridLayout.setColumnCount(GRID_COLUMNS);
        gridLayout.setClipChildren(false);
        gridLayout.setPadding(t.dp(2), t.dp(12), t.dp(2), t.dp(20));
        contentArea.addView(gridLayout, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        syncCategoryPills();
        populateGrid();
    }

    private void populateGrid() {
        if (gridLayout == null) return;
        gridLayout.removeAllViews();

        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<ToolboxCatalog.AppEntry> filtered = new ArrayList<>();
        for (ToolboxCatalog.AppEntry app : catalog.apps) {
            if (!"All".equalsIgnoreCase(selectedCategory) && !selectedCategory.equalsIgnoreCase(app.category)) continue;
            if (!q.isEmpty()) {
                boolean match = app.name.toLowerCase(Locale.ROOT).contains(q)
                        || app.tagline.toLowerCase(Locale.ROOT).contains(q)
                        || app.code.toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
            }
            filtered.add(app);
        }

        gridSubtitle.setText(filtered.isEmpty()
                ? "No apps match your search or category filter."
                : filtered.size() + " app" + (filtered.size() == 1 ? "" : "s") + "   ·   OK installs or opens   ·   Hold OK for options");

        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(20)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gapDp = 16;
        int tileWdp = Math.max(150, Math.round((availDp - (GRID_COLUMNS - 1) * gapDp) / GRID_COLUMNS));
        int tileHdp = Math.round(tileWdp * 9f / 16f);

        for (int i = 0; i < filtered.size(); i++) {
            ToolboxCatalog.AppEntry app = filtered.get(i);
            View tile = buildCatalogTile(app, tileWdp, tileHdp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = t.dp(tileWdp);
            lp.setMargins(0, 0, (i % GRID_COLUMNS == GRID_COLUMNS - 1) ? 0 : t.dp(gapDp), t.dp(14));
            gridLayout.addView(tile, lp);
        }
    }

    // ------------------------------------------------------------------ Catalog Tile Builder

    private View buildCatalogTile(final ToolboxCatalog.AppEntry app, int tileWdp, int tileHdp) {
        final boolean installed = Boolean.TRUE.equals(installedMap.get(app.packageName));
        final int wPx = t.dp(tileWdp);
        final int hPx = t.dp(tileHdp);
        final String displayLabel = app.name + (installed ? "  ·  Installed" : (app.code.isEmpty() ? "" : "  ·  #" + app.code));

        View.OnClickListener click = v -> handleAppClick(app);
        View.OnLongClickListener longClick = v -> {
            openAppManageMenu(app);
            return true;
        };

        LinearLayout col;
        if (t.liquid && t.kit != null) {
            AppArt.Art initialArt = resolveInitialArt(app, wPx, hPx, installed);
            col = w.glassAppTilePx(app.packageName, initialArt, displayLabel, wPx, hPx, true, click, longClick);
            ZeroPulseGlassAppTile card = (ZeroPulseGlassAppTile) Widgets.cardOf(col);
            card.setTag(app.id);

            if (installed) {
                View dot = new View(c);
                dot.setBackground(t.rounded(Theme.SUCCESS, 4));
                dot.setElevation(t.dp(1));
                FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(t.dp(8), t.dp(8), Gravity.TOP | Gravity.END);
                dlp.setMargins(0, t.dp(8), t.dp(8), 0);
                card.addView(dot, dlp);
            } else {
                engine.loadIconTile(app, wPx, hPx, bmp -> {
                    if (card.isAttachedToWindow()) {
                        card.setContent(new TileArtView(c,
                                AppArt.fromIcon(new BitmapDrawable(c.getResources(), bmp), "zpapps", wPx, hPx)));
                    }
                });
            }
        } else {
            Drawable icon = installed ? host.apps().icon(app.packageName)
                    : new BitmapDrawable(c.getResources(), engine.monogramBadge(app, wPx, hPx));
            Drawable banner = installed ? host.apps().banner(app.packageName) : null;
            col = w.appTile(app.packageName, banner, icon, displayLabel, tileWdp, tileHdp, true, click, longClick);
            Widgets.cardOf(col).setTag(app.id);
        }
        return col;
    }

    private AppArt.Art resolveInitialArt(ToolboxCatalog.AppEntry app, int wPx, int hPx, boolean installed) {
        if (installed) {
            Drawable banner = host.apps().banner(app.packageName);
            if (banner != null) {
                AppArt.Art a = AppArt.fromBanner(banner, "pkg", wPx, hPx);
                if (a != null) return a;
            }
            Drawable icon = host.apps().icon(app.packageName);
            if (icon != null) {
                AppArt.Art a = AppArt.fromIcon(icon, "pkg", wPx, hPx);
                if (a != null) return a;
            }
        }
        Bitmap mono = engine.monogramBadge(app, wPx, hPx);
        return AppArt.fromIcon(new BitmapDrawable(c.getResources(), mono), "zpapps", wPx, hPx);
    }

    // ------------------------------------------------------------------ Actions, Download & Essentials Trio

    private void handleAppClick(ToolboxCatalog.AppEntry app) {
        if (Boolean.TRUE.equals(installedMap.get(app.packageName))) {
            if (!host.apps().launch(app.packageName)) {
                host.toast("Could not launch " + app.name);
            }
            return;
        }
        essentialsQueue.clear();
        essentialsTotal = 0;
        startDownload(app.installSource(), app.name);
    }

    private void startEssentialsTrio() {
        refreshInstalledMap();
        essentialsQueue.clear();
        for (String id : ToolboxCatalog.ESSENTIALS_TRIO_IDS) {
            ToolboxCatalog.AppEntry entry = catalog.byId(id);
            if (entry != null && !Boolean.TRUE.equals(installedMap.get(entry.packageName))) {
                essentialsQueue.add(entry);
            }
        }
        if (essentialsQueue.isEmpty()) {
            host.toast("All Essentials Trio apps (AdGuard TV, Wuffy Player, VLC) are already installed.");
            return;
        }
        essentialsTotal = essentialsQueue.size();
        advanceEssentialsQueue();
    }

    private void advanceEssentialsQueue() {
        if (essentialsQueue.isEmpty()) {
            essentialsTotal = 0;
            return;
        }
        ToolboxCatalog.AppEntry next = essentialsQueue.remove(0);
        int currentIdx = essentialsTotal - essentialsQueue.size();
        host.toast("Essentials Trio (" + currentIdx + "/" + essentialsTotal + "): " + next.name);
        startDownload(next.installSource(), next.name);
    }

    private void startDownload(final String source, final String appName) {
        if (!(c instanceof Activity)) {
            host.toast("Return to Launcher UI to install apps.");
            return;
        }
        if (!engine.canRequestPackageInstalls()) {
            host.open(new WelcomeGuideModalPanel(host, false));
            host.toast("Grant 'Install unknown apps' permission first.");
            return;
        }
        progressCard.setVisibility(View.VISIBLE);
        progressBar.setState(GlassProgressBar.State.RUNNING);
        progressBar.setIndeterminate();
        progressTitle.setText("Preparing " + appName + "…");

        engine.downloadAndInstall((Activity) c, source, appName, new ToolboxEngine.DownloadCallback() {
            @Override
            public void onProgress(int percent, String stage) {
                progressCard.setVisibility(View.VISIBLE);
                progressTitle.setText(stage);
                if (percent > 0 && percent < 100) {
                    progressBar.setProgress(percent / 100f);
                } else if (percent >= 100) {
                    progressBar.setProgress(1f);
                } else {
                    progressBar.setIndeterminate();
                }
            }

            @Override
            public void onWebDirectoryDetected(String url) {
                progressCard.setVisibility(View.GONE);
                host.open(new WebDirectoryModalPanel(host, url, apkUrl -> startDownload(apkUrl, appName)));
            }

            @Override
            public void onInstallerLaunched(File apkFile, String launchedName) {
                pendingCleanupFile = apkFile;
                pendingCleanupAppName = launchedName;
                progressCard.setVisibility(View.GONE);
                refreshTelemetry();
            }

            @Override
            public void onInstallOutcome(boolean success, String message) {
                progressCard.setVisibility(View.GONE);
                refreshInstalledMap();
                refreshTelemetry();
                rebuildContent();
                host.toast(message);
            }

            @Override
            public void onError(String message) {
                progressBar.setState(GlassProgressBar.State.FAILED);
                progressTitle.setText("Failed: " + message);
                host.toast(message);
                main.postDelayed(() -> progressCard.setVisibility(View.GONE), 3500);
            }
        });
    }

    private void openAppManageMenu(final ToolboxCatalog.AppEntry app) {
        final boolean installed = Boolean.TRUE.equals(installedMap.get(app.packageName));
        MenuPanel menu = new MenuPanel(host, app.name,
                app.category + (app.code.isEmpty() ? "" : "   ·   Shortcode #" + app.code));
        if (installed) {
            menu.item("Open " + app.name, () -> {
                if (!host.apps().launch(app.packageName)) host.toast("Could not open " + app.name);
            });
            menu.item("Reinstall / Update (#" + app.code + ")", () -> startDownload(app.installSource(), app.name));
            boolean inFavs = host.prefs().dock().contains(app.packageName);
            if (inFavs) {
                menu.item("Remove from Home Favorites", () -> {
                    host.prefs().setDock(DockList.remove(host.prefs().dock(), app.packageName));
                    host.refreshHome();
                    host.toast(app.name + " removed from Home Favorites.");
                });
            } else {
                menu.item("Add to Home Favorites", () -> {
                    host.prefs().setDock(DockList.add(host.prefs().dock(), app.packageName));
                    host.refreshHome();
                    host.toast(app.name + " added to Home Favorites.");
                });
            }
            final Intent info = host.apps().appInfoIntent(app.packageName);
            if (host.apps().resolves(info)) {
                menu.item("App Info", () -> host.apps().start(info));
                if (host.apps().userRemovable(app.packageName)) {
                    menu.item("Uninstall…", () -> host.apps().start(info), true);
                }
            }
        } else {
            menu.item("Download & Install (#" + app.code + ")", () -> startDownload(app.installSource(), app.name));
        }
        menu.item("Back", null);
        host.open(menu);
    }

    private void openCodeModal() {
        host.open(new CodeKeypadModalPanel(host, codeOrUrl -> startDownload(codeOrUrl, "Code " + codeOrUrl)));
    }

    private void openSyncModal() {
        host.open(new SyncCatalogModalPanel(host, (newCatalog, msg, error) -> {
            catalog = newCatalog;
            selectedCategory = "All";
            refreshInstalledMap();
            rebuildContent();
            host.toast(msg);
        }));
    }

    // ------------------------------------------------------------------ Post-Install Cleanup Lifecycle

    @Override
    public void onResume() {
        refreshInstalledMap();
        refreshTelemetry();

        if (pendingCleanupFile != null) {
            final File file = pendingCleanupFile;
            final String appLabel = pendingCleanupAppName == null ? "APK" : pendingCleanupAppName;
            pendingCleanupFile = null;
            pendingCleanupAppName = null;

            if (file.exists()) {
                String sizeMb = ToolboxEngine.formatMb(file.length());
                MenuPanel cleanup = new MenuPanel(host, "Installation Complete",
                        "Delete leftover installer (" + file.getName() + ", " + sizeMb + ") for " + appLabel + " to save TV storage?");
                cleanup.item("Delete APK (" + sizeMb + ")", () -> {
                    file.delete();
                    refreshTelemetry();
                    rebuildContent();
                    host.toast("Leftover APK deleted.");
                    advanceEssentialsQueue();
                }, true);
                cleanup.item("Keep File", () -> {
                    rebuildContent();
                    advanceEssentialsQueue();
                });
                host.open(cleanup);
                return;
            }
            rebuildContent();
            advanceEssentialsQueue();
        } else {
            rebuildContent();
        }
    }

    @Override
    public void onPackagesChanged() {
        refreshInstalledMap();
        refreshTelemetry();
        rebuildContent();
    }

    @Override
    public void onClosed() {
        engine.cancelActive();
        main.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onBack() {
        if (gridMode) {
            gridMode = false;
            syncModePills();
            rebuildContent();
            focusFirst();
            return true;
        }
        return false;
    }

    @Override
    public void focusFirst() {
        if (firstFocusable != null) firstFocusable.requestFocus();
    }

    // ------------------------------------------------------------------ Helpers

    private void refreshInstalledMap() {
        installedMap.clear();
        for (ToolboxCatalog.AppEntry app : catalog.apps) {
            installedMap.put(app.packageName, engine.isInstalled(app.packageName));
        }
    }

    private void refreshTelemetry() {
        if (telemetryText == null) return;
        ToolboxEngine.TelemetrySnapshot snap = engine.telemetry();
        int installedCount = 0;
        for (Boolean b : installedMap.values()) if (Boolean.TRUE.equals(b)) installedCount++;
        String perm = engine.canRequestPackageInstalls() ? "Installer Ready" : "Permission Needed";
        telemetryText.setText(String.format(Locale.US,
                "%s   ·   %s   ·   %s   ·   %d/%d Installed   ·   %s",
                snap.availableStorageGb, snap.cachedApkLabel, snap.networkLabel,
                installedCount, catalog.apps.size(), perm));
    }

    private void syncModePills() {
        setPillActive(discoverBtn, !gridMode);
        setPillActive(gridBtn, gridMode);
    }

    private void syncCategoryPills() {
        if (filterRow == null) return;
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            View child = filterRow.getChildAt(i);
            boolean active = selectedCategory.equals(child.getTag());
            setPillActive(child, active);
        }
    }

    private void setPillActive(View pill, boolean active) {
        if (pill == null) return;
        pill.setSelected(active);
        if (pill instanceof ZeroPulseGlassRow) {
            ((ZeroPulseGlassRow) pill).setMark(active ? ZeroPulseGlassRow.Mark.DOT : ZeroPulseGlassRow.Mark.NONE);
        } else if (!t.liquid) {
            pill.setBackground(active ? t.raised(Theme.R_PILL, false) : t.glass(Theme.R_PILL, 0.72f));
        }
    }

    private View toolbarPill(String label, boolean canShowDot, View.OnClickListener click) {
        TextView tv = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(t.dp(14), t.dp(6), t.dp(14), t.dp(canShowDot ? 9 : 6));
        if (t.liquid && t.kit != null) {
            ZeroPulseGlassRow row = w.glassButton(tv, GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
            row.setContentDescription(label);
            row.setOnClickListener(click);
            return row;
        }
        tv.setBackground(t.glass(Theme.R_PILL, 0.80f));
        w.focusable(tv, Theme.R_PILL, null, true);
        tv.setOnClickListener(click);
        return tv;
    }

    private View compactPill(String label, View.OnClickListener click) {
        TextView tv = t.text(c, Theme.T_CAPTION, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(t.dp(12), t.dp(4), t.dp(12), t.dp(4));
        if (t.liquid && t.kit != null) {
            ZeroPulseGlassRow row = w.glassButton(tv, GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
            row.setContentDescription(label);
            row.setOnClickListener(click);
            return row;
        }
        tv.setBackground(t.glass(Theme.R_PILL, 0.85f));
        w.focusable(tv, Theme.R_PILL, null, true);
        tv.setOnClickListener(click);
        return tv;
    }

    private LinearLayout.LayoutParams spacedTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(dp);
        return lp;
    }
}
