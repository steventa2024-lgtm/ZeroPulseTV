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
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Compact view of the two newest active records. Every active record also appears in Manage Downloads. */
final class ActiveDownloads {
    private final DownloadsPanel page;
    private final Downloads downloads;
    final LinearLayout view;
    private final LinearLayout rows;
    private final TextView count, manage;
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
        view = page.column();
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setPadding(page.t.dp(13), page.t.dp(6), page.t.dp(13), page.t.dp(6));
        LinearLayout header = new LinearLayout(page.c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = page.text("Active Downloads", Theme.T_NAV, true);
        Glyph down = new Glyph(Glyph.Kind.DOWNLOAD, Theme.SOFT_WHITE);
        down.setBounds(0, 0, page.t.dp(16), page.t.dp(16));
        title.setCompoundDrawablesRelative(down, null, null, null);
        title.setCompoundDrawablePadding(page.t.dp(5));
        header.addView(title);
        count = page.text("", Theme.T_CAPTION, true);
        count.setTextColor(Theme.DEEP_BLACK);
        count.setGravity(Gravity.CENTER);
        count.setBackground(page.t.rounded(Theme.SOFT_WHITE, Theme.R_PILL));
        count.setPadding(page.t.dp(5), 0, page.t.dp(5), 0);
        LinearLayout.LayoutParams badge = page.w.wrap();
        badge.leftMargin = page.t.dp(6);
        header.addView(count, badge);
        header.addView(new View(page.c), new LinearLayout.LayoutParams(0, 1, 1));
        manage = page.action("Manage Downloads", () -> page.host.open(DownloadsPanel.library(page.host)));
        manage.setContentDescription("Manage active downloads");
        manage.setPadding(page.t.dp(10), page.t.dp(3), page.t.dp(10), page.t.dp(3));
        header.addView(manage);
        view.addView(header);
        rows = new LinearLayout(page.c);
        rows.setOrientation(LinearLayout.VERTICAL);
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
        List<DownloadRecord> active = new ArrayList<>();
        for (DownloadRecord r : downloads.list()) if (r.inActiveQueue()) active.add(r);
        DownloadPage.updateText(count, String.valueOf(active.size()));
        List<DownloadRecord> visible = active.subList(0, Math.min(2, active.size()));
        for (Long id : new ArrayList<>(shown.keySet())) {
            boolean keep = false;
            for (DownloadRecord r : visible) if (r.id == id) keep = true;
            if (!keep) {
                Row old = shown.remove(id);
                boolean focused = old.root.hasFocus();
                rows.removeView(old.root);
                if (focused) manage.requestFocus();
            }
        }
        if (active.isEmpty()) {
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
                rows.addView(row.root, index, page.spaced(3));
            }
            row.update(record);
            index++;
        }
        view.postDelayed(tick, 900);
    }

    void close() {
        closed = true;
        view.removeCallbacks(tick);
        icons.shutdownNow();
    }

    private final class Row {
        final LinearLayout root, info;
        final ImageView logo;
        final TextView title, status, percent, cancel;
        final ProgressBar bar;
        private String iconPackage = "";

        Row(long id) {
            root = new LinearLayout(page.c);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setClipChildren(false);
            info = new LinearLayout(page.c);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(page.t.dp(9), page.t.dp(2), page.t.dp(7), page.t.dp(2));
            info.setBackground(page.t.glass(Theme.R_PILL, .7f));
            page.w.focusable(info, Theme.R_PILL, null, true);
            info.setOnClickListener(v -> page.host.open(DownloadsPanel.detail(page.host, id)));
            root.addView(info, new LinearLayout.LayoutParams(0, page.t.dp(29), 1));
            logo = new ImageView(page.c);
            logo.setImageDrawable(new Glyph(Glyph.Kind.APK, Theme.SOFT_WHITE));
            info.addView(logo, new LinearLayout.LayoutParams(page.t.dp(18), page.t.dp(18)));
            LinearLayout names = new LinearLayout(page.c);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(page.t.dp(6), 0, page.t.dp(6), 0);
            title = page.text("", Theme.T_LABEL, true);
            title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END);
            status = page.text("", Theme.T_CAPTION, false);
            status.setSingleLine(); status.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(title); names.addView(status);
            info.addView(names, new LinearLayout.LayoutParams(0, Widgets.WRAP, 2));
            bar = new ProgressBar(page.c, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.SOFT_WHITE));
            info.addView(bar, new LinearLayout.LayoutParams(0, page.t.dp(5), 1));
            percent = page.text("", Theme.T_LABEL, true);
            percent.setGravity(Gravity.END);
            info.addView(percent, new LinearLayout.LayoutParams(page.t.dp(65), Widgets.WRAP));
            cancel = page.action("Cancel", () -> {
                try { downloads.cancel(id); }
                catch (IllegalStateException e) { page.host.toast(e.getMessage()); }
                refresh();
            });
            cancel.setPadding(page.t.dp(9), page.t.dp(4), page.t.dp(9), page.t.dp(4));
            LinearLayout.LayoutParams cp = page.w.wrap();
            cp.leftMargin = page.t.dp(5);
            root.addView(cancel, cp);
        }

        void update(DownloadRecord r) {
            DownloadPage.updateText(title, r.displayName());
            DownloadPage.updateText(status, DownloadLibraryPanel.label(r.state));
            int value = r.percentTransferred();
            bar.setVisibility(value >= 0 ? View.VISIBLE : View.INVISIBLE);
            if (value >= 0 && bar.getProgress() != value) bar.setProgress(value);
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
    }
}
