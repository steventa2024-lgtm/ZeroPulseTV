package tv.calmhome.launcher.screens;

import android.content.Intent;
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
    static final int COLUMNS = 5;

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
        subtitle = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        body.addView(subtitle);

        grid = new GridLayout(c);
        grid.setColumnCount(COLUMNS);
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
        populate();
        return frame("All Apps", body);
    }

    private void populate() {
        grid.removeAllViews();
        List<String> all = apps.all();
        List<String> dock = prefs.dock();
        subtitle.setText("Installed on this TV · " + all.size() + " apps · long-press OK for options");
        float availDp = (t.screenW() - 2f * t.safeX() - t.dp(16)) / c.getResources().getDisplayMetrics().density / t.scale;
        int gapDp = 18;
        int tileW = Math.round((availDp - (COLUMNS - 1) * gapDp) / COLUMNS);
        int tileH = Math.round(tileW * 9f / 16f);
        View focusTarget = null;
        for (String pkg : all) {
            LinearLayout tile = w.appTile(apps.banner(pkg), apps.icon(pkg), apps.label(pkg), tileW, tileH, prefs.labelsAlways(),
                    v -> { if (!apps.launch(pkg)) host.toast("Can't open " + apps.label(pkg) + " right now"); },
                    v -> { menu(pkg); return true; });
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
            lp.setMargins(0, 0, grid.getChildCount() % COLUMNS == COLUMNS - 1 ? 0 : t.dp(gapDp), t.dp(8));
            grid.addView(tile, lp);
            if (pkg.equals(focusPkg)) focusTarget = Widgets.cardOf(tile);
        }
        if (focusTarget != null) focusTarget.requestFocus();
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
    }
}
