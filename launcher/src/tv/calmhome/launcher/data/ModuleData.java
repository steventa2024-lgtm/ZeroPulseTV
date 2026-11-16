package tv.calmhome.launcher.data;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;

import java.io.File;
import java.util.List;

/**
 * Stopping the optional modules and deleting what they saved. Each delete touches only that module's own
 * files; the launcher's "calmhome" preferences (dock, settings, feature switches) and every other feature's files and Android
 * Keystore keys are never touched. Files the user downloaded with ZeroPulse Web are not
 * deleted here: they belong to the user, who deletes them from the Downloads list.
 */
public final class ModuleData {

    /** Process of ZeroPulse Web (see AndroidManifest.xml). */
    public static final String WEB_PROCESS_SUFFIX = ":web";
    /** WebView data-directory suffix used by ZeroPulse Web on Android 9+. */
    public static final String WEB_DATA_SUFFIX = "web";

    public static final String STREAMING_DB = "zp_streaming.db";
    public static final String STREAMING_PREFS = "zp_streaming";
    /** Android Keystore key that encrypts the optional Jellyfin token (no other feature's key). */
    public static final String JELLYFIN_KEY_ALIAS = "zp_jellyfin_token";
    public static final String WEB_DB = "zp_web.db";
    public static final String WEB_PREFS = "zp_web";

    private ModuleData() { }

    /**
     * Ends the ZeroPulse Web process if it is running: closes every tab and returns all WebView memory. Allowed
     * because it is this app's own process (same app identity).
     */
    public static void stopWeb(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> running = am == null ? null : am.getRunningAppProcesses();
        if (running == null) return;
        String web = c.getPackageName() + WEB_PROCESS_SUFFIX;
        for (ActivityManager.RunningAppProcessInfo p : running) {
            if (web.equals(p.processName) && p.pid != Process.myPid()) Process.killProcess(p.pid);
        }
    }

    /** Saved sources, channel lists, guide, favourites, watch progress and the Jellyfin connection (with its key). */
    public static void deleteStreamingData(Context c) {
        c.deleteDatabase(STREAMING_DB);
        c.deleteSharedPreferences(STREAMING_PREFS);
        deleteTree(new File(c.getCacheDir(), "streaming"));
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(JELLYFIN_KEY_ALIAS);
        } catch (Exception noKeyOrNoKeystore) {
            // Nothing to delete.
        }
    }

    /** Tabs, bookmarks, history, cookies, cache and site storage. Stops the browser first. */
    public static void deleteWebData(Context c) {
        stopWeb(c);
        c.deleteDatabase(WEB_DB);
        c.deleteSharedPreferences(WEB_PREFS);
        // WebView keeps its data in app_webview[_<suffix>] and its cache in cache/WebView[_<suffix>]. Only the
        // Web process ever creates a WebView, so on Android 8 (no suffix) the unsuffixed folders are its too.
        File dataRoot = c.getDataDir();
        deleteTree(new File(dataRoot, "app_webview_" + WEB_DATA_SUFFIX));
        deleteTree(new File(dataRoot, "app_webview"));
        deleteTree(new File(c.getCacheDir(), "WebView_" + WEB_DATA_SUFFIX));
        deleteTree(new File(c.getCacheDir(), "WebView"));
    }

    static void deleteTree(File f) {
        // A link is removed itself, never followed, so nothing outside the module's folder can be reached.
        File[] kids = isSymlink(f) ? null : f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        // Best effort: a file that cannot be deleted is left in place.
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static boolean isSymlink(File f) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return java.nio.file.Files.isSymbolicLink(f.toPath());
        }
        try {
            File canon = f.getParent() == null ? f : new File(f.getParentFile().getCanonicalFile(), f.getName());
            return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
