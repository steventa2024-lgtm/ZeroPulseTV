package tv.calmhome.launcher.core;

/**
 * What the centre of Home shows, inside the greeting's own fixed bounds. Precedence: an active download, then a temporary notice,
 * then (only when the viewer turned it on) a featured installed app, otherwise the greeting, which is always the default. A state
 * is held for a minimum time so the centre never flickers between states; a download or a notice always wins at once. Featured
 * apps alternate with the greeting by elapsed time (the greeting for {@link #GREETING_TURN_MS}, then a featured app for
 * {@link #FEATURED_TURN_MS}), counted from when the greeting came back. Pure Java: unit-tested off-device.
 */
public final class HeroState {
    public enum Kind { GREETING, ACTIVE_DOWNLOAD, NOTIFICATION, FEATURED }

    /** A state is kept at least this long before a lower-priority one may replace it. */
    public static final long MIN_HOLD_MS = 2500;
    /** A notice shows this long. */
    public static final long NOTICE_MS = 4500;
    /** With featured apps on: the greeting shows this long, then a featured app this long, in turn. */
    public static final long GREETING_TURN_MS = 40_000, FEATURED_TURN_MS = 12_000;

    private Kind kind = Kind.GREETING;
    private long since = Long.MIN_VALUE;
    private String notice;
    private long noticeUntil = Long.MIN_VALUE;

    public Kind kind() {
        return kind;
    }

    /** When the current state began (diagnostics). */
    public long since() {
        return since;
    }

    public String notice() {
        return notice;
    }

    /** Shows {@code text} for {@link #NOTICE_MS} from {@code nowMs} (a download finished, a secret found). */
    public void post(String text, long nowMs) {
        notice = text;
        noticeUntil = nowMs + NOTICE_MS;
    }

    /**
     * The state at {@code nowMs} (a monotonic clock). {@code downloading}: a real download is moving. {@code featuredAllowed}: the viewer
     * turned featured apps on and an installed app with real artwork exists. Returns true when the state changed.
     */
    public boolean update(long nowMs, boolean downloading, boolean featuredAllowed) {
        if (since == Long.MIN_VALUE) since = nowMs;                  // the greeting has been showing since the first update
        Kind want;
        if (downloading) want = Kind.ACTIVE_DOWNLOAD;
        else if (notice != null && nowMs < noticeUntil) want = Kind.NOTIFICATION;
        else if (featuredAllowed && featuredTurn(nowMs)) want = Kind.FEATURED;
        else want = Kind.GREETING;
        if (notice != null && nowMs >= noticeUntil) notice = null;
        if (want == kind) return false;
        boolean urgent = want == Kind.ACTIVE_DOWNLOAD || want == Kind.NOTIFICATION;
        if (!urgent && kind != Kind.GREETING && nowMs - since < MIN_HOLD_MS) return false;
        kind = want;
        since = nowMs;
        return true;
    }

    /** True when a featured app's turn is due: the greeting has had its turn, or a featured app is still within its own. */
    boolean featuredTurn(long nowMs) {
        if (kind == Kind.GREETING) return nowMs - since >= GREETING_TURN_MS;
        if (kind == Kind.FEATURED) return nowMs - since < FEATURED_TURN_MS;
        return false;                                                 // after a download or a notice, the greeting comes first
    }
}
