package tv.calmhome.launcher.downloads;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static tv.calmhome.launcher.downloads.DownloadRecord.State.*;

final class Installer {
    private final Downloads downloads;
    private final PackageInstaller os;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private final java.util.Set<Long> preparing = new java.util.HashSet<>();

    Installer(Downloads downloads) {
        this.downloads = downloads;
        os = downloads.context.getPackageManager().getPackageInstaller();
    }

    void start(Activity activity, DownloadRecord r) {
        foreground = new WeakReference<>(activity);
        r.state = AWAITING_APPROVAL; r.sessionId = -1; r.error = "Preparing the Android installer.";
        downloads.store.save(r);
        preparing.add(r.id);
        worker.execute(() -> stage(r));
    }

    private void stage(DownloadRecord r) {
        int sessionId = -1;
        boolean committed = false;
        try {
            File apk = downloads.apk(r.id);
            if (!apk.isFile() || apk.length() != r.bytes) throw new IOException("Download the APK again.");
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(r.packageName);
            p.setSize(apk.length());
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            sessionId = os.createSession(p);
            synchronized (downloads) {
                r.sessionId = sessionId; downloads.store.save(r);
            }
            try (PackageInstaller.Session session = os.openSession(sessionId)) {
                MessageDigest digest = DownloadPolicy.digest();
                try (FileInputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        digest.update(buffer, 0, n);
                    }
                    session.fsync(out);
                }
                DownloadPolicy.checkDigest(r.sha256, DownloadPolicy.hex(digest.digest()));
                commit(session, r);
                committed = true;
            }
        } catch (Exception e) {
            synchronized (downloads) {
                DownloadRecord current = downloads.store.find(r.id);
                if (current != null && current.state == AWAITING_APPROVAL && current.sessionId == sessionId) {
                    current.state = FAILED; current.error = "Android could not stage this APK. Check free storage and retry.";
                    downloads.store.save(current);
                }
            }
        } finally {
            if (!committed && sessionId >= 0) abandon(sessionId);
            synchronized (downloads) { preparing.remove(r.id); }
        }
    }

    private void commit(PackageInstaller.Session session, DownloadRecord r) {
        Intent callback = new Intent(downloads.context, InstallReceiver.class)
                .setData(identity(r)).setAction(InstallReceiver.RESULT);
        // PackageInstaller supplies the status and system confirmation Intent as fill-in extras.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent sender = PendingIntent.getBroadcast(downloads.context, r.sessionId, callback, flags);
        session.commit(sender.getIntentSender());
    }

    private Uri identity(DownloadRecord r) {
        return Uri.parse("zeropulse-install://session/" + r.id + "/" + r.sessionId);
    }
    private Intent prompt(DownloadRecord r) {
        return new Intent(downloads.context, ApprovalActivity.class).setData(identity(r));
    }
    private PendingIntent pendingPrompt(DownloadRecord r) {
        return PendingIntent.getActivity(downloads.context, r.sessionId, prompt(r),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }
    private void abandon(int sessionId) {
        try { os.abandonSession(sessionId); } catch (RuntimeException ignored) { }
    }

    // Only called by our non-exported receiver via the explicit PackageInstaller callback token.
    void result(Intent intent) {
        Uri data = intent.getData();
        if (data == null || data.getPathSegments().size() != 2) return;
        int session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        synchronized (downloads) {
            DownloadRecord r;
            try {
                if (Integer.parseInt(data.getLastPathSegment()) != session || session < 0) return;
                r = downloads.find(Long.parseLong(data.getPathSegments().get(0)));
            } catch (NumberFormatException e) { return; }
            if (r == null || !r.acceptsInstallResult(session)) return;
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @SuppressWarnings("deprecation")
                Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmation == null) {
                    abandon(session);
                    r.state = FAILED; r.error = "Android did not provide an approval screen.";
                } else {
                    Intent forward = prompt(r).putExtra(Intent.EXTRA_INTENT, confirmation);
                    PendingIntent.getActivity(downloads.context, session, forward,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    r.error = "Continue to Android to approve or cancel installation.";
                    Activity a = foreground.get();
                    if (a != null && !a.isFinishing() && !a.isDestroyed() && a.hasWindowFocus()) {
                        try { a.startActivity(forward); } catch (RuntimeException ignored) {
                            // The explicit Continue approval action can still use the saved system prompt.
                        }
                    }
                }
            } else {
                PendingIntent prompt = pendingPrompt(r);
                if (prompt != null) prompt.cancel();
                boolean success = status == PackageInstaller.STATUS_SUCCESS;
                r.state = DownloadRecord.installOutcome(success, status == PackageInstaller.STATUS_FAILURE_ABORTED,
                        success && detect(r));
                if (r.state == INSTALLED) r.error = "";
                else if (r.state == CANCELED) r.error = "Android installation was canceled.";
                else r.error = failure(status);
            }
            downloads.store.save(r);
        }
    }

    private String failure(int status) {
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS: return "Android reported success, but the installed package could not be confirmed.";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "Android policy or the user denied installation.";
            case PackageInstaller.STATUS_FAILURE_CONFLICT: return "Package conflicts with an installed app or signing key.";
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "Android needs more free storage.";
            case PackageInstaller.STATUS_FAILURE_INVALID: return "Android rejected the APK or its signature.";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "APK is incompatible with this TV.";
            default: return "Android installation failed (status " + status + ").";
        }
    }

    @SuppressWarnings("deprecation")
    boolean detect(DownloadRecord r) {
        try {
            PackageInfo info = downloads.context.getPackageManager().getPackageInfo(r.packageName, 0);
            long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return version >= r.versionCode;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    void resume(Activity a, DownloadRecord r) {
        foreground = new WeakReference<>(a);
        PendingIntent prompt = r.sessionId < 0 ? null : pendingPrompt(r);
        if (prompt != null) {
            try { prompt.send(); return; } catch (PendingIntent.CanceledException ignored) { }
        }
        if (preparing.contains(r.id)) return;
        PackageInstaller.SessionInfo session = r.sessionId < 0 ? null : os.getSessionInfo(r.sessionId);
        // After process loss, an unsealed session has never been handed to Android. Permit an explicit retry.
        if (session == null || !session.isSealed()) {
            if (session != null) abandon(r.sessionId);
            r.state = FAILED; r.error = "Installer session ended before confirmation. Select Retry.";
            downloads.store.save(r);
        } else {
            // Android permits recommitting the original sealed session after a reboot. Never infer a
            // successful install by comparing against a package that might already have been present.
            try (PackageInstaller.Session old = os.openSession(r.sessionId)) {
                commit(old, r);
                r.error = "Asked Android for approval again. Reset the installer session if the prompt does not return.";
            } catch (IOException | RuntimeException e) {
                r.error = "Android could not reopen this session. Reset the installer session to retry.";
            }
            downloads.store.save(r);
        }
    }

    void reset(DownloadRecord r) {
        if (r.state != AWAITING_APPROVAL) throw new IllegalStateException("There is no pending installation.");
        if (preparing.contains(r.id)) throw new IllegalStateException("Android is still preparing the installer. Try again shortly.");
        if (r.sessionId >= 0) {
            PendingIntent prompt = pendingPrompt(r);
            if (os.getSessionInfo(r.sessionId) != null) {
                try { os.abandonSession(r.sessionId); }
                catch (RuntimeException e) {
                    throw new IllegalStateException("Android is finalizing this session. Check its installer and retry.");
                }
            }
            if (prompt != null) prompt.cancel();
        }
        r.state = CANCELED; r.error = "Installer session reset. Select Retry to use the inspected APK.";
        r.sessionId = -1;
        downloads.store.save(r);
    }
}
