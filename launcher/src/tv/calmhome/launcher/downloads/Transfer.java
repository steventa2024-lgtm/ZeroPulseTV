package tv.calmhome.launcher.downloads;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import javax.net.ssl.HttpsURLConnection;

final class Transfer {
    static void run(Context ctx, Downloads downloads, DownloadRecord r, File part) throws IOException {
        if (r.url.startsWith("content://")) {
            Uri local = Uri.parse(r.url);
            try (Cursor c = ctx.getContentResolver().query(local, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) r.total = c.getLong(0);
            }
            DownloadPolicy.validateSize(r.total, part.getParentFile().getUsableSpace());
            downloads.progress(r);
            try (InputStream in = ctx.getContentResolver().openInputStream(local)) {
                if (in == null) throw new IOException("Could not open the selected file.");
                copy(in, downloads, r, part);
            }
        } else fetch(downloads, r, part);
        verify(ctx, downloads, r, part);
    }

    private static void fetch(Downloads downloads, DownloadRecord r, File part) throws IOException {
        URI uri = DownloadPolicy.validateUrl(r.url);
        for (int hops = 0; ; hops++) {
            HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept-Encoding", "identity");
            try {
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    uri = DownloadPolicy.redirect(uri, conn.getHeaderField("Location"), hops);
                    continue;
                }
                if (code != 200) throw new IOException("Developer server returned HTTP " + code + ".");
                String type = conn.getContentType();
                if (type != null && type.toLowerCase(Locale.ROOT).startsWith("text/"))
                    throw new IOException("This link returns text or a webpage. Enter a direct APK link.");
                r.finalUrl = uri.toASCIIString();
                r.fileName = DownloadPolicy.fileName(uri);
                r.total = conn.getContentLengthLong();
                DownloadPolicy.validateSize(r.total, part.getParentFile().getUsableSpace());
                downloads.progress(r);
                try (InputStream in = conn.getInputStream()) { copy(in, downloads, r, part); }
                break;
            } finally {
                conn.disconnect();
            }
        }
    }

    private static void verify(Context ctx, Downloads downloads, DownloadRecord r, File part) throws IOException {
        r.state = DownloadRecord.State.VERIFYING;
        downloads.progress(r);
        MessageDigest digest = DownloadPolicy.digest();
        byte[] buffer = new byte[65536];
        try (InputStream in = new FileInputStream(part)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                digest.update(buffer, 0, n);
            }
        }
        r.sha256 = DownloadPolicy.hex(digest.digest());
        downloads.progress(r);
        DownloadPolicy.checkDigest(r.expectedSha256, r.sha256);
        DownloadPolicy.checkArchive(part);
        parse(ctx, r, part);
        downloads.complete(r, part);
    }

    private static void copy(InputStream in, Downloads downloads, DownloadRecord r, File part) throws IOException {
        long nextUpdate = SystemClock.elapsedRealtime() + 400;
        try (FileOutputStream out = new FileOutputStream(part)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                r.bytes += n;
                if (r.bytes > DownloadPolicy.MAX_BYTES) throw new IOException("APK exceeds the 512 MiB limit.");
                out.write(buffer, 0, n);
                if (SystemClock.elapsedRealtime() >= nextUpdate) {
                    DownloadPolicy.validateSize(0, part.getParentFile().getUsableSpace());
                    downloads.progress(r);
                    nextUpdate = SystemClock.elapsedRealtime() + 400;
                }
            }
            out.getFD().sync();
        }
        if (r.total >= 0 && r.total != r.bytes) throw new IOException("Download was incomplete. Retry the transfer.");
    }

    @SuppressWarnings("deprecation")
    private static void parse(Context ctx, DownloadRecord r, File part) throws IOException {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(part.getAbsolutePath(),
                PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
        if (info == null || info.applicationInfo == null || info.signatures == null || info.signatures.length == 0)
            throw new IOException("Android could not parse a signed standalone APK.");
        info.applicationInfo.sourceDir = info.applicationInfo.publicSourceDir = part.getAbsolutePath();
        r.packageName = info.packageName; r.versionName = info.versionName == null ? "" : info.versionName;
        r.versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        r.appName = String.valueOf(pm.getApplicationLabel(info.applicationInfo));
        r.minSdk = info.applicationInfo.minSdkVersion;
        r.permissions = info.requestedPermissions == null ? "" : String.join("\n", info.requestedPermissions);
        if (ctx.getPackageName().equals(r.packageName)) throw new IOException(
                "Download Center cannot replace this HOME launcher. Use the signed launcher update procedure.");
        if (r.minSdk > Build.VERSION.SDK_INT) throw new IOException("APK requires Android API " + r.minSdk +
                ". This TV runs API " + Build.VERSION.SDK_INT + ".");
    }

    static String message(Exception e, File file) {
        if (e instanceof javax.net.ssl.SSLException) return "TLS validation failed. Check the developer source.";
        if (file.getParentFile().getUsableSpace() < DownloadPolicy.RESERVE_BYTES)
            return "Not enough free storage. Remove downloads and retry.";
        String detail = e.getMessage();
        // Only display our own diagnostic text; network exceptions may include a signed URL or private data.
        if (e.getClass() == IOException.class && detail != null && !detail.contains("://") &&
                (detail.endsWith(".") && !detail.contains("/") || detail.startsWith("APK requires Android API "))) return detail;
        return "Transfer or APK inspection failed. Check the direct link, network and available storage, then retry.";
    }
    private Transfer() {}
}
