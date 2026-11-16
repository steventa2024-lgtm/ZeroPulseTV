package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.view.View;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.core.WhatsNew;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.screens.WhatsNewPanel;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Phase D on the real Home (simulated: Robolectric, not the TV): What's New is a discreet dot on Settings and a modal with this build's
 * real notes, the focused favorite's actions are a menu (the bottom buttons never change meaning), and the row's title says what OK held
 * does while a favorite has focus.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PremiumContextTest {

    private static MainActivity home() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        return HomeTestSupport.startHome(true);
    }

    private static GlassTopNavView nav(MainActivity h) {
        return HomeTestSupport.all(h, GlassTopNavView.class).get(0);
    }

    private static void settle() {
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
    }

    @Test public void whatsNewIsADotOnSettingsUntilItsNotesAreOpenedAndTheNotesAreThisBuildsOwn() {
        MainActivity h = home();
        assertTrue("an unseen version shows the dot", nav(h).itemBadge("Settings"));
        for (String tab : new String[]{"Home", "Apps", "Downloads", "Inputs", "Settings"}) {
            java.util.ArrayList<View> found = new java.util.ArrayList<>();
            nav(h).findViewsWithText(found, tab, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            assertFalse("still the five destinations, no sixth: " + tab, found.isEmpty());
        }
        View before = h.getWindow().getDecorView().findFocus();
        h.open(new WhatsNewPanel(h));
        settle();
        assertTrue(HomeTestSupport.hasText(h, "What's new in ZeroPulse TV " + BuildConfig.VERSION_NAME));
        assertTrue(HomeTestSupport.hasText(h, WhatsNew.current().title));
        assertTrue(HomeTestSupport.hasText(h, "·  " + WhatsNew.current().highlights.get(0)));
        assertEquals("seen once opened", BuildConfig.VERSION_NAME, new Prefs(HomeTestSupport.app()).whatsNewSeen());
        h.onBackPressed();
        settle();
        assertFalse("the dot goes once the notes have been seen", nav(h).itemBadge("Settings"));
        assertEquals("focus returned where it was", before, h.getWindow().getDecorView().findFocus());

        MainActivity again = home();
        assertFalse("and stays gone for this version", nav(again).itemBadge("Settings"));
    }

    @Test public void theFocusedFavoritesActionsAreAMenuWithOpenAppInfoAndASafeUninstall() {
        MainActivity h = home();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(1);
        tile.requestFocus();
        settle();
        assertTrue(tile.performLongClick());
        settle();
        assertTrue(HomeTestSupport.hasText(h, "Open"));
        assertTrue(HomeTestSupport.hasText(h, "Remove from Favorites"));
        assertFalse("no Update without a real update source", HomeTestSupport.hasText(h, "Update"));
        assertTrue("the bottom buttons keep their meaning", HomeTestSupport.hasText(h, "All Apps") && HomeTestSupport.hasText(h, "Wallpapers"));
    }

    @Test public void thisLauncherAndSystemAppsAreNeverOfferedForRemoval() {
        MainActivity h = home();
        tv.calmhome.launcher.data.Apps apps = new tv.calmhome.launcher.data.Apps(h);
        assertFalse("never itself", apps.userRemovable(h.getPackageName()));
        assertFalse("never an app that is not installed", apps.userRemovable("com.not.installed"));
        assertFalse(apps.userRemovable(null));
    }

    @Test public void theRowTitleSaysWhatOkHeldDoesWhileAFavoriteHasFocus() {
        MainActivity h = home();
        HomeTestSupport.glassTiles(h).get(0).requestFocus();
        settle();
        assertEquals(MainActivity.SHELF_HINT, h.shelfTitle().getText().toString());
        assertTrue(h.shelfTitle().getAlpha() > 0.99f);
        View action = HomeTestSupport.glassActions(h).get(0);
        assertTrue(action.requestFocus());
        settle();
        assertEquals("back to its name when focus leaves the row", "Favorites", h.shelfTitle().getText().toString());
    }
}
