package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two optional ZeroPulse modules and the sections inside Streaming. Pure logic (no Android types), so the
 * rules are unit-tested on the JVM.
 *
 * A module that is off has no entry point, refuses to open and keeps its saved data. These are switches in the
 * app, not Android permission isolation: every module shares the app's identity and manifest permissions.
 */
public final class Features {

    public enum Module {
        STREAMING("ZeroPulse Streaming", "Streaming"),
        WEB("ZeroPulse Web", "Web");

        public final String title, tile;

        Module(String title, String tile) {
            this.title = title;
            this.tile = tile;
        }
    }

    /** Destinations inside ZeroPulse Streaming, in display order. */
    public enum Section {
        LIVE_TV("live", "Live TV"),
        MOVIES("movies", "Movies"),
        SHOWS("shows", "TV Shows"),
        SPORTS("sports", "Sports");

        /** Stable preference key suffix: never rename. */
        public final String key;
        public final String label;

        Section(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** What the app knows about the user's switches. */
    public interface State {
        boolean enabled(Module m);
        boolean sectionEnabled(Section s);
    }

    private Features() { }

    /** Module tiles shown at the end of the Home dock, in order. Empty when both modules are off. */
    public static List<Module> homeEntries(State s) {
        List<Module> out = new ArrayList<>();
        for (Module m : Module.values()) if (s.enabled(m)) out.add(m);
        return Collections.unmodifiableList(out);
    }

    /** Streaming sections the user can reach. Empty when Streaming itself is off. */
    public static List<Section> sections(State s) {
        List<Section> out = new ArrayList<>();
        if (!s.enabled(Module.STREAMING)) return out;
        for (Section x : Section.values()) if (s.sectionEnabled(x)) out.add(x);
        return out;
    }

    /** Whether opening {@code m} (from Home, Settings, an intent extra or the Assistant) is allowed. */
    public static boolean mayOpen(State s, Module m) {
        return s.enabled(m);
    }

    /** Whether a Streaming section may be shown. */
    public static boolean mayOpen(State s, Section x) {
        return s.enabled(Module.STREAMING) && s.sectionEnabled(x);
    }
}
