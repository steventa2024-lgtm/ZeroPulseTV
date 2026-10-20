package tv.calmhome.launcher.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A TV action JARVIS may suggest at the end of a reply: [[tv:open:YouTube]], [[tv:input:HDMI 2]] or
 * [[tv:home]]. It is only ever shown as a button; the Assistant resolves the name against the TV's own
 * installed apps and inputs (CommandParser) and runs it when the viewer presses OK. Anything else in the
 * tag is ignored.
 */
public final class TvSuggestion {

    public enum Kind { OPEN, INPUT, HOME }

    private static final Pattern TAG = Pattern.compile("\\[\\[tv:(open|input|home)(?::([^\\]\\[]{1,60}))?\\]\\]",
            Pattern.CASE_INSENSITIVE);

    public final Kind kind;
    public final String target;   // app or input name as JARVIS wrote it; null for HOME

    private TvSuggestion(Kind kind, String target) {
        this.kind = kind;
        this.target = target;
    }

    /** The last well-formed tag in the reply, or null. */
    public static TvSuggestion find(String reply) {
        if (reply == null) return null;
        Matcher m = TAG.matcher(reply);
        TvSuggestion found = null;
        while (m.find()) {
            String k = m.group(1).toLowerCase(Locale.ROOT);
            String t = m.group(2) == null ? null : m.group(2).trim();
            if (k.equals("home")) found = new TvSuggestion(Kind.HOME, null);
            else if (t != null && !t.isEmpty()) found = new TvSuggestion(k.equals("open") ? Kind.OPEN : Kind.INPUT, t);
        }
        return found;
    }

    /** The reply without tags, light Markdown or runs of blank lines, for display on the TV. */
    public static String clean(String reply) {
        if (reply == null) return "";
        String s = TAG.matcher(reply).replaceAll("");
        s = s.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** Text CommandParser understands, so the TV's own matching decides what (if anything) runs. */
    public String asCommand() {
        switch (kind) {
            case HOME: return "home";
            case INPUT: return "switch to " + target;
            default: return "open " + target;
        }
    }
}
