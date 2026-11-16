package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ZeroPulse Web's tabs (pure Java, unit-tested). A tab here is only its address and title; the browser keeps a
 * live WebView for the active tab alone, so saved tabs cost no rendering memory until they are opened.
 */
public final class TabSet {

    /** Upper bound on open tabs; a guard against runaway pages, not a measured memory budget. */
    public static final int MAX_TABS = 12;

    public static final class Tab {
        public final long id;
        public String url;
        public String title;

        Tab(long id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int active = -1;
    private long nextId = 1;

    /** Opens a tab after the active one and makes it active. Returns null when {@link #MAX_TABS} are open. */
    public Tab open(String url) {
        if (tabs.size() >= MAX_TABS) return null;
        Tab t = new Tab(nextId++, url, null);
        int at = active < 0 ? tabs.size() : active + 1;
        tabs.add(at, t);
        active = at;
        return t;
    }

    /** Closes a tab; the neighbour to its right (else left) becomes active. Returns false for an unknown id. */
    public boolean close(long id) {
        int i = indexOf(id);
        if (i < 0) return false;
        tabs.remove(i);
        if (tabs.isEmpty()) active = -1;
        else if (i < active) active--;
        else if (i == active) active = Math.min(i, tabs.size() - 1);   // right neighbour moved into place i
        return true;
    }

    public void closeAll() {
        tabs.clear();
        active = -1;
    }

    public boolean select(long id) {
        int i = indexOf(id);
        if (i < 0) return false;
        active = i;
        return true;
    }

    /** Moves a tab one place left (-1) or right (+1); the same tab stays active. */
    public boolean move(long id, int delta) {
        int i = indexOf(id), j = i + delta;
        if (i < 0 || j < 0 || j >= tabs.size()) return false;
        Tab activeTab = active();
        Collections.swap(tabs, i, j);
        active = tabs.indexOf(activeTab);
        return true;
    }

    public Tab active() {
        return active < 0 ? null : tabs.get(active);
    }

    public int activeIndex() {
        return active;
    }

    public List<Tab> all() {
        return Collections.unmodifiableList(tabs);
    }

    public int size() {
        return tabs.size();
    }

    private int indexOf(long id) {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id == id) return i;
        return -1;
    }
}
