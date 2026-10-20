package tv.calmhome.launcher.core;

/** Time-of-day greeting for the Home screen. Uses only the local clock; no other context is invented. */
public final class Greeting {

    public static final String TAGLINE = "Relax · Watch · Explore";

    private Greeting() { }

    /** @param hour 0-23 in local time */
    public static String forHour(int hour) {
        if (hour >= 5 && hour < 12) return "Good Morning";
        if (hour >= 12 && hour < 17) return "Good Afternoon";
        if (hour >= 17 && hour < 22) return "Good Evening";
        return "Good Night";
    }

    /** Milliseconds until the greeting can next change (top of the next hour). */
    public static long millisToNextHour(long nowMillisOfHour) {
        long hourMs = 60L * 60L * 1000L;
        return hourMs - (nowMillisOfHour % hourMs);
    }
}
