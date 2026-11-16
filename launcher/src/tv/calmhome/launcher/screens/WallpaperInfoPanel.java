package tv.calmhome.launcher.screens;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.core.WallpaperFraming;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Wallpaper information: where the wallpaper on screen came from, who made it, under what licence (with the licence's address),
 * what this app changed about it, how big the file is and how it is framed, and then the same for every bundled wallpaper.
 * This is where the credit lives instead of a line on Home: the credit is complete here (creator, licence, source, changes),
 * one step from Home through Wallpapers > Information, from Settings > Appearance and from About. Plain text, no network.
 */
public class WallpaperInfoPanel extends Panel {

    private LinearLayout list;

    public WallpaperInfoPanel(Host host) {
        super(host);
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, t.safeY());
        LinearLayout card = new LinearLayout(c);
        card.setPadding(t.dp(14), t.dp(12), t.dp(14), t.dp(12));
        card.setBackground(t.glass(Theme.R_PANEL, 0.88f));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        body.addView(card, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));

        Rows rows = new Rows(c, t);
        WallpaperCatalog.Entry cur = host.stage().current();
        if (cur != null) {
            section("On screen now");
            add(rows.info("Title", cur.place, Theme.SOFT_WHITE));
            add(rows.info("Credit", cur.credit, Theme.SOFT_WHITE));
            if (!cur.license.isEmpty()) add(rows.info("Licence", cur.license + (cur.licenseUrl().isEmpty() ? "" : "   ·   " + cur.licenseUrl()), Theme.SOFT_WHITE));
            if (!cur.note.isEmpty()) add(rows.info("About this file", cur.note, Theme.SOFT_WHITE));
            add(rows.info("File", cur.file + describeSize(cur), Theme.SOFT_WHITE));
            add(rows.info("Framing", describeFrame(cur), Theme.SOFT_WHITE));
            add(rows.info("What this app changes", "It is cut to the screen's shape around its focal point and scaled to fit. A gentle navy "
                    + "darkening, only as strong as the picture needs for white text to stay readable, is laid over it; the file is not altered.",
                    Theme.MUTED_GRAY));
        }
        section("Every bundled wallpaper");
        for (WallpaperCatalog.Entry e : host.stage().catalog().all()) {
            String detail = e.credit + (e.license.isEmpty() ? "" : "   ·   " + e.license);
            add(rows.info(e.place, detail, Theme.MUTED_GRAY));
        }
        note("Photographs are CC BY-SA licensed (creativecommons.org/licenses/by-sa/4.0 and /3.0): each credit above names the photographer and "
                + "the licence, and each file's source address is in its row on screen. Wallpapers marked 'Supplied artwork' were supplied by the "
                + "owner of this launcher for it; no third-party credit is recorded for them.");
        return frame("Wallpaper information", body);
    }

    private String describeSize(WallpaperCatalog.Entry e) {
        int[] size = host.stage().sourceSize(e.file);
        if (size == null) return "";
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        boolean enlarged = WallpaperFraming.isUpscaled(size[0], size[1], dm.widthPixels, dm.heightPixels);
        return "   ·   " + size[0] + " x " + size[1] + " pixels" + (enlarged ? ", enlarged to fill this " + dm.widthPixels + " x " + dm.heightPixels + " screen" : "");
    }

    private String describeFrame(WallpaperCatalog.Entry e) {
        float[] f = host.stage().frameOf(e);
        boolean chosen = host.prefs().hasWallpaperFrame(e.file);
        String where = WallpaperFraming.isCentered(f[0], f[1]) ? "centred" : "focus " + Math.round(f[0] * 100) + "% across, " + Math.round(f[1] * 100) + "% down";
        return where + (f[2] > 1.001f ? ", zoom " + Math.round(f[2] * 100) + "%" : "") + (chosen ? "  (set by you on the Wallpapers screen)" : "");
    }

    private void add(View row) {
        list.addView(row, Rows.full(t));
    }

    private void section(String s) {
        TextView v = sectionLabel(s);
        v.setPadding(t.dp(16), t.dp(14), t.dp(16), t.dp(4));
        list.addView(v);
    }

    private void note(String s) {
        TextView v = t.text(c, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        v.setText(s);
        v.setPadding(t.dp(16), t.dp(8), t.dp(16), t.dp(6));
        list.addView(v);
    }

    @Override
    public void focusFirst() {
        if (list.getChildCount() > 0) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (list.getChildAt(i).isFocusable()) {
                    list.getChildAt(i).requestFocus();
                    return;
                }
            }
        }
    }
}
