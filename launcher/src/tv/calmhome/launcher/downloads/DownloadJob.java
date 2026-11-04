package tv.calmhome.launcher.downloads;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Persisted JobScheduler jobs survive reboot; interrupted attempts always restart in a new private file. */
public final class DownloadJob extends JobService {
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Map<Integer, Future<?>> active = new HashMap<>();
    private final Map<Integer, DownloadRecord> attempts = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public boolean onStartJob(JobParameters params) {
        Downloads downloads = Downloads.get(this);
        int id = params.getJobId();
        DownloadRecord r = downloads.beginAttempt(id);
        if (r == null) return false;
        attempts.put(id, r);
        active.put(id, pool.submit(() -> {
            File part = downloads.part(r);
            try {
                Transfer.run(this, downloads, r, part);
            } catch (InterruptedIOException e) {
                if (!Thread.currentThread().isInterrupted())
                    downloads.failTransfer(r, "The network timed out. Check the connection and retry.");
            } catch (Exception e) {
                downloads.failTransfer(r, Transfer.message(e, part));
            } finally {
                if (part.exists()) part.delete();
                main.post(() -> {
                    if (attempts.get(id) == r) {
                        attempts.remove(id); active.remove(id); jobFinished(params, false);
                    }
                });
            }
        }));
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        int id = params.getJobId();
        Future<?> task = active.remove(id);
        if (task != null) task.cancel(true);
        DownloadRecord r = attempts.remove(id);
        return r != null && Downloads.get(this).interrupted(id, r.attempt);
    }

    @Override public void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }
}
