package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassClockView;
import tv.calmhome.glasscomponents.GraphiteGlassRenderer;
import tv.calmhome.launcher.screens.SettingsPanel;
import tv.calmhome.launcher.screens.WallpapersPanel;
import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.LiquidGlass;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * The Wallpapers and Settings screens on the approved glass: every control is one {@link ZeroPulseGlassRow} (no old painter, no
 * wrapper card, no outline), list rows render only once they are reached, the panes carry one hairline rim, the material copies
 * are shared with Home, and the clock and the logo carry the concept's luminous treatment. Native graphics.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PanelGlassTest {

    private static MainActivity home() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MainActivity home = HomeTestSupport.startHome(true);
        GlassKit kit = home.theme().kit;
        assertTrue(HomeTestSupport.pumpUntil(home, () -> kit.surfaceCount() > 0 && kit.renderedSurfaceCount() == kit.surfaceCount(), 200));
        return home;
    }

    private static boolean anyOldPainter(View v) {
        if (v.getBackground() instanceof LiquidGlass || v.getForeground() instanceof LiquidGlass) return true;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (anyOldPainter(((ViewGroup) v).getChildAt(i))) return true;
        return false;
    }

    private static List<ZeroPulseGlassRow> rowsIn(MainActivity home) {
        return HomeTestSupport.all(home, ZeroPulseGlassRow.class);
    }

    private static int luminance(int p) {
        return Math.round(0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p));
    }

    // ------------------------------------------------------------------ the controls are the approved glass

    @Test public void theWallpapersControlsAreAllApprovedGlassWithNoWrapperNoOutlineAndNoOldPainter() throws Exception {
        MainActivity home = home();
        home.open(new WallpapersPanel(home));
        HomeTestSupport.pump(home, 4);
        assertFalse("no surface on the screen is drawn by the old panel painter", anyOldPainter(home.getWindow().getDecorView().findViewById(android.R.id.content)));
        for (String control : new String[]{"Rotation interval", "Dim overlay", "Information", "Zoom", "Focus across", "Focus down"}) {
            View row = HomeTestSupport.withDescription(home, control);
            assertNotNull(control, row);
            assertTrue(control + " is one control of the approved glass", row instanceof ZeroPulseGlassRow);
            assertTrue(control + " shows its glass all the time, as a card", ((ZeroPulseGlassRow) row).idleGlass());
            assertNull("no second background under " + control, row.getBackground());
            assertFalse("and no wrapper card behind it: " + row.getParent(), ((View) row.getParent()).getBackground() != null);
            assertTrue(row.requestFocus());
            ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
            assertNull("focus draws no outline on " + control, row.getBackground());
            assertNull(row.getForeground());
        }
        int tabs = 0, active = 0;
        for (ZeroPulseGlassRow row : rowsIn(home)) {
            if (row.getTag() instanceof String && !((String) row.getTag()).endsWith(".jpg")) {
                tabs++;
                if (row.mark() == ZeroPulseGlassRow.Mark.DOT) active++;
            }
        }
        assertTrue("every collection is a pill of glass: " + tabs, tabs >= 5);
        assertEquals("the rotating collection carries the page dot, like the rail", 1, active);
        int previews = 0;
        for (ZeroPulseGlassRow row : rowsIn(home)) if (row.getTag() instanceof String && ((String) row.getTag()).endsWith(".jpg")) previews++;
        assertEquals("the five previews sit in frames of glass", 5, previews);
    }

    @Test public void theSettingsRowsAndCategoriesAreApprovedGlassThatBelongsToFocus() throws Exception {
        MainActivity home = home();
        home.open(new SettingsPanel(home, "appearance"));
        HomeTestSupport.pump(home, 4);
        assertFalse(anyOldPainter(home.getWindow().getDecorView().findViewById(android.R.id.content)));
        List<ZeroPulseGlassRow> rows = rowsIn(home);
        int categories = 0, listRows = 0;
        for (ZeroPulseGlassRow row : rows) {
            if (row.getVisibility() != View.VISIBLE) continue;
            String d = String.valueOf(row.getContentDescription());
            if (d.equals("Appearance") || d.equals("Wallpapers") || d.equals("About")) categories++;
            else if (!row.idleGlass() && d.contains(",")) listRows++;
            assertNull("no outline or second background on " + d, row.getBackground());
            assertFalse(row.idleGlass() && !(row.getTag() instanceof String));
        }
        assertTrue("the categories are glass: " + categories, categories >= 3);
        assertTrue("and so are the rows: " + listRows, listRows >= 5);
    }

    @Test public void aListRowRendersOnlyOnceItHasFocusAndNothingElseIsRenderedForIt() throws Exception {
        MainActivity home = home();
        long submittedBefore = GraphiteGlassRenderer.Stats.submitted();
        int surfacesBefore = home.theme().kit.surfaceCount();
        home.open(new SettingsPanel(home, "appearance"));
        HomeTestSupport.pump(home, 6);
        ZeroPulseGlassRow selectedCategory = null;
        for (ZeroPulseGlassRow row : rowsIn(home)) if (row.mark() == ZeroPulseGlassRow.Mark.PLATE) selectedCategory = row;
        assertNotNull("the open category keeps a quiet pane", selectedCategory);
        int listRows = 0, rendered = 0;
        for (ZeroPulseGlassRow row : rowsIn(home)) {
            if (row.idleGlass() || row.mark() == ZeroPulseGlassRow.Mark.PLATE) continue;
            if (row.getContentDescription() != null && String.valueOf(row.getContentDescription()).contains(",")) {
                listRows++;
                if (row.glass().hasMaterial()) rendered++;
                assertEquals("an unfocused row shows no glass", 0f, row.glass().presenceLevel(), 0f);
            }
        }
        assertTrue(listRows >= 5);
        assertEquals("not one of the unreached rows was rendered", 0, rendered);

        ZeroPulseGlassRow target = null;
        for (ZeroPulseGlassRow row : rowsIn(home)) if (String.valueOf(row.getContentDescription()).startsWith("Show clock")) target = row;
        assertNotNull(target);
        final ZeroPulseGlassRow reached = target;
        assertTrue(reached.requestFocus());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals("a focused row shows its glass", 1f, reached.glass().presenceLevel(), 0.01f);
        assertTrue("and it is rendered", HomeTestSupport.pumpUntil(home, () -> reached.glass().hasMaterial(), 100));
        assertTrue("the rows still ahead are not", GraphiteGlassRenderer.Stats.submitted() - submittedBefore < 40 + surfacesBefore);
        assertEquals("the kit counts only the glass that is showing", true, home.theme().kit.surfaceCount() > surfacesBefore);
    }

    @Test public void thePanelsControlsShareTheFrostedCopiesHomeAlreadyHas() throws Exception {
        MainActivity home = home();
        int copies = home.theme().kit.surfaceVariantCount();
        home.open(new WallpapersPanel(home));
        HomeTestSupport.pump(home, 8);
        assertEquals("tabs, cards and previews use the quick action's material: no new frosted copy", copies, home.theme().kit.surfaceVariantCount());
        home.closeAll();
        home.open(new SettingsPanel(home, "appearance"));
        HomeTestSupport.pump(home, 8);
        for (ZeroPulseGlassRow row : rowsIn(home)) if (row.idleGlass() || row.mark() != ZeroPulseGlassRow.Mark.NONE) { /* present */ }
        assertEquals(copies, home.theme().kit.surfaceVariantCount());
    }

    // ------------------------------------------------------------------ a focused control reads from across the room without a white border

    @Test public void aFocusedControlIsClearlyLitWithARimOfTheHomeWeightNotAThickWhiteBorder() throws Exception {
        MainActivity home = home();
        home.open(new WallpapersPanel(home));
        HomeTestSupport.pump(home, 10);
        View row = HomeTestSupport.withDescription(home, "Zoom");
        ZeroPulseGlassRow glass = (ZeroPulseGlassRow) row;
        assertTrue(HomeTestSupport.pumpUntil(home, () -> glass.glass().hasMaterial(), 100));
        Bitmap idle = HomeTestSupport.draw(glass);
        assertTrue(glass.requestFocus());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        Bitmap focused = HomeTestSupport.draw(glass);
        double lift = HomeTestSupport.meanBrightness(focused) - HomeTestSupport.meanBrightness(idle);
        assertTrue("focus is clearly brighter than idle: +" + lift, lift > 1.5);
        int x = focused.getWidth() / 2, run = 0, best = 0;
        for (int y = 0; y < Math.min(12, focused.getHeight()); y++) {
            if (luminance(focused.getPixel(x, y)) > 205) run++;
            else { best = Math.max(best, run); run = 0; }
        }
        best = Math.max(best, run);
        assertTrue("the bright rim is no thicker than the glass's own (" + best + " px; the 3005 outline plus rim was 7 or more)", best <= 4);
    }

    // ------------------------------------------------------------------ the logo

    @Test public void theHeaderLogoCarriesTheConceptsLuminousTreatmentOnTheBrandingMastersGeometry() throws Exception {
        PulseLogo logo = new PulseLogo(HomeTestSupport.app(), 40).luminous();
        logo.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.AT_MOST));
        logo.layout(0, 0, logo.getMeasuredWidth(), logo.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(logo.getWidth(), logo.getHeight(), Bitmap.Config.ARGB_8888);
        logo.draw(new Canvas(b));
        int w = b.getWidth(), h = b.getHeight();

        // The mark is the left part; the word and the suffix follow it. Brightest pixels: core, wordmark, "TV".
        int[] brightestMark = brightest(b, 0, (int) (w * 0.30f));
        int[] brightestWord = brightest(b, (int) (w * 0.36f), (int) (w * 0.78f));
        int[] brightestTv = brightest(b, (int) (w * 0.84f), w);
        assertTrue("the core of the pulse is white-cyan like the concept's (219, 253, 255): " + java.util.Arrays.toString(brightestMark),
                brightestMark[0] >= 200 && brightestMark[1] >= 235 && brightestMark[2] >= 240);
        assertTrue("'ZeroPulse' is ice white (204, 243, 255): " + java.util.Arrays.toString(brightestWord), brightestWord[0] >= 190 && brightestWord[1] >= 230);
        assertTrue("'TV' is a clear blue (111, 178, 228), not white: " + java.util.Arrays.toString(brightestTv),
                brightestTv[2] > brightestTv[0] + 60 && brightestTv[0] <= 150);

        // A halo: pixels a few pixels off the line are tinted cyan and translucent (not empty, not solid).
        int haloPixels = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < (int) (w * 0.30f); x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) > 12 && Color.alpha(p) < 120 && Color.blue(p) > Color.red(p)) haloPixels++;
        }
        assertTrue("the mark has a soft cyan halo: " + haloPixels, haloPixels > 80);
        // No badge: nothing round and silver behind the mark; its corners are empty.
        assertEquals(0, Color.alpha(b.getPixel(0, 0)));
        assertEquals(0, Color.alpha(b.getPixel(0, h - 1)));
        // The geometry is the master's: the pulse's width : height is 2.30 : 1 within 3 %.
        float markWidth = 40 * 1.35f, markHeight = 40 * (0.82f - 0.18f);
        float polylineHeight = markHeight * (0.96f - 0.04f);
        assertEquals("the same pulse as branding/logo/zeropulse-pulse-mark.svg", 2.30f, markWidth / polylineHeight, 0.07f);
        assertTrue(logo.isLuminous());
    }

    private static int[] brightest(Bitmap b, int x0, int x1) {
        int best = -1;
        int[] rgb = new int[3];
        for (int y = 0; y < b.getHeight(); y++) for (int x = x0; x < x1; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 240) continue;
            int l = luminance(p);
            if (l > best) { best = l; rgb = new int[]{Color.red(p), Color.green(p), Color.blue(p)}; }
        }
        return rgb;
    }

    // ------------------------------------------------------------------ the clock

    @Test public void theClockNumeralsHaveATranslucentInteriorWithDepthAndKeepTheirShapes() throws Exception {
        MainActivity home = home();
        GlassClockView clock = HomeTestSupport.all(home, GlassClockView.class).get(0);
        assertTrue(HomeTestSupport.pumpUntil(home, clock::hasMaterial, 100));
        clock.setOutlineContrast(false);
        clock.setInteriorFill(false);
        Bitmap before = HomeTestSupport.draw(clock);
        clock.setInteriorFill(true);
        Bitmap after = HomeTestSupport.draw(clock);
        assertEquals(before.getWidth(), after.getWidth());

        long sumBefore = 0, sumAfter = 0, n = 0;
        double varBefore = 0, varAfter = 0;
        List<int[]> inside = new ArrayList<>();
        for (int y = 3; y < before.getHeight() - 3; y++) for (int x = 3; x < before.getWidth() - 3; x++) {
            boolean solid = true;
            for (int dy = -2; dy <= 2 && solid; dy++) for (int dx = -2; dx <= 2; dx++) if (Color.alpha(before.getPixel(x + dx, y + dy)) < 250) { solid = false; break; }
            if (solid) inside.add(new int[]{x, y});
        }
        assertTrue("the numerals have an interior to measure: " + inside.size(), inside.size() > 60);
        for (int[] at : inside) {
            sumBefore += luminance(before.getPixel(at[0], at[1]));
            sumAfter += luminance(after.getPixel(at[0], at[1]));
            n++;
        }
        double meanBefore = sumBefore / (double) n, meanAfter = sumAfter / (double) n;
        for (int[] at : inside) {
            varBefore += Math.pow(luminance(before.getPixel(at[0], at[1])) - meanBefore, 2);
            varAfter += Math.pow(luminance(after.getPixel(at[0], at[1])) - meanAfter, 2);
        }
        assertTrue("the interior is lighter than the bare glass (3005: it was the sky): " + meanBefore + " -> " + meanAfter, meanAfter > meanBefore + 8);
        assertTrue("but still translucent, not a white fill: " + meanAfter, meanAfter < meanBefore + 90);
        assertTrue("the wallpaper still shows through it", Math.sqrt(varAfter / n) > 0.5 * Math.sqrt(varBefore / n) - 0.5);
        for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
            if (Color.alpha(before.getPixel(x, y)) == 0) assertTrue("nothing is added outside the numerals", Color.alpha(after.getPixel(x, y)) <= 2);
        }
    }
}
