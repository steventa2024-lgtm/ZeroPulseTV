package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.HomeShelf;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Home's app row against a device that really has apps (see {@link MockTvApps}): saved favorites come back as real tiles,
 * an empty favorites list offers real installed apps as suggestions that are never saved, a TV with nothing launchable gets
 * an honest empty state, and installing, removing or switching the look never changes what was saved.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)                            // real pixels: the banners are keyed and the glass is rendered
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class HomeAppsTest {

    private static final String NETFLIX = "com.netflix.ninja", YOUTUBE = "com.google.android.youtube.tv", TOOL = "com.acme.tool";

    private static List<String> tags(List<ZeroPulseGlassAppTile> tiles) {
        List<String> out = new ArrayList<>();
        for (ZeroPulseGlassAppTile t : tiles) out.add(t.getTag() instanceof String ? (String) t.getTag() : null);
        return out;
    }

    private static void installThree() {
        MockTvApps.install(NETFLIX, "Netflix", true, MockTvApps.logoBanner(Color.rgb(20, 20, 20), Color.rgb(229, 9, 20)));
        MockTvApps.install(YOUTUBE, "YouTube", true, MockTvApps.pictureBanner());
        MockTvApps.install(TOOL, "Acme Tool", false);                          // a phone-style app: icon only, no TV banner
    }

    // ------------------------------------------------------------------ favorites come back

    @Test public void savedFavoritesAreRestoredAsRealTilesInTheirSavedOrder() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(TOOL, NETFLIX));
        String before = HomeTestSupport.raw().getString("dock", null);

        MainActivity home = HomeTestSupport.startHome(true);

        assertEquals(Arrays.asList(TOOL, NETFLIX), tags(HomeTestSupport.glassTiles(home)));
        assertEquals(HomeShelf.Mode.FAVORITES, home.homeShelf().mode);
        assertEquals("Acme Tool", String.valueOf(HomeTestSupport.glassTiles(home).get(0).getContentDescription()));
        assertEquals("Netflix", String.valueOf(HomeTestSupport.glassTiles(home).get(1).getContentDescription()));
        assertTrue("the real names are on the tiles", HomeTestSupport.hasText(home, "Acme Tool") && HomeTestSupport.hasText(home, "Netflix"));
        assertEquals("opening Home never rewrites the saved favorites", before, HomeTestSupport.raw().getString("dock", null));
    }

    @Test public void theCalmHomeDefaultSevenAreShownWhenInstalledAndNeverWrittenBack() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.pictureBanner());
        MockTvApps.install(TOOL, "Acme Tool", false);
        assertFalse("a fresh install has never saved a dock", HomeTestSupport.raw().contains("dock"));

        MainActivity home = HomeTestSupport.startHome(true);

        List<String> expected = new ArrayList<>();
        for (String[] app : MockTvApps.DEFAULTS) expected.add(app[0]);
        assertEquals(expected, tags(HomeTestSupport.glassTiles(home)));
        assertEquals(7, HomeTestSupport.glassTiles(home).size());
        assertEquals(DockList.DEFAULT, expected);
        assertFalse("and showing the defaults still saves nothing", HomeTestSupport.raw().contains("dock"));
    }

    @Test public void tilesShowTheAppsOwnArtworkNotOurs() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(NETFLIX, YOUTUBE, TOOL));
        MainActivity home = HomeTestSupport.startHome(true);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(home);
        assertEquals(3, tiles.size());
        // Every tile shows the app's own artwork: a TV banner as the app shipped it (never a logo keyed out of it, never text
        // we drew), and, for an app with no banner, its own icon.
        assertTrue("no logo is keyed out of a banner any more", HomeTestSupport.all(home, tv.calmhome.launcher.ui.LiquidLogoView.class).isEmpty());
        int banners = 0, icons = 0;
        for (ZeroPulseGlassAppTile tile : tiles) {
            tv.calmhome.launcher.ui.TileArtView art = tile.artView();
            assertNotNull("every tile has artwork above its glass", art);
            if (art.kind() == tv.calmhome.launcher.core.TileArt.Kind.BANNER) {
                banners++;
                assertEquals("it is the app's own 320 px banner", 320, art.art().sourceWidth);
                assertEquals("activity-banner", art.art().origin);
            } else if (art.kind() == tv.calmhome.launcher.core.TileArt.Kind.ICON || art.kind() == tv.calmhome.launcher.core.TileArt.Kind.FILLED_ICON) {
                icons++;
                assertEquals("it is the app's own 96 px icon", 96, art.art().sourceWidth);
                assertEquals("activity-icon", art.art().origin);
            }
        }
        assertEquals("Netflix and YouTube show their banners", 2, banners);
        assertEquals("the tool shows its icon", 1, icons);
    }

    // ------------------------------------------------------------------ All Apps

    @Test public void allAppsUsesTheSameGlassTilesAsHomeWithTheArtworkFillingThemAndEveryNameShown() {
        installThree();
        MainActivity home = HomeTestSupport.startHome(true);
        home.open(new tv.calmhome.launcher.screens.AppsPanel(home));
        ShadowLooper.idleMainLooper();
        List<android.widget.GridLayout> grids = HomeTestSupport.all(home, android.widget.GridLayout.class);
        assertEquals("one grid of apps", 1, grids.size());
        android.widget.GridLayout grid = grids.get(0);
        int seen = 0, banners = 0, filledIcons = 0;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View column = grid.getChildAt(i);
            View card = tv.calmhome.launcher.ui.Widgets.cardOf(column);
            assertTrue("Updated UI: the glass tile Home uses, not a flat card", card instanceof ZeroPulseGlassAppTile);
            ZeroPulseGlassAppTile tile = (ZeroPulseGlassAppTile) card;
            tv.calmhome.launcher.ui.TileArtView art = tile.artView();
            assertNotNull("every tile has artwork above its glass", art);
            int w = card.getLayoutParams().width, h = card.getLayoutParams().height;
            switch (art.kind()) {
                case BANNER: banners++; break;
                case FILLED_ICON: filledIcons++; break;
                default: break;
            }
            if (art.kind() == tv.calmhome.launcher.core.TileArt.Kind.BANNER || art.kind() == tv.calmhome.launcher.core.TileArt.Kind.FILLED_ICON) {
                assertTrue("the artwork fills the tile: " + art.art().bitmap.getWidth() + " x " + art.art().bitmap.getHeight() + " in " + w + " x " + h,
                        art.art().bitmap.getWidth() >= w * 0.94f && art.art().bitmap.getHeight() >= h * 0.94f);
            }
            android.widget.TextView name = (android.widget.TextView) ((android.view.ViewGroup) column).getChildAt(1);
            assertTrue("the name stays visible under every tile: " + name.getText(), name.getAlpha() >= 0.8f);
            seen++;
        }
        assertTrue("the installed apps are all there: " + seen, seen >= 3);
        assertTrue("banners fill their tiles", banners >= 2);
        assertTrue("so does an icon with a backdrop of its own", filledIcons >= 1);
    }

    @Test public void theDownloadCentersInstalledAppsStripFillsItsTilesWithTheAppsOwnArtworkToo() {
        installThree();
        MainActivity home = HomeTestSupport.startHome(true);
        home.open(new tv.calmhome.launcher.screens.DownloadsPanel(home));
        ShadowLooper.idleMainLooper();
        // 3.0.0-beta.7: the strip's tiles are the same glass tiles as Home's Favorites, with the app's own artwork above the glass.
        ZeroPulseGlassAppTile netflix = null, youtube = null;
        for (ZeroPulseGlassAppTile v : HomeTestSupport.glassTiles(home)) {
            if (NETFLIX.equals(v.getTag())) netflix = v;
            if (YOUTUBE.equals(v.getTag())) youtube = v;
        }
        assertNotNull("Netflix is on the strip", netflix);
        assertNotNull("so is YouTube", youtube);
        for (ZeroPulseGlassAppTile tile : new ZeroPulseGlassAppTile[]{netflix, youtube}) {
            List<View> art = new ArrayList<>();
            HomeTestSupport.collect(tile, art, tv.calmhome.launcher.ui.TileArtView.class);
            assertEquals("one piece of artwork", 1, art.size());
            tv.calmhome.launcher.ui.TileArtView view = (tv.calmhome.launcher.ui.TileArtView) art.get(0);
            assertTrue("the app's own artwork, not a monogram", view.art().isGenuine());
            android.graphics.Bitmap b = view.art().bitmap;
            int h = tile.getLayoutParams().height, w = tile.getLayoutParams().width;
            assertTrue("rendered for the tile's own size: " + b.getHeight() + " of " + h, b.getHeight() <= h);
            assertTrue("and fits its width: " + b.getWidth() + " of " + w, b.getWidth() <= w);
        }
    }

    // ------------------------------------------------------------------ launching

    @Test public void okOnATileLaunchesTheAppThroughItsRealLaunchIntent() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(NETFLIX, TOOL));
        MainActivity home = HomeTestSupport.startHome(true);
        ZeroPulseGlassAppTile netflix = HomeTestSupport.glassTiles(home).get(0);
        assertTrue(netflix.performClick());
        Intent started = shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        assertNotNull("a launch intent was started", started);
        assertEquals(NETFLIX, started.getComponent().getPackageName());
        assertEquals("the leanback entry point is used for a TV app", NETFLIX + ".MainActivity", started.getComponent().getClassName());
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals("the history is recorded", NETFLIX, new Prefs(HomeTestSupport.app()).recentlyOpened().get(0));

        assertTrue(HomeTestSupport.glassTiles(home).get(1).performClick());
        assertEquals("a launcher-only app opens through the regular launcher entry", TOOL,
                shadowOf(HomeTestSupport.app()).getNextStartedActivity().getComponent().getPackageName());
    }

    @Test public void theAppsHelperResolvesBothKindsOfLauncherEntry() {
        installThree();
        Apps apps = new Apps(HomeTestSupport.app());
        assertTrue(apps.usable(NETFLIX) && apps.usable(YOUTUBE) && apps.usable(TOOL));
        assertFalse(apps.usable("com.not.installed"));
        assertTrue(apps.launchIntent(NETFLIX).hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
        assertTrue(apps.launchIntent(TOOL).hasCategory(Intent.CATEGORY_LAUNCHER));
        assertEquals("sorted by name", Arrays.asList(TOOL, NETFLIX, YOUTUBE), apps.all());
        assertEquals(new java.util.HashSet<>(Arrays.asList(NETFLIX, YOUTUBE)), apps.leanbackPackages());
        assertEquals("Acme Tool", apps.label(TOOL));
        assertNotNull(apps.icon(NETFLIX));
        assertNotNull("a TV banner is found", apps.banner(NETFLIX));
        assertNull("an app without one has none", apps.banner(TOOL));
    }

    // ------------------------------------------------------------------ suggestions

    private static void installNine() {
        MockTvApps.install("tv.one", "Alpha TV", true);
        MockTvApps.install("tv.two", "Beta TV", true);
        MockTvApps.install("tv.three", "Gamma TV", true);
        MockTvApps.install("tv.four", "Delta TV", true);
        MockTvApps.install("tv.five", "Epsilon TV", true);
        MockTvApps.install("ph.one", "Aardvark", false);
        MockTvApps.install("ph.two", "Badger", false);
        MockTvApps.install("ph.three", "Camel", false);
        MockTvApps.install("ph.four", "Dingo", false);
    }

    @Test public void emptyFavoritesOfferRealInstalledAppsAsSuggestionsThatAreNeverSaved() {
        installNine();
        HomeTestSupport.raw().edit().putString("dock", "").commit();            // the viewer emptied the dock
        new Prefs(HomeTestSupport.app()).recordOpened("ph.two");

        MainActivity home = HomeTestSupport.startHome(true);

        assertEquals(HomeShelf.Mode.SUGGESTIONS, home.homeShelf().mode);
        List<String> shown = tags(HomeTestSupport.glassTiles(home));
        assertEquals(7, shown.size());
        assertEquals("what the viewer opened last comes first", "ph.two", shown.get(0));
        assertEquals("then the apps made for a TV, by name", Arrays.asList("tv.one", "tv.two", "tv.four", "tv.five", "tv.three"), shown.subList(1, 6));
        assertTrue("the seventh is the first of the rest", shown.get(6).startsWith("ph."));
        assertTrue("the shelf says these are suggestions", HomeTestSupport.hasText(home, "Suggested apps"));
        assertEquals("never saved: the dock is still empty", "", HomeTestSupport.raw().getString("dock", null));
        assertTrue(new Prefs(HomeTestSupport.app()).dock().isEmpty());
        // Seeing them again (a restart) still saves nothing.
        MainActivity again = HomeTestSupport.startHome(true);
        assertEquals("", HomeTestSupport.raw().getString("dock", null));
        assertEquals(shown, tags(HomeTestSupport.glassTiles(again)));
    }

    @Test public void holdingOkOnASuggestionOffersToAddItToFavoritesAndThatIsTheOnlyWayItIsSaved() {
        installNine();
        HomeTestSupport.raw().edit().putString("dock", "").commit();
        MainActivity home = HomeTestSupport.startHome(true);
        ZeroPulseGlassAppTile second = HomeTestSupport.glassTiles(home).get(1);
        String pkg = (String) second.getTag();
        assertTrue(second.performLongClick());
        ShadowLooper.idleMainLooper();
        View add = HomeTestSupport.withDescription(home, "Add to Favorites");
        if (add == null) add = textView(home, "Add to Favorites");
        assertNotNull("the menu offers Add to Favorites", add);
        assertEquals("nothing is saved until it is chosen", "", HomeTestSupport.raw().getString("dock", null));
        add.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(Collections.singletonList(pkg), new Prefs(HomeTestSupport.app()).dock());
        assertEquals(HomeShelf.Mode.FAVORITES, home.homeShelf().mode);
        assertEquals(Collections.singletonList(pkg), tags(HomeTestSupport.glassTiles(home)));
        assertTrue(ShadowToast.getTextOfLatestToast().contains("added to Favorites"));
    }

    private static View textView(MainActivity home, String text) {
        for (android.widget.TextView tv : HomeTestSupport.all(home, android.widget.TextView.class)) {
            if (text.contentEquals(tv.getText())) {
                View v = tv;
                while (v != null && !v.isClickable()) v = v.getParent() instanceof View ? (View) v.getParent() : null;
                return v == null ? tv : v;
            }
        }
        return null;
    }

    @Test public void aTvWithNothingLaunchableGetsAnHonestEmptyStateNotMadeUpTiles() {
        HomeTestSupport.raw().edit().putString("dock", "").commit();
        MainActivity home = HomeTestSupport.startHome(true);

        assertEquals(HomeShelf.Mode.EMPTY, home.homeShelf().mode);
        List<ZeroPulseGlassAppTile> cards = HomeTestSupport.glassTiles(home);
        assertEquals("one message card, no app tiles", 1, cards.size());
        assertNull("it is not an app", cards.get(0).getTag());
        assertTrue(String.valueOf(cards.get(0).getContentDescription()).startsWith("No apps to show yet"));
        assertFalse("no brand is invented", HomeTestSupport.hasText(home, "Netflix") || HomeTestSupport.hasText(home, "YouTube"));
        ViewGroup content = (ViewGroup) home.findViewById(android.R.id.content);
        int before = ((ViewGroup) content.getChildAt(0)).getChildCount();
        assertTrue(cards.get(0).performClick());
        ShadowLooper.idleMainLooper();
        assertTrue("its action opens All Apps", ((ViewGroup) content.getChildAt(0)).getChildCount() > before);
    }

    @Test public void theGraphiteHomeAlsoOffersSuggestionsAndKeepsItsAddAppsTileWhenNothingIsInstalled() {
        installNine();
        HomeTestSupport.raw().edit().putString("dock", "").commit();
        MainActivity graphite = HomeTestSupport.startHome(false);
        assertEquals(HomeShelf.Mode.SUGGESTIONS, graphite.homeShelf().mode);
        assertEquals(7, HomeTestSupport.favoritePackages(graphite).size());
        assertEquals("", HomeTestSupport.raw().getString("dock", null));
    }

    // ------------------------------------------------------------------ install and removal

    @Test public void removingAndReinstallingAnAppUpdatesTheRowAndKeepsTheSavedFavorites() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(NETFLIX, YOUTUBE));
        MainActivity home = HomeTestSupport.startHome(true);
        assertEquals(Arrays.asList(NETFLIX, YOUTUBE), tags(HomeTestSupport.glassTiles(home)));

        MockTvApps.uninstall(YOUTUBE);
        MockTvApps.broadcast(Intent.ACTION_PACKAGE_REMOVED, YOUTUBE);
        assertEquals("the removed app's tile goes", Collections.singletonList(NETFLIX), tags(HomeTestSupport.glassTiles(home)));
        assertEquals("but it stays saved, so it can come back in its place", Arrays.asList(NETFLIX, YOUTUBE), new Prefs(HomeTestSupport.app()).dock());

        MockTvApps.install(YOUTUBE, "YouTube", true, MockTvApps.pictureBanner());
        MockTvApps.broadcast(Intent.ACTION_PACKAGE_ADDED, YOUTUBE);
        assertEquals("reinstalled: back in its old place", Arrays.asList(NETFLIX, YOUTUBE), tags(HomeTestSupport.glassTiles(home)));
    }

    @Test public void aNewlyInstalledAppIsNotAddedToSavedFavorites() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Collections.singletonList(NETFLIX));
        MainActivity home = HomeTestSupport.startHome(true);
        MockTvApps.install("com.new.app", "Brand New", true);
        MockTvApps.broadcast(Intent.ACTION_PACKAGE_ADDED, "com.new.app");
        assertEquals(Collections.singletonList(NETFLIX), tags(HomeTestSupport.glassTiles(home)));
        assertEquals(Collections.singletonList(NETFLIX), new Prefs(HomeTestSupport.app()).dock());
    }

    @Test public void suggestionsGiveWayToFavoritesWhenAppsAreInstalledLater() {
        MockTvApps.install("tv.one", "Alpha TV", true);
        HomeTestSupport.raw().edit().putString("dock", "").commit();
        MainActivity home = HomeTestSupport.startHome(true);
        assertEquals(HomeShelf.Mode.SUGGESTIONS, home.homeShelf().mode);
        assertEquals(Collections.singletonList("tv.one"), tags(HomeTestSupport.glassTiles(home)));
        MockTvApps.uninstall("tv.one");
        MockTvApps.broadcast(Intent.ACTION_PACKAGE_REMOVED, "tv.one");
        assertEquals("the last app goes: an honest empty state", HomeShelf.Mode.EMPTY, home.homeShelf().mode);
    }

    // ------------------------------------------------------------------ the renderer never alters favorites

    @Test public void switchingBetweenGraphiteAndLiquidGlassNeverChangesWhatIsSaved() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(YOUTUBE, TOOL, NETFLIX));
        String saved = HomeTestSupport.raw().getString("dock", null);
        List<String> expected = Arrays.asList(YOUTUBE, TOOL, NETFLIX);
        for (boolean liquid : new boolean[]{false, true, false, true}) {
            MainActivity home = HomeTestSupport.startHome(liquid);
            assertEquals("liquid=" + liquid, expected, HomeTestSupport.favoritePackages(home));
            assertEquals("liquid=" + liquid, saved, HomeTestSupport.raw().getString("dock", null));
        }
    }

    // ------------------------------------------------------------------ recents

    @Test public void liquidGlassHomeHasNoRecentsShelfButKeepsTheHistoryUnderAllApps() {
        installThree();
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(NETFLIX, TOOL));
        MainActivity home = HomeTestSupport.startHome(true);
        assertFalse(HomeTestSupport.hasText(home, "Recently opened"));
        assertFalse("no empty placeholder either", HomeTestSupport.hasText(home, "Apps you open from ZeroPulse"));
        HomeTestSupport.glassTiles(home).get(1).performClick();                 // opens the tool: recorded
        assertEquals(TOOL, new Prefs(HomeTestSupport.app()).recentlyOpened().get(0));

        MainActivity graphite = HomeTestSupport.startHome(false);
        assertTrue("graphite Home keeps its shelf", HomeTestSupport.hasText(graphite, "Recently opened"));
    }

    /** All Apps > Recent lists the history, most recent first. */
    @Test public void allAppsListsTheRecentHistoryMostRecentFirst() {
        installThree();
        new Prefs(HomeTestSupport.app()).setLiquidGlass(true);
        new Prefs(HomeTestSupport.app()).recordOpened(NETFLIX);
        new Prefs(HomeTestSupport.app()).recordOpened(TOOL);
        Intent open = new Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "apps");
        MainActivity home = org.robolectric.Robolectric.buildActivity(MainActivity.class, open).setup().get();
        ShadowLooper.idleMainLooper();
        View recent = null;
        for (View v : HomeTestSupport.all(home, View.class)) if ("Recent".equals(v.getTag())) recent = v;
        assertNotNull("All Apps has a Recent filter", recent);
        recent.performClick();
        ShadowLooper.idleMainLooper();
        List<String> listed = new ArrayList<>();
        for (android.widget.FrameLayout card : HomeTestSupport.all(home, android.widget.FrameLayout.class)) {
            if (card.getTag() instanceof String && card.isFocusable() && card.getParent() != null
                    && card.getParent().getParent() instanceof android.widget.GridLayout) listed.add((String) card.getTag());
        }
        assertEquals("most recent first", Arrays.asList(TOOL, NETFLIX), listed);
    }
}
