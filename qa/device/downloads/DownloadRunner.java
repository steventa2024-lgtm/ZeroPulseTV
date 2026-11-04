package tv.calmhome.downloadqa;

import android.app.Activity;
import android.app.Instrumentation;
import tv.calmhome.launcher.data.Prefs;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.UriPermission;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            // Starting instrumentation force-stops the target, dropping prior temporary URI grants.
            if (!phase.equals("resume") && !phase.equals("inflight-resume")) getTargetContext().getContentResolver().call(FixtureProvider.VALID, "grant", null, null);
            if (phase.equals("inflight-seed")) {
                getTargetContext().getContentResolver().call(FixtureProvider.SLOW, "arm", null, null);
                long id = d.importFile(FixtureProvider.SLOW, fixtureHash());
                long deadline = SystemClock.elapsedRealtime() + 90000;
                DownloadRecord partial;
                do {
                    partial = d.find(id);
                    if (partial.state == FAILED || SystemClock.elapsedRealtime() >= deadline)
                        throw new AssertionError("Expected a gated partial transfer, got " + partial.state + " " + partial.error);
                    Thread.sleep(100);
                } while (partial.state != DOWNLOADING || partial.bytes != 1024 || partial.bytes >= partial.total);
                check(persistedFixtureGrant(), "Missing persisted fixture grant before reboot");
                result.putString("downloadId", String.valueOf(id));
                result.putString("bytesBefore", String.valueOf(partial.bytes));
                result.putString("total", String.valueOf(partial.total));
                sendStatus(1, result); // The host reboots this emulator with the transfer still active.
                Thread.sleep(120000);
                throw new AssertionError("Host did not reboot during the gated transfer.");
            } else if (phase.equals("inflight-resume")) {
                long id = Long.parseLong(args.getString("id"));
                check(persistedFixtureGrant(), "Read grant was lost across reboot");
                DownloadRecord recovered = d.find(id);
                String expected = fixtureHash();
                check(recovered.state == COMPLETED && recovered.bytes == recovered.total && recovered.bytes > 1024,
                        "Incomplete recovered transfer: " + recovered.state + " " + recovered.error);
                check(expected.equals(recovered.expectedSha256) && expected.equals(recovered.sha256), "Recovered digest mismatch");
                check("tv.calmhome.downloadfixture".equals(recovered.packageName) && recovered.versionCode == 1,
                        "Recovered APK metadata mismatch");
                result.putString("bytesAfter", String.valueOf(recovered.bytes));
                result.putString("sha256", recovered.sha256);
                result.putString("package", recovered.packageName);
                d.remove(id);
            } else if (phase.equals("prepare")) {
                Prefs prefs = new Prefs(getTargetContext());
                java.util.List<String> dock = prefs.dock();
                if (dock.remove("tv.calmhome.downloadfixture")) prefs.setDock(dock);
            } else if (phase.equals("resume")) {
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
                long id = Long.parseLong(args.getString("id"));
                DownloadRecord end = d.find(id);
                check(end != null && "tv.calmhome.downloadfixture".equals(end.packageName), "Wrong test record.");
                if (phase.equals("install-allow")) {
                    check(end.state == INSTALLED && d.isInstalled(id), "Missing confirmed installation");
                    check(new Prefs(getTargetContext()).dock().contains(end.packageName), "Fixture is absent from the stored dock.");
                } else {
                    check(end.state == CANCELED || end.state == FAILED && end.error.contains("denied"),
                            "Android did not deny/cancel the installation.");
                }
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
    private boolean persistedFixtureGrant() {
        for (UriPermission grant : getTargetContext().getContentResolver().getPersistedUriPermissions())
            if (FixtureProvider.SLOW.equals(grant.getUri()) && grant.isReadPermission()) return true;
        return false;
    }

    private String fixtureHash() throws Exception {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open("fixture.sha256"), StandardCharsets.US_ASCII))) {
            return input.readLine();
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
