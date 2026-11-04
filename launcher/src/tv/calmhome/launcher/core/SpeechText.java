package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.List;

/** Text boundaries for external recognition results and Android's per-utterance length limit. */
public final class SpeechText {
    private SpeechText() { }

    public static String transcript(List<String> results) {
        if (results != null) {
            for (String candidate : results) {
                if (candidate != null && !candidate.trim().isEmpty()) return candidate.trim();
            }
        }
        return "";
    }

    public static List<String> utterances(String text, int limit) {
        if (limit < 2) throw new IllegalArgumentException("Utterance limit must fit a Unicode code point");
        List<String> parts = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > limit) {
            int end = limit;
            if (Character.isHighSurrogate(remaining.charAt(end - 1)) &&
                    Character.isLowSurrogate(remaining.charAt(end))) end--;
            for (int i = end; i > end / 2; i--) {
                if (Character.isWhitespace(remaining.charAt(i - 1))) { end = i; break; }
            }
            parts.add(remaining.substring(0, end));
            remaining = remaining.substring(end);
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }
}
