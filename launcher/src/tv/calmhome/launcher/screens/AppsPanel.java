package tv.calmhome.launcher.screens;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.util.ArrayList;
import tv.calmhome.launcher.core.AppLibrary;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.data.AppArt;
import tv.calmhome.launcher.data.AppUpdates;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * All installed, launchable apps in a grid, from the TV's real package list. OK opens; holding OK opens the app's menu (Open, Favorites,
 * App info, Uninstall through the system's own confirmation, and Update only when a real update source says so).
 *
 * <p>Views: All, Favorites (the Home row), Recent (apps opened from ZeroPulse, newest first), Games and Media (the category the app
 * itself declares), and System (pre-installed apps that cannot be removed), shown only when this TV has any. An app appears once per
 * view; a favorite carries a small mark in every view.
 *
 * <p>In Liquid Glass the search field, the view pills and the tiles are the approved glass: the same tiles and artwork as Home, the
 * pills like Home's quick actions with the chosen view's page dot, the field lit like a focused button. The grid scrolls inside its own
 * bounds, so a row never passes over the title or the search field.
 */
public class AppsPanel extends Panel {
    private static final String[] FILTERS = {"All", "Favorites", "Recent", "Games", "Media", "System"};
    private EditText search;
    private LinearLayout filters;
    private String filter = "All";
    private List<String> installed;
    private int columns;

    private final Apps apps;
    private final Prefs prefs;
    private final AppUpdates updates = AppUpdates.NONE;      // no authorised update source exists: no update is ever claimed
    private GridLayout grid;
    private TextView subtitle;
    private String focusPkg;

    private final AppArt art;

    public AppsPanel(Host host) {
        super(host);
        apps = host.apps();
        prefs = host.prefs();
        art = new AppArt(host.context(), apps);
    }

    private boolean glass() {
        return t.liquid && t.kit != null;
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        installed = apps.all();
        LinearLayout toolbar = new LinearLayout(c);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setClipChildren(false);
        search = new EditText(c);
        t.style(search, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint("Search installed apps"); search.setHintTextColor(Theme.MUTED_GRAY);
        search.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        search.setContentDescription("Search installed apps");
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager ime = c.getSystemService(InputMethodManager.class);
            if (ime != null) ime.hideSoftInputFromWindow(v.getWindowToken(), 0);
            if (grid.getChildCount() != 0) Widgets.cardOf(grid.getChildAt(0)).requestFocus();
            return true;
        });
        View field = glass() ? w.glassField(search, null, null) : null;
        if (field == null) {
            search.setBackground(t.glass(Theme.R_PILL, .8f));
            search.setOnFocusChangeListener((v, focused) -> v.setForeground(w.ring(focused, Theme.R_PILL)));
            field = search;
        }
        toolbar.addView(field, new LinearLayout.LayoutParams(0, glass() ? t.dp(36) : Widgets.WRAP, 1));
        filters = new LinearLayout(c);
        filters.setPadding(t.dp(10), 0, 0, 0);
        filters.setClipChildren(false);
        for (String group : FILTERS) {
            if ("System".equals(group) && !hasSystemApps()) continue;          // a view this TV has nothing for is not offered
            filters.addView(filterButton(group));
        }
        toolbar.addView(filters);
        updateFilterStyle();
        View zpappsBtn = filterButton("ZPAPPS");
        zpappsBtn.setContentDescription("Open ZPAPPS Toolbox");
        zpappsBtn.setOnClickListener(v -> host.open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(host)));
        LinearLayout.LayoutParams zlp = w.wrap();
        zlp.leftMargin = t.dp(10);
        toolbar.addView(zpappsBtn, zlp);
        body.addView(toolbar);
        subtitle = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        subtitle.setPadding(t.dp(4), t.dp(8), 0, 0);
        body.addView(subtitle);

        grid = new GridLayout(c);
        columns = Math.max(3, Math.min(7, (t.screenW() - 2 * t.safeX()) / t.dp(153)));
        grid.setColumnCount(columns);
        grid.setClipChildren(false);
        grid.setPadding(t.dp(8), t.dp(18), t.dp(8), t.dp(40));
        ScrollView sv = new tv.calmhome.launcher.ui.ContextScrollView(c, t.dp(40));
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        // The grid scrolls inside its own bounds: rows no longer pass over the "All Apps" title and the search field (3006 left the
        // scroller unclipped). The grid's top padding leaves room for a focused tile's lift and glow inside those bounds.
        sv.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        sv.setClipToOutline(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setSmoothScrollingEnabled(!t.reducedMotion);
        sv.addView(grid);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        sl.topMargin = t.dp(2);
        body.addView(sv, sl);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        populate();
        return frame("All Apps", body);
    }

    private boolean hasSystemApps() {
        for (String pkg : installed) if (!apps.userRemovable(pkg)) return true;
        return false;
    }

    private View filterButton(final String group) {
        TextView label = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        label.setText(group);
        label.setSingleLine();
        final View button;
        if (glass()) {
            // A pill of the approved glass, as on Home and Wallpapers; the view that is showing carries the page dot.
            label.setPadding(t.dp(14), t.dp(7), t.dp(14), t.dp(10));
            ZeroPulseGlassRow pill = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CAPSULE, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, true);
            pill.content().addView(label, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.CENTER));
            button = pill;
        } else {
            label.setPadding(t.dp(10), t.dp(8), t.dp(10), t.dp(8));
            w.focusable(label, Theme.R_PILL, null, true);
            button = label;
        }
        button.setTag(group);
        button.setContentDescription(group + " apps");
        button.setOnClickListener(v -> { filter = group; updateFilterStyle(); populate(); });
        LinearLayout.LayoutParams lp = w.wrap();
        lp.leftMargin = filters.getChildCount() == 0 ? 0 : t.dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private void updateFilterStyle() {
        for (int i = 0; i < filters.getChildCount(); i++) {
            View button = filters.getChildAt(i);
            boolean selected = filter.equals(button.getTag());
            button.setSelected(selected);
            if (button instanceof ZeroPulseGlassRow) {
                ((ZeroPulseGlassRow) button).setMark(selected ? ZeroPulseGlassRow.Mark.DOT : ZeroPulseGlassRow.Mark.NONE);
            } else {
                button.setBackground(selected ? t.raised(Theme.R_PILL, false) : t.glass(Theme.R_PILL, .7f));
            }
        }
    }

    /** The view showing now (for tests). */
    String filter() {
        return filter;
    }

    private void populate() {
        grid.removeAllViews();
        List<String> dock = prefs.dock();
        final List<String> recents = prefs.recentlyOpened();       // the history Home no longer shows as a shelf
        List<String> all = new ArrayList<>();
        for (String pkg : installed) {
            if (!AppLibrary.matches(search.getText().toString(), apps.label(pkg), pkg)) continue;
            if ("Favorites".equals(filter) && !dock.contains(pkg)) continue;
            if ("Recent".equals(filter) && !recents.contains(pkg)) continue;
            if ("Games".equals(filter) && apps.category(pkg) != ApplicationInfo.CATEGORY_GAME) continue;
            if ("Media".equals(filter)) {
                int kind = apps.category(pkg);
                if (kind != ApplicationInfo.CATEGORY_AUDIO && kind != ApplicationInfo.CATEGORY_VIDEO) continue;
            }
            if ("System".equals(filter) && apps.userRemovable(pkg)) continue;
            all.add(pkg);
        }
        if ("Recent".equals(filter)) all.sort((a, b) -> Integer.compare(recents.indexOf(a), recents.indexOf(b)));    // most recent first
        if ("Favorites".equals(filter)) all.sort((a, b) -> Integer.compare(dock.indexOf(a), dock.indexOf(b)));       // Home's order
        subtitle.setText(all.isEmpty() ? ("Recent".equals(filter) ? "Apps you open from ZeroPulse will appear here."
                : "Favorites".equals(filter) ? "No favorites yet. Hold OK on an app to add it to Home's row." : "No installed apps match this view.") :
                all.size() + (all.size() == 1 ? " app" : " apps") + "   ·   OK opens   ·   Hold OK for app options");
        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(16)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gapDp = 18;
        int tileW = Math.round((availDp - (columns - 1) * gapDp) / columns);
        int tileH = Math.round(tileW * 9f / 16f);
        View focusTarget = null;
        for (String pkg : all) {
            final View.OnClickListener open = v -> { if (!apps.launch(pkg)) host.toast("Can't open " + apps.label(pkg) + " right now"); };
            final View.OnLongClickListener manage = v -> { menu(pkg); return true; };
            LinearLayout tile;
            if (t.liquid) {
                // The same glass tile and artwork as Home's Favorites, so the app's own banner or icon fills the tile. Names stay visible.
                int tw = t.dp(tileW), th = t.dp(tileH);
                tile = w.glassAppTilePx(pkg, art.get(pkg, tw, th), apps.label(pkg), tw, th, true, open, manage);
            } else {
                android.graphics.drawable.Drawable icon = apps.icon(pkg);
                tile = w.appTile(icon == null ? apps.banner(pkg) : null, icon, apps.label(pkg), tileW, tileH, prefs.labelsAlways(), open, manage);
            }
            Widgets.cardOf(tile).setTag(pkg);
            if (dock.contains(pkg)) {
                FrameLayout card = (FrameLayout) Widgets.cardOf(tile);
                View dot = new View(c);
                // A favorite's mark: in Liquid Glass a small pearl dot (no white ring); graphite keeps its outlined dot.
                dot.setBackground(t.liquid ? t.rounded(0xF2F2F6FA, 4) : t.outlined(Theme.ELECTRIC_BLUE, Theme.SOFT_WHITE, 1.5f, 6));
                if (t.liquid) dot.setElevation(t.dp(1));
                int size = t.dp(t.liquid ? 8 : 11);
                FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
                dl.setMargins(0, t.dp(8), t.dp(8), 0);
                card.addView(dot, dl);
                card.setContentDescription(apps.label(pkg) + ", in Favorites");
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, grid.getChildCount() % columns == columns - 1 ? 0 : t.dp(gapDp), t.dp(8));
            grid.addView(tile, lp);
            if (pkg.equals(focusPkg)) focusTarget = Widgets.cardOf(tile);
        }
        if (focusTarget != null && (grid.hasFocus() || !bodyHasToolbarFocus())) focusTarget.requestFocus();
    }

    private boolean bodyHasToolbarFocus() { return search.hasFocus() || filters.hasFocus(); }

    @Override public void onPackagesChanged() {
        View current = grid.findFocus();
        if (current != null && current.getTag() instanceof String) focusPkg = (String) current.getTag();
        installed = apps.all();
        art.clear();                                  // an updated app may ship new artwork
        populate();
    }

    /** The app's options: Open, its place in Favorites, App info, Update (only with a real update), Uninstall (the system confirms). */
    private void menu(final String pkg) {
        final List<String> dock = prefs.dock();
        boolean inDock = dock.contains(pkg);
        MenuPanel m = new MenuPanel(host, apps.label(pkg), inDock ? "In Favorites" : "Installed app");
        m.item("Open", () -> { if (!apps.launch(pkg)) host.toast("Can't open " + apps.label(pkg) + " right now"); });   // launch records it as recent
        if (inDock) {
            final List<String> visible = DockList.visible(dock, apps::usable);
            int i = visible.indexOf(pkg);
            if (i > 0) m.item("Move left in Favorites", () -> reorder(pkg, -1));
            if (i >= 0 && i < visible.size() - 1) m.item("Move right in Favorites", () -> reorder(pkg, 1));
            m.item("Remove from Favorites", () -> {
                prefs.setDock(DockList.remove(prefs.dock(), pkg));
                changed(pkg, apps.label(pkg) + " removed from Favorites (the app is still installed)");
            });
        } else {
            m.item("Add to Favorites", () -> {
                prefs.setDock(DockList.add(prefs.dock(), pkg));
                changed(pkg, apps.label(pkg) + " added to Favorites");
            });
        }
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) {
            m.item("App info", () -> apps.start(info));
            if (updates.updateAvailable(pkg)) m.item("Update", () -> apps.start(info));
            // Uninstalling is the system's own flow: its App info page asks to confirm. Never offered for system apps or for ZeroPulse.
            if (apps.userRemovable(pkg)) m.item("Uninstall… (App info asks to confirm)", () -> apps.start(info), true);
        }
        host.open(m);
    }

    private void reorder(String pkg, int delta) {
        prefs.setDock(DockList.move(prefs.dock(), DockList.visible(prefs.dock(), apps::usable), pkg, delta));
        changed(pkg, null);
    }

    private void changed(String pkg, String message) {
        focusPkg = pkg;
        populate();
        host.refreshHome();
        if (message != null) host.toast(message);
    }

    @Override
    public void focusFirst() {
        if (grid.getChildCount() > 0) Widgets.cardOf(grid.getChildAt(0)).requestFocus();
        else search.requestFocus();
    }
}
