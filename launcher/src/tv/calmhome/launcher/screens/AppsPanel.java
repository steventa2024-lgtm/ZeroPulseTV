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
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** All installed, launchable apps in a grid. OK opens; long-press manages the dock or opens app info. */
public class AppsPanel extends Panel {
    private static final String[] FILTERS = {"All", "Favorites", "Games", "Media"};
    private EditText search;
    private LinearLayout filters;
    private String filter = "All";
    private List<String> installed;
    private int columns;

    private final Apps apps;
    private final Prefs prefs;
    private GridLayout grid;
    private TextView subtitle;
    private String focusPkg;

    public AppsPanel(Host host) {
        super(host);
        apps = host.apps();
        prefs = host.prefs();
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        installed = apps.all();
        LinearLayout toolbar = new LinearLayout(c);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(c);
        t.style(search, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint("Search installed apps"); search.setHintTextColor(Theme.MUTED_GRAY);
        search.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        search.setBackground(t.glass(Theme.R_PILL, .8f));
        search.setOnFocusChangeListener((v, focused) -> v.setForeground(w.ring(focused, Theme.R_PILL)));
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager ime = c.getSystemService(InputMethodManager.class);
            if (ime != null) ime.hideSoftInputFromWindow(v.getWindowToken(), 0);
            if (grid.getChildCount() != 0) Widgets.cardOf(grid.getChildAt(0)).requestFocus();
            return true;
        });
        toolbar.addView(search, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        filters = new LinearLayout(c);
        filters.setPadding(t.dp(8), 0, 0, 0);
        for (String group : FILTERS) {
            TextView button = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            button.setText(group); button.setTag(group); button.setSingleLine();
            button.setPadding(t.dp(10), t.dp(8), t.dp(10), t.dp(8));
            w.focusable(button, Theme.R_PILL, null, true);
            button.setOnClickListener(v -> { filter = group; updateFilterStyle(); populate(); });
            filters.addView(button);
        }
        toolbar.addView(filters);
        updateFilterStyle();
        body.addView(toolbar);
        subtitle = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        subtitle.setPadding(0, t.dp(6), 0, 0);
        body.addView(subtitle);

        grid = new GridLayout(c);
        columns = Math.max(3, Math.min(7, (t.screenW() - 2 * t.safeX()) / t.dp(153)));
        grid.setColumnCount(columns);
        grid.setClipChildren(false);
        grid.setPadding(t.dp(8), t.dp(16), t.dp(8), t.dp(40));
        ScrollView sv = new ScrollView(c);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(grid);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        sl.topMargin = t.dp(4);
        body.addView(sv, sl);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        populate();
        return frame("All Apps", body);
    }

    private void updateFilterStyle() {
        for (int i = 0; i < filters.getChildCount(); i++) {
            View button = filters.getChildAt(i);
            boolean selected = filter.equals(button.getTag());
            button.setSelected(selected);
            button.setBackground(selected ? t.raised(Theme.R_PILL, false) : t.glass(Theme.R_PILL, .7f));
        }
    }

    private void populate() {
        grid.removeAllViews();
        List<String> dock = prefs.dock();
        List<String> all = new ArrayList<>();
        for (String pkg : installed) {
            if (!AppLibrary.matches(search.getText().toString(), apps.label(pkg), pkg)) continue;
            if ("Favorites".equals(filter) && !dock.contains(pkg)) continue;
            if ("Games".equals(filter) && apps.category(pkg) != ApplicationInfo.CATEGORY_GAME) continue;
            if ("Media".equals(filter)) {
                int kind = apps.category(pkg);
                if (kind != ApplicationInfo.CATEGORY_AUDIO && kind != ApplicationInfo.CATEGORY_VIDEO) continue;
            }
            all.add(pkg);
        }
        subtitle.setText(all.isEmpty() ? "No installed apps match this view." :
                all.size() + " installed apps   ·   Select to open   ·   Hold OK to manage your dock");
        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(16)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gapDp = 18;
        int tileW = Math.round((availDp - (columns - 1) * gapDp) / columns);
        int tileH = Math.round(tileW * 9f / 16f);
        View focusTarget = null;
        for (String pkg : all) {
            android.graphics.drawable.Drawable icon = apps.icon(pkg);
            LinearLayout tile = w.appTile(icon == null ? apps.banner(pkg) : null, icon, apps.label(pkg), tileW, tileH, prefs.labelsAlways(),
                    v -> { if (!apps.launch(pkg)) host.toast("Can't open " + apps.label(pkg) + " right now"); },
                    v -> { menu(pkg); return true; });
            Widgets.cardOf(tile).setTag(pkg);
            if (dock.contains(pkg)) {
                FrameLayout card = (FrameLayout) Widgets.cardOf(tile);
                View dot = new View(c);
                dot.setBackground(t.outlined(Theme.ELECTRIC_BLUE, Theme.SOFT_WHITE, 1.5f, 6));
                FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(t.dp(11), t.dp(11), Gravity.TOP | Gravity.END);
                dl.setMargins(0, t.dp(7), t.dp(7), 0);
                card.addView(dot, dl);
                card.setContentDescription(apps.label(pkg) + ", in dock");
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
        populate();
    }

    private void menu(final String pkg) {
        final List<String> dock = prefs.dock();
        boolean inDock = dock.contains(pkg);
        MenuPanel m = new MenuPanel(host, apps.label(pkg), inDock ? "In your dock" : "Not in your dock");
        if (inDock) {
            final List<String> visible = DockList.visible(dock, apps::usable);
            int i = visible.indexOf(pkg);
            if (i > 0) m.item("Move left in dock", () -> reorder(pkg, -1));
            if (i >= 0 && i < visible.size() - 1) m.item("Move right in dock", () -> reorder(pkg, 1));
            m.item("Remove from dock", () -> {
                prefs.setDock(DockList.remove(prefs.dock(), pkg));
                changed(pkg, apps.label(pkg) + " removed from the dock (the app is still installed)");
            });
        } else {
            m.item("Add to dock", () -> {
                prefs.setDock(DockList.add(prefs.dock(), pkg));
                changed(pkg, apps.label(pkg) + " added to the dock");
            });
        }
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) m.item("App info", () -> apps.start(info));
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
