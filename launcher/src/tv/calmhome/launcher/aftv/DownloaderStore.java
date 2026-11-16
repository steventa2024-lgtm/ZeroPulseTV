package tv.calmhome.launcher.aftv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import tv.calmhome.launcher.core.DownloaderCodes;
import tv.calmhome.launcher.downloads.DownloadPolicy;

/**
 * What the owner keeps for the Downloader screen: AFTVnews codes, and (for a TV with no Play Store) the HTTPS link and SHA-256 of an
 * APK they trust. Nothing is preset: there is no official direct APK link from AFTVnews, so this launcher ships none.
 */
public final class DownloaderStore {
    static final String FILE = "zp_downloader";
    private static final String CODES = "codes", APK_URL = "apk_url", APK_SHA = "apk_sha256";

    private final SharedPreferences sp;

    public DownloaderStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<DownloaderCodes.Entry> codes() {
        return DownloaderCodes.parse(sp.getString(CODES, ""));
    }

    /** Saves a code (or renames it). Throws IllegalArgumentException for a bad code or a full list. */
    public void addCode(String code, String label) {
        sp.edit().putString(CODES, DownloaderCodes.serialize(DownloaderCodes.add(codes(), code, label))).commit();
    }

    public void removeCode(String code) {
        sp.edit().putString(CODES, DownloaderCodes.serialize(DownloaderCodes.remove(codes(), code))).commit();
    }

    public String apkUrl() {
        return sp.getString(APK_URL, "");
    }

    public String apkSha256() {
        return sp.getString(APK_SHA, "");
    }

    /** Remembers an APK link (HTTPS only, as the Download Center requires) and its optional SHA-256. Throws IllegalArgumentException. */
    public void setApkSource(String url, String sha256) {
        String u = DownloadPolicy.validateUrl(url).toASCIIString();
        String sha = DownloadPolicy.normalizeChecksum(sha256);
        sp.edit().putString(APK_URL, u).putString(APK_SHA, sha).commit();
    }
}
