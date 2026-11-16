package tv.calmhome.launcher.core;

/**
 * Watches remote presses for Up, Up, Down, Down, Left, Right, Left, Right. It only observes: the caller still lets every key move
 * focus as usual. The buffer is bounded (the sequence's own length) and forgets a half-typed sequence after a pause, so ordinary
 * browsing cannot slowly build it up. Pure Java: unit-tested off-device.
 */
public final class KonamiDetector {
    public enum Key { UP, DOWN, LEFT, RIGHT, OTHER }

    static final Key[] SEQUENCE = {Key.UP, Key.UP, Key.DOWN, Key.DOWN, Key.LEFT, Key.RIGHT, Key.LEFT, Key.RIGHT};
    /** A pause longer than this between two presses starts over. */
    public static final long RESET_MS = 2500;

    private int matched;
    private long lastAt = Long.MIN_VALUE;

    /** Records one key press (a key-down, not a repeat) at {@code nowMs}; true when it completes the sequence. */
    public boolean press(Key key, long nowMs) {
        if (lastAt != Long.MIN_VALUE && nowMs - lastAt > RESET_MS) matched = 0;
        lastAt = nowMs;
        if (key == SEQUENCE[matched]) {
            matched++;
        } else {
            // Up, Up, Up, Down... must still work: fall back to the longest prefix this key ends. Only Up can start one: after
            // "Up Up" an extra Up still leaves "Up Up"; anywhere else an Up leaves just "Up".
            matched = key == Key.UP ? (matched == 2 ? 2 : 1) : 0;
        }
        if (matched == SEQUENCE.length) {
            matched = 0;
            return true;
        }
        return false;
    }

    /** Forgets any half-typed sequence (text entry started, a dialog opened). */
    public void reset() {
        matched = 0;
        lastAt = Long.MIN_VALUE;
    }

    int progress() {
        return matched;
    }
}
