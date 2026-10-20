package tv.calmhome.launcher.screens;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Wallpaper browser: collection tabs (only non-empty ones), thumbnails, rotation interval and dimming.
 * OK on a tab makes it the active collection; OK on a thumbnail shows that photo now.
 * Thumbnails are 1/8-size decodes and are dropped when the screen closes.
 */
public class WallpapersPanel extends Panel {

    private final Prefs prefs;
    private final WallpaperStage stage;
    private LinearLayout tabs, strip;
    private TextView credit;
    private final List<View> thumbCards = new ArrayList<>();
    private final Map<String, ImageView> thumbViews = new HashMap<>();
    private final List<Bitmap> thumbs = new ArrayList<>();
    private String shownCategory, selectedFile;

    public WallpapersPanel(Host host) {
        super(host);
        prefs = host.prefs();
        stage = host.stage();
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);

        tabs = new LinearLayout(c);
        tabs.setClipChildren(false);
        for (Map.Entry<String, Integer> cat : stage.catalog().categories().entrySet()) tabs.addView(tab(cat.getKey(), cat.getValue()));
        body.addView(tabs, w.wrap());

        strip = new LinearLayout(c);
        strip.setClipChildren(false);
        strip.setPadding(t.dp(6), t.dp(22), t.dp(6), t.dp(12));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.addView(strip);
        body.addView(hs, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        credit = t.text(c, Theme.T_LABEL, Theme.withAlpha(Theme.SOFT_WHITE, 0.8f), Theme.FONT_REGULAR);
        body.addView(credit, w.wrap());

        Rows rows = new Rows(c, t);
        LinearLayout controls = new LinearLayout(c);
        controls.setClipChildren(false);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        cl.topMargin = t.dp(18);
        String[] intervals = new String[WallpaperCatalog.INTERVALS.length];
        int current = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = WallpaperCatalog.intervalLabel(WallpaperCatalog.INTERVALS[i]);
            if (WallpaperCatalog.INTERVALS[i] == prefs.wallpaperInterval()) current = i;
        }
        LinearLayout interval = rows.choice("Rotation interval", intervals, current, i -> {
            prefs.setWallpaperInterval(WallpaperCatalog.INTERVALS[i]);
            stage.reload();
        });
        interval.setBackground(null);
        String[] dims = {"0%", "10%", "20%", "30%", "40%", "50%", "60%"};
        LinearLayout dim = rows.choice("Dim overlay", dims, prefs.dimPercent() / 10, i -> {
            prefs.setDimPercent(i * 10);
            host.refreshHome();
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        controls.addView(panelCard(interval), half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        half2.leftMargin = t.dp(18);
        controls.addView(panelCard(dim), half2);
        body.addView(controls, cl);

        showCategory(prefs.wallpaperCollection());
        return frame("Wallpapers", body, 0.72f);
    }

    private View panelCard(View row) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(t.glass(Theme.R_PANEL, 0.82f));
        f.addView(row, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        return f;
    }

    private TextView tab(final String category, int count) {
        final TextView v = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        v.setText(category + "  " + count);
        v.setPadding(t.dp(16), t.dp(8), t.dp(16), t.dp(8));
        v.setFocusable(true);
        v.setClickable(true);
        v.setTag(category);
        v.setContentDescription(category + " collection, " + count + " photos");
        v.setOnClickListener(x -> {
            prefs.setWallpaperCollection(category);
            stage.reload();
            showCategory(category);
            paintTabs();
            host.toast(category + " is now the rotating collection");
        });
        v.setOnFocusChangeListener((x, has) -> {
            if (has && !category.equals(shownCategory)) showCategory(category);
            paintTabs();
        });
        LinearLayout.LayoutParams lp = w.wrap();
        lp.rightMargin = t.dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private void paintTabs() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView v = (TextView) tabs.getChildAt(i);
            boolean active = v.getTag().equals(prefs.wallpaperCollection());
            if (v.isFocused()) v.setBackground(t.rounded(Theme.ELECTRIC_BLUE, Theme.R_PILL));
            else if (active) v.setBackground(t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.25f), Theme.ELECTRIC_BLUE, 1, Theme.R_PILL));
            else v.setBackground(t.glass(Theme.R_PILL, 0.7f));
        }
    }

    private void showCategory(String category) {
        shownCategory = category;
        strip.removeAllViews();
        thumbCards.clear();
        thumbViews.clear();
        recycleThumbs();
        int tw = 176, th = 99;
        for (final WallpaperCatalog.Entry e : stage.catalog().inCategory(category)) {
            FrameLayout card = new FrameLayout(c);
            card.setBackground(t.rounded(Theme.PANEL_NAVY, Theme.R_TILE));
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
            thumbViews.put(e.file, iv);
            String sel = selectedFile != null ? selectedFile : (stage.current() == null ? null : stage.current().file);
            if (e.file.equals(sel)) {
                View tick = new View(c);
                tick.setBackground(t.outlined(Theme.ELECTRIC_BLUE, Theme.SOFT_WHITE, 2, 8));
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(t.dp(14), t.dp(14), Gravity.TOP | Gravity.END);
                tl.setMargins(0, t.dp(8), t.dp(8), 0);
                card.addView(tick, tl);
            }
            card.setContentDescription(e.place);
            card.setOnClickListener(v -> {
                stage.show(e, true);
                prefs.setWallpaperPicked(e.file);
                selectedFile = e.file;
                host.toast("Showing " + e.place);
                showCategory(shownCategory);
                focusThumb(e.file);
            });
            w.focusable(card, Theme.R_TILE, null, false);
            final View.OnFocusChangeListener ring = card.getOnFocusChangeListener();
            card.setOnFocusChangeListener((v, has) -> {
                ring.onFocusChange(v, has);
                if (has) credit.setText(e.caption());
            });
            card.setTag(e.file);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(tw), t.dp(th));
            if (strip.getChildCount() > 0) lp.leftMargin = t.dp(16);
            strip.addView(card, lp);
            thumbCards.add(card);
            stage.thumbnail(e.file, (file, bmp) -> {
                ImageView target = thumbViews.get(file);
                if (target == null || root == null || !root.isAttachedToWindow()) { bmp.recycle(); return; }
                target.setImageBitmap(bmp);
                thumbs.add(bmp);
            });
        }
        WallpaperCatalog.Entry cur = stage.current();
        credit.setText(cur == null ? "" : "Now showing: " + cur.caption());
    }

    private void focusThumb(String file) {
        for (View v : thumbCards) if (file.equals(v.getTag())) v.requestFocus();
    }

    private void recycleThumbs() {
        for (Bitmap b : thumbs) b.recycle();
        thumbs.clear();
    }

    @Override
    public void focusFirst() {
        paintTabs();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            if (tabs.getChildAt(i).getTag().equals(prefs.wallpaperCollection())) {
                tabs.getChildAt(i).requestFocus();
                return;
            }
        }
        if (tabs.getChildCount() > 0) tabs.getChildAt(0).requestFocus();
    }

    @Override
    public void onClosed() {
        for (ImageView iv : thumbViews.values()) iv.setImageDrawable(null);
        recycleThumbs();
    }
}
