package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.ModuleData;
import tv.calmhome.launcher.data.Prefs;

/**
 * Jellyfin client and screens against a fake server whose answers follow Jellyfin's published OpenAPI shapes.
 * No real server was available: playback and real sign-in are device/server tests.
 */
@RunWith(RobolectricTestRunner.class)
public class JellyfinTest {

    private final Context c = RuntimeEnvironment.getApplication();
    static final String SERVER = "https://media.example.org:8920";
    static final String TOKEN = "tok-SECRET-123";

    /** Requests seen by the fake server: method, url, authorization, body. */
    final List<String[]> seen = new ArrayList<>();

    final JellyfinApi.Transport fake = (method, url, auth, body) -> {
        seen.add(new String[]{method, url, auth, body});
        if (url.contains("/redirect")) return new JellyfinApi.Reply(302, "");
        if (url.endsWith("/System/Info/Public")) return new JellyfinApi.Reply(200, "{\"ServerName\":\"Home Server\",\"Version\":\"10.11.0\",\"Id\":\"srv1\"}");
        if (url.endsWith("/Users/AuthenticateByName")) {
            return body.contains("\"Pw\":\"right\"")
                    ? new JellyfinApi.Reply(200, "{\"AccessToken\":\"" + TOKEN + "\",\"ServerId\":\"srv1\",\"User\":{\"Id\":\"u1\",\"Name\":\"alice\"}}")
                    : new JellyfinApi.Reply(401, "");
        }
        if (url.contains("/Sessions/Logout")) return new JellyfinApi.Reply(204, "");
        if (url.contains("/Items?") && url.contains("includeItemTypes=Movie")) {
            return new JellyfinApi.Reply(200, "{\"Items\":[{\"Id\":\"m1\",\"Name\":\"Night of the Living Dead\",\"Type\":\"Movie\","
                    + "\"ProductionYear\":1968,\"RunTimeTicks\":57600000000,\"Genres\":[\"Horror\"],\"Overview\":\"Public-domain classic.\","
                    + "\"ImageTags\":{\"Primary\":\"abc\"},\"UserData\":{\"PlaybackPositionTicks\":6000000000,\"Played\":false}},"
                    + "{\"Id\":\"m2\",\"Name\":\"Sita Sings the Blues\",\"Type\":\"Movie\",\"ProductionYear\":2008}],\"TotalRecordCount\":2}");
        }
        if (url.contains("/Items?") && url.contains("includeItemTypes=Series")) {
            return new JellyfinApi.Reply(200, "{\"Items\":[{\"Id\":\"s1\",\"Name\":\"Open Show\",\"Type\":\"Series\"}]}");
        }
        if (url.contains("/Shows/s1/Seasons")) return new JellyfinApi.Reply(200, "{\"Items\":[{\"Id\":\"se1\",\"Name\":\"Season 1\",\"Type\":\"Season\"}]}");
        if (url.contains("/Shows/s1/Episodes")) {
            return new JellyfinApi.Reply(200, "{\"Items\":[{\"Id\":\"e1\",\"Name\":\"Pilot\",\"Type\":\"Episode\",\"SeriesId\":\"s1\",\"SeriesName\":\"Open Show\","
                    + "\"SeasonId\":\"se1\",\"IndexNumber\":1,\"ParentIndexNumber\":1,\"RunTimeTicks\":26400000000},"
                    + "{\"Id\":\"e2\",\"Name\":\"Second\",\"Type\":\"Episode\",\"SeriesId\":\"s1\",\"SeasonId\":\"se1\",\"IndexNumber\":2,\"ParentIndexNumber\":1}]}");
        }
        if (url.contains("/Shows/NextUp")) return new JellyfinApi.Reply(200, "{\"Items\":[{\"Id\":\"e2\",\"Name\":\"Second\",\"Type\":\"Episode\","
                + "\"SeriesId\":\"s1\",\"SeasonId\":\"se1\",\"IndexNumber\":2,\"ParentIndexNumber\":1}]}");
        if (url.contains("/UserItems/Resume")) return new JellyfinApi.Reply(200, "{\"Items\":[]}");
        if (url.contains("/redirect")) return new JellyfinApi.Reply(302, "");
        return new JellyfinApi.Reply(404, "");
    };

    /** Test vault: reversible but not plaintext, so the test can tell whether a token was stored in the clear. */
    static final JellyfinAccount.Vault VAULT = new JellyfinAccount.Vault() {
        @Override public String seal(String token) { return new StringBuilder(token).reverse().toString() + "#sealed"; }
        @Override public String open(String sealed) { return new StringBuilder(sealed.replace("#sealed", "")).reverse().toString(); }
        @Override public void destroy() { }
    };

    @After public void clearHooks() {
        StreamingActivity.vaultForTests = null;
        StreamingActivity.transportForTests = null;
    }

    // ------------------------------------------------------------------ client

    @Test public void serverAddressesAreHttpsOnly() {
        assertNull(JellyfinApi.normalise("http://media.example.org"));
        assertTrue(JellyfinApi.isHttp("HTTP://media.example.org"));
        assertEquals("https://media.example.org", JellyfinApi.normalise("media.example.org/"));
        assertEquals("https://media.example.org:8920/jf", JellyfinApi.normalise(" https://Media.Example.org:8920/jf/ "));
        assertNull(JellyfinApi.normalise("https://user:pw@media.example.org"));
        assertNull(JellyfinApi.normalise("https://"));
    }

    @Test public void tokenGoesOnlyToTheSameServer() {
        JellyfinApi api = new JellyfinApi(SERVER, "dev", "TV", fake);
        assertTrue(api.sameServer(SERVER + "/Videos/m1/stream?static=true"));
        assertFalse(api.sameServer("https://media.example.org:8920.evil.example/Videos/m1"));
        assertFalse(api.sameServer("https://media.example.org/Videos/m1"));          // other port
        assertFalse(api.sameServer("https://cdn.example.org/m1.mp4"));
        assertFalse(api.sameServer("http://media.example.org:8920/Videos/m1"));
    }

    @Test public void signInSendsThePasswordOnceAndKeepsOnlyTheToken() throws Exception {
        JellyfinApi api = new JellyfinApi(SERVER, "dev-1", "TV", fake);
        assertEquals("Home Server", api.serverName());
        JellyfinApi.Login l = api.login("alice", "right");
        assertEquals(TOKEN, l.token);
        assertEquals("u1", l.userId);
        String[] auth = seen.get(1);
        assertEquals("POST", auth[0]);
        assertEquals(SERVER + "/Users/AuthenticateByName", auth[1]);
        assertTrue(auth[2].startsWith("MediaBrowser Client=\"ZeroPulse TV\""));
        assertTrue(auth[2].contains("DeviceId=\"dev-1\""));
        assertFalse("no token before sign-in", auth[2].contains("Token="));
        List<JellyfinApi.Item> movies = api.library("Movie", null, 0, 50);
        String[] lib = seen.get(seen.size() - 1);
        assertTrue(lib[1].startsWith(SERVER + "/Items?userId=u1&includeItemTypes=Movie&recursive=true"));
        assertTrue(lib[2].contains("Token=\"" + TOKEN + "\""));
        for (String[] r : seen.subList(2, seen.size())) assertNull("password sent only at sign-in", r[3]);
        assertEquals(2, movies.size());
        JellyfinApi.Item m = movies.get(0);
        assertEquals(1968, m.year);
        assertEquals(96 * 60_000L, m.runtimeMs);
        assertEquals(600_000L, m.positionMs);
        assertEquals("Horror", m.genres.get(0));
        assertFalse(api.streamUrl(m).contains(TOKEN));
        assertEquals(SERVER + "/Videos/m1/stream?static=true&mediaSourceId=m1&deviceId=dev-1", api.streamUrl(m));
        assertEquals(SERVER + "/Items/m1/Images/Primary?maxHeight=300&tag=abc", api.imageUrl(m, 300));
        assertNull(api.imageUrl(movies.get(1), 300));
    }

    @Test public void failuresAreExplainedWithoutSecrets() throws Exception {
        JellyfinApi api = new JellyfinApi(SERVER, "d", "TV", fake);
        try {
            api.login("alice", "wrong");
            fail();
        } catch (JellyfinApi.JellyfinException e) {
            assertTrue(e.signedOut);
            assertFalse(e.getMessage().contains("wrong"));
        }
        JellyfinApi in = new JellyfinApi(SERVER + "/redirect", "d", "TV", fake).signedIn(TOKEN, "u1");
        try {
            in.library("Movie", null, 0, 1);
            fail();
        } catch (JellyfinApi.JellyfinException e) {
            assertTrue(e.getMessage().contains("redirected"));
            assertFalse(e.getMessage().contains(TOKEN));
            assertFalse(e.getMessage().contains("example.org"));
        }
    }

    // ------------------------------------------------------------------ storage

    @Test public void accountNeverStoresThePasswordOrAPlainToken() throws Exception {
        JellyfinAccount acc = new JellyfinAccount(c, VAULT);
        JellyfinApi api = new JellyfinApi(SERVER, acc.deviceId(), "TV", fake);
        acc.save(SERVER, api.serverName(), api.login("alice", "right"));
        assertTrue(acc.connected());
        assertEquals(TOKEN, acc.token());
        String prefs = new String(Files.readAllBytes(new File(c.getDataDir(), "shared_prefs/" + ModuleData.STREAMING_PREFS + ".xml").toPath()),
                StandardCharsets.UTF_8);
        assertFalse(prefs.contains(TOKEN));
        assertFalse(prefs.contains("right"));
        assertNotNull(acc.api(fake));
        acc.forget();
        assertFalse(acc.connected());
        assertNull(acc.api(fake));
    }

    @Test public void deleteStreamingDataRemovesTheConnection() throws Exception {
        JellyfinAccount acc = new JellyfinAccount(c, VAULT);
        JellyfinApi api = new JellyfinApi(SERVER, acc.deviceId(), "TV", fake);
        acc.save(SERVER, "Home Server", api.login("alice", "right"));
        ModuleData.deleteStreamingData(c);
        assertFalse(new JellyfinAccount(c, VAULT).connected());
    }

    // ------------------------------------------------------------------ screens

    private StreamingActivity screen(boolean connected) throws Exception {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingActivity.vaultForTests = VAULT;
        StreamingActivity.transportForTests = fake;
        if (connected) {
            JellyfinAccount acc = new JellyfinAccount(c, VAULT);
            JellyfinApi api = new JellyfinApi(SERVER, acc.deviceId(), "TV", fake);
            acc.save(SERVER, "Home Server", api.login("alice", "right"));
        }
        return Robolectric.buildActivity(StreamingActivity.class).setup().get();
    }

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    private static View find(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return out.isEmpty() ? null : out.get(0);
    }

    private static <T extends View> T first(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            T t = first(((ViewGroup) v).getChildAt(i), type);
            if (t != null) return t;
        }
        return null;
    }

    /** Lets the worker thread finish and runs what it posted back to the UI thread. */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(25);
            ShadowLooper.idleMainLooper();
        }
    }

    @Test public void withoutAServerMoviesExplainsHowToConnect() throws Exception {
        StreamingActivity a = screen(false);
        View root = a.getWindow().getDecorView();
        find(root, "Movies").performClick();
        String text = allText(root);
        assertTrue(text.contains("No movie library connected"));
        assertTrue(text.contains("doesn't include any films"));
    }

    @Test public void connectFromSourcesRefusesHttpAndSignsIn() throws Exception {
        StreamingActivity a = screen(false);
        View root = a.getWindow().getDecorView();
        find(root, "Sources").performClick();
        ((EditText) find(root, "Jellyfin server")).setText("http://media.example.org");
        ((EditText) find(root, "Jellyfin user")).setText("alice");
        ((EditText) find(root, "Jellyfin password")).setText("right");
        find(root, "Connect to Jellyfin").performClick();
        settle();
        assertTrue(allText(root).contains("HTTPS only"));
        assertFalse(new JellyfinAccount(c, VAULT).connected());

        ((EditText) find(root, "Jellyfin server")).setText("media.example.org:8920");
        ((EditText) find(root, "Jellyfin user")).setText("alice");
        ((EditText) find(root, "Jellyfin password")).setText("right");
        find(root, "Connect to Jellyfin").performClick();
        settle();
        assertTrue(allText(root), allText(root).contains("Connected to Home Server as alice"));
        assertTrue(new JellyfinAccount(c, VAULT).connected());
    }

    @Test public void moviesGridDetailsAndResume() throws Exception {
        StreamingActivity a = screen(true);
        View root = a.getWindow().getDecorView();
        find(root, "Movies").performClick();
        settle();
        GridView grid = first(root, GridView.class);
        assertEquals(2, grid.getAdapter().getCount());
        grid.performItemClick(null, 0, 0);
        String details = allText(root);
        assertTrue(details.contains("1968 · 1 h 36 min · Horror"));
        assertTrue(details.contains("Resume from 10:00"));
        find(root, "Resume from 10:00").performClick();
        Intent play = shadowOf(a).getNextStartedActivity();
        assertEquals(PlayerActivity.class.getName(), play.getComponent().getClassName());
        assertEquals("MOVIES", play.getStringExtra(PlayerActivity.EXTRA_SECTION));
        assertTrue(play.getBooleanExtra(PlayerActivity.EXTRA_JELLYFIN, false));
        for (String u : play.getStringArrayExtra(PlayerActivity.EXTRA_URLS)) assertFalse(u.contains(TOKEN));
        assertEquals(600_000L, play.getLongArrayExtra(PlayerActivity.EXTRA_RESUME)[0]);
    }

    @Test public void showsSeasonsEpisodesAndNextUp() throws Exception {
        StreamingActivity a = screen(true);
        View root = a.getWindow().getDecorView();
        find(root, "TV Shows").performClick();
        settle();
        GridView grid = first(root, GridView.class);
        assertEquals(1, grid.getAdapter().getCount());
        grid.performItemClick(null, 0, 0);
        settle();
        assertTrue(allText(root).contains("Next up: S1 E2 · Second"));
        find(root, "Season 1").performClick();
        settle();
        String eps = allText(root);
        assertTrue(eps, eps.contains("S1 E1 · Pilot · 44 min"));
        find(root, "S1 E1 · Pilot · 44 min").performClick();
        Intent play = shadowOf(a).getNextStartedActivity();
        assertEquals(2, play.getStringArrayExtra(PlayerActivity.EXTRA_URLS).length);
        assertEquals(0, play.getIntExtra(PlayerActivity.EXTRA_START, -1));
        assertEquals("SHOWS", play.getStringExtra(PlayerActivity.EXTRA_SECTION));
    }
}
