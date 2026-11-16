package tv.calmhome.launcher.core;

import java.util.List;

/**
 * What Home says about downloads, from the real records only: the one to show (the first in the queue that is actually moving),
 * its percentage when the source gave a size (otherwise none: an indeterminate ring, never a made-up number), its speed from two
 * real samples, and a time left only when both are known. Pure Java: unit-tested off-device; the Android side converts download
 * records into {@link Item}s.
 */
public final class DownloadSummary {

    public enum Status { IDLE, QUEUED, DOWNLOADING, VERIFYING, AWAITING_APPROVAL, COMPLETED, FAILED }

    /** One download as Home sees it. {@code total} is -1 when the source did not say how big the file is. */
    public static final class Item {
        public final long id, bytes, total;
        public final String name;
        public final Status status;

        public Item(long id, String name, Status status, long bytes, long total) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.bytes = bytes;
            this.total = total;
        }
    }

    public final Status status;
    /** The download shown, or null when idle. */
    public final Item shown;
    /** How many are moving (queued, downloading or verifying). */
    public final int active;
    /** 0..100, or -1 when unknown. */
    public final int percent;
    /** Bytes per second from the last two samples, or -1 when unknown. */
    public final long bytesPerSecond;
    /** Seconds left, or -1 when it cannot be estimated honestly. */
    public final long secondsLeft;
    /** Aggregate 0..1 over the downloads with a known size, or -1 when none has one. */
    public final float aggregate;

    private DownloadSummary(Status status, Item shown, int active, int percent, long bps, long left, float aggregate) {
        this.status = status;
        this.shown = shown;
        this.active = active;
        this.percent = percent;
        this.bytesPerSecond = bps;
        this.secondsLeft = left;
        this.aggregate = aggregate;
    }

    public static final DownloadSummary IDLE = new DownloadSummary(Status.IDLE, null, 0, -1, -1, -1, -1f);

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.VERIFYING;
    }

    /**
     * The summary for {@code items}. {@code previous} (may be null) and {@code elapsedMs} give the speed: the same download's bytes
     * a known time ago.
     */
    public static DownloadSummary of(List<Item> items, DownloadSummary previous, long elapsedMs) {
        if (items == null || items.isEmpty()) return IDLE;
        Item shown = null;
        int active = 0;
        long knownBytes = 0, knownTotal = 0;
        for (Item it : items) {
            boolean moving = it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.VERIFYING;
            if (!moving) continue;
            active++;
            if (it.total > 0) { knownBytes += Math.min(it.bytes, it.total); knownTotal += it.total; }
            if (shown == null || rank(it.status) > rank(shown.status)) shown = it;
        }
        float aggregate = knownTotal > 0 ? Math.min(1f, knownBytes / (float) knownTotal) : -1f;
        if (shown == null) return IDLE;
        int percent = shown.total > 0 && shown.bytes >= 0 ? (int) Math.min(100, Math.floor(100d * shown.bytes / shown.total)) : -1;
        long bps = -1;
        if (previous != null && previous.shown != null && previous.shown.id == shown.id && elapsedMs > 0 && shown.bytes >= previous.shown.bytes
                && shown.status == Status.DOWNLOADING) {
            bps = Math.round((shown.bytes - previous.shown.bytes) * 1000d / elapsedMs);
        }
        long left = bps > 0 && shown.total > 0 ? (shown.total - shown.bytes) / bps : -1;
        return new DownloadSummary(shown.status, shown, active, percent, bps, left, aggregate);
    }

    private static int rank(Status s) {
        switch (s) {
            case DOWNLOADING: return 3;
            case VERIFYING: return 2;
            case QUEUED: return 1;
            default: return 0;
        }
    }

    /** "2.4 MB/s", "380 KB/s", or null when unknown. */
    public static String speed(long bps) {
        if (bps < 0) return null;
        if (bps >= 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB/s", bps / 1048576d);
        return Math.max(1, Math.round(bps / 1024d)) + " KB/s";
    }

    /** "About 2 min left", "Less than a minute left", or null when unknown. */
    public static String timeLeft(long seconds) {
        if (seconds < 0) return null;
        if (seconds < 60) return "Less than a minute left";
        long min = Math.round(seconds / 60d);
        return min >= 120 ? "About " + Math.round(min / 60d) + " h left" : "About " + min + " min left";
    }

    public static String statusText(Status s) {
        switch (s) {
            case QUEUED: return "Waiting to start";
            case DOWNLOADING: return "Downloading";
            case VERIFYING: return "Checking the file";
            case AWAITING_APPROVAL: return "Ready to install";
            case COMPLETED: return "Downloaded";
            case FAILED: return "Download failed";
            default: return "";
        }
    }
}
