package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** The pure rules behind the premium interactions: motion tokens, brand colours, the hero state machine, downloads, Konami, tint, notes. */
public class PremiumCoreTest {

    // ---------------------------------------------------------------------------------------------- motion tokens

    @Test public void theMotionTokensAreInsideTheBriefsRangesAndReducedMotionMakesThemZero() {
        assertTrue(MotionTokens.FOCUS_LIFT_MS >= 150 && MotionTokens.FOCUS_LIFT_MS <= 190);
        assertTrue(MotionTokens.FOCUS_RELEASE_MS >= 120 && MotionTokens.FOCUS_RELEASE_MS <= 170);
        assertTrue(MotionTokens.PILL_GLIDE_MS >= 160 && MotionTokens.PILL_GLIDE_MS <= 220);
        assertTrue(MotionTokens.CONTEXT_FADE_MS >= 150 && MotionTokens.CONTEXT_FADE_MS <= 200);
        assertTrue(MotionTokens.HERO_FADE_MS >= 220 && MotionTokens.HERO_FADE_MS <= 300);
        assertTrue(MotionTokens.MODAL_MS >= 180 && MotionTokens.MODAL_MS <= 260);
        assertTrue(MotionTokens.PARALLAX_MS >= 200 && MotionTokens.PARALLAX_MS <= 350);
        assertTrue(MotionTokens.ACKNOWLEDGE_MS >= 300 && MotionTokens.ACKNOWLEDGE_MS <= 600);
        assertTrue(MotionTokens.FOCUS_SCALE >= 1.04f && MotionTokens.FOCUS_SCALE <= 1.06f);
        assertTrue(MotionTokens.PARALLAX_X_DP >= 3 && MotionTokens.PARALLAX_X_DP <= 6 && MotionTokens.PARALLAX_Y_DP >= 1 && MotionTokens.PARALLAX_Y_DP <= 3);
        assertEquals(170, MotionTokens.of(170, false));
        assertEquals(0, MotionTokens.of(170, true));
    }

    // ---------------------------------------------------------------------------------------------- brand colour

    private static int[] fill(int argb, int n) {
        int[] a = new int[n];
        Arrays.fill(a, argb);
        return a;
    }

    @Test public void theGlowColourComesFromTheArtworksDominantColourAndIsToneddDown() {
        int[] red = fill(0xFFE50914, 300);                             // a Netflix-like red
        int c = BrandColor.extract(red, red.length);
        assertTrue("reddish: " + Integer.toHexString(c), ((c >> 16) & 255) > ((c >> 8) & 255) + 60);
        float[] hsv = new float[3];
        BrandColor.toHsv((c >> 16) & 255, (c >> 8) & 255, c & 255, hsv);
        assertTrue("restrained saturation", hsv[1] <= 0.76f);
        int[] mostlyWhite = fill(0xFFFFFFFF, 300);
        for (int i = 0; i < 5; i++) mostlyWhite[i] = 0xFFE50914;
        assertEquals("a few coloured pixels in a white logo are not a brand colour", 0, BrandColor.extract(mostlyWhite, mostlyWhite.length));
        assertEquals("grey artwork has no brand colour", 0, BrandColor.extract(fill(0xFF808080, 100), 100));
        assertEquals(0, BrandColor.extract(null, 0));
        int[] green = fill(0xFF1CE783, 200);
        int g = BrandColor.extract(green, green.length);
        assertTrue("greenish: " + Integer.toHexString(g), ((g >> 8) & 255) > ((g >> 16) & 255) + 40);
    }

    @Test public void knownBrandsHaveRestrainedColoursAndOthersAreLeftToTheirArtwork() {
        assertTrue(BrandColor.known("com.netflix.ninja", "Netflix") != 0);
        assertTrue(BrandColor.known("com.hulu.livingroomplus", "Hulu") != 0);
        assertTrue(BrandColor.known("com.disney.disneyplus", "Disney+") != 0);
        assertTrue(BrandColor.known("com.amazon.amazonvideo.livingroom", "Prime Video") != 0);
        assertTrue(BrandColor.known("com.google.android.youtube.tv", "YouTube") != 0);
        assertEquals(0, BrandColor.known("com.acme.tool", "Acme Tool"));
    }

    // ---------------------------------------------------------------------------------------------- hero

    @Test public void theCentreShowsTheGreetingByDefaultAndADownloadAtOnce() {
        HeroState h = new HeroState();
        assertEquals(HeroState.Kind.GREETING, h.kind());
        assertFalse(h.update(1_000, false, false));
        assertTrue(h.update(2_000, true, false));
        assertEquals(HeroState.Kind.ACTIVE_DOWNLOAD, h.kind());
        h.post("Downloaded: Kodi", 3_000);
        assertFalse("a running download outranks a notice", h.update(3_000, true, false));
        assertTrue("the notice shows when the download ends", h.update(3_100, false, false));
        assertEquals(HeroState.Kind.NOTIFICATION, h.kind());
        assertEquals("Downloaded: Kodi", h.notice());
        assertFalse("held for its time (counted from when it was posted)", h.update(3_000 + HeroState.NOTICE_MS - 10, false, false));
        assertTrue("then it goes", h.update(3_000 + HeroState.NOTICE_MS + 10, false, false));
        assertEquals(HeroState.Kind.GREETING, h.kind());
        assertNull(h.notice());
    }

    @Test public void featuredAppsTakeASlowTurnOnlyWhenAllowedAndNeverFlicker() {
        HeroState h = new HeroState();
        assertFalse(h.update(0, false, false));
        assertFalse("off unless the viewer turned it on", h.update(HeroState.GREETING_TURN_MS + 1_000, false, false));
        HeroState on = new HeroState();
        assertFalse(on.update(0, false, true));
        assertFalse("the greeting has its turn first", on.update(HeroState.GREETING_TURN_MS - 1, false, true));
        assertTrue(on.update(HeroState.GREETING_TURN_MS, false, true));
        assertEquals(HeroState.Kind.FEATURED, on.kind());
        long f = HeroState.GREETING_TURN_MS;
        assertFalse("a featured app keeps its turn", on.update(f + HeroState.FEATURED_TURN_MS - 1, false, true));
        assertTrue("then the greeting comes back", on.update(f + HeroState.FEATURED_TURN_MS, false, true));
        assertEquals(HeroState.Kind.GREETING, on.kind());
        long g = f + HeroState.FEATURED_TURN_MS;
        assertFalse(on.update(g + HeroState.GREETING_TURN_MS - 1, false, true));
        assertTrue("and they alternate slowly", on.update(g + HeroState.GREETING_TURN_MS, false, true));
        HeroState off = new HeroState();
        off.update(0, false, true);
        off.update(HeroState.GREETING_TURN_MS, false, true);
        assertFalse("turned off while featured: it is held for its minimum time", off.update(HeroState.GREETING_TURN_MS + 100, false, false));
        assertTrue(off.update(HeroState.GREETING_TURN_MS + HeroState.MIN_HOLD_MS, false, false));
        assertEquals(HeroState.Kind.GREETING, off.kind());
    }

    // ---------------------------------------------------------------------------------------------- downloads

    private static DownloadSummary.Item item(long id, DownloadSummary.Status s, long bytes, long total) {
        return new DownloadSummary.Item(id, "App " + id, s, bytes, total);
    }

    @Test public void theSummaryShowsTheMovingDownloadWithRealNumbersOnly() {
        assertSame(DownloadSummary.IDLE, DownloadSummary.of(Collections.<DownloadSummary.Item>emptyList(), null, 0));
        DownloadSummary a = DownloadSummary.of(Arrays.asList(item(1, DownloadSummary.Status.QUEUED, 0, -1),
                item(2, DownloadSummary.Status.DOWNLOADING, 25, 100)), null, 0);
        assertEquals(DownloadSummary.Status.DOWNLOADING, a.status);
        assertEquals(2, a.shown.id);
        assertEquals(25, a.percent);
        assertEquals(2, a.active);
        assertEquals("no speed from a single sample", -1, a.bytesPerSecond);
        assertEquals(-1, a.secondsLeft);
        DownloadSummary b = DownloadSummary.of(Arrays.asList(item(2, DownloadSummary.Status.DOWNLOADING, 75, 100)), a, 1000);
        assertEquals("50 bytes in a second", 50, b.bytesPerSecond);
        assertEquals("25 bytes left at 50/s", 0, b.secondsLeft);
        DownloadSummary unknown = DownloadSummary.of(Arrays.asList(item(3, DownloadSummary.Status.DOWNLOADING, 5_000, -1)), null, 0);
        assertEquals("no size from the source: no percentage is invented", -1, unknown.percent);
        assertEquals(-1f, unknown.aggregate, 0f);
        DownloadSummary done = DownloadSummary.of(Arrays.asList(item(4, DownloadSummary.Status.COMPLETED, 100, 100)), null, 0);
        assertSame("finished downloads do not keep the centre busy", DownloadSummary.IDLE, done);
        assertEquals("2.0 MB/s", DownloadSummary.speed(2L * 1024 * 1024));
        assertEquals("380 KB/s", DownloadSummary.speed(380L * 1024));
        assertNull(DownloadSummary.speed(-1));
        assertEquals("Less than a minute left", DownloadSummary.timeLeft(30));
        assertEquals("About 3 min left", DownloadSummary.timeLeft(180));
        assertNull(DownloadSummary.timeLeft(-1));
    }

    // ---------------------------------------------------------------------------------------------- Konami

    private static boolean type(KonamiDetector d, long start, KonamiDetector.Key... keys) {
        boolean hit = false;
        for (int i = 0; i < keys.length; i++) hit = d.press(keys[i], start + i * 200L);
        return hit;
    }

    @Test public void theSecretSequenceIsRecognisedAndOrdinaryBrowsingDoesNotBuildItUp() {
        KonamiDetector.Key U = KonamiDetector.Key.UP, D = KonamiDetector.Key.DOWN, L = KonamiDetector.Key.LEFT, R = KonamiDetector.Key.RIGHT;
        KonamiDetector d = new KonamiDetector();
        assertTrue(type(d, 0, U, U, D, D, L, R, L, R));
        assertEquals("it starts over after a hit", 0, d.progress());
        assertTrue("an extra Up at the start is forgiven", type(d, 10_000, U, U, U, D, D, L, R, L, R));
        assertFalse("a wrong key breaks it", type(d, 20_000, U, U, D, L, L, R, L, R));
        d.reset();
        assertFalse(type(d, 30_000, U, U, D, D, L, R, L));
        assertFalse("a long pause starts over", d.press(R, 30_000 + 6 * 200L + KonamiDetector.RESET_MS + 1));
        assertFalse(d.press(KonamiDetector.Key.OTHER, 50_000));
    }

    // ---------------------------------------------------------------------------------------------- tint, notes

    @Test public void theTimeOfDayTintOnlyNudgesTheApprovedWash() {
        int base = LiquidTokens.WASH;
        assertEquals("the afternoon is the approved wash itself", base, TimeTint.wash(base, 14));
        for (int hour = 0; hour < 24; hour++) {
            int w = TimeTint.wash(base, hour);
            for (int shift = 0; shift <= 24; shift += 8) {
                int d = Math.abs(((w >>> shift) & 255) - ((base >>> shift) & 255));
                assertTrue("hour " + hour + ": a channel moved " + d + " (a nudge, not a filter)", d <= 12);
            }
            int r = (w >> 16) & 255, b = w & 255;
            assertTrue("never purple: blue does not come with extra red at hour " + hour, r <= ((base >> 16) & 255) + 6 || b <= (base & 255));
        }
        assertEquals(TimeTint.Band.MORNING, TimeTint.band(8));
        assertEquals(TimeTint.Band.NIGHT, TimeTint.band(2));
    }

    @Test public void whatsNewHasRealNotesAndShowsOncePerVersion() {
        WhatsNew.Notes n = WhatsNew.current();
        assertFalse(n.highlights.isEmpty());
        assertFalse(n.fixes.isEmpty());
        assertTrue(WhatsNew.unseen("3.0.0-beta.6-ai9", null));
        assertTrue(WhatsNew.unseen("3.0.0-beta.6-ai9", "3.0.0-beta.6-ai8"));
        assertFalse(WhatsNew.unseen("3.0.0-beta.6-ai9", "3.0.0-beta.6-ai9"));
        assertFalse(WhatsNew.unseen(null, null));
    }
}
