package tv.calmhome.launcher.streaming;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/**
 * Minimal client for a user's own Jellyfin server (endpoints checked against Jellyfin's published OpenAPI
 * description, API 12.2): sign-in, movies, series, seasons, episodes, next up, resume and sign-out.
 *
 * Security: HTTPS only with the platform's normal certificate checks (self-signed or HTTP-only servers are
 * refused, never trusted); redirects are not followed, so the access token is only ever sent to the configured
 * server; the password is used once and not kept; errors never include URLs or tokens.
 */
public final class JellyfinApi {

    public static final int MAX_BODY = 8 * 1024 * 1024;

    /** One HTTP exchange; the real one uses HttpsURLConnection, tests supply a fake. */
    public interface Transport {
        Reply send(String method, String url, String authorization, String jsonBody) throws IOException;
    }

    public static final class Reply {
        final int status;
        final String body;

        public Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    public static final class JellyfinException extends IOException {
        private static final long serialVersionUID = 1L;
        public final boolean signedOut;

        JellyfinException(String message, boolean signedOut) {
            super(message);
            this.signedOut = signedOut;
        }
    }

    public static final class Item {
        public final String id, name, type, overview, seriesId, seriesName, imageTag, seasonId;
        public final int year, index, seasonIndex;
        public final long runtimeMs, positionMs;
        public final boolean played;
        public final List<String> genres;

        Item(JSONObject o) {
            id = o.optString("Id", null);
            name = o.optString("Name", "");
            type = o.optString("Type", "");
            overview = str(o, "Overview");
            seriesId = str(o, "SeriesId");
            seriesName = str(o, "SeriesName");
            seasonId = str(o, "SeasonId");
            JSONObject tags = o.optJSONObject("ImageTags");
            imageTag = tags != null ? str(tags, "Primary") : null;
            year = o.optInt("ProductionYear", 0);
            index = o.optInt("IndexNumber", 0);
            seasonIndex = o.optInt("ParentIndexNumber", 0);
            runtimeMs = o.optLong("RunTimeTicks", 0) / 10_000;
            JSONObject ud = o.optJSONObject("UserData");
            positionMs = ud != null ? ud.optLong("PlaybackPositionTicks", 0) / 10_000 : 0;
            played = ud != null && ud.optBoolean("Played", false);
            List<String> g = new ArrayList<>();
            JSONArray ga = o.optJSONArray("Genres");
            if (ga != null) for (int i = 0; i < ga.length() && i < 5; i++) g.add(ga.optString(i));
            genres = g;
        }

        private static String str(JSONObject o, String k) {
            return o.isNull(k) ? null : o.optString(k, null);
        }
    }

    public static final class Login {
        public final String token, userId, userName, serverId;

        Login(String token, String userId, String userName, String serverId) {
            this.token = token;
            this.userId = userId;
            this.userName = userName;
            this.serverId = serverId;
        }
    }

    static final String FIELDS = "Overview,Genres,ProductionYear";

    private final String server;          // normalised https origin + optional base path, no trailing slash
    private final String deviceId, deviceName;
    private final Transport transport;
    private String token, userId;

    public JellyfinApi(String server, String deviceId, String deviceName, Transport transport) {
        this.server = server;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.transport = transport;
    }

    public JellyfinApi signedIn(String token, String userId) {
        this.token = token;
        this.userId = userId;
        return this;
    }

    /**
     * "https://host[:port][/base]" from what the user typed, or null. HTTP is refused (this beta is HTTPS-only);
     * a bare host gets https://.
     */
    public static String normalise(String typed) {
        if (typed == null) return null;
        String s = typed.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) return null;
        if (!lower.startsWith("https://")) s = "https://" + s;
        Uri u = Uri.parse(s);
        if (u.getHost() == null || u.getHost().isEmpty() || u.getUserInfo() != null) return null;
        String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
        return "https://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() > 0 ? ":" + u.getPort() : "") + path;
    }

    public static boolean isHttp(String typed) {
        return typed != null && typed.trim().toLowerCase(Locale.ROOT).startsWith("http://");
    }

    /** True when {@code url} is on this server (same scheme, host, port and base path): only then may the token go. */
    public boolean sameServer(String url) {
        String n = url == null ? null : url.trim();
        return n != null && (n.equals(server) || n.startsWith(server + "/") || n.startsWith(server + "?"));
    }

    String authorization() {
        return "MediaBrowser Client=\"ZeroPulse TV\", Device=\"" + quote(deviceName) + "\", DeviceId=\"" + quote(deviceId)
                + "\", Version=\"2.2.0\"" + (token != null ? ", Token=\"" + token + "\"" : "");
    }

    private static String quote(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }

    // ------------------------------------------------------------------ calls

    /** Public server name and version (no sign-in needed): checks that the address really is Jellyfin. */
    public String serverName() throws IOException {
        JSONObject o = json(call("GET", "/System/Info/Public", null));
        String name = o.optString("ServerName", "");
        if (o.optString("Id", "").isEmpty()) throw new JellyfinException("That address doesn't look like a Jellyfin server.", false);
        return name.isEmpty() ? "Jellyfin" : name;
    }

    public Login login(String user, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("Username", user);
            body.put("Pw", password);
        } catch (JSONException e) {
            throw new JellyfinException("Couldn't prepare the sign-in.", false);
        }
        Reply r = transport.send("POST", server + "/Users/AuthenticateByName", authorization(), body.toString());
        if (r.status == 401 || r.status == 403) throw new JellyfinException("The user name or password wasn't accepted.", true);
        check(r);
        JSONObject o = json(r);
        JSONObject u = o.optJSONObject("User");
        String t = o.optString("AccessToken", "");
        if (t.isEmpty() || u == null) throw new JellyfinException("The server's sign-in answer wasn't understood.", false);
        token = t;
        userId = u.optString("Id");
        return new Login(t, userId, u.optString("Name", user), o.optString("ServerId", ""));
    }

    public void logout() throws IOException {
        if (token == null) return;
        check(transport.send("POST", server + "/Sessions/Logout", authorization(), null));
    }

    /** Movies or series ("Movie" / "Series"), sorted by name; {@code search} may be empty. */
    public List<Item> library(String type, String search, int start, int limit) throws IOException {
        String q = "/Items?userId=" + enc(userId) + "&includeItemTypes=" + type + "&recursive=true&sortBy=SortName"
                + "&sortOrder=Ascending&fields=" + FIELDS + "&enableUserData=true&startIndex=" + start + "&limit=" + limit
                + (search != null && !search.trim().isEmpty() ? "&searchTerm=" + enc(search.trim()) : "");
        return items(call("GET", q, null));
    }

    public List<Item> resume(String type, int limit) throws IOException {
        return items(call("GET", "/UserItems/Resume?userId=" + enc(userId) + "&includeItemTypes=" + type + "&fields="
                + FIELDS + "&enableUserData=true&limit=" + limit, null));
    }

    public List<Item> seasons(String seriesId) throws IOException {
        return items(call("GET", "/Shows/" + enc(seriesId) + "/Seasons?userId=" + enc(userId) + "&enableUserData=true", null));
    }

    public List<Item> episodes(String seriesId, String seasonId) throws IOException {
        return items(call("GET", "/Shows/" + enc(seriesId) + "/Episodes?userId=" + enc(userId) + "&seasonId=" + enc(seasonId)
                + "&fields=Overview&enableUserData=true", null));
    }

    public List<Item> nextUp(String seriesId) throws IOException {
        return items(call("GET", "/Shows/NextUp?userId=" + enc(userId) + "&seriesId=" + enc(seriesId)
                + "&fields=Overview&enableUserData=true&limit=1", null));
    }

    /** Original file, served as is (no transcoding on the server or the TV). Contains no token. */
    public String streamUrl(Item item) {
        return server + "/Videos/" + enc(item.id) + "/stream?static=true&mediaSourceId=" + enc(item.id) + "&deviceId=" + enc(deviceId);
    }

    /** Poster image (served without sign-in by Jellyfin), or null. */
    public String imageUrl(Item item, int maxHeight) {
        if (item.imageTag == null) return null;
        return server + "/Items/" + enc(item.id) + "/Images/Primary?maxHeight=" + maxHeight + "&tag=" + enc(item.imageTag);
    }

    /** Key for local favourites and progress: server-independent of the token, never contains secrets. */
    public static String key(String serverId, Item item) {
        return "jellyfin:" + serverId + ":" + item.id;
    }

    // ------------------------------------------------------------------ plumbing

    private Reply call(String method, String pathAndQuery, String body) throws IOException {
        Reply r = transport.send(method, server + pathAndQuery, authorization(), body);
        check(r);
        return r;
    }

    private static void check(Reply r) throws JellyfinException {
        if (r.status == 200 || r.status == 204) return;
        if (r.status == 401) throw new JellyfinException("Signed out by the server. Sign in again under Sources.", true);
        if (r.status == 403) throw new JellyfinException("The server refused this request.", false);
        if (r.status == 404) throw new JellyfinException("The server doesn't have this (HTTP 404).", false);
        if (r.status >= 300 && r.status < 400) {
            throw new JellyfinException("The server redirected to another address. Enter its final HTTPS address.", false);
        }
        throw new JellyfinException("The server answered with an error (HTTP " + r.status + ").", false);
    }

    private static JSONObject json(Reply r) throws JellyfinException {
        try {
            return new JSONObject(r.body);
        } catch (JSONException | NullPointerException e) {
            throw new JellyfinException("The server's answer wasn't understood.", false);
        }
    }

    private static List<Item> items(Reply r) throws JellyfinException {
        JSONObject o = json(r);
        JSONArray a = o.optJSONArray("Items");
        List<Item> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject it = a.optJSONObject(i);
            if (it != null && it.optString("Id", "").length() > 0) out.add(new Item(it));
        }
        return out;
    }

    private static String enc(String s) {
        return Uri.encode(s == null ? "" : s);
    }

    /** The real transport: HttpsURLConnection, no redirects, bounded body, timeouts. */
    public static final Transport HTTPS = (method, url, authorization, jsonBody) -> {
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            throw new JellyfinException("That isn't a valid server address.", false);
        }
        if (!(c instanceof HttpsURLConnection)) throw new JellyfinException("Only HTTPS servers can be used in this beta.", false);
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(30_000);
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization", authorization);
            c.setRequestProperty("Accept", "application/json");
            if (jsonBody != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            String body = "";
            if (status == 200) {
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (out.size() + n > MAX_BODY) throw new JellyfinException("The server's answer is too large.", false);
                        out.write(buf, 0, n);
                    }
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Reply(status, body);
        } catch (JellyfinException e) {
            throw e;
        } catch (SSLException e) {
            throw new JellyfinException("The server's certificate wasn't accepted. Self-signed certificates can't be used; "
                    + "give the server a certificate the TV trusts.", false);
        } catch (SocketTimeoutException e) {
            throw new JellyfinException("The server took too long to answer.", false);
        } catch (UnknownHostException e) {
            throw new JellyfinException("The server couldn't be found. Check the address.", false);
        } catch (IOException e) {
            throw new JellyfinException("The server couldn't be reached.", false);
        } finally {
            c.disconnect();
        }
    };
}
