package tv.calmhome.launcher.toolbox;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.ui.Theme;

/**
 * Pure-Java download, shortcode resolver, telemetry, icon cache, and
 * {@link PackageInstaller} staging engine for ZPAPPS inside ZeroPulse TV.
 */
public final class ToolboxEngine {

    private static final String PREFS_NAME = "zpapps_prefs";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern HREF_PATTERN = Pattern.compile("(?is)href\\s*=\\s*[\"']([^\"'#\\s]+)[\"']");
    private static final Pattern APK_URL_PATTERN = Pattern.compile("(?i)(https?://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?)");

    private static ToolboxEngine instance;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService downloadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService imagePool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> iconCache = new LruCache<>(48);
    private final Map<Integer, PendingSession> pendingSessions = new ConcurrentHashMap<>();

    private Future<?> activeTask;

    /** Callback for a single APK download + PackageInstaller handoff. */
    public interface DownloadCallback {
        void onProgress(int percent, String stage);
        void onWebDirectoryDetected(String url);
        void onInstallerLaunched(File apkFile, String appName);
        void onInstallOutcome(boolean success, String message);
        void onError(String message);
    }

    /** Callback for async icon bitmap retrieval. */
    public interface IconCallback {
        void onBitmap(Bitmap tileBitmap);
    }

    /** Callback for async APK link scraping on HTML directory pages. */
    public interface ScrapeCallback {
        void onResult(List<String> links, String error);
    }

    /** Snapshot of TV storage, cacheDir APKs, and active network transport. */
    public static final class TelemetrySnapshot {
        public final String availableStorageGb;
        public final int cachedApkCount;
        public final long cachedApkBytes;
        public final String cachedApkLabel;
        public final String networkLabel;
        public final boolean networkOnline;

        TelemetrySnapshot(String availableStorageGb, int cachedApkCount, long cachedApkBytes,
                          String cachedApkLabel, String networkLabel, boolean networkOnline) {
            this.availableStorageGb = availableStorageGb;
            this.cachedApkCount = cachedApkCount;
            this.cachedApkBytes = cachedApkBytes;
            this.cachedApkLabel = cachedApkLabel;
            this.networkLabel = networkLabel;
            this.networkOnline = networkOnline;
        }
    }

    private static final class PendingSession {
        final File apkFile;
        final String appName;
        final WeakReference<Activity> activityRef;
        final DownloadCallback callback;

        PendingSession(File apkFile, String appName, Activity activity, DownloadCallback callback) {
            this.apkFile = apkFile;
            this.appName = appName;
            this.activityRef = new WeakReference<>(activity);
            this.callback = callback;
        }
    }

    public static synchronized ToolboxEngine get(Context context) {
        if (instance == null) {
            instance = new ToolboxEngine(context.getApplicationContext());
        }
        return instance;
    }

    private ToolboxEngine(Context context) {
        this.appContext = context;
    }

    // ------------------------------------------------------------------ onboarding & permissions

    public boolean isOnboardingCompleted() {
        SharedPreferences sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
                .apply();
    }

    public boolean canRequestPackageInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return appContext.getPackageManager().canRequestPackageInstalls();
    }

    public void openInstallPermissionSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + appContext.getPackageName())
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (RuntimeException ignored) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignoredSecondary) { }
        }
    }

    // ------------------------------------------------------------------ package helpers

    public boolean isInstalled(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.getPackageManager().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L));
            } else {
                getPackageInfoLegacy(packageName);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private PackageInfo getPackageInfoLegacy(String packageName) throws PackageManager.NameNotFoundException {
        return appContext.getPackageManager().getPackageInfo(packageName, 0);
    }

    // ------------------------------------------------------------------ telemetry & cache

    public TelemetrySnapshot telemetry() {
        String storage = availableStorageGb();
        int count = 0;
        long bytes = 0L;
        File[] files = appContext.getCacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    count++;
                    bytes += Math.max(0L, f.length());
                }
            }
        }
        String cacheLabel;
        if (count == 0) {
            cacheLabel = "Clean (0 MB)";
        } else if (bytes < 1024L * 1024L) {
            cacheLabel = String.format(Locale.US, "%.0f KB APK Cache", Math.max(1.0, bytes / 1024.0));
        } else {
            cacheLabel = String.format(Locale.US, "%.1f MB APK Cache", bytes / (1024.0 * 1024.0));
        }
        String netLabel = "Offline";
        boolean online = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    online = true;
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) netLabel = "Ethernet";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) netLabel = "Wi-Fi Connected";
                    else netLabel = "Connected";
                }
            }
        } catch (RuntimeException ignored) { }
        return new TelemetrySnapshot(storage, count, bytes, cacheLabel, netLabel, online);
    }

    public String availableStorageGb() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            double gb = stat.getAvailableBytes() / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f GB Free", gb);
        } catch (RuntimeException e) {
            return "-- GB Free";
        }
    }

    public int purgeCachedApks() {
        int removed = 0;
        File[] files = appContext.getCacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    if (f.delete()) removed++;
                }
            }
        }
        return removed;
    }

    public static String formatMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Math.max(0L, bytes) / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ code/url resolution

    public static String resolveInputToUrl(String codeOrUrl) {
        if (codeOrUrl == null) return null;
        String trimmed = codeOrUrl.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.matches("^\\d{4,8}$")) {
            return "https://aftv.news/" + trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) return trimmed;
        if (lower.startsWith("http://")) return "https://" + trimmed.substring(7);
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://" + trimmed;
        }
        return null;
    }

    // ------------------------------------------------------------------ async download + PackageInstaller

    public synchronized void cancelActive() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }

    public synchronized void downloadAndInstall(Activity activity, String codeOrUrl, String appName, DownloadCallback callback) {
        String targetUrl = resolveInputToUrl(codeOrUrl);
        if (targetUrl == null) {
            main.post(() -> callback.onError("Enter a 4–8 digit Downloader code or valid HTTPS URL."));
            return;
        }
        if (!canRequestPackageInstalls()) {
            main.post(() -> callback.onError("Grant 'Install unknown apps' permission in Settings first."));
            return;
        }
        cancelActive();
        activeTask = downloadPool.submit(() -> runDownload(activity, targetUrl, appName, callback));
    }

    private void runDownload(Activity activity, String initialUrl, String appName, DownloadCallback callback) {
        try {
            purgeCachedApks();
            main.post(() -> callback.onProgress(0, "Resolving source…"));

            URI currentUri = new URI(initialUrl);
            HttpsURLConnection conn = null;
            String finalUrl = initialUrl;

            try {
                for (int hop = 0; hop < 8; hop++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (!"https".equalsIgnoreCase(currentUri.getScheme())) {
                        main.post(() -> callback.onError("Source redirected to unencrypted HTTP, which ZeroPulse blocks for TV safety."));
                        return;
                    }
                    conn = (HttpsURLConnection) currentUri.toURL().openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
                    conn.setRequestProperty("Accept-Encoding", "identity");

                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == 303 || status == 307 || status == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = null;
                        if (loc == null || loc.trim().isEmpty()) {
                            main.post(() -> callback.onError("Server returned an empty redirect."));
                            return;
                        }
                        URI nextUri = currentUri.resolve(loc.trim());
                        if ("http".equalsIgnoreCase(nextUri.getScheme())) {
                            // Try seamless HTTPS upgrade first for hosts like aftv.news redirect targets.
                            nextUri = new URI("https", nextUri.getUserInfo(), nextUri.getHost(),
                                    nextUri.getPort(), nextUri.getPath(), nextUri.getQuery(), null);
                        }
                        currentUri = nextUri;
                        finalUrl = currentUri.toASCIIString();
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        final int code = status;
                        final String failedUrl = finalUrl;
                        main.post(() -> callback.onError("Download failed (HTTP " + code + ") for " + failedUrl));
                        return;
                    }
                    break;
                }

                if (conn == null) {
                    main.post(() -> callback.onError("Too many redirects while resolving APK."));
                    return;
                }

                String contentType = safeLower(conn.getContentType());
                String contentDisp = conn.getHeaderField("Content-Disposition");
                if (contentDisp == null) contentDisp = "";
                String guessed = guessApkFileName(currentUri, contentDisp);

                boolean looksHtml = contentType.contains("text/html") || contentType.contains("application/xhtml+xml");
                boolean explicitApk = contentDisp.toLowerCase(Locale.ROOT).contains(".apk")
                        || contentType.contains("application/vnd.android.package-archive");

                if (looksHtml && !explicitApk) {
                    final String detected = finalUrl;
                    main.post(() -> callback.onWebDirectoryDetected(detected));
                    return;
                }

                String safeName = guessed.toLowerCase(Locale.ROOT).endsWith(".apk")
                        ? guessed
                        : (guessed.contains(".") ? guessed.substring(0, guessed.lastIndexOf('.')) : guessed) + ".apk";
                safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
                if (safeName.isEmpty() || safeName.startsWith(".")) safeName = "zpapps_download.apk";

                File apkFile = new File(appContext.getCacheDir(), safeName);
                long total = conn.getContentLengthLong();

                byte[] buf = new byte[16 * 1024];
                long downloaded = 0L;
                int lastPct = -1;

                try (InputStream in = conn.getInputStream()) {
                    int firstRead = in.read(buf);
                    if (firstRead <= 0) {
                        main.post(() -> callback.onError("Downloaded file was empty."));
                        return;
                    }
                    boolean zipMagic = firstRead >= 4 && buf[0] == 0x50 && buf[1] == 0x4B;
                    if (!zipMagic) {
                        String head = new String(buf, 0, Math.min(firstRead, 256), StandardCharsets.UTF_8)
                                .trim().toLowerCase(Locale.ROOT);
                        if (head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<head")) {
                            final String detected = finalUrl;
                            main.post(() -> callback.onWebDirectoryDetected(detected));
                            return;
                        }
                    }

                    try (FileOutputStream out = new FileOutputStream(apkFile)) {
                        out.write(buf, 0, firstRead);
                        downloaded += firstRead;
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                apkFile.delete();
                                return;
                            }
                            out.write(buf, 0, n);
                            downloaded += n;
                            if (total > 0) {
                                int pct = (int) Math.min(100L, Math.max(0L, (downloaded * 100L) / total));
                                if (pct != lastPct) {
                                    lastPct = pct;
                                    final int reportPct = pct;
                                    main.post(() -> callback.onProgress(reportPct, "Downloading " + appName + "… " + reportPct + "%"));
                                }
                            } else if (lastPct != 50) {
                                lastPct = 50;
                                main.post(() -> callback.onProgress(50, "Downloading " + appName + "…"));
                            }
                        }
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    apkFile.delete();
                    return;
                }

                main.post(() -> callback.onProgress(100, "Staging Android installer…"));
                stageWithPackageInstaller(activity, apkFile, appName, callback);
            } finally {
                if (conn != null) conn.disconnect();
            }
        } catch (URISyntaxException e) {
            main.post(() -> callback.onError("Malformed download URL."));
        } catch (IOException e) {
            String msg = e.getMessage();
            main.post(() -> callback.onError(msg == null || msg.isEmpty() ? "Network error while downloading APK." : msg));
        }
    }

    private void stageWithPackageInstaller(Activity activity, File apkFile, String appName, DownloadCallback callback) {
        PackageInstaller os = appContext.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        boolean committed = false;
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(apkFile.length());
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            }
            sessionId = os.createSession(params);
            pendingSessions.put(sessionId, new PendingSession(apkFile, appName, activity, callback));

            try (PackageInstaller.Session session = os.openSession(sessionId);
                 FileInputStream in = new FileInputStream(apkFile);
                 java.io.OutputStream out = session.openWrite("base.apk", 0, apkFile.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return;
                    out.write(buf, 0, n);
                }
                session.fsync(out);

                Intent cbIntent = new Intent(appContext, ToolboxInstallReceiver.class)
                        .setAction(ToolboxInstallReceiver.ACTION_INSTALL_RESULT)
                        .putExtra("zp_session_id", sessionId);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent sender = PendingIntent.getBroadcast(appContext, sessionId, cbIntent, flags);
                session.commit(sender.getIntentSender());
                committed = true;
            }
        } catch (IOException | RuntimeException e) {
            if (!committed && sessionId >= 0) {
                pendingSessions.remove(sessionId);
                try { os.abandonSession(sessionId); } catch (RuntimeException ignored) { }
            }
            main.post(() -> callback.onError("Could not launch Android package installer: " + e.getMessage()));
        }
    }

    @SuppressWarnings("deprecation")
    void onInstallBroadcast(Intent intent) {
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, intent.getIntExtra("zp_session_id", -1));
        PendingSession ps = pendingSessions.get(sessionId);
        if (ps == null) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) {
                pendingSessions.remove(sessionId);
                main.post(() -> ps.callback.onError("Android did not supply a confirmation screen."));
                return;
            }
            main.post(() -> {
                Activity a = ps.activityRef.get();
                if (a != null && !a.isFinishing() && !a.isDestroyed()) {
                    try {
                        a.startActivity(confirm);
                        ps.callback.onInstallerLaunched(ps.apkFile, ps.appName);
                    } catch (RuntimeException e) {
                        pendingSessions.remove(sessionId);
                        ps.callback.onError("Could not open Android package installer: " + e.getMessage());
                    }
                } else {
                    pendingSessions.remove(sessionId);
                    ps.callback.onError("Return to ZPAPPS to approve installation.");
                }
            });
            return;
        }

        pendingSessions.remove(sessionId);
        boolean success = status == PackageInstaller.STATUS_SUCCESS;
        String detail = success
                ? ps.appName + " installed."
                : status == PackageInstaller.STATUS_FAILURE_ABORTED
                        ? ps.appName + " install canceled."
                        : (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) != null
                                ? intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                : "Install failed (status " + status + ").");
        main.post(() -> ps.callback.onInstallOutcome(success, detail));
    }

    // ------------------------------------------------------------------ HTML Web Directory scraper

    public void extractApkLinks(String pageUrl, ScrapeCallback callback) {
        imagePool.execute(() -> {
            try {
                URI base = new URI(pageUrl);
                if (!"https".equalsIgnoreCase(base.getScheme())) {
                    main.post(() -> callback.onResult(Collections.emptyList(), "Directory page must be HTTPS."));
                    return;
                }
                HttpsURLConnection conn = (HttpsURLConnection) base.toURL().openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
                String html;
                try {
                    int code = conn.getResponseCode();
                    if (code < 200 || code >= 300) {
                        main.post(() -> callback.onResult(Collections.emptyList(), "Directory returned HTTP " + code));
                        return;
                    }
                    try (InputStream in = conn.getInputStream()) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int total = 0;
                        int n;
                        while ((n = in.read(buf)) != -1 && total < 1024 * 1024) {
                            total += n;
                            out.write(buf, 0, n);
                        }
                        html = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    }
                } finally {
                    conn.disconnect();
                }

                List<String> found = new ArrayList<>();
                Matcher mHref = HREF_PATTERN.matcher(html);
                while (mHref.find()) {
                    String raw = mHref.group(1);
                    if (raw == null || !raw.toLowerCase(Locale.ROOT).contains(".apk")) continue;
                    try {
                        String resolved = base.resolve(raw).toASCIIString();
                        if (resolved.toLowerCase(Locale.ROOT).startsWith("http://")) {
                            resolved = "https://" + resolved.substring(7);
                        }
                        if (!found.contains(resolved)) found.add(resolved);
                    } catch (IllegalArgumentException ignored) { }
                }
                Matcher mUrl = APK_URL_PATTERN.matcher(html);
                while (mUrl.find()) {
                    String raw = mUrl.group(1);
                    if (raw == null) continue;
                    if (raw.toLowerCase(Locale.ROOT).startsWith("http://")) raw = "https://" + raw.substring(7);
                    if (!found.contains(raw)) found.add(raw);
                }
                main.post(() -> callback.onResult(found, null));
            } catch (IOException | URISyntaxException e) {
                main.post(() -> callback.onResult(Collections.emptyList(), e.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ async glass artwork & monograms

    /** Builds an immediate Liquid-Glass brand badge bitmap for an uninstalled catalog app. */
    public Bitmap monogramBadge(ToolboxCatalog.AppEntry app, int widthPx, int heightPx) {
        int w = Math.max(1, widthPx);
        int h = Math.max(1, heightPx);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        int r = Math.max(4, Math.round(TileArt.bannerRadius(h)));
        int accent = app.accentColor;
        p.setColor(Theme.withAlpha(accent, 0.24f));
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);

        // Soft centered pill emblem
        int side = Math.max(24, Math.round(h * 0.56f));
        float cx = w / 2f;
        float cy = h / 2f;
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(Theme.withAlpha(accent, 0.42f));
        c.drawRoundRect(new RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f),
                side * 0.28f, side * 0.28f, pill);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFF2F6FA);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(side * 0.44f);
        String mono = TileArt.monogram(app.name);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(mono, cx, cy - (fm.ascent + fm.descent) / 2f, text);
        return out;
    }

    /** Async loader for uninstalled catalog app icons rendered into exact tile dimensions. */
    public void loadIconTile(ToolboxCatalog.AppEntry app, int widthPx, int heightPx, IconCallback callback) {
        if (app.iconUrl.isEmpty() || !app.iconUrl.toLowerCase(Locale.ROOT).startsWith("https://")) return;
        String key = app.iconUrl + "@" + widthPx + "x" + heightPx;
        Bitmap cached = iconCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            callback.onBitmap(cached);
            return;
        }
        imagePool.execute(() -> {
            Bitmap rendered = fetchAndComposeIconTile(app, widthPx, heightPx);
            if (rendered != null) {
                iconCache.put(key, rendered);
                main.post(() -> callback.onBitmap(rendered));
            }
        });
    }

    private Bitmap fetchAndComposeIconTile(ToolboxCatalog.AppEntry app, int w, int h) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection) URI.create(app.iconUrl).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            try {
                if (conn.getResponseCode() != 200) return null;
                try (InputStream in = conn.getInputStream()) {
                    Bitmap raw = BitmapFactory.decodeStream(in);
                    if (raw == null || raw.getWidth() < 8 || raw.getHeight() < 8) return null;
                    Bitmap tile = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(tile);
                    Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
                    wash.setColor(Theme.withAlpha(app.accentColor, 0.18f));
                    c.drawRect(0, 0, w, h, wash);
                    int side = Math.max(16, Math.round(h * 0.64f));
                    int left = (w - side) / 2;
                    int top = (h - side) / 2;
                    Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    c.drawBitmap(raw, new Rect(0, 0, raw.getWidth(), raw.getHeight()),
                            new Rect(left, top, left + side, top + side), imgPaint);
                    raw.recycle();
                    return tile;
                }
            } finally {
                conn.disconnect();
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String guessApkFileName(URI uri, String contentDisposition) {
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            Matcher m = Pattern.compile("(?i)filename\\*?=(?:UTF-8'')?\"?([^\";\\r\\n]+)\"?")
                    .matcher(contentDisposition);
            if (m.find()) {
                String found = m.group(1);
                if (found != null && !found.trim().isEmpty()) return found.trim();
            }
        }
        String path = uri.getPath();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            String seg = slash >= 0 ? path.substring(slash + 1) : path;
            if (!seg.trim().isEmpty()) return seg.trim();
        }
        return "zpapps_download.apk";
    }
}
