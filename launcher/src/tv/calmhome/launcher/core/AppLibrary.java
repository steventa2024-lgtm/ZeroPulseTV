package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rules shared by the recent shelf and installed-app search. The inputs come from PackageManager. */
public final class AppLibrary {
    public static final int RECENT_LIMIT = 6;
    private AppLibrary() { }
    public static List<String> record(List<String> previous, String launched) {
        List<String> result = new ArrayList<>();
        result.add(launched);
        for (String pkg : previous) {
            if (!result.contains(pkg)) result.add(pkg);
            if (result.size() == RECENT_LIMIT) break;
        }
        return result;
    }
    public static boolean matches(String query, String label, String pkg) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return label.toLowerCase(Locale.ROOT).contains(needle) || pkg.toLowerCase(Locale.ROOT).contains(needle);
    }
}
