package tv.calmhome.launcher.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.DownloadSummary;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;

/**
 * Keeps Home told about the real download queue: a summary (see {@link DownloadSummary}) and a word when a download finishes or fails.
 * The queue is a SQLite table, so it is read on one background thread, never on the UI thread: every second while something is moving,
 * every four seconds otherwise, and only between {@link #start} and {@link #stop} (while Home is on screen).
 */
public final class DownloadWatcher {

    public interface Listener {
        /** On the UI thread: the latest summary (the same object when nothing changed is not guaranteed). */
        void onSummary(DownloadSummary summary);

        /** On the UI thread: a download that was moving has finished ({@code ok}) or failed. */
        void onFinished(String name, boolean ok);
    }

    /** Where the records come from (a seam for tests). */
    public interface Source {
        List<DownloadRecord> list();
    }

    static final long ACTIVE_MS = 1000, IDLE_MS = 4000;

    private final Source source;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zp-download-watch");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;
    private DownloadSummary last = DownloadSummary.IDLE;
    private long lastAt;
    private final Set<Long> moving = new HashSet<>();
    private boolean primed;

    public DownloadWatcher(Context c, Listener listener) {
        this(() -> Downloads.get(c).list(), listener);
    }

    public DownloadWatcher(Source source, Listener listener) {
        this.source = source;
        this.listener = listener;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            io.execute(DownloadWatcher.this::poll);
        }
    };

    public void start() {
        if (running) return;
        running = true;
        main.post(tick);
    }

    public void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    public void shutdown() {
        stop();
        io.shutdownNow();
    }

    public DownloadSummary last() {
        return last;
    }

    private void poll() {
        List<DownloadRecord> records;
        try {
            records = source.list();
        } catch (RuntimeException e) {
            records = new ArrayList<>();                      // a storage hiccup: report nothing rather than guess
        }
        List<DownloadSummary.Item> items = new ArrayList<>();
        List<String[]> finished = new ArrayList<>();
        Set<Long> nowMoving = new HashSet<>();
        for (DownloadRecord r : records) {
            DownloadSummary.Status s = status(r.state);
            items.add(new DownloadSummary.Item(r.id, r.displayName(), s, r.bytes, r.total > 0 ? r.total : -1));
            boolean isMoving = s == DownloadSummary.Status.QUEUED || s == DownloadSummary.Status.DOWNLOADING || s == DownloadSummary.Status.VERIFYING;
            if (isMoving) nowMoving.add(r.id);
            else if (primed && moving.contains(r.id)) finished.add(new String[]{r.displayName(), s == DownloadSummary.Status.FAILED ? "0" : "1"});
        }
        long now = SystemClock.uptimeMillis();
        final DownloadSummary summary = DownloadSummary.of(items, last, lastAt == 0 ? 0 : now - lastAt);
        last = summary;
        lastAt = now;
        moving.clear();
        moving.addAll(nowMoving);
        primed = true;
        main.post(() -> {
            if (!running) return;
            listener.onSummary(summary);
            for (String[] f : finished) listener.onFinished(f[0], "1".equals(f[1]));
            main.postDelayed(tick, summary.isActive() ? ACTIVE_MS : IDLE_MS);
        });
    }

    static DownloadSummary.Status status(DownloadRecord.State s) {
        if (s == null) return DownloadSummary.Status.IDLE;
        switch (s) {
            case QUEUED: return DownloadSummary.Status.QUEUED;
            case DOWNLOADING: return DownloadSummary.Status.DOWNLOADING;
            case VERIFYING: return DownloadSummary.Status.VERIFYING;
            case AWAITING_APPROVAL: return DownloadSummary.Status.AWAITING_APPROVAL;
            case COMPLETED:
            case INSTALLED: return DownloadSummary.Status.COMPLETED;
            case FAILED: return DownloadSummary.Status.FAILED;
            default: return DownloadSummary.Status.IDLE;      // cancelled: nothing to say
        }
    }
}
