package tv.calmhome.launcher;

import java.lang.reflect.Method;

/**
 * Bytes the calling thread allocates while a piece of code runs, from the JVM's per-thread allocation counter. The Robolectric
 * suites compile against the Android classpath (which has no java.lang.management), so the counter is reached by reflection;
 * the tests run on a real JVM where it exists.
 */
public final class AllocationMeter {
    private AllocationMeter() { }

    private static final Object BEAN;
    private static final Method BYTES;

    static {
        try {
            BEAN = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
            BYTES = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
            BYTES.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        // The JVM turns a reflective call into generated code after about fifteen calls, and that conversion allocates tens of kilobytes
        // once: do it now, so it is never counted against the code being measured.
        for (int i = 0; i < 200; i++) now();
    }

    @SuppressWarnings("deprecation")          // Thread.getId() is deprecated on newer JDKs; threadId() is not in every Android jar the suites compile against
    private static long now() {
        try {
            return (Long) BYTES.invoke(BEAN, Thread.currentThread().getId());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Bytes allocated by the current thread while {@code r} ran (the measurement itself allocates a few boxed longs, never more than a few hundred bytes). */
    public static long during(Runnable r) {
        now();                                                          // the first call warms the reflection path
        long before = now();
        r.run();
        return now() - before;
    }
}
