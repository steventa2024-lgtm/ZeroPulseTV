package tv.calmhome.launcher.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Liquid Glass Home, the pure parts: how the approved graphite material scales to a surface, what the app row shows
 * (favorites, suggestions, an empty state), how dark logos are lifted on the glass, and which wallpaper credits Home
 * may drop. The real views are covered by the Robolectric suites (see docs/LIQUID_GLASS.md).
 */
public final class HomeGlassTests {
    private static int checks;

    public static void main(String[] args) {
        material();
        shelf();
        ink();
        credits();
        System.out.println("Home glass checks: " + checks + " passed");
    }

    // ---------------------------------------------------------------------------------------------- material scale

    private static void material() {
        check(GlassMaterial.REF_WIDTH == 300f && GlassMaterial.REF_HEIGHT == 152f, "the approved reference card is 300 x 152");
        check(GlassMaterial.REF_RADIUS == 32f && GlassMaterial.REF_BEVEL == 23f, "radius 32, bevel 23");
        check(GlassMaterial.REFRACTION_UNITS == 11f && GlassMaterial.FROST_UNITS == 2f, "refraction 11, frost 2");
        check(GlassMaterial.SMOKE == 0.09f, "smoke 9 %");
        near(1f, GlassMaterial.unit(152f), 1e-6f, "the reference card is unit 1");
        near(0.5f, GlassMaterial.unit(76f), 1e-6f, "half the height, half the unit");
        near(1f, GlassMaterial.unit(0f), 1e-6f, "an unmeasured surface falls back to unit 1");
        near(23f, GlassMaterial.bevel(152f), 1e-4f, "the reference bevel is 23");
        // A 125 px tile (a Home tile at 1080p): k = 0.822, bevel 18.9 (22 % of 125 is 27.5, so the scaled bevel rules).
        near(23f * 125f / 152f, GlassMaterial.bevel(125f), 1e-3f, "a Home tile's bevel scales with its height");
        // The bevel is 23/152 = 15 % of the height at any size, so the 22 % ceiling is a guard that never has to bite.
        for (float h : new float[]{20f, 72f, 125f, 152f, 400f, 2160f}) {
            check(GlassMaterial.bevel(h) <= GlassMaterial.MAX_BEVEL_SHARE * Math.max(1f, h) + 1e-3f, "the bevel stays under 22 % of the height at " + h);
        }
        check(GlassMaterial.bevel(1f) >= 1f, "the bevel is never below one pixel");
        near(32f * 125f / 152f, GlassMaterial.radius(125f, false), 1e-3f, "a card's corner radius scales");
        near(36f, GlassMaterial.radius(72f, true), 1e-4f, "a capsule's radius is half its height");
        for (float h : new float[]{20f, 60f, 125f, 152f, 400f}) check(GlassMaterial.radius(h, false) <= h / 2f, "a card is never rounder than a capsule at " + h);
        float tileUnit = GlassMaterial.unit(125f), actionUnit = GlassMaterial.unit(72f);
        float tileSigma = GlassMaterial.frostSigma(tileUnit, 0.5f), actionSigma = GlassMaterial.frostSigma(actionUnit, 0.5f);
        near(2f * tileUnit * 0.5f, tileSigma, 1e-4f, "frost is 2 units, in cache pixels");
        check(tileSigma > actionSigma, "a taller surface has a wider frost");
        check(GlassMaterial.frostSigma(1000f, 1f) == GlassMaterial.MAX_FROST_SIGMA, "frost never exceeds what the renderer accepts");
        check(GlassMaterial.frostKey(tileSigma) != GlassMaterial.frostKey(actionSigma), "tiles and actions get different frost copies");
        check(GlassMaterial.frostKey(GlassMaterial.frostSigma(GlassMaterial.unit(125f), 0.5f)) == GlassMaterial.frostKey(tileSigma),
                "surfaces of one size share one frost copy");
        near(tileSigma, GlassMaterial.sigmaOf(GlassMaterial.frostKey(tileSigma)), 1f / 16f + 1e-6f, "a key stands for its sigma to 1/16 px");
        int[] tile = GlassMaterial.tileSizeDp(960f, 48f, 7, 14f);
        check(tile[0] == 111 && tile[1] == 69, "seven tiles fit a 1080p row, a little taller than 16:9: " + Arrays.toString(tile));
        check(7 * tile[0] + 6 * 14 <= 960 - 96, "and the row fits inside the safe margins");
        check(GlassMaterial.tileSizeDp(960f, 48f, 0, 14f)[0] > 0, "zero columns is treated as one");
    }

    // ---------------------------------------------------------------------------------------------- the app row

    private static final List<String> NONE = Collections.<String>emptyList();

    private static DockList.Predicate only(final String... usable) {
        final Set<String> set = new HashSet<>(Arrays.asList(usable));
        return set::contains;
    }

    private static void shelf() {
        List<String> installed = Arrays.asList("a.app", "b.app", "c.app", "d.app", "e.app", "f.app", "g.app", "h.app", "i.app");
        Set<String> leanback = new HashSet<>(Arrays.asList("e.app", "f.app", "g.app", "h.app", "i.app", "c.app"));
        DockList.Predicate all = only(installed.toArray(new String[0]));

        HomeShelf saved = HomeShelf.compute(Arrays.asList("c.app", "gone.app", "a.app"), Arrays.asList("b.app"), installed, leanback, all, 7);
        check(saved.mode == HomeShelf.Mode.FAVORITES, "saved favorites win");
        check(saved.packages.equals(Arrays.asList("c.app", "a.app")), "in the stored order, minus what is not installed");

        HomeShelf one = HomeShelf.compute(Arrays.asList("a.app"), NONE, installed, leanback, all, 7);
        check(one.mode == HomeShelf.Mode.FAVORITES && one.packages.size() == 1, "a single favorite is not padded with suggestions");

        HomeShelf viaDefault = HomeShelf.compute(DockList.parse(null), NONE, installed, leanback, all, 7);
        check(viaDefault.mode == HomeShelf.Mode.SUGGESTIONS, "the Calm Home default with none of its apps installed offers suggestions");

        HomeShelf none = HomeShelf.compute(NONE, NONE, installed, leanback, all, 7);
        check(none.mode == HomeShelf.Mode.SUGGESTIONS && none.packages.size() == 7, "no favorites: seven suggestions");
        check(none.packages.subList(0, 5).equals(Arrays.asList("c.app", "e.app", "f.app", "g.app", "h.app")), "TV apps come first, in order");
        check(none.packages.get(5).equals("i.app") && none.packages.get(6).equals("a.app"), "then the rest, in order");
        check(new HashSet<>(none.packages).size() == 7, "no duplicates");

        HomeShelf recent = HomeShelf.compute(NONE, Arrays.asList("d.app", "gone.app", "b.app"), installed, leanback, all, 7);
        check(recent.packages.get(0).equals("d.app") && recent.packages.get(1).equals("b.app"), "recently opened apps lead the suggestions");
        check(!recent.packages.contains("gone.app"), "an uninstalled recent is never suggested");

        HomeShelf limited = HomeShelf.compute(NONE, NONE, installed, leanback, all, 3);
        check(limited.packages.size() == 3, "the number of suggestions is capped");

        HomeShelf unusable = HomeShelf.compute(NONE, NONE, installed, leanback, only("a.app", "h.app"), 7);
        check(unusable.packages.equals(Arrays.asList("h.app", "a.app")), "only usable apps are ever suggested");

        HomeShelf empty = HomeShelf.compute(NONE, NONE, NONE, new HashSet<String>(), only(), 7);
        check(empty.mode == HomeShelf.Mode.EMPTY && empty.isEmpty(), "nothing launchable: an empty row, never a made-up tile");
        HomeShelf allGone = HomeShelf.compute(Arrays.asList("x.app"), Arrays.asList("y.app"), installed, leanback, only(), 7);
        check(allGone.mode == HomeShelf.Mode.EMPTY, "apps that exist but cannot launch count as nothing");

        List<String> stored = new ArrayList<>(Arrays.asList("c.app", "gone.app"));
        HomeShelf.compute(stored, NONE, installed, leanback, all, 7);
        check(stored.equals(Arrays.asList("c.app", "gone.app")), "computing the row never touches the stored dock");
        check(DockList.serialize(stored).equals("c.app,gone.app"), "and the stored format is Calm Home's, unchanged");
        try {
            saved.packages.add("zzz");
            check(false, "the row is read-only");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    // ---------------------------------------------------------------------------------------------- dark ink

    private static void ink() {
        check(LiquidInk.needsLift(0.02f) && LiquidInk.needsLift(0.1f), "a dark wordmark needs lifting");
        check(!LiquidInk.needsLift(0.3f) && !LiquidInk.needsLift(0.8f), "a light or colourful logo does not");
        int black = LiquidInk.lift(0xFF000000);
        check((black >> 16 & 255) > 190 && (black >> 8 & 255) > 190 && (black & 255) > 190, "black ink becomes a light grey on graphite");
        check((black >>> 24) == 255, "and stays opaque");
        int navy = LiquidInk.lift(0xFF0A1E50);
        check((navy & 255) > (navy >> 16 & 255), "a navy wordmark keeps a blue cast: " + Integer.toHexString(navy));
        check(luma(navy) > luma(0xFF0A1E50) + 0.25f, "and is clearly lighter");
        eq(0xFFE50914, LiquidInk.lift(0xFFE50914), "a bright red logo is left alone");
        eq(0xFFFFFFFF, LiquidInk.lift(0xFFFFFFFF), "white is left alone");
        eq(0x00000000, LiquidInk.lift(0x00000000), "a transparent pixel is left alone");
        int half = LiquidInk.lift(0x80000000);
        check((half >>> 24) == 0x80, "a half-transparent pixel keeps its opacity");
        check(luma(LiquidInk.lift(0xFF303030)) < luma(LiquidInk.lift(0xFF000000)), "darker ink lifts further than mid-dark ink");
        check(luma(LiquidInk.lift(0xFF303030)) > luma(0xFF303030), "but mid-dark ink is lifted too");
        int[] logo = {0xFF000000, 0x00000000, 0xFF101010};
        int[] untouched = LiquidInk.liftedIfDark(logo, 0.5f);
        check(untouched == logo, "a logo that is not dark ink is returned as it is");
        int[] lifted = LiquidInk.liftedIfDark(logo, 0.03f);
        check(lifted != logo && logo[0] == 0xFF000000, "a dark logo is lifted in a copy, the key's pixels are not changed");
        check(lifted.length == 3 && lifted[1] == 0 && luma(lifted[0]) > 0.5f, "the copy keeps its shape and lifts the ink");
    }

    private static float luma(int argb) {
        return LiquidKey.luminance((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f);
    }

    // ---------------------------------------------------------------------------------------------- wallpaper credits

    private static void credits() {
        List<String> lines = Arrays.asList(
                "wp04.jpg\tPiedras Rojas, Chile\tPhoto: Diego Delso, CC BY-SA 4.0\tNature,Desert",
                "zp04.jpg\tTwilight Coast\tOriginal artwork: ZeroPulse TV\tZeroPulse,Twilight");
        WallpaperCatalog cat = WallpaperCatalog.parse(lines);
        WallpaperCatalog.Entry photo = cat.byFile("wp04.jpg"), own = cat.byFile("zp04.jpg");
        check(photo.requiresAttribution(), "a CC BY-SA photograph must be credited");
        check(photo.homeCredit().isEmpty(), "Liquid Glass Home carries no credit line: the Wallpaper information screen gives it (see VisualPolishTests)");
        check(!own.requiresAttribution(), "ZeroPulse's own artwork needs no credit");
        check(own.homeCredit().isEmpty(), "so Home shows none for it");
        check(own.caption().contains("Original artwork"), "while its caption is still there for Wallpapers and About");
        check(photo.caption().contains("Diego Delso"), "the photographer is named in the caption");
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static void near(float expected, float actual, float tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        checks++;
    }

    private static void eq(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected 0x" + Integer.toHexString(expected) + " but was 0x" + Integer.toHexString(actual));
        checks++;
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
        checks++;
    }
}
