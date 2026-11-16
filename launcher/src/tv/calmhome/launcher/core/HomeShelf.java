package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What Home's app row shows. Saved favorites always win: the row is the stored dock (same "calmhome"/"dock" preference,
 * same order) minus apps that are not currently usable. Only when that list is genuinely empty does the row offer
 * suggestions: real, launchable, installed apps. Suggestions are a view only. This class never writes anything, so a
 * suggestion never becomes a favorite until the viewer adds it. If nothing at all is launchable the row is empty
 * (Home then shows its honest empty state). Pure Java: unit-tested off-device.
 */
public final class HomeShelf {

    public enum Mode { FAVORITES, SUGGESTIONS, EMPTY }

    public final Mode mode;
    public final List<String> packages;

    private HomeShelf(Mode mode, List<String> packages) {
        this.mode = mode;
        this.packages = Collections.unmodifiableList(packages);
    }

    /** Apps per row at 1080p, as in the approved layout. */
    public static final int SUGGESTION_COUNT = 7;

    /**
     * @param storedDock   the stored dock (or Calm Home's default when never saved)
     * @param recents      packages opened from ZeroPulse, most recent first (history; not shown as its own shelf)
     * @param installed    every launchable app except this launcher, in a stable order (label order)
     * @param leanback     packages that have a TV (leanback) launcher entry: preferred as suggestions
     * @param usable       installed, enabled and launchable
     * @param max          most suggestions to offer
     */
    public static HomeShelf compute(List<String> storedDock, List<String> recents, List<String> installed, Set<String> leanback,
                                    DockList.Predicate usable, int max) {
        List<String> favorites = DockList.visible(storedDock, usable);
        if (!favorites.isEmpty()) return new HomeShelf(Mode.FAVORITES, favorites);
        List<String> out = new ArrayList<>();
        for (String p : recents) if (out.size() < max && !out.contains(p) && installed.contains(p) && usable.test(p)) out.add(p);
        for (String p : installed) if (out.size() < max && !out.contains(p) && leanback.contains(p) && usable.test(p)) out.add(p);
        for (String p : installed) if (out.size() < max && !out.contains(p) && usable.test(p)) out.add(p);
        return new HomeShelf(out.isEmpty() ? Mode.EMPTY : Mode.SUGGESTIONS, out);
    }

    public boolean isEmpty() {
        return packages.isEmpty();
    }
}
