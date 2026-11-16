package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.core.GlassMaterial;
import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.TileArtView;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;

/**
 * Home's app row with the polished tiles: the app's own artwork centred in an even frame of glass, a label that belongs to the
 * focused card only, a row that ends exactly where the quick actions end, and the header and greeting proportions.
 * Every app here is a clearly synthetic fixture (see {@link MockTvApps}); none of it is a real installed application.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")        // the TCL: 1920x1080 at xhdpi
public class HomeArtworkTest {

    private static final String[] PKGS = {"mock.tv.alpha", "mock.tv.bravo", "mock.tv.charlie", "mock.tv.delta", "mock.tv.echo", "mock.tv.foxtrot", "mock.tv.golf"};

    @Before public void installFixtures() {
        for (int i = 0; i < 5; i++) MockTvApps.install(PKGS[i], "Mock " + (char) ('A' + i), true, MockTvApps.neutralBanner(i * 60, i));
        MockTvApps.install(PKGS[5], "Mock Icon Only", false);                       // an app with an icon and no banner
        MockTvApps.install(PKGS[6], "Mock Nothing", true, null, false);             // an app that ships no artwork at all
        new Prefs(HomeTestSupport.app()).setDock(Arrays.asList(PKGS));
    }

    private static MainActivity home() throws Exception {
        MainActivity h = HomeTestSupport.startHome(true);
        HomeTestSupport.pump(h, 3);
        return h;
    }

    private static Rect inWindow(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return new Rect(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
    }

    /** The label under a tile: the column's second child. */
    private static TextView labelOf(ZeroPulseGlassAppTile tile) {
        LinearLayout column = (LinearLayout) tile.getParent();
        return (TextView) column.getChildAt(1);
    }

    // ------------------------------------------------------------------ the artwork in the glass

    @Test public void everyTileShowsItsAppsOwnArtworkAndTheMonogramOnlyWhereThereIsNone() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        assertEquals(7, tiles.size());
        int banners = 0, icons = 0, monograms = 0;
        for (ZeroPulseGlassAppTile tile : tiles) {
            TileArtView art = tile.artView();
            assertNotNull(art);
            switch (art.kind()) {
                case BANNER: banners++; break;
                case FILLED_ICON:                       // an icon whose own backdrop fills the tile
                case ICON: icons++; break;
                case MONOGRAM: monograms++; break;
                default: throw new AssertionError("unexpected " + art.kind());
            }
        }
        assertEquals(5, banners);
        assertEquals(1, icons);
        assertEquals("only the app with no artwork gets a monogram", 1, monograms);
        assertTrue("and no logo is keyed out of a banner", HomeTestSupport.all(h, tv.calmhome.launcher.ui.LiquidLogoView.class).isEmpty());
    }

    @Test public void theArtworkIsCentredInAnEvenFrameOfGlass() throws Exception {
        MainActivity h = home();
        for (ZeroPulseGlassAppTile tile : HomeTestSupport.glassTiles(h)) {
            if (tile.hasFocus()) continue;                 // the focused tile's content swells 5 % about its centre, so its bounds are scaled
            TileArtView art = tile.artView();
            Rect card = inWindow(tile), view = inWindow(art);
            Rect drawn = art.drawnRect();
            drawn.offset(view.left, view.top);
            assertEquals("centred across: " + tile.getTag(), card.centerX(), drawn.centerX(), 1);
            assertEquals("and down: " + tile.getTag(), card.centerY(), drawn.centerY(), 1);
            if (art.kind() == TileArt.Kind.BANNER || art.kind() == TileArt.Kind.FILLED_ICON) {
                int margin = TileArt.margin(tile.getHeight());
                assertEquals("only a hairline of glass is left around the artwork, evenly on every side", margin, drawn.left - card.left, 2);
                assertEquals(margin, card.right - drawn.right, 2);
                assertEquals(margin, drawn.top - card.top, 2);
                assertEquals(margin, card.bottom - drawn.bottom, 2);
                assertTrue("the artwork fills the tile: " + drawn.width() + " x " + drawn.height() + " in " + card.width() + " x " + card.height(),
                        drawn.width() >= card.width() * 0.94f && drawn.height() >= card.height() * 0.94f);
            } else {
                assertTrue("a bare icon is large but still smaller than the tile", drawn.width() < card.width() * 0.6f && drawn.height() < card.height() * 0.8f);
            }
        }
    }

    @Test public void theArtworkIsBlittedAtItsRenderedSizeSoItIsNeverResampled() throws Exception {
        MainActivity h = home();
        for (ZeroPulseGlassAppTile tile : HomeTestSupport.glassTiles(h)) {
            TileArtView art = tile.artView();
            Rect drawn = art.drawnRect();
            assertEquals(art.art().bitmap.getWidth(), drawn.width());
            assertEquals(art.art().bitmap.getHeight(), drawn.height());
        }
    }

    @Test public void theGlassFinishSitsAboveTheArtworkAndTakesNoFocusOrClicks() throws Exception {
        MainActivity h = home();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(0);
        View finish = tile.finishLayer();
        assertFalse(finish.isFocusable());
        assertFalse(finish.isClickable());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, finish.getImportantForAccessibility());
        assertEquals("it is the last child, above the content", tile.getChildCount() - 1, tile.indexOfChild(finish));
        assertFalse("the glass layer itself is inert too", tile.glass().isFocusable() || tile.glass().isClickable());
        assertEquals("every Home tile has its finish layer (7 + 0 quick actions)", 7, countFinishLayers(h));
        assertTrue("duplicate quick-action pills removed", HomeTestSupport.glassActions(h).isEmpty());
    }

    private static int countFinishLayers(MainActivity h) {
        int n = 0;
        for (ZeroPulseGlassAppTile t : HomeTestSupport.glassTiles(h)) if (t.finishLayer() != null) n++;
        for (ZeroPulseGlassQuickAction a : HomeTestSupport.glassActions(h)) if (a.finishLayer() != null) n++;
        return n;
    }

    // ------------------------------------------------------------------ the label under a focused card

    @Test public void theLabelBelongsToTheFocusedCardAndIsCentredUnderItWhileTheOthersAreHidden() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        assertTrue(tiles.get(0).hasFocus());
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        TextView first = labelOf(tiles.get(0));
        assertEquals("the focused card shows its name", "Mock A", first.getText().toString());
        assertEquals(1f, first.getAlpha(), 0.02f);
        Rect card = inWindow(tiles.get(0)), label = inWindow(first);
        assertEquals("centred under its card", card.centerX(), label.centerX(), 1);
        assertTrue("below the card, not over it", label.top >= card.bottom);
        for (int i = 1; i < 6; i++) assertEquals("an unfocused card shows no name: " + i, 0f, labelOf(tiles.get(i)).getAlpha(), 0.02f);
        assertEquals("an app with no artwork always shows its name (nothing else says what it is)", 0.85f, labelOf(tiles.get(6)).getAlpha(), 0.02f);

        assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        assertTrue(HomeTestSupport.dpad(h, View.FOCUS_RIGHT));
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("the label moves with the focus", 1f, labelOf(tiles.get(2)).getAlpha(), 0.02f);
        assertEquals(0f, labelOf(tiles.get(0)).getAlpha(), 0.02f);
        Rect third = inWindow(tiles.get(2));
        assertEquals(third.centerX(), inWindow(labelOf(tiles.get(2))).centerX(), 1);
        assertEquals("every label sits at the same height", label.top, inWindow(labelOf(tiles.get(2))).top);
    }

    @Test public void labelsAlwaysKeepsEveryNameVisibleAndAligned() throws Exception {
        new Prefs(HomeTestSupport.app()).setLabelsAlways(true);
        MainActivity h = home();
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        int top = -1;
        for (ZeroPulseGlassAppTile tile : HomeTestSupport.glassTiles(h)) {
            TextView l = labelOf(tile);
            assertTrue("visible: " + l.getText(), l.getAlpha() >= 0.8f);
            if (top < 0) top = inWindow(l).top;
            assertEquals(top, inWindow(l).top);
        }
    }

    // ------------------------------------------------------------------ the row, the actions, the margins

    @Test public void theTileRowIsEvenlySpacedWithZeroDuplicateQuickActions() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        assertTrue("duplicate quick actions removed", HomeTestSupport.glassActions(h).isEmpty());
        Rect firstTile = inWindow(tiles.get(0)), lastTile = inWindow(tiles.get(6));
        assertTrue("starts inside 5 % safe margin: " + firstTile, firstTile.left >= 96 - 1);
        assertTrue("ends inside 5 % safe margin: " + lastTile, lastTile.right <= 1920 - 96 + 1);
        int tileGap = inWindow(tiles.get(1)).left - firstTile.right;
        for (int i = 1; i < tiles.size(); i++) {
            assertEquals("equal tile gaps", tileGap, inWindow(tiles.get(i)).left - inWindow(tiles.get(i - 1)).right, 1);
        }
    }

    @Test public void theBottomDrawerStartsHiddenAndThePeekHandleSitsInsideSafeMargins() throws Exception {
        MainActivity h = home();
        assertFalse("drawer closed at rest", h.isAllAppsDrawerOpen());
        View drawer = h.allAppsDrawer();
        assertNotNull(drawer);
        assertEquals(View.INVISIBLE, drawer.getVisibility());
        assertEquals(0f, drawer.getAlpha(), 0.01f);
        assertTrue("translated off-screen at rest", drawer.getTranslationY() > 0f);
        TextView peek = null;
        for (TextView tv : HomeTestSupport.all(h, TextView.class)) {
            if (tv.getText() != null && tv.getText().toString().contains("All Apps & ZPAPPS")) { peek = tv; break; }
        }
        assertNotNull("slim peek handle shown", peek);
        Rect r = inWindow((View) peek.getParent());
        assertTrue("peek left inside 5 %: " + r, r.left >= 96 - 1);
        assertTrue("peek right inside 5 %: " + r, r.right <= 1920 - 96 + 1);
        assertTrue("peek above bottom safe margin: " + r, r.bottom <= 1080 - 54 + 2);
    }

    @Test public void thereIsNoRecentlyOpenedPlaceholderAndNoFooterOnLiquidGlassHome() throws Exception {
        MainActivity h = home();
        assertFalse(HomeTestSupport.hasText(h, "Recently opened"));
        assertFalse(HomeTestSupport.hasText(h, "Apps you open from ZeroPulse"));
        for (TextView tv : HomeTestSupport.all(h, TextView.class)) {
            assertFalse("no bottom branding (a hidden view does not count, a shown one does)", tv.isShown() && tv.getText().toString().startsWith("ZeroPulse TV  ·  Local launcher"));
        }
    }

    // ------------------------------------------------------------------ proportions

    @Test public void theGreetingSitsCloserToTheAppRowAndTheHeaderIsSlimmer() throws Exception {
        MainActivity h = home();
        TextView greeting = null;
        for (TextView tv : HomeTestSupport.all(h, TextView.class)) if (tv.getVisibility() == View.VISIBLE && tv.getText().toString().startsWith("Good ")) greeting = tv;
        assertNotNull(greeting);
        TextView tagline = null;
        for (TextView tv : HomeTestSupport.all(h, TextView.class)) if (tv.getText().toString().startsWith("Relax")) tagline = tv;
        assertNotNull("the subtitle is kept", tagline);
        assertEquals("centred", 960, inWindow(greeting).centerX(), 2);
        int rowTop = inWindow(HomeTestSupport.glassTiles(h).get(0)).top;
        int gap = rowTop - inWindow(tagline).bottom;
        assertTrue("less empty space between the greeting block and the app row (was about 340 px, now under 270): " + gap, gap < 270);
        assertTrue("and they do not collide: " + gap, gap > 100);

        GlassTopNavView nav = HomeTestSupport.all(h, GlassTopNavView.class).get(0);
        assertEquals("the rail is 56 dp tall (was 72)", 112, nav.getHeight());
        assertTrue("and no longer stretches across the header (was about 1440 px): " + nav.getWidth(), nav.getWidth() < 1200);
    }

    @Test public void theTileMaterialStillScalesWithTheTilesNewHeight() throws Exception {
        MainActivity h = home();
        ZeroPulseGlassAppTile tile = HomeTestSupport.glassTiles(h).get(0);
        float height = tile.glass().getHeight();
        assertEquals(GlassMaterial.unit(height), tile.glass().unit(), 1e-4f);
        assertEquals(GlassMaterial.bevel(height), tile.glass().bevel(), 1e-3f);
    }
}
