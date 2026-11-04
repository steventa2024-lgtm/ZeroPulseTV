package tv.calmhome.launcher.screens;

import android.view.View;
import android.widget.LinearLayout;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Theme;

final class SourceCategoryPanel extends DownloadPage {
    private final SourceEntry.Category category;
    private final SourceCatalog catalog;
    private LinearLayout entries;
    private String returnUrl;
    SourceCategoryPanel(Host host, SourceEntry.Category category) {
        super(host);
        this.category = category;
        catalog = new SourceCatalog(c);
    }
    @Override protected View build() {
        body.addView(text("Developer sources saved on this TV", Theme.T_BODY, false));
        first = action("Add a trusted source", () -> host.open(new SourceEditorPanel(host, category)));
        body.addView(first, spaced(9));
        body.addView(action("Choose an APK file", () -> host.pickApk(category)), spaced(4));
        entries = column();
        body.addView(entries, spaced(9));
        populate();
        return screen(category.title);
    }
    private void populate() {
        entries.removeAllViews();
        for (SourceEntry entry : catalog.all()) {
            if (entry.category != category) continue;
            android.widget.TextView row = action(entry.label + (entry.code.isEmpty() ? "" : "   ·   " + entry.code),
                    () -> {
                        returnUrl = entry.url;
                        host.open(new SourceReviewPanel(host, entry));
                    });
            row.setTag(entry.url);
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            entries.addView(row, spaced(4));
        }
        for (DownloadRecord file : Downloads.get(c).list()) {
            if (!catalog.containsFile(file.id, category)) continue;
            String tag = "file:" + file.id;
            View row = action(file.fileName + "   ·   " + DownloadLibraryPanel.label(file.state), () -> {
                returnUrl = tag;
                host.open(DownloadsPanel.detail(host, file.id));
            });
            row.setTag(tag);
            entries.addView(row, spaced(4));
        }
        if (entries.getChildCount() == 0)
            entries.addView(text("No saved sources yet. Add a developer’s HTTPS link or choose an APK file to get started.", Theme.T_BODY, false));
    }
    @Override public void onUncovered() { populate(); }
    @Override public void focusFirst() {
        View previous = returnUrl == null ? null : entries.findViewWithTag(returnUrl);
        if (previous == null || !previous.requestFocus()) super.focusFirst();
    }
}
