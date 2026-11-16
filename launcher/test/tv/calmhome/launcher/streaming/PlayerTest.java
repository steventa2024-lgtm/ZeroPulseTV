package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.M3uParser;
import tv.calmhome.launcher.data.Prefs;

/**
 * Player lifecycle and error wording. Robolectric has no video decoder and these tests make no network request
 * that could succeed: whether a real stream plays is a device test.
 */
@RunWith(RobolectricTestRunner.class)
public class PlayerTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private long oneChannel() {
        StreamingStore s = new StreamingStore(c);
        long src = s.addSource(StreamingStore.KIND_URL, "https://lists.example.org/tv.m3u", "lists.example.org");
        // Port 9 on loopback: nothing listens, so any load attempt fails at once without leaving the PC.
        s.replaceChannels(src, Collections.singletonList(M3uParser.single("Test", "https://127.0.0.1:9/none.m3u8")), "1 stream");
        long id = s.channels().get(0).id;
        s.close();
        return id;
    }

    @Test public void refusesWhileStreamingIsOff() {
        long id = oneChannel();
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class,
                new Intent(c, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, id)).setup().get();
        assertTrue(p.isFinishing());
        assertFalse(p.hasPlayer());
    }

    @Test public void refusesWhileLiveTvSectionIsOff() {
        long id = oneChannel();
        Prefs prefs = new Prefs(c);
        prefs.setEnabled(Features.Module.STREAMING, true);
        prefs.setSectionEnabled(Features.Section.LIVE_TV, false);
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class,
                new Intent(c, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, id)).setup().get();
        assertTrue(p.isFinishing());
    }

    @Test public void playerExistsOnlyWhileVisible() {
        long id = oneChannel();
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        ActivityController<PlayerActivity> ctl = Robolectric.buildActivity(PlayerActivity.class,
                new Intent(c, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, id)).setup();
        assertTrue(ctl.get().hasPlayer());
        ctl.pause().stop();
        assertFalse("released on stop: no background decoding or downloading", ctl.get().hasPlayer());
        ctl.restart().start().resume();
        assertTrue(ctl.get().hasPlayer());
        ctl.pause().stop().destroy();
        assertFalse(ctl.get().hasPlayer());
    }

    private Intent library(String section) {
        return new Intent(c, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_SECTION, section)
                .putExtra(PlayerActivity.EXTRA_TITLES, new String[]{"Episode 1", "Episode 2"})
                .putExtra(PlayerActivity.EXTRA_URLS, new String[]{"https://127.0.0.1:9/e1.mp4", "https://127.0.0.1:9/e2.mp4"})
                .putExtra(PlayerActivity.EXTRA_KEYS, new String[]{"test:e1", "test:e2"})
                .putExtra(PlayerActivity.EXTRA_START, 1);
    }

    @Test public void libraryModeFollowsItsSectionSwitch() {
        Prefs prefs = new Prefs(c);
        prefs.setEnabled(Features.Module.STREAMING, true);
        prefs.setSectionEnabled(Features.Section.SHOWS, false);
        assertTrue(Robolectric.buildActivity(PlayerActivity.class, library("SHOWS")).setup().get().isFinishing());
        prefs.setSectionEnabled(Features.Section.SHOWS, true);
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class, library("SHOWS")).setup().get();
        assertFalse(p.isFinishing());
        assertTrue(p.hasPlayer());
        assertTrue(Robolectric.buildActivity(PlayerActivity.class, library("NOT_A_SECTION")).setup().get().isFinishing());
        Intent broken = library("SHOWS").putExtra(PlayerActivity.EXTRA_KEYS, new String[]{"only one"});
        assertTrue(Robolectric.buildActivity(PlayerActivity.class, broken).setup().get().isFinishing());
    }

    @Test public void menuOpensPlaybackOptionsAndBackClosesThemFirst() {
        Prefs prefs = new Prefs(c);
        prefs.setEnabled(Features.Module.STREAMING, true);
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class, library("SHOWS")).setup().get();
        p.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_MENU));
        assertTrue(p.optionsOpen());
        StringBuilder text = new StringBuilder();
        collect(p.getWindow().getDecorView(), text);
        // Nothing loaded (no decoder here): the panel says so rather than offering choices that don't exist.
        assertTrue(text.toString(), text.toString().contains("This stream has no subtitles."));
        assertTrue(text.toString().contains("Mute"));
        p.onBackPressed();
        assertFalse(p.optionsOpen());
        assertFalse("Back closed the panel, not the player", p.isFinishing());
    }

    /** The TCL remote has no Menu key: holding OK opens the options; a short OK doesn't. */
    @Test public void holdingOkOpensPlaybackOptions() {
        Prefs prefs = new Prefs(c);
        prefs.setEnabled(Features.Module.STREAMING, true);
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class, library("SHOWS")).setup().get();
        android.view.KeyEvent down = new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(p.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_CENTER, down));
        assertTrue(p.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER)));
        assertFalse("a short press plays or pauses", p.optionsOpen());
        assertTrue(p.onKeyLongPress(android.view.KeyEvent.KEYCODE_DPAD_CENTER, down));
        assertTrue(p.optionsOpen());
        StringBuilder text = new StringBuilder();
        collect(p.getWindow().getDecorView(), text);
        assertTrue(text.toString().contains("Mute"));
    }

    @Test public void trackLabelsAreReadable() {
        androidx.media3.common.Format fr = new androidx.media3.common.Format.Builder().setLanguage("fr").setChannelCount(6).build();
        assertTrue(PlayerActivity.trackLabel(fr, "Audio 1").endsWith("5.1"));
        androidx.media3.common.Format named = new androidx.media3.common.Format.Builder().setLabel("Commentary").setChannelCount(2).build();
        assertEquals("Commentary · stereo", PlayerActivity.trackLabel(named, "Audio 2"));
        assertEquals("Audio 3", PlayerActivity.trackLabel(new androidx.media3.common.Format.Builder().build(), "Audio 3"));
        androidx.media3.common.Format ac3 = new androidx.media3.common.Format.Builder().setChannelCount(6).setSampleMimeType("audio/ac3").build();
        androidx.media3.common.Format eac3 = new androidx.media3.common.Format.Builder().setChannelCount(6).setSampleMimeType("audio/eac3").build();
        assertEquals("5.1 · Dolby Digital", PlayerActivity.trackLabel(ac3, "x"));
        assertEquals("5.1 · Dolby Digital Plus", PlayerActivity.trackLabel(eac3, "x"));
        androidx.media3.common.Format cc = new androidx.media3.common.Format.Builder().setSampleMimeType("application/cea-608").build();
        assertEquals("CC", PlayerActivity.trackLabel(cc, "x"));
    }

    private static void collect(android.view.View v, StringBuilder b) {
        if (v instanceof android.widget.TextView) b.append(((android.widget.TextView) v).getText()).append('\n');
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) collect(((android.view.ViewGroup) v).getChildAt(i), b);
        }
    }

    @Test public void unknownChannelClosesTheScreen() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        PlayerActivity p = Robolectric.buildActivity(PlayerActivity.class,
                new Intent(c, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, 12345L)).setup().get();
        assertTrue(p.isFinishing());
    }

    @Test public void errorsAreExplainedWithoutTheUrl() {
        PlaybackException https = new PlaybackException("x", new PlaybackErrors.HttpsOnly(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        assertEquals("HTTP streams aren't supported", PlaybackErrors.title(https));

        HttpDataSource.InvalidResponseCodeException forbidden = new HttpDataSource.InvalidResponseCodeException(403, "Forbidden",
                null, Collections.<String, java.util.List<String>>emptyMap(),
                new androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://s.example.org/a.m3u8?token=SECRET")), new byte[0]);
        PlaybackException denied = new PlaybackException("x", forbidden, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS);
        assertEquals("The stream refused access", PlaybackErrors.title(denied));
        assertTrue(PlaybackErrors.detail(denied).contains("403"));
        assertFalse(PlaybackErrors.detail(denied).contains("SECRET"));

        for (int code : Arrays.asList(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)) {
            assertEquals("This TV can't decode this stream", PlaybackErrors.title(new PlaybackException("x", null, code)));
        }
        assertEquals("This stream is copy-protected",
                PlaybackErrors.title(new PlaybackException("x", null, PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED)));
        assertEquals("HTTP streams aren't supported", PlaybackErrors.title(new PlaybackException("x", null,
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)));
        assertEquals("The stream couldn't be reached", PlaybackErrors.title(new PlaybackException("x", new IOException(),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)));
    }
}
