package tv.calmhome.launcher.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What's New for this build: real release notes only (each line describes a change that is in this build), and whether the viewer
 * has already seen them. The notice shows once per version name; opening the notes or dismissing them records the version.
 * Pure Java: unit-tested off-device.
 */
public final class WhatsNew {
    private WhatsNew() { }

    public static final class Notes {
        public final String title;
        public final List<String> highlights, fixes, performance;

        Notes(String title, List<String> highlights, List<String> fixes, List<String> performance) {
            this.title = title;
            this.highlights = Collections.unmodifiableList(highlights);
            this.fixes = Collections.unmodifiableList(fixes);
            this.performance = Collections.unmodifiableList(performance);
        }
    }

    /** The notes for this build (3.0.0-beta.7: the glass on every screen, the Favorites carousel, the Assistant removed). */
    public static Notes current() {
        return new Notes("One glass, everywhere",
                Arrays.asList(
                        "Apps, Downloads, Inputs, Settings and menus now use the same Liquid Glass as Home",
                        "The Favorites row glides to keep the app you reach fully on screen",
                        "Changing screens keeps the navigation still; only the content moves",
                        "Downloads show real progress, speed and how each download ended",
                        "Settings switches are glass, and change only when you press OK",
                        "All Apps has a System view, and holding OK offers Open, Favorites, App info and Uninstall"),
                Arrays.asList(
                        "The Assistant (JARVIS), its AI accounts and voice were removed; what they stored is deleted",
                        "All Apps rows no longer scroll over the title and the search field",
                        "After Back, Home's navigation marks Home again, not the screen you visited",
                        "Every wallpaper collection shows its full name and count",
                        "Navigation now has five destinations: Home, Apps, Downloads, Inputs, Settings"),
                Arrays.asList(
                        "The new header takes over only once its glass is ready, so changing screens never flashes a grey bar"));
    }

    /** True when {@code version} has notes the viewer has not seen ({@code dismissed} is the last version they saw, or null). */
    public static boolean unseen(String version, String dismissed) {
        return version != null && !version.isEmpty() && !version.equals(dismissed);
    }
}
