package tv.calmhome.launcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassClockView;
import tv.calmhome.launcher.core.ContrastProbe;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.core.WallpaperFraming;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.screens.WallpaperInfoPanel;
import tv.calmhome.launcher.ui.AdaptiveScrimView;
import tv.calmhome.launcher.ui.FramedImageView;

/**
 * The wallpaper experience: the five supplied wallpapers are the Liquid Glass collection, the picker previews them, the
 * choice and the framing persist, the contrast layer follows the picture, the credit lives on the information screen rather
 * than on Home, and the clock shows the real time. Native graphics, bundled assets decoded for real.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class WallpaperPolishTest {

    private static final String[] SUPPLIED = {"lg01.jpg", "lg02.jpg", "lg03.jpg", "lg04.jpg", "lg05.jpg"};

    @Before public void installSomeApps() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.neutralBanner(app[0].hashCode() % 360, 0));
    }

    private static MainActivity homeAt(String open) {
        new Prefs(HomeTestSupport.app()).setLiquidGlass(true);
        new Prefs(HomeTestSupport.app()).setLiquidStarting(false);
        Intent i = new Intent(HomeTestSupport.app(), MainActivity.class);
        if (open != null) i.putExtra(MainActivity.EXTRA_OPEN, open);
        MainActivity h = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        ShadowLooper.idleMainLooper();
        return h;
    }

    private static boolean waitFor(MainActivity h, String file) throws Exception {
        return HomeTestSupport.pumpUntil(h, () -> file.equals(h.currentWallpaperFile()), 200);
    }

    private static ImageView pictureIn(android.view.View v) {
        if (v instanceof ImageView) return (ImageView) v;
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
                ImageView found = pictureIn(((android.view.ViewGroup) v).getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<FrameLayout> thumbnailCards(MainActivity h) {
        List<FrameLayout> out = new ArrayList<>();
        for (FrameLayout f : HomeTestSupport.all(h, FrameLayout.class)) {
            if (f.getTag() instanceof String && ((String) f.getTag()).endsWith(".jpg") && f.isClickable()) out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ the collection and the picker

    @Test public void liquidGlassRotatesThroughTheFiveSuppliedWallpapersByDefault() throws Exception {
        MainActivity h = homeAt(null);
        assertEquals(WallpaperCatalog.LIQUID, new Prefs(HomeTestSupport.app()).wallpaperCollection());
        List<WallpaperCatalog.Entry> pool = h.wallpaperStage().rotation();
        assertEquals(5, pool.size());
        for (WallpaperCatalog.Entry e : pool) assertTrue(e.file + " is one of the supplied files", java.util.Arrays.asList(SUPPLIED).contains(e.file));
        assertTrue("the first wallpaper loads from the supplied set", HomeTestSupport.pumpUntil(h, () -> h.currentWallpaperFile() != null, 200));
        assertTrue(java.util.Arrays.asList(SUPPLIED).contains(h.currentWallpaperFile()));
    }

    @Test public void theWallpapersScreenPreviewsAllFiveAndSelectingOneIsRememberedAcrossRestarts() throws Exception {
        MainActivity h = homeAt("wallpapers");
        HomeTestSupport.pump(h, 10);
        assertTrue("the supplied collection is the first tab", HomeTestSupport.hasText(h, "Liquid Glass  5"));
        List<FrameLayout> cards = thumbnailCards(h);
        assertEquals("five previews", 5, cards.size());
        int withPicture = 0;
        for (FrameLayout card : cards) {
            ImageView iv = pictureIn(card);                          // the picture sits inside the glass frame, not as its first child
            assertNotNull(card.getTag() + " has a picture view", iv);
            if (iv.getDrawable() != null) withPicture++;
        }
        assertEquals("each preview is decoded", 5, withPicture);
        FrameLayout third = null;
        for (FrameLayout card : cards) if ("lg03.jpg".equals(card.getTag())) third = card;
        assertNotNull(third);
        third.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("lg03.jpg", new Prefs(HomeTestSupport.app()).wallpaperPicked());
        assertTrue("it is on screen", waitFor(h, "lg03.jpg"));

        MainActivity again = homeAt(null);
        assertTrue("and after a restart it is still the wallpaper", waitFor(again, "lg03.jpg"));
        assertEquals("the stored choice is the same file", "lg03.jpg", new Prefs(HomeTestSupport.app()).wallpaperPicked());
    }

    @Test public void largeAndSmallPicturesAreBothDecodedAtTheirOwnSizeAndNeverBiggerThanTheScreenNeeds() throws Exception {
        MainActivity h = homeAt(null);
        WallpaperStage stage = h.wallpaperStage();
        assertArrayEquals(new int[]{1024, 572}, stage.sourceSize("lg02.jpg"));
        assertArrayEquals(new int[]{2752, 1536}, stage.sourceSize("lg05.jpg"));
        assertNull("a file that is not there has no size", stage.sourceSize("missing.jpg"));
        assertEquals(1, WallpaperFraming.sampleSize(2752, 1536, 1920, 1080));
        assertEquals(1, WallpaperFraming.sampleSize(1024, 572, 1920, 1080));
        new Prefs(HomeTestSupport.app()).setWallpaperPicked("lg05.jpg");
        MainActivity big = homeAt(null);
        assertTrue(waitFor(big, "lg05.jpg"));
        FramedImageView shown = null;
        for (FramedImageView v : HomeTestSupport.all(big, FramedImageView.class)) if (v.getDrawable() != null && v.getAlpha() > 0.5f) shown = v;
        assertNotNull("the 2752 x 1536 wallpaper is on screen", shown);
        assertEquals(2752, shown.getDrawable().getIntrinsicWidth());
    }

    // ------------------------------------------------------------------ framing

    @Test public void theFramingIsAppliedToTheScreenAndKeptPerWallpaper() throws Exception {
        new Prefs(HomeTestSupport.app()).setWallpaperPicked("lg02.jpg");
        MainActivity h = homeAt(null);
        assertTrue(waitFor(h, "lg02.jpg"));
        WallpaperCatalog.Entry e = h.wallpaperStage().catalog().byFile("lg02.jpg");
        assertArrayEquals("centred at zoom 1 until the viewer says otherwise", new float[]{0.5f, 0.5f, 1f}, h.wallpaperStage().frameOf(e), 1e-6f);
        h.wallpaperStage().setFrame(e, 0.3f, 0.6f, 1.2f);
        ShadowLooper.idleMainLooper();
        float[] kept = new Prefs(HomeTestSupport.app()).wallpaperFrame("lg02.jpg", 0.5f, 0.5f);
        assertArrayEquals(new float[]{0.3f, 0.6f, 1.2f}, kept, 1e-6f);
        assertArrayEquals("another wallpaper is untouched", new float[]{0.5f, 0.5f, 1f}, new Prefs(HomeTestSupport.app()).wallpaperFrame("lg03.jpg", 0.5f, 0.5f), 1e-6f);
        FramedImageView shown = null;
        for (FramedImageView v : HomeTestSupport.all(h, FramedImageView.class)) if (v.getDrawable() != null && v.getAlpha() > 0.5f) shown = v;
        assertNotNull(shown);
        assertEquals("the view on screen is framed with it", 1.2f, shown.zoom(), 1e-6f);
        assertEquals(0.3f, shown.focusX(), 1e-6f);

        MainActivity again = homeAt(null);
        assertTrue(waitFor(again, "lg02.jpg"));
        FramedImageView after = null;
        for (FramedImageView v : HomeTestSupport.all(again, FramedImageView.class)) if (v.getDrawable() != null && v.getAlpha() > 0.5f) after = v;
        assertNotNull(after);
        assertEquals("and it is still framed that way after a restart", 1.2f, after.zoom(), 1e-6f);

        again.wallpaperStage().resetFrame(e);
        assertFalse(new Prefs(HomeTestSupport.app()).hasWallpaperFrame("lg02.jpg"));
    }

    @Test public void theWallpapersScreenOffersZoomAndBothFocusControlsAndTheInformationButton() throws Exception {
        MainActivity h = homeAt("wallpapers");
        HomeTestSupport.pump(h, 5);
        assertTrue(HomeTestSupport.hasText(h, "Zoom"));
        assertTrue(HomeTestSupport.hasText(h, "Focus across"));
        assertTrue(HomeTestSupport.hasText(h, "Focus down"));
        assertTrue(HomeTestSupport.hasText(h, "Information"));
        assertTrue(HomeTestSupport.hasText(h, "Framing for"));
    }

    // ------------------------------------------------------------------ the contrast layer

    @Test public void theScrimFollowsThePictureLightForABrightLandscapeAndTheFloorForTheDarkGlass() throws Exception {
        Prefs prefs = new Prefs(HomeTestSupport.app());
        prefs.setWallpaperCollection(WallpaperCatalog.ALL);
        prefs.setWallpaperPicked("wp01.jpg");
        MainActivity h = homeAt(null);
        AdaptiveScrimView scrim = HomeTestSupport.all(h, AdaptiveScrimView.class).get(0);
        assertTrue("a bright mountain photograph is darkened where it needs to be", HomeTestSupport.pumpUntil(h, () -> scrim.plan()[ContrastProbe.GREETING] > ContrastProbe.FLOOR[ContrastProbe.GREETING] + 0.05f, 250));
        assertTrue("but never beyond the cap", scrim.plan()[ContrastProbe.GREETING] <= ContrastProbe.CAP + 1e-4f);

        prefs.setWallpaperCollection(WallpaperCatalog.LIQUID);
        h.wallpaperStage().show(h.wallpaperStage().catalog().byFile("lg02.jpg"), false);
        assertTrue("then the dark glass wallpaper is left at the gentle floor", HomeTestSupport.pumpUntil(h, () -> {
            float[] p = scrim.plan();
            for (int b = 0; b < ContrastProbe.BANDS; b++) if (Math.abs(p[b] - ContrastProbe.FLOOR[b]) > 0.011f) return false;
            return true;
        }, 250));
    }

    // ------------------------------------------------------------------ credit and information

    @Test public void homeCarriesNoCreditAndTheInformationScreenGivesTheFullAttribution() throws Exception {
        Prefs prefs = new Prefs(HomeTestSupport.app());
        prefs.setWallpaperCollection(WallpaperCatalog.ALL);
        prefs.setWallpaperPicked("wp04.jpg");
        MainActivity h = homeAt(null);
        assertTrue(waitFor(h, "wp04.jpg"));
        HomeTestSupport.pump(h, 5);
        assertFalse("no credit line on Home", HomeTestSupport.hasText(h, "Piedras Rojas"));
        assertFalse(HomeTestSupport.hasText(h, "Diego Delso"));

        h.open(new WallpaperInfoPanel(h));
        ShadowLooper.idleMainLooper();
        assertTrue("the title", HomeTestSupport.hasText(h, "Piedras Rojas, Salar de Aguas Calientes, Chile"));
        assertTrue("the photographer and the licence", HomeTestSupport.hasText(h, "Photo: Diego Delso, CC BY-SA 4.0"));
        assertTrue("the licence's address", HomeTestSupport.hasText(h, "CC BY-SA 4.0   ·   https://creativecommons.org/licenses/by-sa/4.0/"));
        assertTrue("the source", HomeTestSupport.hasText(h, "Source: https://commons.wikimedia.org/wiki/File:Piedras_Rojas"));
        assertTrue("what was changed", HomeTestSupport.hasText(h, "It is cut to the screen's shape"));
        assertTrue("and the full list, including the supplied ones", HomeTestSupport.hasText(h, "Teal Glass Currents"));
    }

    @Test public void theInformationScreenIsReachableFromTheWallpapersScreenSettingsAndAbout() throws Exception {
        MainActivity wallpapers = homeAt("wallpapers");
        assertTrue(HomeTestSupport.hasText(wallpapers, "Information"));
        MainActivity settings = homeAt("settings:appearance");
        assertTrue(HomeTestSupport.hasText(settings, "Wallpaper information"));
        MainActivity about = homeAt("about");
        assertTrue(HomeTestSupport.hasText(about, "Wallpaper information"));
        MainActivity direct = homeAt("wallpaper-info");
        assertTrue("and it opens by route (it survives Android reclaiming the launcher)", HomeTestSupport.hasText(direct, "EVERY BUNDLED WALLPAPER"));
    }

    @Test public void aboutNoLongerClaimsAHomeCreditAndNamesTheSuppliedWallpapers() throws Exception {
        MainActivity about = homeAt("about");
        assertTrue(HomeTestSupport.hasText(about, "5 supplied glass wallpapers + 9 photos + 4 ZeroPulse originals"));
        assertFalse(HomeTestSupport.hasText(about, "A photograph keeps its credit on Home"));
    }

    // ------------------------------------------------------------------ the clock is the real time

    private static long local(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance();                       // the default time zone, as the app reads it
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    /** What the app must show at a moment, in the default time zone: "07:45 AM" style (the 12-hour setting). */
    private static String shownAt(long millis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(millis);
        int h = now.get(Calendar.HOUR_OF_DAY);
        return String.format(java.util.Locale.ROOT, "%02d:%02d %s", h % 12 == 0 ? 12 : h % 12, now.get(Calendar.MINUTE), h < 12 ? "AM" : "PM");
    }

    @Test public void theClockStartsAtTheRealTimeFollowsItsSourceAndNeverShowsAPlaceholder() throws Exception {
        long morning = local(2026, 11, 6, 7, 45), night = local(2026, 11, 6, 21, 9);
        assertFalse("the two test times read differently", shownAt(morning).equals(shownAt(night)));
        assertFalse("and neither is the old placeholder", shownAt(morning).startsWith("10:08") || shownAt(night).startsWith("10:08"));

        // A new clock reads the real time at once (within a minute of the wall clock), never a baked-in 10:08.
        GlassClockView fresh = new GlassClockView(HomeTestSupport.app());
        Calendar real = Calendar.getInstance();
        real.setTimeInMillis(System.currentTimeMillis());
        int realMinutes = real.get(Calendar.HOUR_OF_DAY) * 60 + real.get(Calendar.MINUTE), shownMinutes = fresh.getHour() * 60 + fresh.getMinute();
        int apart = Math.abs(realMinutes - shownMinutes);
        assertTrue("a new clock shows the real time: " + fresh.getHour() + ":" + fresh.getMinute(), Math.min(apart, 1440 - apart) <= 1);
        assertTrue("and the app's source is the system clock", tv.calmhome.launcher.core.SystemTime.isSystemClock());

        fresh.setTimeSource(() -> morning);
        Calendar m = Calendar.getInstance();
        m.setTimeInMillis(morning);
        assertEquals("the clock follows its time source", m.get(Calendar.HOUR_OF_DAY), fresh.getHour());
        assertEquals(m.get(Calendar.MINUTE), fresh.getMinute());

        try {
            tv.calmhome.launcher.core.SystemTime.useForTests(() -> morning);
            MainActivity morningHome = homeAt(null);
            GlassClockView clock = HomeTestSupport.all(morningHome, GlassClockView.class).get(0);
            assertEquals(shownAt(morning), String.valueOf(clock.getContentDescription()));
            assertTrue("with the outline that keeps the numerals readable", clock.hasOutlineContrast());
            assertTrue("the date line follows the same time", HomeTestSupport.hasText(morningHome, "Fri, Nov"));
            assertTrue("and so does the greeting: it is morning", HomeTestSupport.hasText(morningHome, "Good Morning"));

            tv.calmhome.launcher.core.SystemTime.useForTests(() -> night);
            MainActivity nightHome = homeAt(null);
            assertEquals("the same code shows another time when its source says so", shownAt(night),
                    String.valueOf(HomeTestSupport.all(nightHome, GlassClockView.class).get(0).getContentDescription()));
            assertTrue("and another greeting", HomeTestSupport.hasText(nightHome, "Good "));
        } finally {
            tv.calmhome.launcher.core.SystemTime.reset();
        }
    }
}
