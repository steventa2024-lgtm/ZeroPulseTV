package tv.calmhome.launcher.downloads;

/** Snapshot of a persistent download. Private APKs are named only with database IDs. */
public final class DownloadRecord {
    public enum State { QUEUED, DOWNLOADING, VERIFYING, COMPLETED, AWAITING_APPROVAL, INSTALLED, FAILED, CANCELED }
    public long id, bytes, total, versionCode;
    public String url, finalUrl, fileName, expectedSha256, sha256, packageName, versionName, permissions, error, appName;
    public State state;
    public int minSdk;
    boolean inspected;
    int sessionId, jobId;
    long attempt;
    boolean acceptsAttempt(long token) { return attempt == token && transferActive(); }
    boolean acceptsInstallResult(int session) {
        return state == State.AWAITING_APPROVAL && session >= 0 && sessionId == session;
    }
    static State installOutcome(boolean success, boolean aborted, boolean detected) {
        if (success && detected) return State.INSTALLED;
        return aborted ? State.CANCELED : State.FAILED;
    }
    boolean transferActive() {
        return state == State.QUEUED || state == State.DOWNLOADING || state == State.VERIFYING;
    }

    public boolean inActiveQueue() { return transferActive() || state == State.AWAITING_APPROVAL; }

    /** -1 means the source did not advertise a usable size. */
    public int percentTransferred() {
        if (total <= 0 || bytes < 0) return -1;
        return (int) Math.min(100, Math.floor(100d * bytes / total));
    }
}
