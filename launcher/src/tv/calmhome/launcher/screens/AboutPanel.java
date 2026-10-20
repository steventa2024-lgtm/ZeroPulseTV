package tv.calmhome.launcher.screens;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tv.calmhome.launcher.BuildInfo;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** About: branding, real version/device information, photo credits, licenses and recovery pointers. */
public class AboutPanel extends Panel {

    private LinearLayout list;

    public AboutPanel(Host host) {
        super(host);
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setPadding(0, 0, 0, t.safeY());

        LinearLayout brand = new LinearLayout(c);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.addView(new PulseLogo(c, t.dp(54)));
        TextView powered = t.text(c, Theme.T_TAGLINE, Theme.HOLO_BLUE, Theme.FONT_REGULAR);
        powered.setText("Powered by ZeroPulse AI");
        powered.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams pl = w.wrap();
        pl.topMargin = t.dp(16);
        brand.addView(powered, pl);
        body.addView(brand, new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(14), t.dp(12), t.dp(14), t.dp(12));
        card.setBackground(t.glass(Theme.R_PANEL, 0.88f));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1.25f);
        cl.leftMargin = t.dp(24);
        body.addView(card, cl);

        Rows rows = new Rows(c, t);
        String version = "?";
        long code = 0;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = pi.versionName;
            code = pi.getLongVersionCode();
        } catch (Exception ignored) { }
        WallpaperCatalog cat = host.stage().catalog();
        int photos = 0, originals = 0;
        for (WallpaperCatalog.Entry e : cat.all()) {
            if (e.credit.startsWith("Photo: ")) photos++; else originals++;
        }
        add(rows.info("Launcher version", version + " (" + code + ")", Theme.SOFT_WHITE));
        add(rows.info("Build number", BuildInfo.BUILD, Theme.SOFT_WHITE));
        add(rows.info("Source", BuildInfo.COMMIT, Theme.SOFT_WHITE));
        add(rows.info("Package ID", c.getPackageName(), Theme.SOFT_WHITE));
        add(rows.info("Device", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")", Theme.SOFT_WHITE));
        add(rows.info("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", Theme.SOFT_WHITE));
        add(rows.info("Wallpaper pack", photos + " photos" + (originals > 0 ? " + " + originals + " ZeroPulse originals" : ""),
                Theme.SOFT_WHITE));
        add(rows.info("Based on", "Calm Home 1.0", Theme.SOFT_WHITE));

        section("Photo credits");
        for (WallpaperCatalog.Entry e : cat.all()) add(rows.info(e.place, e.credit.replaceFirst("^Photo: ", ""), Theme.MUTED_GRAY));
        note("Photos from Wikimedia Commons Featured Pictures, cropped to 16:9. CC BY-SA licensed: "
                + "creativecommons.org/licenses/by-sa/4.0 (and /3.0). Full source links: launcher/CREDITS.md.");

        section("Licenses");
        add(rows.info("ZeroPulse logo, icons, app", "Original work", Theme.MUTED_GRAY));
        add(rows.info("Font", "Roboto (Android system, Apache 2.0)", Theme.MUTED_GRAY));

        section("Recovery");
        note("Back to Calm Home: run restore-calmhome.ps1 on the PC.\nBack to the stock TCL/Google TV setup: run "
                + "restore-stock.ps1. Both are described in the project README.");
        return frame("About", body);
    }

    private void add(View row) {
        list.addView(row, Rows.full(t));
    }

    private void section(String s) {
        TextView v = sectionLabel(s);
        v.setPadding(t.dp(16), t.dp(18), t.dp(16), t.dp(4));
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
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }
}
