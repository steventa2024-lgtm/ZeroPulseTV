package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.screens.DownloadsPanel;
import tv.calmhome.launcher.toolbox.CodeKeypadModalPanel;
import tv.calmhome.launcher.toolbox.ToolboxCatalog;
import tv.calmhome.launcher.toolbox.ToolboxEngine;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/** Verifies the ZPAPPS native toolbox catalog, URL resolver, Liquid Glass surfaces, and navigation routes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31})
public class ZpAppsToolboxTest {

    @Test
    public void loadsAllTwentyAppsAndSixCategoriesFromAssetCatalog() {
        ToolboxCatalog.Catalog catalog = ToolboxCatalog.load(HomeTestSupport.app());
        assertEquals(6, catalog.categories.size());
        assertEquals("Live TV & Sports", catalog.categories.get(1));
        assertEquals(20, catalog.apps.size());
        ToolboxCatalog.AppEntry ak47 = catalog.byId("ak47sports");
        assertNotNull(ak47);
        assertEquals("3975858", ak47.code);
        ToolboxCatalog.AppEntry adguard = catalog.byId("adguard");
        assertNotNull(adguard);
        assertEquals("1900064", adguard.code);
    }

    @Test
    public void resolvesShortcodesAndHttpsUrls() {
        assertEquals("https://aftv.news/3975858", ToolboxEngine.resolveInputToUrl("3975858"));
        assertEquals("https://aftv.news/54794", ToolboxEngine.resolveInputToUrl(" 54794 "));
        assertEquals("https://example.com/app.apk", ToolboxEngine.resolveInputToUrl("https://example.com/app.apk"));
        assertEquals("https://example.com/app.apk", ToolboxEngine.resolveInputToUrl("http://example.com/app.apk"));
        assertEquals("https://example.com/app.apk", ToolboxEngine.resolveInputToUrl("example.com/app.apk"));
        assertNull(ToolboxEngine.resolveInputToUrl("abc"));
        assertNull(ToolboxEngine.resolveInputToUrl("   "));
    }

    @Test
    public void zpAppsOpensFromDownloadsAndExtraOpenWithUnscaledGlass() {
        ToolboxEngine.get(HomeTestSupport.app()).setOnboardingCompleted(true);
        MainActivity home = HomeTestSupport.startHome(true);

        home.open(new DownloadsPanel(home));
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertTrue(HomeTestSupport.hasText(home, "ZPAPPS Toolbox"));

        home.onNewIntent(new Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "zpapps"));
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        HomeTestSupport.layoutTv(home);

        assertTrue(HomeTestSupport.hasText(home, "ZPAPPS Toolbox"));
        assertTrue(HomeTestSupport.hasText(home, "Discover"));
        assertTrue(HomeTestSupport.hasText(home, "All 20 Apps"));
        assertTrue(HomeTestSupport.hasText(home, "Essentials (3)"));
        assertTrue(HomeTestSupport.hasText(home, "Purge APK Cache"));

        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        assertFalse("ZPAPPS shelves show glass app tiles", tiles.isEmpty());
        for (View surface : HomeTestSupport.all(home, GraphiteGlassSurface.class)) {
            assertEquals("glass primitives must never scaleX", 1f, surface.getScaleX(), 0.0001f);
            assertEquals("glass primitives must never scaleY", 1f, surface.getScaleY(), 0.0001f);
        }

        home.open(new CodeKeypadModalPanel(home, code -> { }));
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
        assertTrue(HomeTestSupport.hasText(home, "Downloader Shortcode"));
        assertTrue(HomeTestSupport.hasText(home, "Download & Install"));

        home.onBackPressed();
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
        assertTrue("returning to ZPAPPS restores toolbox", HomeTestSupport.hasText(home, "Purge APK Cache"));
    }
}
