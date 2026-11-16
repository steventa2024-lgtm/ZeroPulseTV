package tv.calmhome.launcher.downloads;

import java.util.Collections;
import java.util.List;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.core.SourceEntry.Category;

public final class CatalogTests {
    public static void main(String[] args) {
        SourceEntry source = new SourceEntry(Category.SPORTS, "Example", "https://example.org/app.apk", "0012", "");
        List<SourceEntry> entries = Collections.singletonList(source);
        check(SourceEntry.byCode(entries, "0012") == source);
        check(SourceEntry.byCode(entries, "12") == null);
        check(SourceEntry.byCode(entries, "") == null);
        check(!SourceEntry.isCode("123x"));
        check(!SourceEntry.isCode("12345678901234567"));
        reject(() -> source.checkNew(entries));
        reject(() -> new SourceEntry(Category.ANIME, "Two", "https://example.org/b.apk", "0012", "").checkNew(entries));
        reject(() -> new SourceEntry(Category.ANIME, "Two", "http://example.org/a.apk", "", ""));
        reject(() -> new SourceEntry(Category.MOVIES, "Title", "https://example.org", "1", "bad checksum"));
        reject(() -> new SourceEntry(Category.MOVIES, " ", "https://example.org", "1", ""));
        check(Category.valueOf("SPORTS").title.equals("Live TV"));
        check(Category.valueOf("ANIME").title.equals("Anime"));
        check(Category.valueOf("MOVIES").title.equals("Movies & TV"));
        check(Category.valueOf("TOOLS").title.equals("Tools"));
        System.out.println("Catalog tests: 14 passed");
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    private static void reject(Runnable f) {
        try { f.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe or conflicting source accepted");
    }
}
