package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundled wallpapers, read from assets/wallpapers/credits.tsv:
 *   file \t place \t credit \t categories(comma-separated, optional) \t focus "x,y" (optional) \t license (optional) \t note (optional)
 * Calm Home 1.0 used the first three columns (those lines count as "Nature"); every later column is additive. Only categories
 * that actually contain images are exposed. "Nature" is the default rotation, so original ZeroPulse artwork stays out of it
 * unless the user picks that collection; Liquid Glass rotates through the "Liquid Glass" collection, the artwork supplied for it.
 */
public final class WallpaperCatalog {

    /** Display order of categories; anything not listed sorts after these. */
    public static final String[] CATEGORY_ORDER = {"Liquid Glass", "Nature", "Mountains", "Ocean", "Forest", "Desert", "Space", "ZeroPulse", "Twilight"};
    public static final String ALL = "Nature";
    /** The collection Liquid Glass rotates through by default: the wallpapers supplied for it, dark and rich enough for glass. */
    public static final String LIQUID = "Liquid Glass";
    /** The dark, misty twilight artwork (kept as its own collection; the Liquid Glass collection also holds the twilight photograph). */
    public static final String TWILIGHT = "Twilight";

    /** Rotation choices in seconds; 0 = static. Default 60 s, as in Calm Home. */
    public static final int[] INTERVALS = {30, 60, 300, 900, 0};
    public static final int DEFAULT_INTERVAL = 60;

    public static final class Entry {
        public final String file, place, credit, license, note;
        public final List<String> categories;
        /** Where the picture's subject is, 0..1 of its width and height; the screen crop is centred on it. */
        public final float focusX, focusY;

        Entry(String file, String place, String credit, List<String> categories, float focusX, float focusY, String license, String note) {
            this.file = file;
            this.place = place;
            this.credit = credit;
            this.categories = Collections.unmodifiableList(categories);
            this.focusX = focusX;
            this.focusY = focusY;
            this.license = license;
            this.note = note;
        }

        /** Footer text: place and the license-required credit. */
        public String caption() {
            return place + "   ·   " + credit;
        }

        /**
         * True for a third-party photograph (its licence, CC BY-SA, requires the credit to be given). ZeroPulse's own artwork
         * ("Original artwork: ...") and artwork supplied by the owner ("Supplied artwork: ...") name no third party; they stay
         * listed on the Wallpaper information screen and in About.
         */
        public boolean requiresAttribution() {
            return !credit.startsWith("Original artwork") && !credit.startsWith("Supplied artwork");
        }

        /**
         * The credit Liquid Glass Home shows under the wallpaper: none. The credit, the licence and its address, and what was
         * changed are on the Wallpaper information screen, which every Home and the Wallpapers screen reach in one step (CC BY-SA
         * allows the credit to be given in any reasonable manner for the medium, and a screen of the app that lists it is one).
         * The earlier Home footer line is gone; the graphite Home still shows {@link #caption()} as it always did.
         */
        public String homeCredit() {
            return "";
        }

        /** The Creative Commons licence's address, when the credit names one; "" otherwise. */
        public String licenseUrl() {
            String c = credit;
            int i = c.indexOf("CC BY-SA ");
            if (i < 0) return "";
            String v = c.substring(i + "CC BY-SA ".length()).trim();
            int end = 0;
            while (end < v.length() && (Character.isDigit(v.charAt(end)) || v.charAt(end) == '.')) end++;
            return end == 0 ? "" : "https://creativecommons.org/licenses/by-sa/" + v.substring(0, end) + "/";
        }

        /** True if the picture is shown off-centre by default. */
        public boolean hasFocus() {
            return !WallpaperFraming.isCentered(focusX, focusY);
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
            String[] p = line.split("\t", -1);
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
            float[] focus = WallpaperFraming.parse(p.length >= 5 ? p[4].trim() : null);
            out.add(new Entry(p[0].trim(), p[1].trim(), p[2].trim(), cats, focus[0], focus[1],
                    p.length >= 6 ? p[5].trim() : "", p.length >= 7 ? p[6].trim() : ""));
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
