package tv.calmhome.launcher.core;

import java.util.List;
import java.util.Locale;

/**
 * Turns a typed Assistant command into a local action. Local only: it matches against apps and inputs
 * that are actually present on the TV and never pretends to understand anything else.
 *
 * Understood: "home", "apps"/"all apps", "inputs", "settings", "wallpapers",
 *             "open <app>" / "launch <app>" / just "<app>", "switch to <input>" / "input <input>".
 */
public final class CommandParser {

    public enum Kind { HOME, ALL_APPS, INPUTS, SETTINGS, WALLPAPERS, OPEN_APP, SWITCH_INPUT, UNKNOWN }

    public static final class Result {
        public final Kind kind;
        /** Package name for OPEN_APP, input id for SWITCH_INPUT, otherwise null. */
        public final String target;
        /** Human label of the target (app or input name). */
        public final String label;

        Result(Kind kind, String target, String label) {
            this.kind = kind;
            this.target = target;
            this.label = label;
        }
    }

    /** A named, launchable thing on this TV (app or input). */
    public static final class Named {
        public final String id, name;

        public Named(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private CommandParser() { }

    public static Result parse(String raw, List<Named> apps, List<Named> inputs) {
        String s = normalize(raw);
        if (s.isEmpty()) return new Result(Kind.UNKNOWN, null, null);
        switch (s) {
            case "home": case "go home": case "return home": return new Result(Kind.HOME, null, "Home");
            case "apps": case "all apps": case "show apps": case "open apps": return new Result(Kind.ALL_APPS, null, "All Apps");
            case "inputs": case "show inputs": case "sources": return new Result(Kind.INPUTS, null, "Inputs");
            case "settings": case "open settings": return new Result(Kind.SETTINGS, null, "Settings");
            case "wallpapers": case "wallpaper": return new Result(Kind.WALLPAPERS, null, "Wallpapers");
            default: break;
        }
        for (String prefix : new String[]{"switch to ", "input ", "go to "}) {
            if (s.startsWith(prefix)) {
                Named n = best(s.substring(prefix.length()), inputs);
                if (n != null) return new Result(Kind.SWITCH_INPUT, n.id, n.name);
            }
        }
        String appQuery = s;
        for (String prefix : new String[]{"open ", "launch ", "start ", "play "}) {
            if (s.startsWith(prefix)) appQuery = s.substring(prefix.length());
        }
        Named app = best(appQuery, apps);
        if (app != null) return new Result(Kind.OPEN_APP, app.id, app.name);
        Named input = best(s, inputs);
        if (input != null) return new Result(Kind.SWITCH_INPUT, input.id, input.name);
        return new Result(Kind.UNKNOWN, null, null);
    }

    /** Exact name match wins, then a name that starts with the query, then one that contains it. Partial matches need 3+ chars. */
    static Named best(String query, List<Named> items) {
        String q = normalize(query);
        if (q.isEmpty()) return null;
        for (Named n : items) if (normalize(n.name).equals(q)) return n;
        if (q.length() < 3) return null;
        for (Named n : items) if (normalize(n.name).startsWith(q)) return n;
        for (Named n : items) if (normalize(n.name).contains(q)) return n;
        return null;
    }

    static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9+ ]", " ").replaceAll("\\s+", " ").trim();
        if (t.startsWith("please ")) t = t.substring(7);
        if (t.endsWith(" please")) t = t.substring(0, t.length() - 7);
        return t;
    }
}
