package tv.calmhome.launcher.core;

/**
 * The one place the launcher reads the wall clock for what it shows (the clock, the date, the greeting). In the app it is
 * {@code System.currentTimeMillis()}, always. The seam exists so a test or a screenshot run can show a chosen time without the
 * app ever holding a time of its own: production code never calls {@link #useForTests} (a guard test reads the sources to check),
 * and nothing is stored, so a build never carries a test time.
 */
public final class SystemTime {
    private SystemTime() { }

    /** A source of time in milliseconds since the epoch. */
    public interface Source {
        long now();
    }

    private static final Source SYSTEM = System::currentTimeMillis;
    private static volatile Source source = SYSTEM;

    /** The time to show, in milliseconds since the epoch: the system clock unless a test has replaced it. */
    public static long now() {
        return source.now();
    }

    /** Tests and screenshot runs only: show this time instead of the system's. Always undo with {@link #reset}. */
    public static void useForTests(Source replacement) {
        source = replacement == null ? SYSTEM : replacement;
    }

    public static void reset() {
        source = SYSTEM;
    }

    /** True while the system clock is in use (nothing has replaced it). */
    public static boolean isSystemClock() {
        return source == SYSTEM;
    }
}
