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

import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.core.WallpaperFraming;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

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
    private final boolean glass;                       // Liquid Glass: the controls are the approved glass (ZeroPulseGlassRow)
    private final Map<String, ImageView> thumbViews = new HashMap<>();
    private final List<Bitmap> thumbs = new ArrayList<>();
    private String shownCategory, selectedFile;
    private Rows rows;
    private LinearLayout framingRow;
    private TextView framingCaption;
    private WallpaperCatalog.Entry framingEntry;

    public WallpapersPanel(Host host) {
        super(host);
        prefs = host.prefs();
        stage = host.stage();
        glass = host.theme().liquid && host.theme().kit != null;
    }

    @Override
    protected View build() {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);

        tabs = new LinearLayout(c);
        tabs.setClipChildren(false);
        tabs.setClipToPadding(false);
        for (Map.Entry<String, Integer> cat : stage.catalog().categories().entrySet()) tabs.addView(tab(cat.getKey(), cat.getValue()));
        // The collections sit in a scroller (as the previews do): eight pills could be wider than the screen, and a plain row then
        // squeezed the last one ("Twilight" lost its count on the TCL). A pill now always keeps its full width; if the row is wider
        // than the screen, focus scrolling keeps the focused pill inside the safe margin.
        tv.calmhome.launcher.ui.SafeMarginScrollView tabScroll = new tv.calmhome.launcher.ui.SafeMarginScrollView(c);
        tabScroll.setCarousel(!t.reducedMotion, tv.calmhome.launcher.core.MotionTokens.CAROUSEL_MS);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setClipToPadding(false);
        tabScroll.setClipChildren(false);
        tabScroll.addView(tabs);
        body.addView(tabScroll, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

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

        rows = new Rows(c, t, true);                          // the controls here are cards: their glass shows all the time
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
        View interval = rows.choice("Rotation interval", intervals, current, i -> {
            prefs.setWallpaperInterval(WallpaperCatalog.INTERVALS[i]);
            stage.reload();
        });
        String[] dims = {"0%", "10%", "20%", "30%", "40%", "50%", "60%"};
        View dim = rows.choice("Dim overlay", dims, prefs.dimPercent() / 10, i -> {
            prefs.setDimPercent(i * 10);
            host.refreshHome();
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        controls.addView(panelCard(interval), half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        half2.leftMargin = t.dp(18);
        controls.addView(panelCard(dim), half2);
        View info = rows.action("Information", "Credit, licence, framing", false, v -> host.open(new WallpaperInfoPanel(host)));
        LinearLayout.LayoutParams half3 = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        half3.leftMargin = t.dp(18);
        controls.addView(panelCard(info), half3);
        body.addView(controls, cl);

        framingCaption = t.text(c, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        LinearLayout.LayoutParams fc = w.wrap();
        fc.topMargin = t.dp(12);
        fc.bottomMargin = t.dp(10);                                       // clear of the Zoom card below it (3005: it sat against it)
        body.addView(framingCaption, fc);
        framingRow = new LinearLayout(c);
        framingRow.setClipChildren(false);
        body.addView(framingRow, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        showCategory(prefs.wallpaperCollection());
        bindFraming(stage.current() != null ? stage.current() : firstOf(prefs.wallpaperCollection()));
        return frame("Wallpapers", body, 0.72f);
    }

    private WallpaperCatalog.Entry firstOf(String category) {
        java.util.List<WallpaperCatalog.Entry> in = stage.catalog().inCategory(category);
        return in.isEmpty() ? null : in.get(0);
    }

    /**
     * Framing of one wallpaper: zoom, and where the screen's window sits on the picture (the focal point), across and down. The
     * changes are kept per wallpaper and shown at once when that wallpaper is on screen. Left and Right step by one tenth.
     */
    private void bindFraming(WallpaperCatalog.Entry entry) {
        framingEntry = entry;
        int focusedIndex = -1;
        for (int i = 0; i < framingRow.getChildCount(); i++) if (framingRow.getChildAt(i).hasFocus()) focusedIndex = i;
        framingRow.removeAllViews();
        if (entry == null) {
            framingCaption.setText("");
            return;
        }
        float[] f = stage.frameOf(entry);
        framingCaption.setText("Framing for “" + entry.place + "” (kept for this wallpaper)");
        String[] zooms = new String[WallpaperFraming.ZOOMS.length];
        int zoomIndex = 0;
        for (int i = 0; i < zooms.length; i++) {
            zooms[i] = Math.round(WallpaperFraming.ZOOMS[i] * 100) + "%";
            if (Math.abs(WallpaperFraming.ZOOMS[i] - f[2]) < Math.abs(WallpaperFraming.ZOOMS[zoomIndex] - f[2])) zoomIndex = i;
        }
        String[] tenths = new String[11];
        for (int i = 0; i <= 10; i++) tenths[i] = (i * 10) + "%";
        final float[] frame = {f[0], f[1], f[2]};
        View zoom = rows.choice("Zoom", zooms, zoomIndex, i -> { frame[2] = WallpaperFraming.ZOOMS[i]; applyFraming(frame); });
        View across = rows.choice("Focus across", tenths, Math.round(f[0] * 10), i -> { frame[0] = i / 10f; applyFraming(frame); });
        View down = rows.choice("Focus down", tenths, Math.round(f[1] * 10), i -> { frame[1] = i / 10f; applyFraming(frame); });
        View[] cards = {panelCard(zoom), panelCard(across), panelCard(down)};
        for (int i = 0; i < cards.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
            if (i > 0) lp.leftMargin = t.dp(18);
            framingRow.addView(cards[i], lp);
        }
        if (focusedIndex >= 0 && focusedIndex < cards.length) cards[focusedIndex].requestFocus();
    }

    private void applyFraming(float[] frame) {
        if (framingEntry == null) return;
        stage.setFrame(framingEntry, frame[0], frame[1], frame[2]);
    }

    private View panelCard(View row) {
        if (glass) return row;                         // the row is itself the card of glass: no wrapper, no second background
        FrameLayout f = new FrameLayout(c);
        f.setBackground(t.glass(Theme.R_PANEL, 0.82f));
        f.addView(row, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        return f;
    }

    private View tab(final String category, int count) {
        final TextView text = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        text.setText(category + "  " + count);
        text.setSingleLine(true);                                        // eight collections fit one line; none wraps
        final View v;
        if (glass) {
            // A pill of the approved glass, as Home's quick actions are; the collection that rotates carries the rail's page dot.
            ZeroPulseGlassRow pill = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CAPSULE, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, true);
            text.setPadding(t.dp(14), t.dp(9), t.dp(14), t.dp(11));       // 14 dp a side: all eight collections fit the safe area
            pill.content().addView(text, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.CENTER));
            v = pill;
        } else {
            text.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
            text.setFocusable(true);
            text.setClickable(true);
            v = text;
        }
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
            View v = tabs.getChildAt(i);
            boolean active = v.getTag().equals(prefs.wallpaperCollection());
            if (v instanceof ZeroPulseGlassRow) {                          // glass: focus is the glass, the rotating collection has the dot
                ((ZeroPulseGlassRow) v).setMark(active ? ZeroPulseGlassRow.Mark.DOT : ZeroPulseGlassRow.Mark.NONE);
                continue;
            }
            if (v.isFocused()) v.setBackground(t.outlined(0xFF50545A, Theme.ELECTRIC_BLUE, 2, Theme.R_PILL));
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
        java.util.List<WallpaperCatalog.Entry> entries = stage.catalog().inCategory(category);
        // 176 x 99 dp previews; a collection of five or fewer shrinks them just enough that all of them fit across the screen.
        float availDp = (t.screenW() - 2f * t.safeX()) / c.getResources().getDisplayMetrics().density / t.scale - 12f;
        int tw = entries.size() <= 5 ? (int) Math.min(176f, (availDp - 16f * (entries.size() - 1)) / entries.size()) : 176, th = Math.round(tw * 9f / 16f);
        for (final WallpaperCatalog.Entry e : entries) {
            final FrameLayout card;
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            String sel = selectedFile != null ? selectedFile : (stage.current() == null ? null : stage.current().file);
            boolean chosen = e.file.equals(sel);
            if (glass) {
                // The preview sits in a frame of the approved glass, the way an app tile does on Home; the chosen one carries the page dot.
                // The preview (never the glass frame) lifts a little on focus, as a Home tile's artwork does.
                ZeroPulseGlassRow frame = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CARD, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, true, 1.04f);
                int heightPx = t.dp(th), margin = TileArt.margin(heightPx);
                frame.content().setPadding(margin, margin, margin, margin);
                final float radius = TileArt.bannerRadius(heightPx);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                iv.setClipToOutline(true);
                frame.content().addView(iv, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
                frame.setMark(chosen ? ZeroPulseGlassRow.Mark.DOT : ZeroPulseGlassRow.Mark.NONE);
                card = frame;
            } else {
                card = new FrameLayout(c);
                card.setBackground(t.rounded(Theme.PANEL_NAVY, Theme.R_TILE));
                card.addView(iv, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
                if (chosen) {
                    View tick = new View(c);
                    tick.setBackground(t.outlined(Theme.ELECTRIC_BLUE, Theme.SOFT_WHITE, 2, 8));
                    FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(t.dp(14), t.dp(14), Gravity.TOP | Gravity.END);
                    tl.setMargins(0, t.dp(8), t.dp(8), 0);
                    card.addView(tick, tl);
                }
            }
            thumbViews.put(e.file, iv);
            card.setContentDescription(chosen ? e.place + ", showing now" : e.place);
            card.setOnClickListener(v -> {
                stage.show(e, true);
                prefs.setWallpaperPicked(e.file);
                selectedFile = e.file;
                host.toast("Showing " + e.place);
                showCategory(shownCategory);
                bindFraming(e);
                focusThumb(e.file);
            });
            final View.OnFocusChangeListener ring;
            if (glass) {
                ring = null;                                   // the glass frame is the focus: no ring, no swell
            } else {
                w.focusable(card, Theme.R_TILE, null, false);
                ring = card.getOnFocusChangeListener();
            }
            card.setOnFocusChangeListener((v, has) -> {
                if (ring != null) ring.onFocusChange(v, has);
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

    /**
     * The wallpaper changed under the open screen (the rotation timer, or the first picture finishing its load): the framing
     * controls follow it, unless the viewer is using one right now (then they keep adjusting the picture they chose).
     */
    @Override
    public void updateCredit(String caption) {
        super.updateCredit(caption);
        WallpaperCatalog.Entry cur = stage.current();
        if (framingRow != null && cur != null && cur != framingEntry && !framingRow.hasFocus()) bindFraming(cur);
    }

    @Override
    public boolean onBack() {
        return rows != null && rows.endEditing();          // Back first ends a change in progress (zoom, focus, dim, interval)
    }

    @Override
    public void onClosed() {
        for (ImageView iv : thumbViews.values()) iv.setImageDrawable(null);
        recycleThumbs();
    }
}
