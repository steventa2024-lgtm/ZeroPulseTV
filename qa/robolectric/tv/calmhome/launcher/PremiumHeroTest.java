package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.core.DownloadSummary;
import tv.calmhome.launcher.core.HeroState;
import tv.calmhome.launcher.data.DownloadWatcher;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.downloads.DownloadRecord;
import tv.calmhome.launcher.ui.HeroView;

import static org.robolectric.Shadows.shadowOf;

/**
 * Phase C and the download part of Phase D on the real Home. The download samples here are SIMULATED (built in the test, labelled
 * "Simulated"): the TV's real queue was not used. What is checked: the centre shows a real-looking sample's own numbers only, keeps
 * the greeting's bounds, never takes focus, acknowledges the finish, and returns to the greeting.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PremiumHeroTest {

    private static MainActivity home() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MainActivity h = HomeTestSupport.startHome(true);
        h.pauseDownloadWatcherForTests();                  // the samples below are simulated; the real (empty) queue must not overwrite them
        return h;
    }

    private static DownloadSummary sample(DownloadSummary.Status s, long bytes, long total) {
        return DownloadSummary.of(Collections.singletonList(new DownloadSummary.Item(7, "Simulated app", s, bytes, total)), null, 0);
    }

    private static GlassTopNavView nav(MainActivity h) {
        return HomeTestSupport.all(h, GlassTopNavView.class).get(0);
    }

    private static void settle(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    @Test public void aRunningDownloadTakesTheCentreWithItsOwnNumbersInTheGreetingsBoundsWithoutTakingFocus() {
        MainActivity h = home();
        HeroView hero = h.hero();
        int[] at = new int[2];
        hero.getLocationInWindow(at);
        int top = at[1], height = hero.getHeight(), width = hero.getWidth();
        assertTrue("the region exists", height > 0);
        View focused = h.getWindow().getDecorView().findFocus();
        assertEquals(HeroState.Kind.GREETING, hero.showing());

        h.onDownloads(sample(DownloadSummary.Status.DOWNLOADING, 42, 100));
        settle(400);
        assertEquals(HeroState.Kind.ACTIVE_DOWNLOAD, hero.showing());
        assertEquals(View.VISIBLE, hero.downloadLayer().getVisibility());
        assertEquals(1f, hero.downloadLayer().getAlpha(), 0.01f);
        assertEquals(View.INVISIBLE, hero.greetingLayer().getVisibility());
        assertTrue(String.valueOf(hero.downloadStatusText()), hero.downloadStatusText().toString().contains("42%"));
        assertEquals(0.42f, hero.ring().fraction(), 0.001f);
        hero.getLocationInWindow(at);
        assertEquals("the centre did not move", top, at[1]);
        assertEquals("or grow", height, hero.getHeight());
        assertEquals(width, hero.getWidth());
        assertEquals("focus stayed where the viewer left it", focused, h.getWindow().getDecorView().findFocus());
        assertFalse(hero.isFocusable());
        assertEquals("Downloads shows the real progress", 0.42f, nav(h).itemProgress("Downloads"), 0.001f);

        h.onDownloads(DownloadSummary.IDLE);
        h.onDownloadFinished("Simulated app", true);
        assertTrue("a brief acknowledgment on Downloads", nav(h).acknowledgment("Downloads") > 0f);
        settle(400);
        assertEquals(HeroState.Kind.NOTIFICATION, hero.showing());
        assertTrue(Float.isNaN(nav(h).itemProgress("Downloads")));
        settle(HeroState.NOTICE_MS + 1500);
        assertEquals("back to the greeting", HeroState.Kind.GREETING, hero.showing());
        assertEquals(View.VISIBLE, hero.greetingLayer().getVisibility());
    }

    @Test public void aDownloadOfUnknownSizeShowsAWorkingRingAndNoInventedPercentage() {
        MainActivity h = home();
        h.onDownloads(sample(DownloadSummary.Status.DOWNLOADING, 5_000, -1));
        settle(400);
        assertEquals(-1f, h.hero().ring().fraction(), 0f);
        assertFalse(h.hero().downloadStatusText().toString().contains("%"));
        assertTrue("Downloads shows a working ring", nav(h).itemProgress("Downloads") < 0f);
    }

    @Test public void featuredAppsAreOffByDefaultAndWhenOnShowAnInstalledAppsOwnArtworkInTurn() {
        MainActivity h = home();
        settle(HeroState.GREETING_TURN_MS + 3_000);
        assertEquals("off by default: the greeting stays", HeroState.Kind.GREETING, h.hero().showing());

        new Prefs(HomeTestSupport.app()).setFeaturedOnHome(true);
        MainActivity on = home();
        boolean seen = false;
        long cycle = HeroState.GREETING_TURN_MS + HeroState.FEATURED_TURN_MS;
        for (long waited = 0; waited <= cycle + 3_000 && !seen; waited += 1_000) {    // the greeting's turn, then a featured app's
            settle(1_000);
            seen = on.hero().showing() == HeroState.Kind.FEATURED;
        }
        assertTrue("within one cycle a featured app takes its turn", seen);
        assertEquals(View.VISIBLE, on.hero().featuredLayer().getVisibility());
    }

    @Test public void theWatcherReadsTheQueueOffTheUiThreadAndReportsAFinishOnce() throws Exception {
        final List<DownloadRecord> queue = new ArrayList<>();
        DownloadRecord r = new DownloadRecord();
        r.id = 1; r.appName = "Simulated app"; r.state = DownloadRecord.State.DOWNLOADING; r.bytes = 10; r.total = 100;
        queue.add(r);
        final List<String> finished = new ArrayList<>();
        final DownloadSummary[] last = new DownloadSummary[1];
        final List<Boolean> onUiThread = new ArrayList<>();
        DownloadWatcher w = new DownloadWatcher(() -> {
            onUiThread.add(Looper.myLooper() == Looper.getMainLooper());
            return new ArrayList<>(queue);
        }, new DownloadWatcher.Listener() {
            @Override public void onSummary(DownloadSummary s) { last[0] = s; }
            @Override public void onFinished(String name, boolean ok) { finished.add(name + ":" + ok); }
        });
        w.start();
        for (int i = 0; i < 40 && last[0] == null; i++) { Thread.sleep(25); shadowOf(Looper.getMainLooper()).idle(); }
        assertTrue(last[0] != null && last[0].isActive());
        assertEquals(10, last[0].percent);
        r.state = DownloadRecord.State.AWAITING_APPROVAL;
        for (int i = 0; i < 80 && finished.isEmpty(); i++) { Thread.sleep(25); shadowOf(Looper.getMainLooper()).idleFor(1100, TimeUnit.MILLISECONDS); }
        assertEquals(Arrays.asList("Simulated app:true"), finished);
        for (int i = 0; i < 10; i++) { Thread.sleep(20); shadowOf(Looper.getMainLooper()).idleFor(4100, TimeUnit.MILLISECONDS); }
        assertEquals("reported once", 1, finished.size());
        assertFalse("never read on the UI thread", onUiThread.contains(Boolean.TRUE));
        w.shutdown();
    }
}
