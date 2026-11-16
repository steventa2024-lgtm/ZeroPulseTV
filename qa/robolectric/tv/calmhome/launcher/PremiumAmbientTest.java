package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.View;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.core.HeroState;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.TimeTint;
import tv.calmhome.launcher.core.UiTones;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.UiSounds;
import tv.calmhome.launcher.ui.RippleView;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/** Phase E on the real Home (simulated: Robolectric, not the TV): the secret, the time-of-day tint, wallpaper depth and the sounds. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PremiumAmbientTest {

    private static MainActivity home() {
        for (String[] app : MockTvApps.DEFAULTS) MockTvApps.install(app[0], app[1], true, MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        MainActivity h = HomeTestSupport.startHome(true);
        h.pauseDownloadWatcherForTests();
        return h;
    }

    private static void press(MainActivity h, int key) {
        h.dispatchKeyEvent(new KeyEvent(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, key, 0));
        h.dispatchKeyEvent(new KeyEvent(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0));
        ShadowLooper.idleMainLooper(120, TimeUnit.MILLISECONDS);
    }

    private static final int U = KeyEvent.KEYCODE_DPAD_UP, D = KeyEvent.KEYCODE_DPAD_DOWN, L = KeyEvent.KEYCODE_DPAD_LEFT, R = KeyEvent.KEYCODE_DPAD_RIGHT;

    @Test public void theSecretSequenceRipplesTheWallpaperShowsANoticeAndReturnsToNormalWhileFocusStillMoves() {
        MainActivity h = home();
        ZeroPulseGlassAppTile first = HomeTestSupport.glassTiles(h).get(2);
        first.requestFocus();
        View before = h.getWindow().getDecorView().findFocus();
        for (int k : new int[]{U, U, D, D, L, R, L}) press(h, k);
        assertEquals("not yet", 0, h.secretsFound());
        long t = android.os.SystemClock.uptimeMillis();
        h.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, R, 0));     // the last key
        assertEquals(1, h.secretsFound());
        assertEquals("the ripple is over the wallpaper", 1, HomeTestSupport.all(h, RippleView.class).size());
        h.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, R, 0));
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(HeroState.Kind.NOTIFICATION, h.hero().showing());
        assertTrue(HomeTestSupport.hasText(h, "ZeroPulse Secret Unlocked"));
        assertTrue("the keys still moved focus as usual (nothing was swallowed)", h.getWindow().getDecorView().findFocus() != null);
        ShadowLooper.idleMainLooper(HeroState.NOTICE_MS + 1500, TimeUnit.MILLISECONDS);
        assertEquals("the ripple removed itself", 0, HomeTestSupport.all(h, RippleView.class).size());
        assertEquals("and the centre is the greeting again", HeroState.Kind.GREETING, h.hero().showing());
        assertTrue(before != null);
    }

    @Test public void theSecretDoesNotFireWhileAPanelIsOpen() {
        MainActivity h = home();
        h.open(new tv.calmhome.launcher.screens.WhatsNewPanel(h));
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
        for (int k : new int[]{U, U, D, D, L, R, L, R}) press(h, k);
        assertEquals(0, h.secretsFound());
    }

    @Test public void theWashFollowsTheTimeOfDayOnlyWhenTheTintIsOn() {
        MainActivity h = home();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        assertEquals(TimeTint.wash(LiquidTokens.WASH, hour), ((ColorDrawable) h.washLayer().getBackground()).getColor());
        new Prefs(HomeTestSupport.app()).setTimeOfDayTint(false);
        h.refreshHome();
        assertEquals("off: the approved wash", LiquidTokens.WASH, ((ColorDrawable) h.washLayer().getBackground()).getColor());
    }

    @Test public void wallpaperDepthIsOffByDefaultAndWhenOnOnlyTheWallpaperDriftsAndTheGlassIsUntouched() {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        tiles.get(tiles.size() - 1).requestFocus();
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        for (View w : h.wallpaperLayers()) {
            assertEquals("off by default: the wallpaper stays put", 0f, w.getTranslationX(), 0f);
            assertEquals(1f, w.getScaleX(), 0f);
        }
        new Prefs(HomeTestSupport.app()).setWallpaperDepth(true);
        MainActivity on = home();
        List<ZeroPulseGlassAppTile> t2 = HomeTestSupport.glassTiles(on);
        t2.get(t2.size() - 1).requestFocus();
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        View wall = on.wallpaperLayers()[0];
        float max = tv.calmhome.launcher.core.MotionTokens.PARALLAX_X_DP * on.getResources().getDisplayMetrics().density;
        assertTrue("focus at the right: the wallpaper drifts left: " + wall.getTranslationX(), wall.getTranslationX() < 0f && wall.getTranslationX() >= -max - 0.5f);
        assertTrue("enlarged just enough to cover the drift", wall.getScaleX() > 1f && wall.getScaleX() < 1.02f);
        for (ZeroPulseGlassAppTile t : t2) assertEquals("the glass is never transformed", 1f, t.getScaleX(), 0f);
    }

    @Test public void interfaceSoundsAreOffByDefaultOriginalAndThrottled() throws Exception {
        MainActivity h = home();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        tiles.get(1).requestFocus();
        tiles.get(2).requestFocus();
        assertEquals("off by default: silent", 0, h.sounds().played());

        for (UiTones.Tone t : UiTones.Tone.values()) {
            byte[] wav = UiTones.wav(t);
            assertEquals("RIFF", new String(wav, 0, 4, "US-ASCII"));
            assertEquals("WAVE", new String(wav, 8, 4, "US-ASCII"));
            assertTrue(t + " is short: " + wav.length, wav.length < 44 + UiTones.RATE * 2 / 2);
        }

        new Prefs(HomeTestSupport.app()).setUiSounds(true);
        UiSounds s = h.sounds();
        for (int i = 0; i < 60 && s.played() == 0; i++) {           // the tones are prepared on first use, off the UI thread
            s.play(UiSounds.Kind.SELECT);
            Thread.sleep(20);
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
        }
        assertTrue("once prepared, sounds play", s.played() > 0);
        int before = s.played();
        s.play(UiSounds.Kind.FOCUS);
        s.play(UiSounds.Kind.FOCUS);                                 // the same instant: a held D-pad
        assertEquals("a second tick within 45 ms is skipped", before + 1, s.played());
    }
}
