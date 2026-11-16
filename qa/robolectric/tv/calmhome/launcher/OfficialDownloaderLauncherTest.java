package tv.calmhome.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;

import tv.calmhome.launcher.aftv.DownloaderStore;
import tv.calmhome.launcher.aftv.OfficialDownloaderLauncher;
import tv.calmhome.launcher.aftv.OfficialDownloaderLauncher.Outcome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

/** Opening, installing and feeding codes to the official Downloader by AFTVnews (com.esaba.downloader). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31})
public class OfficialDownloaderLauncherTest {

    private static final String DOWNLOADER = "com.esaba.downloader";
    private Context app;
    private ShadowApplication shadowApp;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        shadowApp = shadowOf(RuntimeEnvironment.getApplication());
        shadowApp.checkActivities(true);         // an intent nothing can handle throws, as on a real TV
    }

    // ---------------------------------------------------------------------------------------------- helpers

    /** A TV with a Play Store: it handles market://details links. */
    static void installStore() {
        ShadowPackageManager spm = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        ComponentName store = new ComponentName("com.android.vending", "com.android.vending.AssetBrowserActivity");
        spm.addActivityIfNotPresent(store);
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataScheme("market");
        spm.addIntentFilterForActivity(store, filter);
    }

    /** Downloader taking http links (the intent filter the code path needs; Downloader's own is not documented). */
    static void downloaderTakesLinks() {
        ShadowPackageManager spm = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        ComponentName view = new ComponentName(DOWNLOADER, DOWNLOADER + ".LinkActivity");
        spm.addActivityIfNotPresent(view);
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataScheme("http");
        spm.addIntentFilterForActivity(view, filter);
    }

    private Intent next() {
        return shadowApp.getNextStartedActivity();
    }

    private String clip() {
        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data = clipboard.getPrimaryClip();
        return data == null || data.getItemCount() == 0 ? null : String.valueOf(data.getItemAt(0).getText());
    }

    // ------------------------------------------------------------------------------------------ open or install

    @Test public void anInstalledDownloaderIsStartedThroughItsTvEntryInANewTask() {
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        installStore();
        assertTrue(OfficialDownloaderLauncher.isInstalled(app, DOWNLOADER));
        assertEquals(Outcome.OPENED, OfficialDownloaderLauncher.openOrInstallDownloader(app));
        Intent started = next();
        assertNotNull(started);
        assertEquals(DOWNLOADER, started.getComponent().getPackageName());
        assertTrue("a new task", (started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull("the store is not opened when the app is there", next());
    }

    @Test public void anAppWithOnlyAnOrdinaryLauncherEntryStillOpens() {
        MockTvApps.install(DOWNLOADER, "Downloader", false);
        assertEquals(Outcome.OPENED, OfficialDownloaderLauncher.openOrInstallDownloader(app));
        assertEquals(DOWNLOADER, next().getComponent().getPackageName());
    }

    @Test public void aMissingDownloaderOpensItsPlayStorePageOnTheInstallButton() {
        installStore();
        assertFalse(OfficialDownloaderLauncher.isInstalled(app, DOWNLOADER));
        assertEquals(Outcome.STORE_OPENED, OfficialDownloaderLauncher.openOrInstallDownloader(app));
        Intent started = next();
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals("market://details?id=com.esaba.downloader", started.getDataString());
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test public void aTvWithNoStoreSaysSoInsteadOfCrashing() {
        assertEquals(Outcome.STORE_UNAVAILABLE, OfficialDownloaderLauncher.openOrInstallDownloader(app));
        assertNull("nothing was started", next());
        assertTrue(OfficialDownloaderLauncher.message(Outcome.STORE_UNAVAILABLE, null).contains("no Play Store"));
    }

    @Test public void vlcGetsTheSameOpenOrInstallHelper() {
        installStore();
        assertEquals(Outcome.STORE_OPENED, OfficialDownloaderLauncher.openOrInstallVlc(app));
        assertEquals("market://details?id=org.videolan.vlc", next().getDataString());
        MockTvApps.install("org.videolan.vlc", "VLC", true);
        assertEquals(Outcome.OPENED, OfficialDownloaderLauncher.openOrInstallVlc(app));
        assertEquals("org.videolan.vlc", next().getComponent().getPackageName());
    }

    // ---------------------------------------------------------------------------------------------------- codes

    @Test public void aCodeIsHandedToDownloaderAsAnAftvNewsLinkAddressedToItAlone() {
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        downloaderTakesLinks();
        assertEquals(Outcome.CODE_SENT, OfficialDownloaderLauncher.sendCode(app, "1234567"));
        Intent started = next();
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals("http://aftv.news/1234567", started.getDataString());
        assertEquals("only Downloader receives it", DOWNLOADER, started.getPackage());
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNull("the app is not also started separately", next());
        assertEquals("Sent code 1234567 to Downloader.", OfficialDownloaderLauncher.message(Outcome.CODE_SENT, "1234567"));
    }

    @Test public void ifDownloaderWillNotTakeTheLinkTheCodeIsCopiedAndTheAppOpens() {
        MockTvApps.install(DOWNLOADER, "Downloader", true);          // no link handler
        assertEquals(Outcome.CODE_COPIED_AND_OPENED, OfficialDownloaderLauncher.sendCode(app, "4455"));
        assertEquals("4455", clip());
        assertEquals(DOWNLOADER, next().getComponent().getPackageName());
        assertTrue(OfficialDownloaderLauncher.message(Outcome.CODE_COPIED_AND_OPENED, "4455").contains("4455"));
    }

    @Test public void aCodeForAMissingDownloaderGoesToTheStoreAndSendsNothingElsewhere() {
        installStore();
        downloaderTakesLinks();
        assertEquals(Outcome.STORE_OPENED, OfficialDownloaderLauncher.sendCode(app, "777"));
        Intent started = next();
        assertEquals("market://details?id=com.esaba.downloader", started.getDataString());
        assertNull(next());
    }

    @Test public void onlyOneToSixteenDigitsAreEverSent() {
        MockTvApps.install(DOWNLOADER, "Downloader", true);
        downloaderTakesLinks();
        for (String bad : new String[]{"", "abc", "12 34", "12345678901234567", "1;2", "../x", null}) {
            assertEquals(String.valueOf(bad), Outcome.INVALID_CODE, OfficialDownloaderLauncher.sendCode(app, bad));
        }
        assertNull("nothing was started for a bad code", next());
        assertNull(clip());
        try {
            OfficialDownloaderLauncher.codeIntent("12x");
            fail("a link is only built from digits");
        } catch (IllegalArgumentException expected) { /* fine */ }
    }

    // ----------------------------------------------------------------------------------------------- the store

    @Test public void codesAreKeptInOrderAndSurviveANewStore() {
        DownloaderStore store = new DownloaderStore(app);
        assertTrue(store.codes().isEmpty());
        store.addCode("1234567", "My apps");
        store.addCode("42", "");
        assertEquals(2, new DownloaderStore(app).codes().size());
        assertEquals("1234567", new DownloaderStore(app).codes().get(0).code);
        assertEquals("My apps", new DownloaderStore(app).codes().get(0).label);
        try {
            store.addCode("4 2", "");
            fail();
        } catch (IllegalArgumentException expected) { /* digits only */ }
        store.removeCode("1234567");
        assertEquals("42", new DownloaderStore(app).codes().get(0).code);
    }

    @Test public void anApkLinkMustBeHttpsAndAChecksumMustBeSha256ButNothingIsPreset() {
        DownloaderStore store = new DownloaderStore(app);
        assertEquals("no APK link ships with the launcher", "", store.apkUrl());
        assertEquals("", store.apkSha256());
        for (String bad : new String[]{"http://example.com/d.apk", "ftp://example.com/d.apk", "https://user:pw@example.com/d.apk",
                "https://example.com/d.apk#frag", "example.com/d.apk", ""}) {
            try {
                store.setApkSource(bad, "");
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) { /* refused */ }
        }
        try {
            store.setApkSource("https://example.com/d.apk", "abc123");
            fail("short checksum");
        } catch (IllegalArgumentException expected) { /* refused */ }
        assertEquals("a refused link is not remembered", "", store.apkUrl());
        String sha = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789";
        store.setApkSource("https://example.com/d.apk", sha);
        assertEquals("https://example.com/d.apk", new DownloaderStore(app).apkUrl());
        assertEquals(sha.toLowerCase(java.util.Locale.ROOT), new DownloaderStore(app).apkSha256());
    }

    @Test public void theLauncherNeedsNoNewPermissionToDoAnyOfThis() throws Exception {
        PackageManager pm = app.getPackageManager();
        String[] requested = pm.getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        java.util.List<String> list = requested == null ? new java.util.ArrayList<String>() : java.util.Arrays.asList(requested);
        assertFalse(list.contains("android.permission.QUERY_ALL_PACKAGES"));
        assertFalse(list.contains("android.permission.RECORD_AUDIO"));
    }
}
