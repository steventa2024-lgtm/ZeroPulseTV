package tv.calmhome.launcher.screens;

import android.app.Activity;
import android.os.Build;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Theme;

/** One persistent download. Replacing action controls only when they change preserves remote focus. */
final class DownloadDetailPanel extends DownloadPage {
    private final long id;
    private final Downloads downloads;
    private TextView summary, progressText, metadata, error;
    private ProgressBar bar;
    private LinearLayout controls;
    private String controlState = "";
    private boolean closed;
    DownloadDetailPanel(Host host, long id) { super(host); this.id = id; downloads = Downloads.get(c); }

    @Override protected View build() {
        LinearLayout card = column();
        summary = text("", Theme.T_TITLE, true);
        progressText = text("", Theme.T_BODY, false);
        bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        card.addView(summary);
        card.addView(progressText, spaced(4));
        card.addView(bar, spaced(4));
        error = text("", Theme.T_BODY, true);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(error);
        body.addView(card);
        controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.VERTICAL);
        body.addView(controls);
        metadata = text("", Theme.T_BODY, false);
        LinearLayout info = column();
        info.addView(metadata);
        body.addView(info, spaced(8));
        body.addView(action("Back", () -> host.close(this)), spaced(6));
        refresh();
        body.postDelayed(tick, 850);
        return screen("Download details");
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (body.isShown() && body.hasWindowFocus()) refresh();
            body.postDelayed(this, 850);
        }
    };
    private void refresh() {
        DownloadRecord r = downloads.find(id);
        if (r == null) {
            summary.setText("This download was removed.");
            controls.removeAllViews();
            first = action("Back", () -> host.close(this));
            controls.addView(first);
            return;
        }
        boolean installed = downloads.isInstalled(id);
        boolean launchable = installed && host.apps().usable(r.packageName);
        boolean inDock = launchable && host.prefs().dock().contains(r.packageName);
        updateText(summary, present(r.appName, present(r.fileName, "APK download")));
        updateText(progressText, DownloadLibraryPanel.label(r.state) + "    ·    " + Formatter.formatFileSize(c, r.bytes)
                + (r.total > 0 ? " of " + Formatter.formatFileSize(c, r.total) : " transferred"));
        bar.setVisibility(r.state == DownloadRecord.State.DOWNLOADING ? View.VISIBLE : View.GONE);
        bar.setIndeterminate(r.total <= 0);
        if (r.total > 0) bar.setProgress((int) Math.min(1000, r.bytes * 1000L / r.total));
        if (!android.text.TextUtils.equals(error.getText(), r.error)) error.setText(r.error);
        StringBuilder facts = new StringBuilder();
        boolean local = r.url.startsWith("content://");
        line(facts, "Source", local ? "File chosen in Android’s document picker" : android.net.Uri.parse(r.url).getHost());
        line(facts, local ? "Document URI" : "Requested URL", r.url);
        if (!local && !r.url.equals(r.finalUrl)) line(facts, "Final HTTPS destination", r.finalUrl);
        line(facts, "File", r.fileName);
        line(facts, "Package", r.packageName);
        line(facts, "Version", r.versionName);
        if (r.minSdk > 0) line(facts, "Android compatibility", "Requires API " + r.minSdk + " • this TV uses API " + Build.VERSION.SDK_INT);
        if (r.packageName != null && !r.packageName.isEmpty()) line(facts, "Requested permissions", present(r.permissions, "None listed"));
        line(facts, "Computed SHA-256", r.sha256);
        line(facts, "Supplied publisher SHA-256", present(r.expectedSha256, "None supplied"));
        if (r.sha256 != null && !r.sha256.isEmpty()) {
            if (r.expectedSha256 == null || r.expectedSha256.isEmpty()) line(facts, "Integrity", "Local fingerprint only. Publisher identity is unconfirmed.");
            else line(facts, "Checksum comparison", r.sha256.equalsIgnoreCase(r.expectedSha256) ? "Matches supplied checksum" : "Mismatch");
        }
        if (r.state == DownloadRecord.State.INSTALLED && !installed)
            line(facts, "Current installation", "Package or installed version is no longer present.");
        updateText(metadata, facts.toString());
        String key = r.state + "/" + launchable + "/" + inDock;
        if (key.equals(controlState)) return;
        controlState = key;
        boolean hadFocus = controls.hasFocus();
        controls.removeAllViews();
        first = null;
        switch (r.state) {
            case QUEUED: case DOWNLOADING: case VERIFYING:
                add("Cancel download", () -> downloads.cancel(id)); break;
            case COMPLETED: add("Install with Android approval", () -> downloads.install((Activity)c, id)); break;
            case AWAITING_APPROVAL:
                add("Continue Android approval", () -> downloads.install((Activity)c, id));
                add("Reset installer session", () -> host.open(new MenuPanel(host, "Reset this session?",
                        "Use this if Android stopped showing the confirmation prompt. The downloaded file is retained.")
                        .item("Keep session", null).item("Reset session", () -> {
                            try { downloads.resetApproval(id); refresh(); }
                            catch (IllegalStateException e) { host.toast(e.getMessage()); }
                        }, true)));
                break;
            case FAILED: case CANCELED: add("Retry", () -> downloads.retry(id)); break;
            case INSTALLED:
                if (launchable) add("Open app", () -> { if (!host.apps().launch(r.packageName)) host.toast("App could not open"); });
                if (launchable && !inDock) add("Add to dock", () -> {
                    host.prefs().setDock(DockList.add(host.prefs().dock(), r.packageName));
                    host.refreshHome(); host.toast("Added to dock");
                });
                break;
        }
        if (r.state != DownloadRecord.State.AWAITING_APPROVAL) add("Remove file and history", this::confirmRemove);
        if (hadFocus && first != null) first.requestFocus();
    }
    private static String present(String text, String fallback) { return text == null || text.isEmpty() ? fallback : text; }
    private static void line(StringBuilder b, String key, String value) {
        if (value != null && !value.isEmpty()) {
            if (b.length() > 0) b.append("\n\n");
            b.append(key).append("   ").append(value);
        }
    }
    private void add(String label, Runnable run) {
        View v = action(label, () -> {
            try { run.run(); if (!closed) refresh(); }
            catch (IllegalArgumentException | IllegalStateException | SecurityException e) { host.toast(e.getMessage()); }
        });
        controls.addView(v, spaced(5));
        if (first == null) first = v;
    }
    private void confirmRemove() {
        host.open(new MenuPanel(host, "Remove download?", "This deletes the private APK and its history. An installed app stays on the TV.")
                .item("Keep download", null)
                .item("Remove download", () -> {
                    try {
                        downloads.remove(id);
                        new tv.calmhome.launcher.data.SourceCatalog(c).forgetFile(id);
                        host.close(this);
                    }
                    catch (IllegalStateException e) { host.toast(e.getMessage()); }
                }, true));
    }
    @Override public void onClosed() { closed = true; body.removeCallbacks(tick); }
}
