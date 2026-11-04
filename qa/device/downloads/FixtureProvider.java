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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Disposable test app. Only the target package can read the two fixed test files. */
public class FixtureProvider extends ContentProvider {
    static final Uri VALID = Uri.parse("content://tv.calmhome.downloadqa.samples/fixture.apk");
    static final Uri INVALID = Uri.parse("content://tv.calmhome.downloadqa.samples/page.html");
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
        if (!"grant".equals(method) || Binder.getCallingUid() != Process.SHELL_UID) throw new SecurityException();
        for (Uri uri : new Uri[]{VALID, INVALID}) {
            getContext().grantUriPermission(TARGET, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
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
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return VALID.equals(uri) ? "application/vnd.android.package-archive" : "text/html"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String w, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String w, String[] a) { throw new UnsupportedOperationException(); }
}
