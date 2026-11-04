package tv.calmhome.launcher.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VoiceTests {
    private static int checks;

    public static void main(String[] args) {
        check(SpeechText.transcript(null).isEmpty(), "missing result extras");
        check(SpeechText.transcript(Collections.emptyList()).isEmpty(), "empty result list");
        check(SpeechText.transcript(Arrays.asList(null, "  ")).isEmpty(), "blank result");
        check(SpeechText.transcript(Arrays.asList("", "  Open downloads. ", "ignored")).equals("Open downloads."),
                "first nonempty result becomes an editable draft");
        check(SpeechText.utterances(null, 5).isEmpty(), "absent speech");
        check(SpeechText.utterances("   ", 5).isEmpty(), "blank speech");
        check(SpeechText.utterances(" hello ", 5).equals(Collections.singletonList("hello")), "one exact utterance");
        verify("a b c d e f g", 5);
        verify("z\uD83D\uDCE1abc", 2);
        verify("one.extendedword", 3);
        boolean rejected = false;
        try { SpeechText.utterances("text", 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid limit");
        System.out.println("Voice text checks: " + checks + " passed");
    }

    private static void verify(String text, int limit) {
        List<String> parts = SpeechText.utterances(text, limit);
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            check(part.length() <= limit && !part.isEmpty(), "bounded utterance");
            check(!Character.isHighSurrogate(part.charAt(part.length() - 1)) &&
                    !Character.isLowSurrogate(part.charAt(0)), "whole code points");
            joined.append(part);
        }
        check(joined.toString().equals(text), "no text lost");
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
        checks++;
    }
}
