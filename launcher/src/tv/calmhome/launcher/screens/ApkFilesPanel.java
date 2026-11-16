package tv.calmhome.launcher.screens;

import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Theme;

/** A scoped file manager for APKs retained by ZeroPulse. No broad storage access is needed. */
final class ApkFilesPanel extends DownloadPage {
    private final boolean install;
    private LinearLayout files;
    private View pick;
    private long selectedId;
    ApkFilesPanel(Host host, boolean install) { super(host); this.install = install; }
    @Override protected View build() {
        body.addView(text(install ? "Choose a checked APK, review its details, then approve installation in Android."
                : "APK files saved by ZeroPulse TV. Select a file to review or remove it.", Theme.T_BODY, false));
        pick = action("Choose an APK file", () -> host.pickApk(null));
        body.addView(pick, spaced(7));
        files = column();
        body.addView(files, spaced(7));
        populate();
        return screen(install ? "Install APK" : "File Manager");
    }
    private void populate() {
        files.removeAllViews();
        Downloads downloads = Downloads.get(c);
        first = pick;
        for (DownloadRecord r : downloads.list()) {
            if (!downloads.hasFile(r)) continue;
            if (install && r.state != DownloadRecord.State.COMPLETED && r.state != DownloadRecord.State.AWAITING_APPROVAL) continue;
            View row = action(r.displayName() + "   ·   " + Formatter.formatFileSize(c, r.bytes) + "   ·   " + DownloadLibraryPanel.label(r.state),
                    () -> { selectedId = r.id; host.open(DownloadsPanel.detail(host, r.id)); });
            files.addView(row, spaced(4));
            if (r.id == selectedId || first == pick) first = row;
        }
        if (files.getChildCount() == 0) files.addView(text(install ? "No APKs are ready to install." :
                "No retained APK files. Download from a trusted link or choose a file using Android’s document picker.", Theme.T_BODY, false));
    }
    @Override public void onUncovered() { populate(); }
}
