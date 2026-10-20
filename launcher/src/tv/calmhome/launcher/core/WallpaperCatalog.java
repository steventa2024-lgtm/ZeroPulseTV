package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundled wallpapers, read from assets/wallpapers/credits.tsv:
 *   file \t place \t credit \t categories(comma-separated, optional)
 * Calm Home 1.0 used the first three columns (those lines count as "Nature"); the category column is
 * additive. Only categories that actually contain images are exposed. "Nature" is the default rotation,
 * so original ZeroPulse artwork stays out of it unless the user picks that collection.
 */
public final class WallpaperCatalog {

    /** Display order of categories; anything not listed sorts after these. */
    public static final String[] CATEGORY_ORDER = {"Nature", "Mountains", "Ocean", "Forest", "Desert", "Space", "ZeroPulse"};
    public static final String ALL = "Nature";

    /** Rotation choices in seconds; 0 = static. Default 60 s, as in Calm Home. */
    public static final int[] INTERVALS = {30, 60, 300, 900, 0};
    public static final int DEFAULT_INTERVAL = 60;

    public static final class Entry {
        public final String file, place, credit;
        public final List<String> categories;

        Entry(String file, String place, String credit, List<String> categories) {
            this.file = file;
            this.place = place;
            this.credit = credit;
            this.categories = Collections.unmodifiableList(categories);
        }

        /** Footer text: place and the license-required credit. */
        public String caption() {
            return place + "   ·   " + credit;
        }
    }

    private final List<Entry> entries;

    private WallpaperCatalog(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static WallpaperCatalog parse(List<String> lines) {
        List<Entry> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t");
            if (p.length < 3) continue;
            // Categories are explicit in the 4th column; Calm Home's 3-column lines are nature photos.
            List<String> cats = new ArrayList<>();
            if (p.length >= 4) {
                for (String c : p[3].split(",")) {
                    String t = c.trim();
                    if (!t.isEmpty() && !cats.contains(t)) cats.add(t);
                }
            }
            if (cats.isEmpty()) cats.add(ALL);
            out.add(new Entry(p[0].trim(), p[1].trim(), p[2].trim(), cats));
        }
        return new WallpaperCatalog(out);
    }

    public List<Entry> all() {
        return entries;
    }

    /** Non-empty categories in display order, with their image counts. */
    public Map<String, Integer> categories() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : CATEGORY_ORDER) counts.put(c, 0);
        for (Entry e : entries) {
            for (String c : e.categories) {
                Integer n = counts.get(c);
                counts.put(c, n == null ? 1 : n + 1);
            }
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> m : counts.entrySet()) if (m.getValue() > 0) out.put(m.getKey(), m.getValue());
        return out;
    }

    public List<Entry> inCategory(String category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.categories.contains(category)) out.add(e);
        return out.isEmpty() ? new ArrayList<>(entries) : out;
    }

    public Entry byFile(String file) {
        for (Entry e : entries) if (e.file.equals(file)) return e;
        return null;
    }

    public static boolean isValidInterval(int seconds) {
        for (int i : INTERVALS) if (i == seconds) return true;
        return false;
    }

    public static String intervalLabel(int seconds) {
        if (seconds == 0) return "Static";
        if (seconds < 60) return seconds + " seconds";
        if (seconds == 60) return "60 seconds";
        return (seconds / 60) + " minutes";
    }

    /** Next interval in the cycle (for left/right on a settings row). */
    public static int nextInterval(int seconds, int direction) {
        int i = 0;
        for (int k = 0; k < INTERVALS.length; k++) if (INTERVALS[k] == seconds) i = k;
        int n = INTERVALS.length;
        return INTERVALS[((i + direction) % n + n) % n];
    }
}
