package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ChannelFilterTest {

    static final class Ch implements ChannelFilter.Item {
        final String name, group, url;

        Ch(String name, String group, String url) {
            this.name = name;
            this.group = group;
            this.url = url;
        }

        @Override public String name() { return name; }
        @Override public String group() { return group; }
        @Override public String url() { return url; }
    }

    private final List<Ch> items = Arrays.asList(
            new Ch("Café Noticias", "News", "u1"), new Ch("World News", "News", "u2"),
            new Ch("Cartoons", "Kids", "u3"), new Ch("Ungrouped One", null, "u4"), new Ch("Radio Jazz", " Music ", "u5"));

    private static String names(List<Ch> l) {
        StringBuilder b = new StringBuilder();
        for (Ch c : l) b.append(b.length() > 0 ? "|" : "").append(c.name);
        return b.toString();
    }

    @Test public void filtersComeFromPlaylistGroupsOnly() {
        Set<String> none = Collections.emptySet();
        assertEquals(Arrays.asList(ChannelFilter.ALL, "News", "Kids", "Music"),
                ChannelFilter.filters(items, none, Collections.<String>emptyList()));
        assertEquals(Arrays.asList(ChannelFilter.ALL, ChannelFilter.FAVOURITES, ChannelFilter.RECENT, "News", "Kids", "Music"),
                ChannelFilter.filters(items, new HashSet<>(Arrays.asList("u3")), Arrays.asList("u1")));
    }

    @Test public void groupFavouriteAndRecentFilters() {
        Set<String> fav = new HashSet<>(Arrays.asList("u3", "u5", "gone"));
        List<String> recent = Arrays.asList("u5", "missing", "u1");
        assertEquals("Café Noticias|World News", names(ChannelFilter.apply(items, "News", "", fav, recent)));
        assertEquals("Radio Jazz", names(ChannelFilter.apply(items, "Music", "", fav, recent)));
        assertEquals("Cartoons|Radio Jazz", names(ChannelFilter.apply(items, ChannelFilter.FAVOURITES, "", fav, recent)));
        assertEquals("Radio Jazz|Café Noticias", names(ChannelFilter.apply(items, ChannelFilter.RECENT, "", fav, recent)));
        assertEquals(5, ChannelFilter.apply(items, ChannelFilter.ALL, "", fav, recent).size());
    }

    @Test public void searchIgnoresCaseAndAccents() {
        Set<String> none = Collections.emptySet();
        List<String> noRecent = Collections.emptyList();
        assertEquals("Café Noticias", names(ChannelFilter.apply(items, ChannelFilter.ALL, "CAFE", none, noRecent)));
        assertEquals("World News", names(ChannelFilter.apply(items, ChannelFilter.ALL, "  NEWS ", none, noRecent)));
        assertEquals("", names(ChannelFilter.apply(items, "Kids", "news", none, noRecent)));
    }
}
