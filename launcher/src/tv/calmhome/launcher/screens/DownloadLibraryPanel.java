package tv.calmhome.launcher.screens;

import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Theme;

final class DownloadLibraryPanel extends DownloadPage {
    private final Downloads downloads;
    private final Map<Long,TextView> rows = new LinkedHashMap<>();
    private LinearLayout list;
    private TextView empty;
    private boolean closed;
    DownloadLibraryPanel(Host host) { super(host); downloads = Downloads.get(c); }
    static String label(DownloadRecord.State state) {
        switch (state) {
            case QUEUED: return "Queued";
            case DOWNLOADING: return "Downloading";
            case VERIFYING: return "Checking APK";
            case COMPLETED: return "Ready for installation";
            case AWAITING_APPROVAL: return "Awaiting Android approval";
            case INSTALLED: return "Installed";
            case FAILED: return "Failed";
            case CANCELED: return "Canceled";
            default: throw new IllegalStateException("Unknown download state");
        }
    }
    @Override protected View build() {
        list = column();
        empty = text("No downloads yet. Enter a trusted HTTPS link in Download Center or choose a local APK.", Theme.T_BODY, false);
        body.addView(list);
        View picker = action("Choose an APK file", host::pickApk);
        body.addView(picker, spaced(8));
        first = picker;
        refresh();
        body.postDelayed(tick, 1000);
        return screen("Manage Downloads");
    }
    private void refresh() {
        List<DownloadRecord> history = downloads.list();
        if (empty.getParent() == list) list.removeView(empty);
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        for (DownloadRecord r : history) {
            ids.add(r.id);
            TextView row = rows.get(r.id);
            if (row == null) {
                row = action("", () -> host.open(DownloadsPanel.detail(host, r.id)));
                rows.put(r.id, row);
                list.addView(row, spaced(4));
            }
            row.setText(r.fileName + "   ·   " + label(r.state) + "   ·   " + Formatter.formatFileSize(c, r.bytes) + "    ›");
        }
        for (Long id : new java.util.ArrayList<>(rows.keySet())) {
            if (!ids.contains(id)) list.removeView(rows.remove(id));
        }
        if (history.isEmpty()) list.addView(empty);
        first = history.isEmpty() ? body.getChildAt(1) : rows.get(history.get(0).id);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (body.isShown() && body.hasWindowFocus()) refresh();
            body.postDelayed(this, 1000);
        }
    };
    @Override public void onUncovered() { refresh(); }
    @Override public void onClosed() { closed = true; body.removeCallbacks(tick); }
}
