package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.data.AppArt;
import tv.calmhome.launcher.data.Apps;

/**
 * The artwork pipeline: what a tile shows for an installed app (its TV banner, else its icon, else, only when it ships neither,
 * a plain monogram), at what size and with what shape, how it is cached and invalidated, and that it also works against real
 * packaged resources resolved by the real PackageManager path, not only against the registered fixtures. Native graphics.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class AppArtTest {

    private static final int TILE_W = 240, TILE_H = 150;          // a Home tile at 1080p

    private AppArt art;
    private Apps apps;

    @Before public void setUp() {
        apps = new Apps(HomeTestSupport.app());
        art = new AppArt(HomeTestSupport.app(), apps);
        AppArt.Stats.reset();
    }

    private static Drawable bitmap(int w, int h, int fill) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(fill);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** A bare logo: nothing but a white shape on a fully transparent banner. */
    private static Drawable transparentLogo() {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        c.drawRoundRect(60, 50, 260, 130, 20, 20, p);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    private static int alpha(Bitmap b, int x, int y) {
        return b.getPixel(x, y) >>> 24;
    }

    // ------------------------------------------------------------------ banner first, then icon, then monogram

    @Test public void aTvBannerIsShownAsTheAppShippedItFillingTheGlassFrameWithRoundedCorners() {
        MockTvApps.install("mock.banner", "Mock Banner", true, MockTvApps.neutralBanner(200, 0));
        AppArt.Art a = art.get("mock.banner", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.BANNER, a.kind);
        assertEquals("activity-banner", a.origin);
        assertTrue("the app's own artwork, not ours", a.isGenuine());
        assertEquals(320, a.sourceWidth);
        assertEquals(180, a.sourceHeight);
        assertEquals("a 16:9 banner fills the frame exactly", box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        assertEquals("the glass shows in the corners (concentric rounding)", 0, alpha(a.bitmap, 0, 0));
        assertEquals("and the banner is opaque inside", 255, alpha(a.bitmap, a.bitmap.getWidth() / 2, a.bitmap.getHeight() / 2));
        assertEquals(255, alpha(a.bitmap, a.bitmap.getWidth() / 2, 1));
    }

    @Test public void theBannerWinsOverTheIcon() {
        MockTvApps.install("mock.both", "Mock Both", true, MockTvApps.neutralBanner(30, 1));
        assertNotNull("the app also has an icon", apps.iconSource("mock.both"));
        assertEquals(TileArt.Kind.BANNER, art.get("mock.both", TILE_W, TILE_H).kind);
    }

    @Test public void anAppWithoutABannerFillsItsTileWithItsOwnIconAndItsBackdrop() {
        MockTvApps.install("mock.icon", "Mock Icon", false);
        AppArt.Art a = art.get("mock.icon", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals("the icon has a flat backdrop of its own, so it fills the tile", TileArt.Kind.FILLED_ICON, a.kind);
        assertEquals("activity-icon", a.origin);
        assertEquals("rendered at exactly the size it is drawn, so it is never scaled again", box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        assertEquals("the glass shows in the corners (concentric rounding)", 0, alpha(a.bitmap, 0, 0));
        assertEquals(255, alpha(a.bitmap, a.bitmap.getWidth() / 2, a.bitmap.getHeight() / 2));
        assertEquals("opaque right out to the sides", 255, alpha(a.bitmap, 1, a.bitmap.getHeight() / 2));
        assertEquals(255, alpha(a.bitmap, a.bitmap.getWidth() - 2, a.bitmap.getHeight() / 2));
        assertEquals("not stretched: the picture's own 96 px size is recorded", 96, a.sourceWidth);
    }

    // ------------------------------------------------------------------ icons that fill the tile

    private static final int BACKDROP = Color.rgb(30, 136, 229), LOGO = Color.rgb(250, 250, 250);

    /** A 96 px icon: {@code backdrop} everywhere with a white square logo in the middle (40 px). */
    private static Bitmap iconBitmap(boolean transparentBackdrop, boolean roundedCorners) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!transparentBackdrop) {
            p.setColor(BACKDROP);
            if (roundedCorners) c.drawRoundRect(0, 0, 96, 96, 20, 20, p); else c.drawRect(0, 0, 96, 96, p);
        }
        p.setColor(LOGO);
        c.drawRect(28, 28, 68, 68, p);
        return b;
    }

    private static Drawable drawable(Bitmap b) {
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** The bounding box of the pixels that are close to {@code colour}: {left, top, right, bottom}, or null. */
    private static int[] find(Bitmap b, int colour) {
        int l = b.getWidth(), t = b.getHeight(), r = -1, bt = -1;
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
            int p = b.getPixel(x, y);
            if ((p >>> 24) == 255 && Math.abs(Color.red(p) - Color.red(colour)) < 6 && Math.abs(Color.green(p) - Color.green(colour)) < 6
                    && Math.abs(Color.blue(p) - Color.blue(colour)) < 6) { l = Math.min(l, x); t = Math.min(t, y); r = Math.max(r, x); bt = Math.max(bt, y); }
        }
        return r < 0 ? null : new int[]{l, t, r, bt};
    }

    @Test public void anIconWithAFlatBackdropFillsTheTileAndItsLogoIsScaledUniformlyNeverStretched() {
        AppArt.Art a = AppArt.fromIcon(drawable(iconBitmap(false, false)), "test", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.FILLED_ICON, a.kind);
        assertEquals(box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        int mid = a.bitmap.getHeight() / 2;
        assertEquals("the backdrop reaches the left and right edges in the icon's own colour", BACKDROP & 0xFFFFFF, a.bitmap.getPixel(a.bitmap.getWidth() / 2 - 70, mid) & 0xFFFFFF);
        assertEquals(BACKDROP & 0xFFFFFF, a.bitmap.getPixel(2, mid) & 0xFFFFFF);
        assertEquals(BACKDROP & 0xFFFFFF, a.bitmap.getPixel(a.bitmap.getWidth() - 3, mid) & 0xFFFFFF);
        int[] logo = find(a.bitmap, LOGO);
        assertNotNull(logo);
        int lw = logo[2] - logo[0] + 1, lh = logo[3] - logo[1] + 1;
        assertEquals("a square logo stays square: scaled by one factor", lw, lh, 2);
        assertEquals("by the icon's height over its own 96 px: 40 px becomes " + Math.round(40f * box[1] / 96), Math.round(40f * box[1] / 96), lh, 3);
        assertEquals("centred", a.bitmap.getWidth() / 2f, (logo[0] + logo[2]) / 2f, 1.5f);
    }

    @Test public void aRoundedSquareIconIsSquaredOffSoItsCornersDoNotSmear() {
        AppArt.Art a = AppArt.fromIcon(drawable(iconBitmap(false, true)), "test", TILE_W, TILE_H);
        assertEquals(TileArt.Kind.FILLED_ICON, a.kind);
        int mid = a.bitmap.getHeight() / 2;
        int top = Math.round(TileArt.bannerRadius(TILE_H)) + 2;                 // below the tile's own rounded corner
        for (int y = top; y < a.bitmap.getHeight() - top; y++) {
            assertEquals("row " + y + " is the backdrop's colour at the left edge", BACKDROP & 0xFFFFFF, a.bitmap.getPixel(2, y) & 0xFFFFFF);
            assertEquals(255, alpha(a.bitmap, 2, y));
        }
        assertEquals("the tile's corner is rounded to the glass", 0, alpha(a.bitmap, 0, 0));
        assertEquals(mid, a.bitmap.getHeight() / 2);
    }

    @Test public void aBareGlyphWithNoBackdropIsShownLargeOnTheGlassNotStretchedAcrossIt() {
        AppArt.Art a = AppArt.fromIcon(drawable(iconBitmap(true, false)), "test", TILE_W, TILE_H);
        int side = TileArt.iconSide(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.ICON, a.kind);
        assertEquals(side, a.bitmap.getWidth());
        assertEquals("square, as shipped", side, a.bitmap.getHeight());
        assertTrue("large: " + side + " of " + TILE_H, side >= Math.round(TILE_H * 0.66f));
        assertEquals("no backdrop is invented", 0, alpha(a.bitmap, 1, 1));
    }

    @Test public void aLogoRunningOffTheIconsEdgeIsNotSmearedAcrossTheTile() {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setColor(BACKDROP);
        c.drawRect(0, 0, 96, 96, p);
        p.setColor(LOGO);
        for (int y = 0; y < 96; y += 16) c.drawRect(0, y, 96, y + 8, p);        // stripes cut by the left and right edges
        AppArt.Art a = AppArt.fromIcon(drawable(b), "test", TILE_W, TILE_H);
        assertEquals("falls back to a large icon on the glass", TileArt.Kind.ICON, a.kind);
        assertEquals(a.bitmap.getWidth(), a.bitmap.getHeight());
    }

    @Test public void aSmoothlyShadedBackdropIsCarriedOnRowByRow() {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) b.setPixel(x, y, Color.rgb(20 + y, 60 + y / 2, 200));
        AppArt.Art a = AppArt.fromIcon(drawable(b), "test", TILE_W, TILE_H);
        assertEquals(TileArt.Kind.FILLED_ICON, a.kind);
        int h = a.bitmap.getHeight();
        int inside = a.bitmap.getPixel(a.bitmap.getWidth() / 2, h / 2), outside = a.bitmap.getPixel(2, h / 2);
        assertEquals("the same row, the same colour, inside the icon and beyond it", Color.red(inside), Color.red(outside), 3);
        assertEquals(Color.blue(inside), Color.blue(outside), 3);
        int topOut = a.bitmap.getPixel(2, h / 4), topIn = a.bitmap.getPixel(a.bitmap.getWidth() / 2, h / 4);
        assertEquals("and the gradient is the icon's own at every row", Color.red(topIn), Color.red(topOut), 3);
    }

    @Test public void anAdaptiveIconsBackgroundCoversTheTileAndItsForegroundSitsInTheMiddleAtFullHeight() {
        Bitmap fg = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(fg);
        Paint p = new Paint();
        p.setColor(LOGO);
        c.drawRect(34, 34, 74, 74, p);                                           // a 40 unit mark inside the 72 unit safe zone
        Drawable back = new android.graphics.drawable.ColorDrawable(Color.rgb(200, 30, 60));
        android.graphics.drawable.AdaptiveIconDrawable icon = new android.graphics.drawable.AdaptiveIconDrawable(back, drawable(fg));
        AppArt.Art a = AppArt.fromIcon(icon, "test", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.FILLED_ICON, a.kind);
        assertEquals(box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        assertEquals("the background layer reaches the edges", Color.rgb(200, 30, 60) & 0xFFFFFF, a.bitmap.getPixel(2, a.bitmap.getHeight() / 2) & 0xFFFFFF);
        int[] mark = find(a.bitmap, LOGO);
        assertNotNull(mark);
        int mh = mark[3] - mark[1] + 1, mw = mark[2] - mark[0] + 1;
        assertEquals("not stretched", mw, mh, 2);
        assertEquals("the 40 unit mark is about " + Math.round(40f / 108 * TileArt.ADAPTIVE_FOREGROUND * 100) + " % of the tile height", 40f / 108 * TileArt.ADAPTIVE_FOREGROUND * box[1], mh, 3);
        assertEquals(0, alpha(a.bitmap, 0, 0));
    }

    @Test public void anAppThatDeclaresNoArtworkGetsAPlainMonogramAndIsMarkedAsNotGenuine() {
        MockTvApps.install("mock.nothing", "Mock Nothing", true, null, false);
        assertNull("no banner", apps.bannerSource("mock.nothing"));
        assertNull("and no icon: Android's generic placeholder is not the app's artwork and is never used as if it were", apps.iconSource("mock.nothing"));
        AppArt.Art a = art.get("mock.nothing", TILE_W, TILE_H);
        assertEquals(TileArt.Kind.MONOGRAM, a.kind);
        assertEquals("monogram", a.origin);
        assertFalse(a.isGenuine());
        assertEquals(TileArt.iconSide(TILE_W, TILE_H), a.bitmap.getWidth());
        assertEquals("MN", TileArt.monogram("Mock Nothing"));
    }

    @Test public void aBareLogoWithATransparentBackdropSitsOnTheGlassHeldInsideTheFrame() {
        MockTvApps.install("mock.logo", "Mock Logo", true, transparentLogo());
        AppArt.Art a = art.get("mock.logo", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.LOGO, a.kind);
        assertTrue("held inside the glass, not edge to edge", a.bitmap.getWidth() <= Math.round(box[0] * TileArt.LOGO_SHARE) + 1);
        assertEquals("its shape is kept", 320f / 180f, a.bitmap.getWidth() / (float) a.bitmap.getHeight(), 0.03f);
        assertEquals("no backdrop is invented behind it", 0, alpha(a.bitmap, 1, 1));
    }

    @Test public void aBannerOfAnotherShapeKeepsItsProportionsAndItsFlatBackdropIsCarriedToTheEdges() {
        MockTvApps.install("mock.square", "Mock Square", true, bitmap(400, 400, Color.rgb(40, 90, 160)));
        AppArt.Art a = art.get("mock.square", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals("a flat backdrop is carried on, so the tile is still filled", TileArt.Kind.FILLED_ICON, a.kind);
        assertEquals(box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        assertEquals(Color.rgb(40, 90, 160) & 0xFFFFFF, a.bitmap.getPixel(3, box[1] / 2) & 0xFFFFFF);
    }

    @Test public void aBannerOfAnotherShapeWithNoBackdropToCarryIsLetterBoxedNeverStretched() {
        Bitmap b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setColor(Color.rgb(40, 90, 160));
        c.drawRect(0, 0, 400, 400, p);
        p.setColor(Color.WHITE);
        for (int y = 0; y < 400; y += 60) c.drawRect(0, y, 400, y + 30, p);       // stripes cut by the edge: nothing smooth to carry
        MockTvApps.install("mock.stripes", "Mock Stripes", true, new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b));
        AppArt.Art a = art.get("mock.stripes", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(TileArt.Kind.BANNER, a.kind);
        assertEquals("scaled uniformly to the frame's height", box[1], a.bitmap.getHeight());
        assertEquals("so it is as wide as it is tall", a.bitmap.getHeight(), a.bitmap.getWidth());
        assertTrue(a.bitmap.getWidth() < box[0]);
    }

    @Test public void aHugeBannerIsReducedInStepsToTheTileSize() {
        MockTvApps.install("mock.huge", "Mock Huge", true, bitmap(1920, 1080, Color.rgb(200, 120, 20)));
        AppArt.Art a = art.get("mock.huge", TILE_W, TILE_H);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(box[0], a.bitmap.getWidth());
        assertEquals("a flat colour stays that colour through the reduction", Color.rgb(200, 120, 20) & 0xFFFFFF, a.bitmap.getPixel(box[0] / 2, box[1] / 2) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ the cache

    @Test public void artworkIsRenderedOnceCachedPerSizeAndForgottenOnInvalidate() {
        MockTvApps.install("mock.cache", "Mock Cache", true, MockTvApps.neutralBanner(100, 2));
        AppArt.Art first = art.get("mock.cache", TILE_W, TILE_H);
        assertEquals(1, AppArt.Stats.renders());
        assertEquals(0, AppArt.Stats.hits());
        assertSame("the second request is the cached bitmap", first, art.get("mock.cache", TILE_W, TILE_H));
        assertEquals(1, AppArt.Stats.hits());
        assertEquals("and rendered nothing", 1, AppArt.Stats.renders());
        AppArt.Art other = art.get("mock.cache", 300, 188);
        assertNotSame("another tile size is another render", first, other);
        assertEquals(2, AppArt.Stats.renders());
        assertEquals(2, art.cachedCount());
        assertTrue("the cache holds only what it rendered", art.cachedBytes() >= first.bitmap.getByteCount() && art.cachedBytes() < 8 * 1024 * 1024);
        art.invalidate("mock.cache");
        assertEquals("an updated or removed package is forgotten at every size", 0, art.cachedCount());
        assertNotSame(first, art.get("mock.cache", TILE_W, TILE_H));
        assertEquals(3, AppArt.Stats.renders());
        assertTrue("a render of a tile costs milliseconds, not frames", AppArt.Stats.longestMillis() < 500);
    }

    @Test public void aRemovedAppIsForgottenAndOthersAreKept() {
        MockTvApps.install("mock.keep", "Mock Keep", true, MockTvApps.neutralBanner(10, 0));
        MockTvApps.install("mock.gone", "Mock Gone", true, MockTvApps.neutralBanner(250, 3));
        AppArt.Art keep = art.get("mock.keep", TILE_W, TILE_H);
        art.get("mock.gone", TILE_W, TILE_H);
        MockTvApps.uninstall("mock.gone");
        art.invalidate("mock.gone");
        assertSame("the others stay cached", keep, art.get("mock.keep", TILE_W, TILE_H));
        assertEquals(1, art.cachedCount());
    }

    // ------------------------------------------------------------------ real packaged resources, not registered fixtures

    @Test public void theLaunchersOwnRealBannerAndIconAreResolvedByThePackageManagerAndRenderedByThePipeline() {
        String own = HomeTestSupport.app().getPackageName();
        Apps.Source banner = apps.bannerSource(own);
        assertNotNull("the launcher's manifest declares android:banner, and the PackageManager resolves that resource", banner);
        assertTrue("declared on the app, found by the PackageManager: " + banner.origin, banner.origin.endsWith("-banner"));
        assertTrue("a vector drawn from the packaged res/drawable/zp_banner.xml", banner.drawable.getIntrinsicWidth() > 0);
        AppArt.Art a = art.get(own, TILE_W, TILE_H);
        assertTrue("genuine artwork, nothing of ours drawn on top", a.isGenuine());
        assertEquals(TileArt.Kind.BANNER, a.kind);
        int[] box = TileArt.artBox(TILE_W, TILE_H);
        assertEquals(box[0], a.bitmap.getWidth());
        assertEquals(box[1], a.bitmap.getHeight());
        assertTrue("the real banner has its navy backdrop, so the middle is opaque", alpha(a.bitmap, box[0] / 2, box[1] / 2) == 255);

        Apps.Source icon = apps.iconSource(own);
        assertNotNull("and android:icon", icon);
        AppArt.Art i = AppArt.fromIcon(icon.drawable, icon.origin, TILE_W, TILE_H);
        assertEquals("the real icon is an adaptive icon: its background layer fills the tile", TileArt.Kind.FILLED_ICON, i.kind);
        assertEquals(box[0], i.bitmap.getWidth());
        assertEquals(box[1], i.bitmap.getHeight());
        assertEquals("opaque right out to the sides", 255, alpha(i.bitmap, 2, box[1] / 2));
    }

    @Test public void aRealManifestEntryIsLaunchedThroughTheLeanbackIntentThePackageManagerResolvesNotThroughAFixture() {
        String own = HomeTestSupport.app().getPackageName();
        assertTrue("a real installed package with a real launch entry (the launcher's own manifest)", apps.usable(own));
        android.content.Intent intent = apps.launchIntent(own);
        assertNotNull(intent);
        assertEquals(own, intent.getComponent().getPackageName());
        assertEquals("the TV (leanback) entry is preferred when the package has one", "tv.calmhome.launcher.MainActivity", intent.getComponent().getClassName());
        assertTrue(apps.launch(own));
        android.content.Intent started = org.robolectric.Shadows.shadowOf(HomeTestSupport.app()).getNextStartedActivity();
        assertNotNull("the intent was really started", started);
        assertEquals(intent.getComponent(), started.getComponent());
        assertTrue("as a new task", (started.getFlags() & android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test public void thePipelineNeverDrawsAnythingForAnAppThatIsNotInstalled() {
        AppArt.Art a = art.get("not.installed.anywhere", TILE_W, TILE_H);
        assertEquals("with nothing to show it falls to the monogram, never a brand", TileArt.Kind.MONOGRAM, a.kind);
        assertFalse(a.isGenuine());
    }
}
