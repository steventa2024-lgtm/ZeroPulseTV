package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The dock: an ordered list of package names, stored exactly as Calm Home 1.0 stored it
 * (SharedPreferences "calmhome", key "dock", comma-separated). Keeping the format lets the saved dock
 * survive the 2.0 update and a rollback to Calm Home. Pure Java so it can be unit-tested off-device.
 */
public final class DockList {

    /** Calm Home 1.0 default, used when the user never edited the dock (key absent). */
    public static final List<String> DEFAULT = Collections.unmodifiableList(Arrays.asList(
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.hulu.livingroomplus",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvunplugged",
            "com.google.android.youtube.tvmusic"));

    private DockList() { }

    /** Parses the stored value; null (never saved) gives the default. Blank entries and duplicates are dropped. */
    public static List<String> parse(String stored) {
        if (stored == null) return new ArrayList<>(DEFAULT);
        List<String> out = new ArrayList<>();
        for (String p : stored.split(",")) {
            String t = p.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    public static String serialize(List<String> dock) {
        StringBuilder sb = new StringBuilder();
        for (String p : dock) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    /** Moves {@code pkg} one step left (delta -1) or right (+1) among the visible entries; hidden ones keep their slots. */
    public static List<String> move(List<String> dock, List<String> visible, String pkg, int delta) {
        int vi = visible.indexOf(pkg);
        int target = vi + delta;
        if (vi < 0 || target < 0 || target >= visible.size()) return new ArrayList<>(dock);
        List<String> out = new ArrayList<>(dock);
        int a = out.indexOf(pkg), b = out.indexOf(visible.get(target));
        if (a < 0 || b < 0) return out;
        Collections.swap(out, a, b);
        return out;
    }

    public static List<String> remove(List<String> dock, String pkg) {
        List<String> out = new ArrayList<>(dock);
        out.remove(pkg);
        return out;
    }

    public static List<String> add(List<String> dock, String pkg) {
        List<String> out = new ArrayList<>(dock);
        if (!out.contains(pkg)) out.add(pkg);
        return out;
    }

    /**
     * Entries to show: stored order, minus apps that are not currently usable (uninstalled or disabled).
     * Unusable entries stay stored, so an app that comes back reappears in its old place.
     */
    public static List<String> visible(List<String> dock, Predicate usable) {
        List<String> out = new ArrayList<>();
        for (String p : dock) if (usable.test(p)) out.add(p);
        return out;
    }

    /** Minimal functional interface (java.util.function needs API 24 desugaring; this keeps core dependency-free). */
    public interface Predicate {
        boolean test(String pkg);
    }
}
