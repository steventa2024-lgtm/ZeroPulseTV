package tv.calmhome.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.aftv.DownloaderStore;
import tv.calmhome.launcher.data.Prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The official Downloader by AFTVnews, wired in: the Download Center's Downloader button and screen, built the way the remote drives
 * them. (The Assistant's "Open Downloader" and "Download code" commands went with the Assistant in 3.0.0-beta.7.) Renders PNGs into build/ui-renders.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = {31}, qualifiers = "w960dp-h540dp-land-xhdpi")
public class DownloaderUiTest {

    private static final String DOWNLOADER = "com.esaba.downloader";
    private Context app;
    private ShadowApplication shadowApp;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        shadowApp = shadowOf(RuntimeEnvironment.getApplication());
        shadowApp.checkActivities(true);
        app.getSharedPreferences("zp_downloader", Context.MODE_PRIVATE).edit().clear().commit();
        Prefs prefs = new Prefs(app);
        prefs.setWallpaperPicked("lg03.jpg");
        prefs.setWallpaperInterval(0);
        prefs.setLiquidGlass(true);
        prefs.setLiquidStarting(false);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private MainActivity home(String open) {
        Intent i = new Intent(app, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, open);
        MainActivity a = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        HomeTestSupport.leaveTouchMode(a.getWindow().getDecorView());
        HomeTestSupport.layoutTv(a);
        ShadowLooper.idleMainLooper();
        return a;
    }

    private static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    private static List<View> all(Activity a) {
        List<View> out = new ArrayList<>();
        collect(a.getWindow().getDecorView(), out);
        return out;
    }

    /** The view on screen with this content description or text, or null. */
    private static View shown(Activity a, String label) {
        for (View v : all(a)) {
            if (!v.isShown()) continue;
            if (label.equals(String.valueOf(v.getContentDescription()))) return v;
            if (v instanceof TextView && !(v instanceof EditText) && label.equals(String.valueOf(((TextView) v).getText()))) return v;
        }
        return null;
    }

    private static String texts(Activity a) {
        StringBuilder b = new StringBuilder();
        for (View v : all(a)) if (v instanceof TextView && v.isShown()) b.append(((TextView) v).getText()).append('\n');
        return b.toString();
    }

    private static EditText field(Activity a, String hint) {
        for (View v : all(a)) if (v instanceof EditText && hint.equals(String.valueOf(((EditText) v).getHint()))) return (EditText) v;
        return null;
    }

    private static void click(Activity a, String label) {
        View v = shown(a, label);
        assertNotNull("on screen: " + label, v);
        v.performClick();
        ShadowLooper.idleMainLooper();
    }

    private Intent next() {
        return shadowApp.getNextStartedActivity();
    }

    private static void render(String name, Activity a) throws IOException, InterruptedException {
        HomeTestSupport.pump(a, 20);
        Bitmap b = HomeTestSupport.draw(a.getWindow().getDecorView());
        File dir = new File("build/ui-renders");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void type(Activity a, String text) {
        EditText e = field(a, "Ask a question or type a TV command");
        assertNotNull(e);
        e.setText(text);
        e.onEditorAction(EditorInfo.IME_ACTION_SEND);
        ShadowLooper.idleMainLooper();
    }

    // --------------------------------------------------------------------------------------------- Download Center

    @Test public void theDownloadCenterHasADownloaderButtonThatOpensTheDownloaderScreen() throws Exception {
        MainActivity h = home("downloads");
        assertNotNull("the existing Download Center is still there", shown(h, "Install APK"));
        assertNull("no Assistant shortcut: the Assistant was removed", shown(h, "Open Assistant"));
        render("download-center-with-downloader", h);
        click(h, "Open Downloader");
        String t = texts(h);
        assertTrue(t, t.contains("Downloader by AFTVnews"));
        assertTrue(t, t.contains("Your codes"));
        // The Download Center's header stays a moment, until the Downloader screen's own header shows real glass (at most 450 ms).
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNull("the Download Center is behind it, not on top", shown(h, "Install APK"));
    }

    @Test public void theDownloaderScreenIsAPanelThatBackAndRestoreKnow() {
        MainActivity h = home("downloader");
        assertTrue(texts(h).contains("Downloader by AFTVnews"));
        h.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertFalse("Back closes it", texts(h).contains("Your codes"));
    }

    // -------------------------------------------------------------------------------------------- not installed

    @Test public void notInstalledItOffersThePlayStoreAndAnApkLinkAndNothingIsPreset() throws Exception {
        OfficialDownloaderLauncherTest.installStore();
        MainActivity h = home("downloader");
        assertNotNull(shown(h, "Get Downloader from the Play Store"));
        String t = texts(h);
        assertTrue(t, t.contains("Not installed on this TV"));
        assertTrue(t, t.contains("Install from an APK link"));
        assertEquals("no APK link ships", "", field(h, "HTTPS link to the APK").getText().toString());
        render("downloader-not-installed", h);
        click(h, "Get Downloader from the Play Store");
        Intent store = next();
        assertEquals("market://details?id=com.esaba.downloader", store.getDataString());
        assertTrue(texts(h).contains("Opening its page in the Play Store"));
    }

    @Test public void withNoStoreTheScreenSaysSoAndSendsYouToTheApkLink() {
        MainActivity h = home("downloader");
        click(h, "Get Downloader from the Play Store");
        assertNull("nothing was started", next());
        assertTrue(texts(h).contains("no Play Store"));
        assertTrue("focus lands on the link field", field(h, "HTTPS link to the APK").hasFocus());
    }

    @Test public void theApkLinkGoesThroughTheReviewedInstallAndNoFurther() {
        MainActivity h = home("downloader");
        EditText url = field(h, "HTTPS link to the APK"), sha = field(h, "SHA-256 (64 hexadecimal digits, recommended)");
        url.setText("http://example.com/downloader.apk");
        click(h, "Review and install");
        assertTrue(texts(h), texts(h).contains("HTTPS link"));
        assertEquals("", new DownloaderStore(app).apkUrl());
        url.setText("https://example.com/downloader.apk");
        sha.setText("1234");
        click(h, "Review and install");
        assertTrue(texts(h), texts(h).contains("64 hexadecimal digits"));
        sha.setText("");
        url.setText("https://example.com/downloader.apk");
        click(h, "Review and install");
        String t = texts(h);
        assertTrue("the Download Center's own review screen: " + t, t.contains("Developer host"));
        assertTrue(t, t.contains("https://example.com/downloader.apk"));
        assertEquals("the link is remembered for next time", "https://example.com/downloader.apk", new DownloaderStore(app).apkUrl());
        assertNull("nothing was installed or started", next());
    }

    // ------------------------------------------------------------------------------------------------ installed

    @Test public void installedItOpensTheRealAppAndHidesTheApkLink() throws Exception {
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        MainActivity h = home("downloader");
        assertNotNull(shown(h, "Open Downloader"));
        assertNull("no Play Store button when it is there", shown(h, "Get Downloader from the Play Store"));
        assertNull("no APK link card when it is there", shown(h, "Install from an APK link"));
        assertTrue(texts(h).contains("Installed on this TV"));
        click(h, "Open Downloader");
        assertEquals(DOWNLOADER, next().getComponent().getPackageName());
    }

    @Test public void installingItFromTheStoreAndComingBackUpdatesTheScreen() {
        OfficialDownloaderLauncherTest.installStore();
        MainActivity h = home("downloader");
        assertNotNull(shown(h, "Get Downloader from the Play Store"));
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        h.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_ADDED, android.net.Uri.parse("package:" + DOWNLOADER)));
        ShadowLooper.idleMainLooper();
        assertNotNull("the button follows the install", shown(h, "Open Downloader"));
        assertNull(shown(h, "Get Downloader from the Play Store"));
    }

    // ------------------------------------------------------------------------------------------------- codes

    @Test public void codesCanBeSavedSentAndRemovedWithTheRemote() throws Exception {
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        OfficialDownloaderLauncherTest.downloaderTakesLinks();
        MainActivity h = home("downloader");
        assertTrue(texts(h).contains("No codes saved yet."));
        field(h, "Code (digits)").setText("1234567");
        field(h, "Name (optional)").setText("My app list");
        click(h, "Save code");
        View row = shown(h, "Send code 1234567 to Downloader");
        assertNotNull("saved and listed", row);
        assertTrue("the row shows the code's name", texts(h).contains("1234567   ·   My app list"));
        assertEquals("1234567", new DownloaderStore(app).codes().get(0).code);
        assertEquals("the new row has focus", row, h.getWindow().getDecorView().findFocus());
        field(h, "Code (digits)").setText("9876");
        click(h, "Save code");
        render("downloader-installed-with-codes", h);
        click(h, "Send code 1234567 to Downloader");
        Intent sent = next();
        assertEquals("http://aftv.news/1234567", sent.getDataString());
        assertEquals(DOWNLOADER, sent.getPackage());
        assertTrue(texts(h).contains("Sent code 1234567 to Downloader."));
        click(h, "Remove code 1234567");
        assertNull(shown(h, "Send code 1234567 to Downloader"));
        assertNotNull(shown(h, "Send code 9876 to Downloader"));
        assertEquals(1, new DownloaderStore(app).codes().size());
        click(h, "Remove code 9876");
        assertTrue(texts(h).contains("No codes saved yet."));
        assertTrue("focus is not lost", h.getWindow().getDecorView().findFocus() != null);
    }

    @Test public void aBadCodeIsRefusedWithAReasonAndNothingIsSaved() {
        MainActivity h = home("downloader");
        field(h, "Code (digits)").setText("12ab");
        click(h, "Save code");
        assertTrue(texts(h), texts(h).contains("1 to 16 digits"));
        assertTrue(new DownloaderStore(app).codes().isEmpty());
        field(h, "Code (digits)").setText("");
        click(h, "Save code");
        assertTrue(new DownloaderStore(app).codes().isEmpty());
    }

    @Test public void everyControlOnTheDownloaderScreenIsReachableWithTheRemote() {
        new DownloaderStore(app).addCode("111", "One");
        new DownloaderStore(app).addCode("222", "");
        MainActivity h = home("downloader");
        View decor = h.getWindow().getDecorView();
        View start = shown(h, "Get Downloader from the Play Store");
        assertTrue(start.requestFocus());
        List<View> reached = new ArrayList<>();
        reached.add(start);
        for (int i = 0; i < reached.size() && i < 80; i++) {
            for (int dir : new int[]{View.FOCUS_DOWN, View.FOCUS_UP, View.FOCUS_RIGHT, View.FOCUS_LEFT}) {
                reached.get(i).requestFocus();
                View f = decor.findFocus();
                View nextFocus = f == null ? null : f.focusSearch(dir);
                if (nextFocus != null && nextFocus != f && nextFocus.requestFocus(dir)) {
                    View now = decor.findFocus();
                    if (now != null && !reached.contains(now)) reached.add(now);
                }
            }
        }
        for (String label : new String[]{"Get Downloader from the Play Store", "Send code 111 to Downloader", "Remove code 111",
                "Send code 222 to Downloader", "Remove code 222", "Save code", "Review and install"}) {
            View v = shown(h, label);
            assertNotNull(label, v);
            assertTrue(label + " is reachable", reached.contains(v));
        }
        assertTrue("both code fields", reached.contains(field(h, "Code (digits)")) && reached.contains(field(h, "Name (optional)")));
        assertTrue("both APK fields", reached.contains(field(h, "HTTPS link to the APK"))
                && reached.contains(field(h, "SHA-256 (64 hexadecimal digits, recommended)")));
    }
}
