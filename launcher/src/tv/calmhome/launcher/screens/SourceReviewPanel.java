package tv.calmhome.launcher.screens;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.downloads.DownloadPolicy;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Theme;

final class SourceReviewPanel extends DownloadPage {
    private final String url, checksum, title;
    private SourceEntry entry;
    SourceReviewPanel(Host host, SourceEntry entry) {
        this(host, entry.url, entry.sha256, entry.label);
        this.entry = entry;
    }
    SourceReviewPanel(Host host, String url, String checksum, String title) {
        super(host);
        this.url = url;
        this.checksum = checksum;
        this.title = title;
    }
    @Override protected View build() {
        LinearLayout card = column();
        card.addView(text(title, Theme.T_TITLE, true));
        card.addView(text("Developer host • " + DownloadPolicy.validateUrl(url).getHost(), Theme.T_BODY, true));
        card.addView(text(url, Theme.T_BODY, false));
        card.addView(text(checksum.isEmpty() ? "No publisher checksum supplied. A local hash alone cannot establish who published the APK." :
                "Compare with supplied publisher SHA-256\n" + checksum, Theme.T_BODY, false), spaced(8));
        body.addView(card);
        TextView error = text("", Theme.T_BODY, false);
        body.addView(error);
        first = action("Start download", () -> {
            try {
                long id = Downloads.get(c).enqueue(url, checksum);
                host.close(this);
                host.open(DownloadsPanel.detail(host, id));
            } catch (IllegalArgumentException | IllegalStateException e) { showError(error, e); }
        });
        body.addView(first, spaced(8));
        if (entry != null) body.addView(action("Remove saved source", () -> host.open(
                new MenuPanel(host, "Remove saved source?", entry.label)
                        .item("Keep source", null)
                        .item("Remove source", () -> { new SourceCatalog(c).remove(entry); host.close(this); }, true))), spaced(6));
        return screen("Review source");
    }
}
