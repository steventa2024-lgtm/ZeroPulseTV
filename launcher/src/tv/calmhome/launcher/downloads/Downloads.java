package tv.calmhome.launcher.downloads;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.Settings;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.List;
import static tv.calmhome.launcher.downloads.DownloadRecord.State.*;

/** Process-wide owner of transfers. All record transitions and file publication are serialized. */
public final class Downloads {
    private static Downloads instance;
    final Context context;
    final DownloadStore store;
    final Installer installer;
    private final JobScheduler jobs;
    private final File dir;
    private final SharedPreferences scheduling;
    public static synchronized Downloads get(Context context) {
        if (instance == null) instance = new Downloads(context.getApplicationContext());
        return instance;
    }
    private Downloads(Context c) {
        context = c;
        store = new DownloadStore(c);
        scheduling = c.getSharedPreferences("download_jobs", Context.MODE_PRIVATE);
        installer = new Installer(this);
        jobs = c.getSystemService(JobScheduler.class);
        dir = new File(c.getNoBackupFilesDir(), "apks");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create private APK storage.");
        // Constructor runs before any workers in this process. Unpublished partial files can be discarded.
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.getName().endsWith(".part.apk")) f.delete();
            else if (f.getName().matches("[0-9]+\\.apk")) {
                long id = Long.parseLong(f.getName().substring(0, f.getName().length() - 4));
                DownloadRecord r = store.find(id);
                if (r == null || !r.inspected) f.delete();
            }
        }
        for (DownloadRecord r : store.list()) {
            if (r.transferActive() && jobs.getPendingJob(r.jobId) == null) schedule(r);
        }
    }
    public synchronized List<DownloadRecord> list() { return store.list(); }
    public synchronized DownloadRecord find(long id) { return store.find(id); }
    DownloadRecord require(long id) {
        DownloadRecord r = store.find(id);
        if (r == null) throw new IllegalArgumentException("Download was removed.");
        return r;
    }
    public synchronized long enqueue(String url, String expectedSha256) {
        URI uri = DownloadPolicy.validateUrl(url);
        return add(uri.toASCIIString(), expectedSha256, DownloadPolicy.fileName(uri));
    }
    public synchronized long importFile(Uri uri, String expectedSha256) {
        if (uri == null || !"content".equals(uri.getScheme()) || uri.getAuthority() == null)
            throw new IllegalArgumentException("Select an APK using Android's file picker.");
        DownloadPolicy.normalizeChecksum(expectedSha256);
        context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            String name = "selected.apk";
            try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    String label = c.getString(0).replaceAll("[\\p{Cntrl}/\\\\]", "_");
                    if (!label.isEmpty()) name = label.substring(0, Math.min(100, label.length()));
                }
            }
            return add(uri.toString(), expectedSha256, name);
        } catch (RuntimeException e) {
            releaseUnusedGrant(uri.toString());
            throw e;
        }
    }
    private long add(String url, String checksum, String name) {
        if (store.list().size() >= 100) throw new IllegalStateException("History is full. Remove an old download first.");
        long id = store.insert(url, DownloadPolicy.normalizeChecksum(checksum), name);
        schedule(require(id));
        return id;
    }
    private void schedule(DownloadRecord r) {
        try {
            // Android can deliver an old stop after a rapid Cancel → Retry. Allocate a fresh OS job
            // ID every time, while keeping the stable download history ID and private APK path.
            int last = scheduling.getInt("last", 0);
            for (DownloadRecord old : store.list()) last = Math.max(last, old.jobId);
            if (last == Integer.MAX_VALUE || !scheduling.edit().putInt("last", last + 1).commit())
                throw new IllegalStateException("Could not reserve a transfer job.");
            r.jobId = last + 1;
            store.save(r);
            PersistableBundle meta = new PersistableBundle();
            meta.putLong("download", r.id);
            JobInfo.Builder b = new JobInfo.Builder(r.jobId, new ComponentName(context, DownloadJob.class))
                    .setExtras(meta).setPersisted(true).setMinimumLatency(0);
            if (!r.url.startsWith("content://")) b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            if (jobs.schedule(b.build()) == JobScheduler.RESULT_SUCCESS) return;
        } catch (RuntimeException ignored) { }
        r.state = FAILED; r.error = "Android could not queue the transfer. Retry later."; store.save(r);
    }

    synchronized DownloadRecord beginAttempt(long id, int jobId) {
        DownloadRecord r = store.find(id);
        if (r == null || r.jobId != jobId || !r.transferActive()) return null;
        r.attempt++;
        resetProgress(r);
        r.state = DOWNLOADING;
        store.save(r);
        return r;
    }
    private void resetProgress(DownloadRecord r) {
        r.inspected = false; r.bytes = 0; r.total = -1; r.error = ""; r.sha256 = ""; r.finalUrl = r.url;
        r.packageName = ""; r.versionCode = 0; r.versionName = ""; r.minSdk = 0; r.permissions = ""; r.appName = "";
    }
    synchronized boolean isLatest(long id, long attempt) {
        DownloadRecord r = store.find(id);
        return r != null && r.attempt == attempt;
    }
    synchronized void progress(DownloadRecord r) throws InterruptedIOException {
        DownloadRecord current = store.find(r.id);
        if (current == null || !current.acceptsAttempt(r.attempt)) throw new InterruptedIOException();
        store.save(r);
    }
    synchronized void complete(DownloadRecord r, File part) throws IOException {
        progress(r);
        if (!part.renameTo(apk(r.id))) throw new IOException("Could not save the inspected APK.");
        r.inspected = true; r.state = COMPLETED; store.save(r);
    }
    synchronized void failTransfer(DownloadRecord r, String reason) {
        if (!isLatest(r.id, r.attempt)) return;
        r.state = FAILED; r.error = reason; store.save(r);
    }
    synchronized boolean interrupted(long id, long attempt) {
        DownloadRecord r = store.find(id);
        if (r == null || !r.acceptsAttempt(attempt)) return false;
        r.attempt++; r.state = QUEUED; resetProgress(r); store.save(r);
        return true;
    }
    public synchronized void cancel(long id) {
        DownloadRecord r = require(id);
        if (!r.transferActive()) throw new IllegalStateException("This transfer is no longer active.");
        r.attempt++; r.state = CANCELED; r.error = "Transfer canceled."; store.save(r);
        jobs.cancel(r.jobId);
    }
    public synchronized void retry(long id) {
        DownloadRecord r = require(id);
        if (r.state != FAILED && r.state != CANCELED) throw new IllegalStateException("This download cannot be retried.");
        if (r.inspected && apk(id).isFile()) {
            r.state = COMPLETED; r.error = ""; store.save(r);
        } else {
            resetProgress(r); r.state = QUEUED; store.save(r); schedule(r);
        }
    }
    public synchronized void remove(long id) {
        DownloadRecord r = require(id);
        if (r.state == AWAITING_APPROVAL) throw new IllegalStateException("Finish Android installation before removing this download.");
        if (r.transferActive()) cancel(id);
        File file = apk(id);
        if (file.exists() && !file.delete()) throw new IllegalStateException("Could not delete the private APK.");
        store.remove(id); releaseUnusedGrant(r.url);
    }
    private void releaseUnusedGrant(String url) {
        if (!url.startsWith("content://")) return;
        for (DownloadRecord r : store.list()) if (r.url.equals(url)) return;
        try {
            context.getContentResolver().releasePersistableUriPermission(Uri.parse(url), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    File part(DownloadRecord r) { return new File(dir, r.id + "-" + r.attempt + ".part.apk"); }
    File apk(long id) { return new File(dir, id + ".apk"); }
    public synchronized boolean isInstalled(long id) {
        DownloadRecord r = store.find(id);
        return r != null && r.state == INSTALLED && installer.detect(r);
    }
    public synchronized void resetApproval(long id) { installer.reset(require(id)); }

    public synchronized void install(Activity activity, long id) {
        DownloadRecord r = require(id);
        if (r.state == AWAITING_APPROVAL) { installer.resume(activity, r); return; }
        if (r.state != COMPLETED) throw new IllegalStateException("Finish APK inspection before installing.");
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            r.error = "Allow this source in Android settings, then select Install again."; store.save(r);
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.fromParts("package", context.getPackageName(), null)));
            } catch (android.content.ActivityNotFoundException e) {
                throw new IllegalStateException("This TV cannot open installation-source settings.");
            }
            return;
        }
        installer.start(activity, r);
    }
}
