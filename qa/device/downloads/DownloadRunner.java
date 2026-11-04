package tv.calmhome.downloadqa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.downloads.Downloads;
import static tv.calmhome.launcher.downloads.DownloadRecord.State.*;

/** Requires a disposable emulator and an explicit choice for interactive PackageInstaller tests. */
public final class DownloadRunner extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))
                throw new IllegalStateException("Disposable Android Emulator required.");
            Downloads d = Downloads.get(getTargetContext());
            String phase = args.getString("phase", "verify");
            if (phase.equals("resume")) {
                long id = Long.parseLong(args.getString("id"));
                verify(d.find(id));
                d.remove(id);
            } else if (phase.equals("https")) {
                String hash = "ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401";
                long id = d.enqueue("https://github.com/steventa2024-lgtm/ZeroPulseTV/releases/download/" +
                        "v2.1.0-beta.1/ZeroPulseTV-v2.1.0-beta.1.apk", hash);
                try {
                    DownloadRecord release = await(d, id);
                    check(release.bytes == 5744160 && release.sha256.equals(hash), "Release size/hash mismatch: " + release.error);
                    check(release.state == FAILED && release.packageName.equals("tv.calmhome.launcher") &&
                            release.error.contains("HOME launcher"), "Unapproved self-update was not refused.");
                    result.putString("finalHost", android.net.Uri.parse(release.finalUrl).getHost());
                    result.putString("sha256", release.sha256);
                    result.putString("bytes", String.valueOf(release.bytes));
                } finally { d.remove(id); }
            } else if (phase.equals("seed")) {
                long id = d.importFile(FixtureProvider.VALID, "");
                verify(await(d, id));
                result.putString("downloadId", Long.toString(id));
            } else if (phase.equals("verify")) {
                long html = d.importFile(FixtureProvider.INVALID, "");
                try { check(await(d, html).state == FAILED, "Expected malformed archive rejection"); }
                finally { d.remove(html); }
                long wrong = d.importFile(FixtureProvider.VALID, new String(new char[64]).replace('\0', '0'));
                try {
                    DownloadRecord bad = await(d, wrong);
                    check(bad.state == FAILED && bad.error.contains("checksum"), "Expected checksum mismatch");
                } finally { d.remove(wrong); }
                long retry = d.importFile(FixtureProvider.VALID, "");
                try {
                    d.cancel(retry);
                    check(d.find(retry).state == CANCELED, "Cancellation failed");
                    d.retry(retry);
                    verify(await(d, retry));
                } finally { d.remove(retry); }
            } else if (phase.equals("install-allow") || phase.equals("install-deny")) {
                check(getTargetContext().getPackageManager().canRequestPackageInstalls(),
                        "Enable the launcher as an installation source in Android settings on this emulator.");
                long id = d.importFile(FixtureProvider.VALID, "");
                verify(await(d, id));
                Activity home = startActivitySync(new Intent().setClassName("tv.calmhome.launcher",
                        "tv.calmhome.launcher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                runOnMainSync(() -> d.install(home, id));
                DownloadRecord end = await(d, id);
                if (phase.equals("install-allow")) check(end.state == INSTALLED && d.isInstalled(id), "Missing confirmed installation");
                else check(end.state == CANCELED || end.state == FAILED, "Android did not deny/cancel the installation");
                result.putString("status", end.state + " " + end.error);
                d.remove(id);
            } else throw new IllegalArgumentException("Unknown test phase");
            result.putString("result", "PASS " + phase);
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError e) {
            result.putString("result", "FAIL " + e.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private DownloadRecord await(Downloads d, long id) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + 180000;
        for (;;) {
            DownloadRecord r = d.find(id);
            if (r == null) throw new AssertionError("Record disappeared");
            if (r.state == FAILED || r.state == CANCELED || r.state == COMPLETED || r.state == INSTALLED) return r;
            if (SystemClock.elapsedRealtime() >= deadline) throw new AssertionError("Timeout in " + r.state + ": " + r.error);
            Thread.sleep(250);
        }
    }
    private void verify(DownloadRecord r) {
        check(r != null && r.state == COMPLETED && r.bytes == r.total && r.bytes > 0, "Missing verified file");
        check(r.sha256.length() == 64 && r.expectedSha256.isEmpty(), "Incorrect digest reporting");
        check("tv.calmhome.downloadfixture".equals(r.packageName) && r.versionCode == 1, "Wrong parsed package");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
