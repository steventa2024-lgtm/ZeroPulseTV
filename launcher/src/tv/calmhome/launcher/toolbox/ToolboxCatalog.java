package tv.calmhome.launcher.toolbox;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Pure Java data models and catalog loader for the ZPAPPS TV Toolbox.
 * Reads bundled {@code zeropulse_toolbox.json} assets or synced {@code cached_toolbox.json}.
 */
public final class ToolboxCatalog {

    public static final String ASSETS_FILE = "zeropulse_toolbox.json";
    public static final String CACHED_FILE = "cached_toolbox.json";

    public static final List<String> DEFAULT_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "All",
            "Live TV & Sports",
            "IPTV Players",
            "Media Players",
            "Movies & VOD",
            "Utilities"
    ));

    public static final List<String> ESSENTIALS_TRIO_IDS = Collections.unmodifiableList(Arrays.asList(
            "adguard",
            "wuffy",
            "vlc"
    ));

    private ToolboxCatalog() { }

    /** Single app entry in the ZPAPPS toolbox catalog. */
    public static final class AppEntry {
        public final String id;
        public final String name;
        public final String category;
        public final String packageName;
        public final String code;
        public final int accentColor;
        public final String tagline;
        public final String iconUrl;
        public final String directUrl;

        public AppEntry(String id, String name, String category, String packageName,
                        String code, int accentColor, String tagline, String iconUrl, String directUrl) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.packageName = packageName;
            this.code = code == null ? "" : code;
            this.accentColor = accentColor;
            this.tagline = tagline == null ? "" : tagline;
            this.iconUrl = iconUrl == null ? "" : iconUrl;
            this.directUrl = directUrl == null ? "" : directUrl;
        }

        public String installSource() {
            if (!directUrl.isEmpty()) return directUrl;
            return code;
        }
    }

    /** Complete parsed toolbox catalog. */
    public static final class Catalog {
        public final List<String> categories;
        public final List<AppEntry> apps;

        public Catalog(List<String> categories, List<AppEntry> apps) {
            this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
            this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        }

        public AppEntry byId(String id) {
            for (AppEntry app : apps) {
                if (app.id.equals(id)) return app;
            }
            return null;
        }
    }

    /** Result of a remote JSON catalog sync request. */
    public static final class SyncResult {
        public final Catalog catalog;
        public final String message;
        public final boolean error;

        public SyncResult(Catalog catalog, String message, boolean error) {
            this.catalog = catalog;
            this.message = message;
            this.error = error;
        }
    }

    /** Loads the best available local catalog (cached remote sync > bundled asset > built-in fallback). */
    public static Catalog load(Context context) {
        File cached = new File(context.getFilesDir(), CACHED_FILE);
        if (cached.isFile() && cached.length() > 0) {
            try (InputStream in = new FileInputStream(cached)) {
                Catalog parsed = parse(readAllUtf8(in));
                if (parsed != null && !parsed.apps.isEmpty()) return parsed;
            } catch (IOException ignored) { }
        }
        try (InputStream in = context.getAssets().open(ASSETS_FILE)) {
            Catalog parsed = parse(readAllUtf8(in));
            if (parsed != null && !parsed.apps.isEmpty()) return parsed;
        } catch (IOException ignored) { }
        return builtinFallback();
    }

    /** Fetches and validates a remote HTTPS JSON catalog, caching it locally on success. */
    public static SyncResult syncFromRemote(Context context, String remoteUrl) {
        String trimmed = remoteUrl == null ? "" : remoteUrl.trim();
        if (trimmed.isEmpty()) {
            return new SyncResult(load(context), "Enter an HTTPS JSON catalog URL to sync.", true);
        }
        try {
            URI uri = validateHttpsUri(trimmed);
            String raw = fetchTextHttps(uri);
            Catalog parsed = parse(raw);
            if (parsed == null || parsed.apps.isEmpty()) {
                return new SyncResult(load(context), "Catalog JSON is invalid or contains no apps.", true);
            }
            File target = new File(context.getFilesDir(), CACHED_FILE);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(raw.getBytes(StandardCharsets.UTF_8));
            }
            return new SyncResult(parsed, "Synced " + parsed.apps.size() + " apps into ZeroPulse Toolbox.", false);
        } catch (IllegalArgumentException e) {
            return new SyncResult(load(context), e.getMessage(), true);
        } catch (IOException e) {
            return new SyncResult(load(context), "Sync failed (" + e.getMessage() + "). Using local catalog.", true);
        }
    }

    public static Catalog parse(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(rawJson);
            List<String> categories = new ArrayList<>();
            JSONArray catArr = root.optJSONArray("categories");
            if (catArr != null) {
                for (int i = 0; i < catArr.length(); i++) {
                    String cat = catArr.optString(i, "").trim();
                    if (!cat.isEmpty() && !categories.contains(cat)) categories.add(cat);
                }
            }
            if (categories.isEmpty()) categories.addAll(DEFAULT_CATEGORIES);

            List<AppEntry> apps = new ArrayList<>();
            JSONArray appArr = root.optJSONArray("apps");
            if (appArr != null) {
                for (int i = 0; i < appArr.length(); i++) {
                    JSONObject obj = appArr.optJSONObject(i);
                    if (obj == null) continue;
                    String id = obj.optString("id", "").trim();
                    String name = obj.optString("name", "").trim();
                    String pkg = obj.optString("packageName", "").trim();
                    if (id.isEmpty() || name.isEmpty() || pkg.isEmpty()) continue;
                    String cat = obj.optString("category", "Utilities").trim();
                    if (cat.isEmpty()) cat = "Utilities";
                    String code = obj.optString("code", "").trim();
                    String accentHex = obj.optString("accentHex", "#00E5FF").trim();
                    String tagline = obj.optString("tagline", "").trim();
                    String iconUrl = obj.optString("iconUrl", "").trim();
                    String directUrl = obj.optString("directUrl", "").trim();
                    apps.add(new AppEntry(id, name, cat, pkg, code, parseColorSafe(accentHex), tagline, iconUrl, directUrl));
                }
            }
            if (apps.isEmpty()) return null;
            return new Catalog(categories, apps);
        } catch (JSONException e) {
            return null;
        }
    }

    public static int parseColorSafe(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return 0xFF00E5FF;
        }
    }

    private static URI validateHttpsUri(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Catalog URL must use HTTPS without embedded credentials.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid HTTPS catalog URL.");
        }
    }

    private static String fetchTextHttps(URI uri) throws IOException {
        URI current = uri;
        for (int hops = 0; hops <= 5; hops++) {
            HttpsURLConnection conn = (HttpsURLConnection) current.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ZeroPulseTV/3.0");
            conn.setRequestProperty("Accept-Encoding", "identity");
            try {
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new IOException("Empty redirect.");
                    current = validateHttpsUri(current.resolve(location).toASCIIString());
                    continue;
                }
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                try (InputStream in = conn.getInputStream()) {
                    return readAllUtf8(in);
                }
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static String readAllUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > 2 * 1024 * 1024) throw new IOException("Catalog exceeds 2 MB limit.");
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Catalog builtinFallback() {
        List<AppEntry> apps = Arrays.asList(
                new AppEntry("ak47sports", "AK47 Sports", "Live TV & Sports", "com.ak47.sports", "3975858", 0xFFE50914, "Multi-link live sports & combat events", "https://ak47sports.app/wp-content/uploads/2026/05/cropped-AK47-Sports-1-192x192.webp", ""),
                new AppEntry("sportzx", "SportzX", "Live TV & Sports", "com.sportzx.tv", "1499898", 0xFF00B4D8, "200+ global live sports channels", "https://sportzx.org/wp-content/uploads/2026/05/cropped-Sportzx-TV.webp", ""),
                new AppEntry("fctv33", "FCTV33 (RBTV77)", "Live TV & Sports", "com.fctv33.tv", "3854690", 0xFF00C853, "International live sports & entertainment", "https://fctv33.vip/wp-content/uploads/2026/02/image-1-300x300.webp", ""),
                new AppEntry("cricfytv", "CricFy TV", "Live TV & Sports", "com.cricfy.tv", "6281845", 0xFFFF6B00, "Live global sports & M3U playlist support", "https://www.google.com/s2/favicons?domain=cricfy.net&sz=128", ""),
                new AppEntry("geesports", "GeeSports", "Live TV & Sports", "com.geesports.tv", "8338279", 0xFF2979FF, "Live HD sports channels & match events", "", ""),
                new AppEntry("1pixmedia", "1 Pix Media", "Live TV & Sports", "com.onepix.media", "790093", 0xFF7C4DFF, "Live TV & sports (pair with Wuffy Player)", "", ""),
                new AppEntry("tivimate", "TiviMate IPTV", "IPTV Players", "ar.tvplayer.tv", "272483", 0xFF2979FF, "Premium TV EPG guide for M3U & Xtream", "https://play-lh.googleusercontent.com/lXaMnFHizRDP9eHCRJCMwy9qYS2U9FDvwsNOUO9qappEEfhNzSuC6r2YTC8a-RYQqlCBc_hKiYOH2UXgtuiP=w240-h240", ""),
                new AppEntry("xciptv", "XCIPTV Player", "IPTV Players", "com.nathnetwork.xciptv", "537992", 0xFF00E5FF, "Built-in EPG, VOD & Xtream Codes player", "https://play-lh.googleusercontent.com/tjOabfZ3dXAOppuHCyUaTehPvO7dPwv5DNggDQRrCg2bFZLNalPsgWwGvWbjWx-uziCZMPzGXCHm5buW9wrF4g=w240-h240", ""),
                new AppEntry("streamvault", "StreamVault", "IPTV Players", "com.streamvault.iptv", "6443624", 0xFF00C853, "Clean live TV EPG guide & Xtream player", "", ""),
                new AppEntry("wuffy", "Wuffy Player", "Media Players", "co.wuffy.player", "337646", 0xFFFF9100, "Required stream engine for 1 Pix Media", "https://www.google.com/s2/favicons?domain=wuffy.co&sz=128", ""),
                new AppEntry("vlc", "VLC for Android", "Media Players", "org.videolan.vlc", "921602", 0xFFFF6D00, "Universal 4K & network media player", "https://play-lh.googleusercontent.com/BG5YwziGHjh0wvWg6VO9c3iprYa3vFww1CnNqPvFszUVxcY4yB29dy7t2jsLwwI4jAqX08_--12FmMxgNG1JFg=w240-h240", ""),
                new AppEntry("stremio", "Stremio", "Movies & VOD", "com.stremio.one", "71893", 0xFF8A2BE2, "4K HDR addon-powered streaming hub", "https://raw.githubusercontent.com/Stremio/stremio-web/development/assets/images/icon.png", ""),
                new AppEntry("nuvio", "Nuvio", "Movies & VOD", "com.nuvio.tv", "6817136", 0xFF00E5FF, "Modern plugin-based 4K media hub", "https://www.google.com/s2/favicons?domain=nuvioapp.space&sz=128", ""),
                new AppEntry("cloudstream", "CloudStream", "Movies & VOD", "com.lagradost.cloudstream3", "4132632", 0xFF3D5AFE, "Ad-free multi-repository movies & anime", "https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/ic_launcher-playstore.png", ""),
                new AppEntry("flixvision", "Flix Vision", "Movies & VOD", "com.flixvision.tv", "6101716", 0xFFFF1744, "Movies, series, anime & live channels", "https://flixvision.co/wp-content/uploads/2026/08/flixvision-removebg-preview-150x150.webp", ""),
                new AppEntry("kodi", "Kodi 21 Omega", "Movies & VOD", "org.xbmc.kodi", "4697031", 0xFF17B2E7, "Full home theater & custom build engine", "https://raw.githubusercontent.com/xbmc/xbmc/master/media/icon256x256.png", ""),
                new AppEntry("smarttube", "SmartTube", "Utilities", "com.teamsmart.videomanager.tv", "54794", 0xFFFF0000, "Ad-free 4K YouTube client for Android TV", "https://raw.githubusercontent.com/yuliskov/SmartTube/master/smarttubetv/src/ststable/res/mipmap-nodpi/app_icon.png", ""),
                new AppEntry("adguard", "AdGuard TV", "Utilities", "com.adguard.android", "1900064", 0xFF68BC71, "System-wide ad & popup redirect blocker", "https://www.google.com/s2/favicons?domain=adguard.com&sz=128", ""),
                new AppEntry("virustotal", "VirusTotal", "Utilities", "com.funnycat.virustotal", "7801475", 0xFF394EFF, "Scan sideloaded APKs before installing", "https://play-lh.googleusercontent.com/sGPQMb5fLb4LbJZ5jrbpkGEy3LlHkqThmBXAPqQyadXaEjsUPZAezp7KjgT-ufca1U8qVSJt_qWoqE4xl5ep=w240-h240", ""),
                new AppEntry("sdmaid", "SD Maid Pro", "Utilities", "eu.thedarken.sdm", "8720930", 0xFF00BFA5, "Storage cleaner & cache manager for TV", "https://play-lh.googleusercontent.com/hCO0dtqMxIJXdcMfaWEIxFeo_TI6DJLBtjcpLsIgT5zDMchE2T2qmHuzWuFqdoOqLpKav5rr7ioyWdoGKp7Fcz8=w240-h240", "")
        );
        return new Catalog(DEFAULT_CATEGORIES, apps);
    }
}
