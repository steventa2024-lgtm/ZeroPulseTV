package tv.calmhome.launcher.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live TV browsing rules (pure Java, unit-tested): categories come only from the playlists' own group names;
 * search matches channel names; Favourites and Recent are the user's own lists.
 */
public final class ChannelFilter {

    public static final String ALL = "All channels", FAVOURITES = "Favourites", RECENT = "Recently watched";

    /** What the filter needs to know about a channel. */
    public interface Item {
        String name();
        String group();
        String url();
    }

    private ChannelFilter() { }

    /** Filter names in display order: All, Favourites/Recent when non-empty, then groups by size (largest first). */
    public static <T extends Item> List<String> filters(List<T> items, Set<String> favourites, List<String> recent) {
        List<String> out = new ArrayList<>();
        out.add(ALL);
        boolean fav = false;
        for (T i : items) if (favourites.contains(i.url())) { fav = true; break; }
        if (fav) out.add(FAVOURITES);
        if (!recent.isEmpty()) out.add(RECENT);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (T i : items) {
            String g = i.group();
            if (g != null && !g.trim().isEmpty()) counts.put(g.trim(), counts.getOrDefault(g.trim(), 0) + 1);
        }
        List<Map.Entry<String, Integer>> groups = new ArrayList<>(counts.entrySet());
        Collections.sort(groups, (a, b) -> b.getValue() != a.getValue().intValue()
                ? b.getValue() - a.getValue() : a.getKey().compareToIgnoreCase(b.getKey()));
        for (Map.Entry<String, Integer> g : groups) if (!out.contains(g.getKey())) out.add(g.getKey());
        return out;
    }

    /** Channels matching {@code filter} and {@code query}; Recent keeps most-recent-first order. */
    public static <T extends Item> List<T> apply(List<T> items, String filter, String query, Set<String> favourites,
                                                 List<String> recent) {
        String q = fold(query);
        List<T> out = new ArrayList<>();
        if (RECENT.equals(filter)) {
            Map<String, T> byUrl = new HashMap<>();
            for (T i : items) if (!byUrl.containsKey(i.url())) byUrl.put(i.url(), i);
            for (String u : recent) {
                T i = byUrl.get(u);
                if (i != null && matches(i, q)) out.add(i);
            }
            return out;
        }
        for (T i : items) {
            if (FAVOURITES.equals(filter) && !favourites.contains(i.url())) continue;
            if (!ALL.equals(filter) && !FAVOURITES.equals(filter) && (i.group() == null || !i.group().trim().equals(filter))) continue;
            if (matches(i, q)) out.add(i);
        }
        return out;
    }

    private static boolean matches(Item i, String foldedQuery) {
        return foldedQuery.isEmpty() || fold(i.name()).contains(foldedQuery);
    }

    /** Lower case without accents, so "cafe" finds "Café". */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }
}
