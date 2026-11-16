package tv.calmhome.launcher.core;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XMLTV rules (pure Java, unit-tested): programme times, and matching playlist channels to guide channels.
 * Matching is exact on tvg-id first, then on a normalised name; anything else stays unmatched and is reported as
 * such rather than guessed.
 */
public final class Guide {

    private Guide() { }

    private static final Pattern TIME = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})?\\s*([+-]\\d{4}|Z|UTC|GMT)?.*");

    /** Result of reading an XMLTV time: epoch ms (or -1 if unreadable), and whether it carried a time zone. */
    public static final class Time {
        public final long ms;
        public final boolean zoned;

        Time(long ms, boolean zoned) {
            this.ms = ms;
            this.zoned = zoned;
        }
    }

    /**
     * "20261020203000 +0100" → epoch ms. Without an offset the XMLTV time zone is unknown; it is read as UTC and
     * {@link Time#zoned} is false, so the screen can say the guide's times may be off.
     */
    public static Time time(String s) {
        if (s == null) return new Time(-1, false);
        Matcher m = TIME.matcher(s.trim());
        if (!m.matches()) return new Time(-1, false);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.clear();
        int mon = Integer.parseInt(m.group(2)), day = Integer.parseInt(m.group(3));
        int h = Integer.parseInt(m.group(4)), min = Integer.parseInt(m.group(5));
        int sec = m.group(6) != null ? Integer.parseInt(m.group(6)) : 0;
        if (mon < 1 || mon > 12 || day < 1 || day > 31 || h > 23 || min > 59 || sec > 60) return new Time(-1, false);
        c.set(Integer.parseInt(m.group(1)), mon - 1, day, h, min, sec);
        long ms = c.getTimeInMillis();
        String z = m.group(7);
        if (z == null) return new Time(ms, false);
        if (z.startsWith("+") || z.startsWith("-")) {
            int off = Integer.parseInt(z.substring(1, 3)) * 60 + Integer.parseInt(z.substring(3, 5));
            if (off > 14 * 60) return new Time(-1, false);
            ms -= (z.startsWith("+") ? 1 : -1) * off * 60_000L;
        }
        return new Time(ms, true);
    }

    /** Name used to compare channels: folded, without punctuation and common quality suffixes. */
    public static String key(String name) {
        String k = ChannelFilter.fold(name).replaceAll("[^a-z0-9]+", " ").trim();
        k = k.replaceAll("\\b(hd|fhd|uhd|4k|sd|hevc|h265|tv)\\b", " ").replaceAll("\\s+", " ").trim();
        return k;
    }

    /** Index of guide channels: XMLTV id → display names. Built once per screen. */
    public static final class Index {
        private final Map<String, String> byId = new HashMap<>();
        private final Map<String, String> byName = new HashMap<>();
        private final java.util.Set<String> ambiguous = new java.util.HashSet<>();

        public void add(String xmltvId, Collection<String> displayNames) {
            if (xmltvId == null || xmltvId.isEmpty()) return;
            byId.put(xmltvId.toLowerCase(Locale.ROOT), xmltvId);
            for (String n : displayNames) {
                String k = key(n);
                if (k.isEmpty()) continue;
                String prev = byName.put(k, xmltvId);
                if (prev != null && !prev.equals(xmltvId)) ambiguous.add(k);
            }
        }

        /** Guide id for a playlist channel, or null when nothing matches unambiguously. */
        public String match(String tvgId, String tvgName, String name) {
            if (tvgId != null) {
                String id = byId.get(tvgId.trim().toLowerCase(Locale.ROOT));
                if (id != null) return id;
            }
            for (String n : new String[]{tvgName, name}) {
                String k = key(n);
                if (!k.isEmpty() && !ambiguous.contains(k) && byName.containsKey(k)) return byName.get(k);
            }
            return null;
        }

        public boolean isEmpty() {
            return byId.isEmpty();
        }
    }
}
