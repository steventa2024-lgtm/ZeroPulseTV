package tv.calmhome.downloadqa;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Disposable test app. Only the target package can read the two fixed test files. */
public class FixtureProvider extends ContentProvider {
    static final Uri VALID = Uri.parse("content://tv.calmhome.downloadqa.samples/fixture.apk");
    static final Uri INVALID = Uri.parse("content://tv.calmhome.downloadqa.samples/page.html");
    static final Uri SLOW = Uri.parse("content://tv.calmhome.downloadqa.samples/reboot.apk");
    static final String TARGET = "tv.calmhome.launcher";
    @Override public boolean onCreate() {
        try (InputStream in = getContext().getAssets().open("fixture.apk");
             FileOutputStream out = new FileOutputStream(file(VALID))) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        } catch (Exception e) { throw new IllegalStateException(e); }
        try (FileOutputStream out = new FileOutputStream(file(INVALID))) {
            out.write("<html>not an APK</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
        return true;
    }
    private File file(Uri uri) {
        if (SLOW.equals(uri)) uri = VALID;
        if (!VALID.equals(uri) && !INVALID.equals(uri)) throw new IllegalArgumentException();
        return new File(getContext().getFilesDir(), uri.getLastPathSegment());
    }
    private void requireTarget() {
        try {
            if (Binder.getCallingUid() == getContext().getPackageManager().getApplicationInfo(TARGET, 0).uid) return;
        } catch (PackageManager.NameNotFoundException ignored) { }
        throw new SecurityException();
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != Process.SHELL_UID) requireTarget();
        SharedPreferences state = getContext().getSharedPreferences("reboot-fixture", 0);
        if ("arm".equals(method)) {
            if (!state.edit().putInt("armed", bootCount()).remove("reopened").commit())
                throw new IllegalStateException("Could not arm the reboot fixture.");
            return new Bundle();
        }
        if ("probe".equals(method)) {
            Bundle result = new Bundle();
            result.putInt("reopened", state.getInt("reopened", -1));
            return result;
        }
        if (!"grant".equals(method)) throw new SecurityException();
        long identity = Binder.clearCallingIdentity();
        try {
          for (Uri uri : new Uri[]{VALID, INVALID, SLOW}) {
            getContext().grantUriPermission(TARGET, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
          }
        } finally { Binder.restoreCallingIdentity(identity); }
        return new Bundle();
    }
    @Override public Cursor query(Uri uri, String[] projection, String where, String[] args, String sort) {
        requireTarget();
        File f = file(uri);
        MatrixCursor c = new MatrixCursor(projection);
        MatrixCursor.RowBuilder row = c.newRow();
        for (String col : projection) row.add(OpenableColumns.SIZE.equals(col) ? f.length() : f.getName());
        return c;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireTarget();
        if (!"r".equals(mode)) throw new SecurityException();
        if (SLOW.equals(uri)) {
            SharedPreferences state = getContext().getSharedPreferences("reboot-fixture", 0);
            int boot = bootCount();
            if (state.getInt("armed", -1) == boot) return gatedPipe(uri);
            if (!state.edit().putInt("reopened", boot).commit())
                throw new FileNotFoundException("Could not record the post-reboot read.");
        }
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    private int bootCount() {
        int count = Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (count < 0) throw new IllegalStateException("Emulator boot count is unavailable.");
        return count;
    }

    private ParcelFileDescriptor gatedPipe(Uri uri) throws FileNotFoundException {
        return openPipeHelper(uri, getType(uri), null, null, (pipe, source, type, opts, args) -> {
            try (InputStream in = new FileInputStream(file(VALID));
                 FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe)) {
                byte[] block = new byte[512];
                for (int i = 0; i < 2; i++) {
                    if (in.read(block) != block.length) throw new IOException("Fixture is too small.");
                    out.write(block);
                    Thread.sleep(650); // Allow the production byte progress checkpoint to run.
                }
                // The first boot cannot supply the rest. Only a new Android boot opens the full APK.
                Thread.sleep(120000);
            } catch (IOException ignored) {
                // Android may close the reader during job cancellation or shutdown.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }
    @Override public String getType(Uri uri) { return !INVALID.equals(uri) ? "application/vnd.android.package-archive" : "text/html"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String w, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String w, String[] a) { throw new UnsupportedOperationException(); }
}
