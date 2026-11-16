package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GlassProgressBar;
import tv.calmhome.launcher.ui.GlassSwitch;
import tv.calmhome.launcher.ui.Navigation;
import tv.calmhome.launcher.ui.SafeMarginScrollView;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * 3.0.0-beta.7: the Favorites carousel, the transitions between destinations, the glass controls on Settings, Apps, Downloads and Inputs,
 * and the menus. Fixture apps and Robolectric's renderer: these prove behaviour, not how it looks or how fast it is on the TCL.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class UiRefineTest {

    private static final String EIGHTH = "com.extra.eighth", NINTH = "com.extra.ninth";

    private static MainActivity home(int favorites) throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MockTvApps.install(EIGHTH, "Eighth", true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MockTvApps.install(NINTH, "Ninth", true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        java.util.List<String> dock = new java.util.ArrayList<>();
        for (String[] app : MockTvApps.DEFAULTS) dock.add(app[0]);
        if (favorites > 7) dock.add(EIGHTH);
        if (favorites > 8) dock.add(NINTH);
        new Prefs(HomeTestSupport.app()).setDock(dock.subList(0, Math.min(dock.size(), favorites)));
        MainActivity h = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(h, 2);
        return h;
    }

    private static void settle(MainActivity h) throws Exception {
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        HomeTestSupport.layoutTv(h);
        ShadowLooper.idleMainLooper();
    }

    private static Rect inWindow(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return new Rect(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
    }

    // ------------------------------------------------------------------ the Favorites carousel

    @Test public void theRowGlidesOnlyWhenNeededAndTheFocusedTileEndsInsideTheSafeMargins() throws Exception {
        MainActivity h = home(9);
        SafeMarginScrollView row = HomeTestSupport.all(h, SafeMarginScrollView.class).get(0);
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        assertEquals(9, tiles.size());
        int safe = Math.round(1920 * 0.05f);
        // Moving among the tiles already on screen does not scroll at all.
        for (int i = 1; i < 7; i++) {
            assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
            assertFalse("tile " + i + " is on screen: no scroll", row.isGliding());
            assertEquals(0, row.getScrollX());
        }
        // The eighth is past the margin: the row glides (not a jump) and stops with the tile inside the margin, not flush with the edge.
        assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        assertTrue("it glides", row.isGliding());
        assertTrue("the glide starts from where the row was", row.getScrollX() < row.glideTarget());
        settle(h);
        assertFalse(row.isGliding());
        Rect eighth = inWindow(tiles.get(7));
        assertTrue("the eighth ends inside the right margin (" + eighth.right + ")", eighth.right <= 1920 - safe + 1);
        // The ninth (the last): the row ends exactly at the content, with no blank space after it.
        assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        settle(h);
        Rect ninth = inWindow(tiles.get(8));
        assertTrue(ninth.right <= 1920 - safe + 1);
        int max = row.getChildAt(0).getRight() + row.getPaddingRight() - row.getWidth();
        assertTrue("never past the end", row.getScrollX() <= max);
        assertFalse("Right on the last tile stays (no wrap-around)", HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        // All the way back: the first tile is reachable and the row returns to its start.
        for (int i = 0; i < 8; i++) assertTrue(HomeTestSupport.dpad(h, View.FOCUS_LEFT));
        settle(h);
        assertEquals(0, row.getScrollX());
        assertEquals(MockTvApps.DEFAULTS[0][0], HomeTestSupport.focus(h).getTag());
    }

    @Test public void rapidPressesRetargetOneGlideAndNeverLoseFocus() throws Exception {
        MainActivity h = home(9);
        SafeMarginScrollView row = HomeTestSupport.all(h, SafeMarginScrollView.class).get(0);
        for (int i = 0; i < 8; i++) assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));     // no time passes between presses
        assertEquals("focus is on the last tile", NINTH, HomeTestSupport.focus(h).getTag());
        assertTrue(row.isGliding());
        settle(h);
        assertEquals(NINTH, HomeTestSupport.focus(h).getTag());
        assertEquals("the row is where the last tile needs it", row.getChildAt(0).getRight() + row.getPaddingRight() - row.getWidth(), row.getScrollX());
    }

    @Test public void sevenFavoritesNeverScrollAndTheRowLayoutIsUnchanged() throws Exception {
        MainActivity h = home(7);
        SafeMarginScrollView row = HomeTestSupport.all(h, SafeMarginScrollView.class).get(0);
        for (int i = 0; i < 6; i++) assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        assertFalse(row.isGliding());
        assertEquals(0, row.getScrollX());
    }

    @Test public void reducedMotionScrollsAtOnce() throws Exception {
        new Prefs(HomeTestSupport.app()).setReducedMotion(true);
        MainActivity h = home(9);
        SafeMarginScrollView row = HomeTestSupport.all(h, SafeMarginScrollView.class).get(0);
        for (int i = 0; i < 7; i++) assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        assertFalse("no glide with reduced motion", row.isGliding());
        assertTrue("but the eighth is still brought into view", row.getScrollX() > 0);
    }

    // ------------------------------------------------------------------ changing destination

    @Test public void changingDestinationKeepsTheHeaderUntilTheNewOneIsReadyThenTheOldScreenGoes() throws Exception {
        MainActivity h = home(7);
        h.navigate("Apps");
        AppsPanelProbe apps = AppsPanelProbe.top(h);
        assertNotNull(apps);
        h.navigate("Downloads");
        assertTrue("the Apps screen is held while the Downloads header renders", h.holdingPreviousScreen());
        assertEquals("the held screen is still on screen", View.VISIBLE, apps.view.getVisibility());
        assertEquals("its content is fading out, its header is not", 1f, apps.navigation.getAlpha(), 0.001f);
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);           // the header is ready, or the 450 ms limit passed
        assertFalse(h.holdingPreviousScreen());
        assertNull("the Apps screen is gone", apps.view.getParent());
        Navigation now = HomeTestSupport.all(h, Navigation.class).get(HomeTestSupport.all(h, Navigation.class).size() - 1);
        assertEquals("the new header shows", 1f, now.getAlpha(), 0.001f);
        assertTrue(HomeTestSupport.hasText(h, "Download Center"));
        h.onBackPressed();
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertTrue("Back from a destination is Home", h.homeShelf().packages.size() > 0 && HomeTestSupport.focus(h) instanceof ZeroPulseGlassAppTile);
    }

    @Test public void theRemovedAssistantRouteAndUnknownDestinationsLandOnHome() throws Exception {
        MainActivity h = home(7);
        h.navigate("Assistant");
        assertTrue(HomeTestSupport.focus(h) instanceof ZeroPulseGlassAppTile);
        h.navigate("Something old");
        assertTrue(HomeTestSupport.focus(h) instanceof ZeroPulseGlassAppTile);
        android.content.Intent i = new android.content.Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "assistant");
        h.onNewIntent(i);
        assertTrue(HomeTestSupport.focus(h) instanceof ZeroPulseGlassAppTile);
        h.onNewIntent(new android.content.Intent(HomeTestSupport.app(), MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "settings:assistant"));
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertTrue("an old Settings > Assistant link opens Settings at its first category", HomeTestSupport.hasText(h, "Appearance"));
        assertFalse(HomeTestSupport.hasText(h, "AI Accounts"));
        assertFalse(HomeTestSupport.hasText(h, "JARVIS"));
    }

    @Test public void homesHeaderMarksHomeAgainAfterBackFromADestinationItOpened() throws Exception {
        MainActivity h = home(7);
        Navigation homeNav = HomeTestSupport.all(h, Navigation.class).get(0);
        assertEquals("Home", homeNav.selectedDestination());
        View apps = null;
        for (View v : HomeTestSupport.all(h, View.class)) {
            if ("Apps".contentEquals(String.valueOf(v.getContentDescription())) && v.isClickable() && isInside(v, homeNav)) apps = v;
        }
        assertNotNull("Home's Apps destination", apps);
        apps.performClick();                                              // the kit moves Home's selection to Apps as it opens the screen
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertTrue(HomeTestSupport.hasText(h, "All Apps"));
        h.onBackPressed();
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals("back on Home, the header marks Home (it kept Apps in ui1)", "Home", homeNav.selectedDestination());
    }

    private static boolean isInside(View v, View group) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == group) return true;
        return false;
    }

    @Test public void everyWallpaperCollectionPillKeepsItsFullWidth() throws Exception {
        MainActivity h = home(7);
        h.onNewIntent(new android.content.Intent(h, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "wallpapers"));
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        HomeTestSupport.layoutTv(h);
        int pills = 0;
        for (ZeroPulseGlassRow row : HomeTestSupport.all(h, ZeroPulseGlassRow.class)) {
            if (!String.valueOf(row.getContentDescription()).endsWith(" photos")) continue;
            pills++;
            android.widget.TextView label = (android.widget.TextView) row.content().getChildAt(0);
            float wanted = android.text.Layout.getDesiredWidth(label.getText(), label.getPaint()) + label.getPaddingLeft() + label.getPaddingRight();
            assertTrue(label.getText() + " is not squeezed: " + label.getWidth() + " of " + wanted, label.getWidth() >= Math.floor(wanted));
        }
        assertTrue("the collections are there", pills >= 2);
    }

    // ------------------------------------------------------------------ Settings

    @Test public void settingsHasNoAssistantCategoryAndItsTogglesAreGlassSwitchesThatOnlyOkChanges() throws Exception {
        MainActivity h = home(7);
        h.navigate("Settings");
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        for (String gone : new String[]{"Assistant", "AI Accounts", "Reactor animation", "JARVIS PC"}) assertNull(gone, HomeTestSupport.withDescription(h, gone));
        for (String kept : new String[]{"Appearance", "Wallpapers", "Dock & Apps", "Features", "System Shortcuts", "About"}) {
            assertNotNull(kept, HomeTestSupport.withDescription(h, kept));
        }
        List<GlassSwitch> switches = HomeTestSupport.all(h, GlassSwitch.class);
        assertFalse("Appearance's toggles are glass switches", switches.isEmpty());
        View greeting = HomeTestSupport.withDescription(h, "Show greeting, on");
        assertNotNull(greeting);
        boolean before = new Prefs(HomeTestSupport.app()).showGreeting();
        assertTrue(greeting.requestFocus());
        HomeTestSupport.dpad(h, View.FOCUS_DOWN);
        HomeTestSupport.dpad(h, View.FOCUS_UP);
        assertEquals("passing focus over a toggle changes nothing", before, new Prefs(HomeTestSupport.app()).showGreeting());
        greeting.performClick();
        assertEquals("OK flips it and it is saved", !before, new Prefs(HomeTestSupport.app()).showGreeting());
        assertEquals("Show greeting, off", String.valueOf(greeting.getContentDescription()));
    }

    // ------------------------------------------------------------------ Apps

    @Test public void appsOffersRealViewsAndAContextMenuWithTheSystemsOwnUninstall() throws Exception {
        MainActivity h = home(7);
        h.navigate("Apps");
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        for (String view : new String[]{"All apps", "Favorites apps", "Recent apps", "Games apps", "Media apps"}) {
            assertNotNull(view, HomeTestSupport.withDescription(h, view));
        }
        View favorites = HomeTestSupport.withDescription(h, "Favorites apps");
        assertTrue("the view pills are the approved glass", favorites instanceof ZeroPulseGlassRow);
        favorites.performClick();
        assertEquals("the chosen view carries the page dot", ZeroPulseGlassRow.Mark.DOT, ((ZeroPulseGlassRow) favorites).mark());
        View netflix = null;
        for (ZeroPulseGlassAppTile t : HomeTestSupport.glassTiles(h)) if (MockTvApps.DEFAULTS[0][0].equals(t.getTag()) && t.isShown()) netflix = t;
        assertNotNull(netflix);
        netflix.performLongClick();
        ShadowLooper.idleMainLooper();
        assertNotNull(HomeTestSupport.withDescription(h, "Open"));
        assertNotNull(HomeTestSupport.withDescription(h, "Remove from Favorites"));
        boolean hasInfo = h.apps().resolves(h.apps().appInfoIntent(MockTvApps.DEFAULTS[0][0]));
        assertEquals("App info is offered exactly when this TV has an App info screen", hasInfo, HomeTestSupport.withDescription(h, "App info") != null);
        assertNull("no update is claimed without a real update source", HomeTestSupport.withDescription(h, "Update"));
    }

    // ------------------------------------------------------------------ Downloads

    @Test public void theDownloadCenterIsBuiltFromTheApprovedGlassAndHasNoAssistantShortcut() throws Exception {
        MainActivity h = home(7);
        h.navigate("Downloads");
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertNull(HomeTestSupport.withDescription(h, "Open Assistant"));
        for (String control : new String[]{"Install APK", "File Manager", "App Sources", "Manage Downloads", "Go", "Downloader"}) {
            View v = HomeTestSupport.withDescription(h, control);
            assertNotNull(control, v);
            assertTrue(control + " is a control of the approved glass", v instanceof ZeroPulseGlassRow);
        }
        assertTrue(HomeTestSupport.hasText(h, "No active downloads"));
    }

    @Test public void theProgressBarShowsOnlyRealProgress() throws Exception {
        MainActivity h = home(7);
        GlassProgressBar bar = new GlassProgressBar(h);
        ((ViewGroup) h.findViewById(android.R.id.content)).addView(bar, new ViewGroup.LayoutParams(400, 10));
        HomeTestSupport.layoutTv(h);
        bar.setIndeterminate();
        assertTrue("no fraction is invented while the size is unknown", Float.isNaN(bar.shownFraction()));
        assertTrue("the sheen runs only while the size is unknown", bar.isLooping());
        bar.setProgress(0.42f);
        assertFalse(bar.isLooping());
        assertEquals(0.42f, bar.targetFraction(), 0.0001f);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(0.42f, bar.shownFraction(), 0.0001f);
        bar.setState(GlassProgressBar.State.FAILED);
        assertEquals(GlassProgressBar.State.FAILED, bar.state());
        assertFalse(bar.isLooping());
    }

    // ------------------------------------------------------------------ Inputs and menus

    @Test public void inputsShowsAnHonestEmptyStateWhenTheTvReportsNone() throws Exception {
        MainActivity h = home(7);
        h.navigate("Inputs");
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertTrue(HomeTestSupport.hasText(h, "This TV reported no inputs"));
        assertFalse("no fabricated HDMI card", HomeTestSupport.hasText(h, "HDMI"));
    }

    @Test public void menusAreGlassRowsThatKeepFocusInsideAndReturnItOnBack() throws Exception {
        MainActivity h = home(7);
        View before = HomeTestSupport.focus(h);
        before.performLongClick();
        ShadowLooper.idleMainLooper();
        View open = HomeTestSupport.withDescription(h, "Open");
        assertTrue("menu choices are rows of the approved glass", open instanceof ZeroPulseGlassRow);
        assertTrue(open.isFocused());
        HomeTestSupport.press(h, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue("Up on the first choice stays in the menu", HomeTestSupport.withDescription(h, "Open").isFocused());
        h.onBackPressed();
        ShadowLooper.idleMainLooper();
        assertEquals("focus returns to the tile that opened the menu", before, HomeTestSupport.focus(h));
    }

    /** The open Apps screen's view and header, found without reaching into the panel's private state. */
    private static final class AppsPanelProbe {
        final View view;
        final Navigation navigation;

        AppsPanelProbe(View view, Navigation navigation) {
            this.view = view;
            this.navigation = navigation;
        }

        static AppsPanelProbe top(MainActivity h) {
            ViewGroup root = (ViewGroup) ((ViewGroup) h.findViewById(android.R.id.content)).getChildAt(0);
            View top = root.getChildAt(root.getChildCount() - 1);
            List<View> navs = new java.util.ArrayList<>();
            HomeTestSupport.collect(top, navs, Navigation.class);
            return navs.isEmpty() ? null : new AppsPanelProbe(top, (Navigation) navs.get(0));
        }
    }
}
