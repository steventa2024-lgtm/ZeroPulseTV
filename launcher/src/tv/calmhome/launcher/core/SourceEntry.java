package tv.calmhome.launcher.core;

import java.util.List;
import tv.calmhome.launcher.downloads.DownloadPolicy;

/** An explicitly saved owner source. Numeric shortcuts are local to this TV. */
public final class SourceEntry {
    public enum Category {
        APPS("Apps", "Applications for your TV"),
        MOVIES("Movies & TV", "Movie & TV apps"),
        // SPORTS is the persisted key used by existing live-TV sources and local APK indexes.
        SPORTS("Live TV", "Live TV & sports apps"),
        GAMES("Games", "Games for your TV"),
        TOOLS("Tools", "Utilities for your TV"),
        ANIME("Anime", "Anime streaming apps");
        public final String title, subtitle;
        Category(String title, String subtitle) { this.title = title; this.subtitle = subtitle; }
    }

    public final Category category;
    public final String label, url, code, sha256;

    public SourceEntry(Category category, String label, String url, String code, String sha256) {
        this.category = category;
        this.url = DownloadPolicy.validateUrl(url).toASCIIString();
        this.label = label.trim();
        if (this.label.isEmpty() || this.label.length() > 100)
            throw new IllegalArgumentException("Use a source name between 1 and 100 characters.");
        this.code = code.trim();
        if (!this.code.isEmpty() && !isCode(this.code))
            throw new IllegalArgumentException("Use 1 to 16 digits for a local shortcut.");
        this.sha256 = DownloadPolicy.normalizeChecksum(sha256);
    }

    public static boolean isCode(String value) { return value != null && value.matches("[0-9]{1,16}"); }

    public static SourceEntry byCode(List<SourceEntry> entries, String code) {
        if (!isCode(code)) return null;
        for (SourceEntry source : entries) if (source.code.equals(code)) return source;
        return null;
    }

    public void checkNew(List<SourceEntry> entries) {
        if (byCode(entries, code) != null) throw new IllegalArgumentException("This shortcut already belongs to a source.");
        for (SourceEntry source : entries) {
            if (source.category == category && source.url.equals(url))
                throw new IllegalArgumentException("This link is already in this category.");
        }
    }
}
