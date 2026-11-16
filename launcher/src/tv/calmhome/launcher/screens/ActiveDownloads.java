package tv.calmhome.launcher.screens;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tv.calmhome.launcher.core.MotionTokens;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.GlassProgressBar;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Compact view of the two newest active records. Every active record also appears in Manage Downloads.
 *
 * <p>Everything shown is the queue's own state, read every 0.9 s while the screen is on top: the name, the state, the percent (or, when
 * the source gave no size, the bytes so far and "Working" with no invented fraction), the speed (measured from two real samples, only
 * while bytes are moving), and Cancel. In Liquid Glass each download is a capsule of the approved glass with a {@link GlassProgressBar}
 * that eases between real values. A download that leaves the queue stays one moment longer, showing how it ended ("Downloaded", "Failed"
 * with the reason, "Canceled") before it goes; the next step for a finished APK (the system's install approval) is in its details.
 */
final class ActiveDownloads {
    /** How long a finished download stays in the list, showing how it ended. */
    static final int FINISH_SHOW_MS = 1800;

    private final DownloadsPanel page;
    private final Downloads downloads;
    private final boolean glass;
    final LinearLayout view;
    private final LinearLayout rows;
    private final TextView count;
    private final View manage;
    private final LinearLayout empty;
    private final Map<Long, Row> shown = new LinkedHashMap<>();
    private final ExecutorService icons = Executors.newSingleThreadExecutor();
    private boolean closed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (view.isShown() && view.hasWindowFocus()) refresh();
        }
    };

    ActiveDownloads(DownloadsPanel page) {
        this.page = page;
        downloads = Downloads.get(page.c);
        glass = page.t.liquid && page.t.kit != null;
        view = page.column();
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setPadding(page.t.dp(13), page.t.dp(8), page.t.dp(13), page.t.dp(8));
        LinearLayout header = new LinearLayout(page.c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        TextView title = page.text("Active Downloads", Theme.T_NAV, true);
        Glyph down = new Glyph(Glyph.Kind.DOWNLOAD, Theme.SOFT_WHITE);
        down.setBounds(0, 0, page.t.dp(16), page.t.dp(16));
        title.setCompoundDrawablesRelative(down, null, null, null);
        title.setCompoundDrawablePadding(page.t.dp(5));
        header.addView(title);
        count = page.text("", Theme.T_CAPTION, true);
        count.setGravity(Gravity.CENTER);
        if (glass) {
            // A quiet graphite badge with the hairline rim, not a white slab.
            count.setTextColor(Theme.SOFT_WHITE);
            count.setBackground(page.t.glass(Theme.R_PILL, 0.6f));
            count.setPadding(page.t.dp(7), page.t.dp(1), page.t.dp(7), page.t.dp(1));
        } else {
            count.setTextColor(Theme.DEEP_BLACK);
            count.setBackground(page.t.rounded(Theme.SOFT_WHITE, Theme.R_PILL));
            count.setPadding(page.t.dp(5), 0, page.t.dp(5), 0);
        }
        LinearLayout.LayoutParams badge = page.w.wrap();
        badge.leftMargin = page.t.dp(7);
        header.addView(count, badge);
        header.addView(new View(page.c), new LinearLayout.LayoutParams(0, 1, 1));
        manage = page.action("Manage Downloads", () -> page.host.open(DownloadsPanel.library(page.host)));
        manage.setContentDescription("Manage active downloads");
        page.compact(manage);
        header.addView(manage);
        view.addView(header);
        rows = new LinearLayout(page.c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setMinimumHeight(page.t.dp(57));
        rows.setGravity(Gravity.CENTER_VERTICAL);
        view.addView(rows);
        empty = new LinearLayout(page.c);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(page.t.dp(7), page.t.dp(5), 0, page.t.dp(5));
        ImageView file = new ImageView(page.c);
        file.setBackground(page.t.glass(9, .66f));
        file.setImageDrawable(new Glyph(Glyph.Kind.APK, Theme.MUTED_GRAY));
        file.setPadding(page.t.dp(5), page.t.dp(5), page.t.dp(5), page.t.dp(5));
        empty.addView(file, new LinearLayout.LayoutParams(page.t.dp(31), page.t.dp(31)));
        LinearLayout copy = new LinearLayout(page.c);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(page.t.dp(9), 0, 0, 0);
        copy.addView(page.text("No active downloads", Theme.T_NAV, true));
        copy.addView(page.text("Enter a developer link or browse your saved sources.", Theme.T_LABEL, false));
        empty.addView(copy);
        refresh();
    }

    void refresh() {
        view.removeCallbacks(tick);
        if (closed) return;
        List<DownloadRecord> all = downloads.list();
        List<DownloadRecord> active = new ArrayList<>();
        for (DownloadRecord r : all) if (r.inActiveQueue()) active.add(r);
        DownloadPage.updateText(count, String.valueOf(active.size()));
        List<DownloadRecord> visible = active.subList(0, Math.min(2, active.size()));
        long now = android.os.SystemClock.uptimeMillis();
        for (Long id : new ArrayList<>(shown.keySet())) {
            boolean keep = false;
            for (DownloadRecord r : visible) if (r.id == id) keep = true;
            if (keep) continue;
            Row old = shown.get(id);
            if (old.finishedAt == 0) {
                // It just left the queue: show how it ended for a moment (only if the record still says so), then remove it.
                DownloadRecord last = null;
                for (DownloadRecord r : all) if (r.id == id) last = r;
                if (last != null && !last.inActiveQueue()) {
                    old.finish(last, now);
                    continue;
                }
            } else if (now - old.finishedAt < FINISH_SHOW_MS) {
                continue;
            }
            shown.remove(id);
            boolean focused = old.root.hasFocus();
            rows.removeView(old.root);
            if (focused) manage.requestFocus();
        }
        boolean finishing = false;
        for (Row r : shown.values()) if (r.finishedAt != 0) finishing = true;
        if (active.isEmpty() && !finishing) {
            if (empty.getParent() == null) rows.addView(empty);
            return;
        }
        if (empty.getParent() != null) rows.removeView(empty);
        int index = 0;
        for (DownloadRecord record : visible) {
            Row row = shown.get(record.id);
            if (row == null) {
                row = new Row(record.id);
                shown.put(record.id, row);
                rows.addView(row.root, Math.min(index, rows.getChildCount()), page.spaced(4));
                row.enter();
            }
            row.update(record, now);
            index++;
        }
        view.postDelayed(tick, finishing && active.isEmpty() ? 300 : 900);
    }

    void close() {
        closed = true;
        view.removeCallbacks(tick);
        icons.shutdownNow();
    }

    /** The row shown for a download (for tests). */
    View rowFor(long id) {
        Row r = shown.get(id);
        return r == null ? null : r.root;
    }

    /** What the row for a download says about its state (for tests). */
    String statusFor(long id) {
        Row r = shown.get(id);
        return r == null ? null : r.status.getText().toString();
    }

    /** The progress bar of a download's row (for tests; null in graphite). */
    GlassProgressBar progressFor(long id) {
        Row r = shown.get(id);
        return r == null ? null : r.glassBar;
    }

    private final class Row {
        final LinearLayout root, line;
        final View info, cancel;
        final ImageView logo;
        final TextView title, status, percent;
        final ProgressBar bar;                    // graphite
        final GlassProgressBar glassBar;          // Liquid Glass
        private String iconPackage = "";
        long finishedAt;
        private long lastBytes = -1, lastSampleAt;
        private double bytesPerSecond;

        Row(long id) {
            root = new LinearLayout(page.c);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setClipChildren(false);
            line = new LinearLayout(page.c);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(page.t.dp(12), page.t.dp(3), page.t.dp(12), page.t.dp(3));
            logo = new ImageView(page.c);
            logo.setImageDrawable(new Glyph(Glyph.Kind.APK, Theme.SOFT_WHITE));
            line.addView(logo, new LinearLayout.LayoutParams(page.t.dp(18), page.t.dp(18)));
            LinearLayout names = new LinearLayout(page.c);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(page.t.dp(7), 0, page.t.dp(8), 0);
            title = page.text("", Theme.T_LABEL, true);
            title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END);
            status = page.text("", Theme.T_CAPTION, false);
            status.setSingleLine(); status.setEllipsize(TextUtils.TruncateAt.END);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            names.addView(title); names.addView(status);
            line.addView(names, new LinearLayout.LayoutParams(0, Widgets.WRAP, 2));
            if (glass) {
                glassBar = new GlassProgressBar(page.c);
                glassBar.setReducedMotion(page.t.reducedMotion);
                glassBar.setErrorColor(Theme.ERROR);
                line.addView(glassBar, new LinearLayout.LayoutParams(0, page.t.dp(5), 1));
                bar = null;
            } else {
                bar = new ProgressBar(page.c, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.SOFT_WHITE));
                line.addView(bar, new LinearLayout.LayoutParams(0, page.t.dp(5), 1));
                glassBar = null;
            }
            percent = page.text("", Theme.T_LABEL, true);
            percent.setGravity(Gravity.END);
            line.addView(percent, new LinearLayout.LayoutParams(page.t.dp(72), Widgets.WRAP));
            ZeroPulseGlassRow capsule = page.w.glassButton(line, GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
            if (capsule != null) {
                info = capsule;
            } else {
                line.setBackground(page.t.glass(Theme.R_PILL, .7f));
                page.w.focusable(line, Theme.R_PILL, null, true);
                info = line;
            }
            info.setOnClickListener(v -> page.host.open(DownloadsPanel.detail(page.host, id)));
            root.addView(info, new LinearLayout.LayoutParams(0, page.t.dp(34), 1));
            cancel = page.action("Cancel", () -> {
                try { downloads.cancel(id); }
                catch (IllegalStateException e) { page.host.toast(e.getMessage()); }
                refresh();
            });
            page.compact(cancel);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Widgets.WRAP, page.t.dp(34));
            cp.leftMargin = page.t.dp(6);
            root.addView(cancel, cp);
        }

        /** A new download slides into its place: a short fade and rise (no motion when motion is reduced). */
        void enter() {
            if (page.t.reducedMotion) return;
            root.setAlpha(0f);
            root.setTranslationY(page.t.dp(6));
            root.animate().alpha(1f).translationY(0f).setDuration(MotionTokens.HERO_FADE_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }

        void update(DownloadRecord r, long now) {
            if (finishedAt != 0) {                              // it is back in the queue (retried): a running row again
                finishedAt = 0;
                status.setTextColor(Theme.MUTED_GRAY);
                if (glassBar != null) glassBar.setState(GlassProgressBar.State.RUNNING);
            }
            DownloadPage.updateText(title, r.displayName());
            int value = r.percentTransferred();
            boolean moving = r.state == DownloadRecord.State.DOWNLOADING;
            // Speed from two real samples, smoothed; shown only while bytes are moving and only once there is a measurement.
            if (moving && lastBytes >= 0 && now > lastSampleAt && r.bytes >= lastBytes) {
                double sample = (r.bytes - lastBytes) * 1000d / (now - lastSampleAt);
                bytesPerSecond = bytesPerSecond <= 0 ? sample : bytesPerSecond * 0.6 + sample * 0.4;
            }
            if (!moving) bytesPerSecond = 0;
            lastBytes = r.bytes;
            lastSampleAt = now;
            String state = DownloadLibraryPanel.label(r.state);
            if (moving && bytesPerSecond > 1) state += "   ·   " + Formatter.formatShortFileSize(page.c, Math.round(bytesPerSecond)) + "/s";
            if (moving && r.total > 0) state += "   ·   " + Formatter.formatShortFileSize(page.c, r.total);
            if (moving && value < 0) state += "   ·   Working";
            DownloadPage.updateText(status, state);
            if (glassBar != null) {
                if (value >= 0) glassBar.setProgress(value / 100f);
                else if (r.state == DownloadRecord.State.AWAITING_APPROVAL) glassBar.setProgress(1f);
                else glassBar.setIndeterminate();
                glassBar.setActive(moving);
            } else {
                bar.setVisibility(value >= 0 ? View.VISIBLE : View.INVISIBLE);
                if (value >= 0 && bar.getProgress() != value) bar.setProgress(value);
            }
            DownloadPage.updateText(percent, value >= 0 ? value + "%" : Formatter.formatFileSize(page.c, r.bytes));
            String description = title.getText() + ", " + status.getText() + ", " + percent.getText();
            if (!TextUtils.equals(info.getContentDescription(), description)) info.setContentDescription(description);
            if (r.state == DownloadRecord.State.AWAITING_APPROVAL && cancel.hasFocus()) info.requestFocus();
            cancel.setVisibility(r.state == DownloadRecord.State.AWAITING_APPROVAL ? View.GONE : View.VISIBLE);
            String pkg = r.packageName;
            if (pkg != null && !pkg.isEmpty() && !pkg.equals(iconPackage)) {
                iconPackage = pkg;
                icons.execute(() -> {
                    Drawable image = downloads.inspectedIcon(r);
                    if (image != null) page.body.post(() -> {
                        if (!closed && shown.get(r.id) == this) logo.setImageDrawable(image);
                    });
                });
            }
        }

        /** The download left the queue: say how it ended (the record's own state and error), briefly, then the list removes it. */
        void finish(DownloadRecord r, long now) {
            finishedAt = now;
            boolean ok = r.state == DownloadRecord.State.COMPLETED || r.state == DownloadRecord.State.INSTALLED;
            boolean failed = r.state == DownloadRecord.State.FAILED;
            String text = r.state == DownloadRecord.State.COMPLETED ? "Downloaded   ·   open it to install"
                    : r.state == DownloadRecord.State.INSTALLED ? "Installed"
                    : failed ? "Failed" + (r.error == null || r.error.isEmpty() ? "" : "   ·   " + r.error)
                    : DownloadLibraryPanel.label(r.state);
            DownloadPage.updateText(status, text);
            status.setTextColor(failed ? Theme.ERROR : ok ? Theme.SOFT_WHITE : Theme.MUTED_GRAY);
            DownloadPage.updateText(percent, ok ? "100%" : "");
            if (glassBar != null) {
                if (ok) glassBar.setState(GlassProgressBar.State.COMPLETE);
                else if (failed) glassBar.setState(GlassProgressBar.State.FAILED);
                else {                                          // canceled: the bar simply stops where it was
                    glassBar.setActive(false);
                    if (glassBar.isIndeterminate()) glassBar.setProgress(0f);
                }
            } else if (ok) {
                bar.setProgress(100);
            }
            boolean hadFocus = cancel.hasFocus();
            cancel.setVisibility(View.GONE);
            if (hadFocus) info.requestFocus();
            info.setContentDescription(title.getText() + ", " + text);
        }
    }
}
