package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The AFTVnews short codes the owner keeps for the official Downloader app, and how a spoken or typed code is read. Pure Java, no
 * Android. A code is 1 to 16 digits (the numbers on go.aftvnews.com); it is sent to Downloader as {@code http://aftv.news/<code>}.
 */
public final class DownloaderCodes {
    public static final int MAX_CODES = 40, MAX_LABEL = 40;
    /** The short-link address a code is sent as. It is passed to the Downloader app; this launcher never fetches it. */
    public static final String LINK_PREFIX = "http://aftv.news/";

    public static final class Entry {
        public final String code, label;

        public Entry(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private DownloaderCodes() { }

    public static boolean isCode(String value) {
        return value != null && value.matches("[0-9]{1,16}");
    }

    public static String url(String code) {
        if (!isCode(code)) throw new IllegalArgumentException("A code is 1 to 16 digits.");
        return LINK_PREFIX + code;
    }

    /** A label with control characters removed, trimmed and cut to {@link #MAX_LABEL}. */
    public static String cleanLabel(String label) {
        if (label == null) return "";
        String s = label.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > MAX_LABEL ? s.substring(0, MAX_LABEL).trim() : s;
    }

    /** The list with {@code code} added (or its name replaced when it is already there). Throws for a bad code or a full list. */
    public static List<Entry> add(List<Entry> list, String code, String label) {
        String c = code == null ? "" : code.trim();
        if (!isCode(c)) throw new IllegalArgumentException("A code is 1 to 16 digits.");
        List<Entry> out = new ArrayList<>(list);
        String name = cleanLabel(label);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).code.equals(c)) {
                out.set(i, new Entry(c, name.isEmpty() ? out.get(i).label : name));
                return out;
            }
        }
        if (out.size() >= MAX_CODES) throw new IllegalArgumentException("The list is full. Remove a code first.");
        out.add(new Entry(c, name));
        return out;
    }

    public static List<Entry> remove(List<Entry> list, String code) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : list) if (!e.code.equals(code)) out.add(e);
        return out;
    }

    /** One line per code: the code, a tab, the name. Anything that is not a valid line is dropped. */
    public static String serialize(List<Entry> list) {
        StringBuilder b = new StringBuilder();
        for (Entry e : list) b.append(e.code).append('\t').append(cleanLabel(e.label)).append('\n');
        return b.toString();
    }

    public static List<Entry> parse(String stored) {
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        List<Entry> out = new ArrayList<>();
        for (String line : stored.split("\n")) {
            int tab = line.indexOf('\t');
            String code = (tab < 0 ? line : line.substring(0, tab)).trim();
            if (!isCode(code) || out.size() >= MAX_CODES) continue;
            boolean seen = false;
            for (Entry e : out) seen |= e.code.equals(code);
            if (!seen) out.add(new Entry(code, tab < 0 ? "" : cleanLabel(line.substring(tab + 1))));
        }
        return out;
    }

    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    /**
     * Reads what follows "download code": digits as typed ("1234567", "123 456"), or spoken one digit at a time ("one two three").
     * Filler words ("number", "to downloader", "please") are ignored. Returns null when it is not a code (no digits, a word that is
     * not a digit, a number said as a quantity such as "twelve", or more than 16 digits).
     */
    public static String spokenDigits(String text) {
        if (text == null) return null;
        StringBuilder digits = new StringBuilder();
        for (String token : text.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.matches("[0-9]+")) { digits.append(token); continue; }
            int word = -1;
            for (int i = 0; i < WORDS.length; i++) if (WORDS[i].equals(token)) word = i;
            if (token.equals("oh") || token.equals("o")) word = 0;
            if (word >= 0) { digits.append(word); continue; }
            if (token.equals("number") || token.equals("the") || token.equals("to") || token.equals("in") || token.equals("into")
                    || token.equals("downloader") || token.equals("please") || token.equals("a") || token.equals("code")) continue;
            return null;
        }
        return isCode(digits.toString()) ? digits.toString() : null;
    }
}
