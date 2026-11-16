package tv.calmhome.launcher.streaming;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.List;
import java.util.Locale;

import tv.calmhome.launcher.ModuleActivity;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Full-screen playback with AndroidX Media3. The player exists only while this screen is visible: it is
 * created in onStart and released in onStop, so nothing decodes or downloads after Back, Home or switching
 * Streaming off. Every request (manifest, segments, keys, redirects) must be HTTPS.
 *
 * Two modes: a live channel from the user's playlists (Up/Down switch channel), or a list of library items such as a
 * movie or the rest of a season (the next item starts when one ends; Up/Down move between items). For a Jellyfin
 * item the server's Authorization header is added only to requests on that exact server.
 *
 * Remote: OK / Play-Pause toggles · Left/Right and Rewind/Fast-forward seek 10 s where the stream allows ·
 * Up/Down and Channel ± switch · Back or Stop leaves.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends ModuleActivity {

    public static final String EXTRA_CHANNEL_ID = "zp_channel";
    /** Library mode: parallel arrays describing the items, the index to start at and the Streaming section. */
    public static final String EXTRA_TITLES = "zp_titles", EXTRA_SUBTITLES = "zp_subtitles", EXTRA_URLS = "zp_urls",
            EXTRA_KEYS = "zp_keys", EXTRA_RESUME = "zp_resume", EXTRA_START = "zp_start", EXTRA_SECTION = "zp_section",
            EXTRA_JELLYFIN = "zp_jellyfin";

    /** Something to play: title, a line under it, the HTTPS URL, a key for saved progress, a server resume point. */
    static final class Playable {
        final String title, subtitle, url, key;
        final long serverResumeMs;

        Playable(String title, String subtitle, String url, String key, long serverResumeMs) {
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.key = key;
            this.serverResumeMs = serverResumeMs;
        }
    }
    static final long SEEK_MS = 10_000;
    static final long OVERLAY_MS = 4_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private StreamingStore store;
    private final List<Playable> items = new java.util.ArrayList<>();
    private boolean channelMode;
    private JellyfinApi jellyfin;
    private String jellyfinAuth;
    private int index;
    private ExoPlayer player;
    private boolean watchedMarked;

    private FrameLayout root;
    private SurfaceView surface;
    private LinearLayout overlay;
    private TextView title, status, quality, captions;
    private View error, options;

    private final Runnable hideOverlay = () -> {
        if (error == null && player != null && player.isPlaying()) overlay.animate().alpha(0f).setDuration(theme.fadeMs(250)).start();
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected Features.Module module() {
        return Features.Module.STREAMING;
    }

    @Override
    protected void onModuleCreate(Bundle state) {
        store = new StreamingStore(this);
        if (!loadItems()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH, Gravity.CENTER));

        captions = theme.text(this, 24, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        captions.setShadowLayer(6, 0, 2, Color.BLACK);
        captions.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM);
        cl.setMargins(theme.safeX(), 0, theme.safeX(), theme.safeY() + theme.dp(30));
        root.addView(captions, cl);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(theme.dp(22), theme.dp(16), theme.dp(22), theme.dp(16));
        overlay.setBackground(theme.glass(Theme.R_PANEL, 0.8f));
        title = theme.text(this, Theme.T_TITLE + 4, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        status = theme.text(this, Theme.T_BODY, Theme.PULSE_CYAN, Theme.FONT_REGULAR);
        quality = theme.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        TextView hint = theme.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        hint.setText(channelMode ? "OK play/pause · hold OK: audio, subtitles, mute · ◀ ▶ seek · ▲ ▼ channel · Back exit"
                : "OK play/pause · hold OK: audio, subtitles, speed, mute · ◀ ▶ seek 10 s · ▲ ▼ previous/next · Back exit");
        overlay.addView(title);
        overlay.addView(status);
        overlay.addView(quality);
        overlay.addView(hint);
        FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.TOP | Gravity.START);
        ol.setMargins(theme.safeX(), theme.safeY(), theme.safeX(), 0);
        root.addView(overlay, ol);
        setContentView(root);
    }

    /** Fills {@link #items}; false when the request is invalid or its section is switched off. */
    private boolean loadItems() {
        android.content.Intent in = getIntent();
        if (in.hasExtra(EXTRA_CHANNEL_ID)) {
            channelMode = true;
            if (!Features.mayOpen(prefs, Features.Section.LIVE_TV)) return false;
            long id = in.getLongExtra(EXTRA_CHANNEL_ID, -1);
            index = -1;
            for (StreamingStore.Channel ch : store.channels()) {
                if (ch.id == id) index = items.size();
                items.add(new Playable(ch.name, ch.group, ch.url, ch.url, 0));
            }
            return index >= 0;
        }
        String section = in.getStringExtra(EXTRA_SECTION);
        Features.Section sec;
        try {
            sec = Features.Section.valueOf(section);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        if (!Features.mayOpen(prefs, sec)) return false;
        String[] titles = in.getStringArrayExtra(EXTRA_TITLES), subs = in.getStringArrayExtra(EXTRA_SUBTITLES),
                urls = in.getStringArrayExtra(EXTRA_URLS), keys = in.getStringArrayExtra(EXTRA_KEYS);
        long[] resume = in.getLongArrayExtra(EXTRA_RESUME);
        if (titles == null || urls == null || keys == null || titles.length != urls.length || keys.length != urls.length) return false;
        for (int i = 0; i < urls.length; i++) {
            items.add(new Playable(titles[i], subs != null && i < subs.length ? subs[i] : null, urls[i], keys[i],
                    resume != null && i < resume.length ? resume[i] : 0));
        }
        index = in.getIntExtra(EXTRA_START, 0);
        if (in.getBooleanExtra(EXTRA_JELLYFIN, false)) {
            jellyfin = new JellyfinAccount(this).api(JellyfinApi.HTTPS);
            if (jellyfin == null) return false;                 // signed out: nothing to send
            jellyfinAuth = jellyfin.authorization();
        }
        return !items.isEmpty() && index >= 0 && index < items.size();
    }

    // ------------------------------------------------------------------ player lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        if (root == null || isFinishing()) return;
        DataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("ZeroPulseTV/2.2")
                .setAllowCrossProtocolRedirects(false)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(20_000);
        DataSource.Factory httpsOnly = new ResolvingDataSource.Factory(http, spec -> {
            if (!"https".equalsIgnoreCase(spec.uri.getScheme())) throw new PlaybackErrors.HttpsOnly();
            // The server's token goes only to that server. (Android's HTTP stack also drops Authorization when a
            // redirect leaves the host.)
            if (jellyfin != null && jellyfin.sameServer(spec.uri.toString())) {
                return spec.withAdditionalHeaders(java.util.Collections.singletonMap("Authorization", jellyfinAuth));
            }
            return spec;
        });
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpsOnly))
                .setLoadControl(loadControl())
                .build();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !watchedMarked && channelMode) {
                    Playable p = items.get(index);
                    store.markWatched(p.url, p.title);
                    watchedMarked = true;
                }
                if (state == Player.STATE_ENDED && !channelMode) {
                    store.saveProgress(items.get(index).key, 0, Math.max(1, player.getDuration()));   // watched to the end
                    if (index < items.size() - 1) play(index + 1);
                }
                updateStatus();
            }

            @Override public void onIsPlayingChanged(boolean playing) {
                updateStatus();
                showOverlay();
            }

            @Override public void onPlayerError(PlaybackException e) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                showError(PlaybackErrors.title(e), PlaybackErrors.detail(e));
            }

            @Override public void onCues(CueGroup cues) {
                StringBuilder b = new StringBuilder();
                for (Cue c : cues.cues) if (c.text != null) b.append(b.length() > 0 ? "\n" : "").append(c.text);
                captions.setText(b);
            }
        });
        play(index);
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    /** Saves the position of seekable (non-live) videos, then releases the decoder and network. */
    private void releasePlayer() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hideOverlay);
        if (player == null) return;
        long dur = player.getDuration();
        if (!player.isCurrentMediaItemLive() && dur != C.TIME_UNSET && dur > 0) {
            store.saveProgress(items.get(index).key, player.getCurrentPosition(), dur);
        }
        player.release();
        player = null;
    }

    /** Media buffered ahead, in time and in bytes. */
    static final int MIN_BUFFER_MS = 15_000, MAX_BUFFER_MS = 30_000, TARGET_BUFFER_BYTES = 32 * 1024 * 1024;

    /**
     * Buffering sized for a TV. Media3's default may hold ~130 MB of video in memory for a high-bitrate stream
     * (measured on the TCL: 217 MB process memory playing an 18 Mbit/s DASH film); the byte cap keeps the buffer near
     * 32 MB, which is still ~14 s at 18 Mbit/s and much longer at typical bitrates.
     */
    static androidx.media3.exoplayer.LoadControl loadControl() {
        return new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, 2_500, 5_000)
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
    }

    /** For tests: whether a player currently exists. */
    boolean hasPlayer() {
        return player != null;
    }

    private void play(int i) {
        index = (i % items.size() + items.size()) % items.size();
        Playable p = items.get(index);
        watchedMarked = false;
        hideError();
        title.setText(p.title);
        quality.setText(p.subtitle != null ? p.subtitle : "");
        captions.setText("");
        player.setMediaItem(MediaItem.fromUri(p.url));
        long resume = store.progress(p.key);
        if (resume <= 0) resume = p.serverResumeMs;
        if (resume > 5_000) player.seekTo(resume);
        player.prepare();
        player.setPlayWhenReady(true);
        showOverlay();
    }

    // ------------------------------------------------------------------ overlay

    private void updateStatus() {
        if (player == null || status == null) return;
        String s;
        switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING: s = "Loading…"; break;
            case Player.STATE_ENDED: s = "Ended"; break;
            case Player.STATE_READY: s = player.isPlaying() ? (player.isCurrentMediaItemLive() ? "● Live" : "Playing") : "Paused"; break;
            default: s = "Stopped"; break;
        }
        long dur = player.getDuration();
        if (!player.isCurrentMediaItemLive() && dur != C.TIME_UNSET && dur > 0) {
            s += "  " + clock(player.getCurrentPosition()) + " / " + clock(dur);
        }
        status.setText(s);
        Format f = player.getVideoFormat();
        Playable p = items.get(index);
        String q = p.subtitle != null ? p.subtitle : "";
        if (f != null && f.height > 0) {
            // Real size: "856p" undersold a 1920×856 widescreen film.
            q += (q.isEmpty() ? "" : " · ") + (f.width > 0 ? f.width + "×" + f.height : f.height + "p");
            if (f.bitrate > 0) q += " · " + String.format(Locale.ROOT, "%.1f Mbit/s", f.bitrate / 1_000_000f);
        }
        quality.setText(q);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private void showOverlay() {
        if (overlay == null) return;
        overlay.animate().cancel();
        overlay.setAlpha(1f);
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, OVERLAY_MS);
    }

    private void showError(String head, String body) {
        hideError();
        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.75f));
        View card = w.stateCard(Glyph.Kind.INFO, Theme.ERROR, head, body + (channelMode ? "\n\nOK: try again · ▲ ▼: other channel · Back: leave"
                : "\n\nOK: try again · Back: leave"));
        holder.addView(card, new FrameLayout.LayoutParams(theme.dp(620), Widgets.WRAP, Gravity.CENTER));
        error = holder;
        root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        showOverlay();
    }

    private void hideError() {
        if (error != null) root.removeView(error);
        error = null;
    }

    // ------------------------------------------------------------------ remote

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player == null || options != null) return super.onKeyDown(keyCode, event);   // the options panel takes the D-pad
        showOverlay();
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_CAPTIONS:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                showOptions();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // Play/pause on release; holding OK opens the options instead (the TCL remote has no Menu key).
                if (event.getRepeatCount() == 0) event.startTracking();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                playPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: player.play(); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: player.pause(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-SEEK_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(SEEK_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                saveAndSwitch(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                saveAndSwitch(index + 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (player != null && options == null && isOk(keyCode)) {
            showOptions();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (player != null && options == null && isOk(keyCode)) {
            if (event.isTracking() && !event.isCanceled() && (event.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) == 0) playPause();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    private void playPause() {
        if (error != null) play(index);
        else if (player.isPlaying()) player.pause();
        else player.play();
    }

    /** Back closes the options panel first, then leaves. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (options != null) {
            closeOptions();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ audio, subtitles, speed, mute

    /** Options for what is playing now; only choices the stream actually offers are listed. */
    void showOptions() {
        if (player == null) return;
        closeOptions();
        LinearLayout card = w.stateCard(Glyph.Kind.GEAR, Theme.HOLO_BLUE, "Playback options", "Choices offered by this stream.");
        androidx.media3.common.Tracks tracks = player.getCurrentTracks();
        androidx.media3.common.TrackSelectionParameters params = player.getTrackSelectionParameters();

        card.addView(sectionTitle("Audio"));
        int audio = 0;
        for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                final androidx.media3.common.Tracks.Group group = g;
                final int track = i;
                audio++;
                card.addView(option((g.isTrackSelected(i) ? "● " : "   ") + trackLabel(g.getTrackFormat(i), "Audio " + audio), v -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), track))
                            .build());
                    closeOptions();
                }));
            }
        }
        if (audio <= 1) card.addView(note(audio == 0 ? "No audio track the TV can play." : "Only one audio track."));

        card.addView(sectionTitle("Subtitles"));
        boolean textOff = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        int text = 0;
        for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                final androidx.media3.common.Tracks.Group group = g;
                final int track = i;
                text++;
                card.addView(option((!textOff && g.isTrackSelected(i) ? "● " : "   ") + trackLabel(g.getTrackFormat(i), "Subtitles " + text), v -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), track))
                            .build());
                    closeOptions();
                }));
            }
        }
        if (text == 0) {
            card.addView(note("This stream has no subtitles."));
        } else {
            card.addView(option((textOff ? "● " : "   ") + "Off", v -> {
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                captions.setText("");
                closeOptions();
            }));
        }

        if (!player.isCurrentMediaItemLive() && player.isCurrentMediaItemSeekable()) {
            card.addView(sectionTitle("Speed"));
            float now = player.getPlaybackParameters().speed;
            for (final float sp : new float[]{0.75f, 1f, 1.25f, 1.5f}) {
                card.addView(option((Math.abs(now - sp) < 0.01f ? "● " : "   ") + (sp == 1f ? "Normal" : sp + "×"), v -> {
                    player.setPlaybackSpeed(sp);
                    closeOptions();
                }));
            }
        }

        card.addView(sectionTitle("Sound"));
        final boolean muted = player.getVolume() == 0f;
        card.addView(option(muted ? "   Unmute" : "   Mute (this video only; the TV's volume is unchanged)", v -> {
            player.setVolume(muted ? 1f : 0f);
            closeOptions();
        }));
        card.addView(option("   Close", v -> closeOptions()));

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(card);
        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.6f));
        holder.setClickable(true);
        holder.addView(sc, new FrameLayout.LayoutParams(theme.dp(520), Widgets.MATCH, Gravity.END));
        options = holder;
        root.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        for (int i = 0; i < card.getChildCount(); i++) if (card.getChildAt(i).isFocusable()) { card.getChildAt(i).requestFocus(); break; }
    }

    /** For tests: whether the options panel is open. */
    boolean optionsOpen() {
        return options != null;
    }

    private void closeOptions() {
        if (options != null) root.removeView(options);
        options = null;
    }

    static String trackLabel(Format f, String fallback) {
        StringBuilder b = new StringBuilder();
        if (f.label != null && !f.label.trim().isEmpty()) b.append(f.label.trim());
        if (f.language != null && !"und".equals(f.language)) {
            String lang = Locale.forLanguageTag(f.language).getDisplayLanguage();
            if (!lang.isEmpty() && b.indexOf(lang) < 0) b.append(b.length() > 0 ? " · " : "").append(lang);
        }
        if (f.channelCount > 0) b.append(b.length() > 0 ? " · " : "").append(f.channelCount == 6 ? "5.1" : f.channelCount == 2 ? "stereo" : f.channelCount + " ch");
        // Format names tell otherwise identical tracks apart (seen on the TV: two "English · 5.1" tracks, AC-3 and E-AC-3).
        String kind = formatName(f.sampleMimeType);
        if (kind != null) b.append(b.length() > 0 ? " · " : "").append(kind);
        return b.length() > 0 ? b.toString() : fallback;
    }

    static String formatName(String mime) {
        if (mime == null) return null;
        switch (mime) {
            case "audio/mp4a-latm": return "AAC";
            case "audio/ac3": return "Dolby Digital";
            case "audio/eac3": return "Dolby Digital Plus";
            case "audio/eac3-joc": return "Dolby Atmos";
            case "audio/ac4": return "Dolby AC-4";
            case "audio/true-hd": return "Dolby TrueHD";
            case "audio/vnd.dts": case "audio/vnd.dts.hd": return "DTS";
            case "audio/opus": return "Opus";
            case "application/cea-608": case "application/cea-708": return "CC";
            default: return null;
        }
    }

    private TextView sectionTitle(String s) {
        TextView v = theme.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        v.setText(s.toUpperCase(Locale.ROOT));
        v.setPadding(0, theme.dp(14), 0, theme.dp(2));
        return v;
    }

    private TextView note(String s) {
        TextView v = theme.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        v.setText(s);
        return v;
    }

    private TextView option(String label, View.OnClickListener click) {
        TextView v = w.navItem(label, false, click);
        v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        v.setContentDescription(label.trim());
        return v;
    }

    private void seekBy(long delta) {
        if (!player.isCurrentMediaItemSeekable()) return;
        long target = Math.max(0, player.getCurrentPosition() + delta);
        long dur = player.getDuration();
        if (dur != C.TIME_UNSET) target = Math.min(target, dur);
        player.seekTo(target);
    }

    private void saveAndSwitch(int to) {
        if (items.size() < 2) return;
        if (!channelMode && (to < 0 || to >= items.size())) return;
        long dur = player.getDuration();
        if (!player.isCurrentMediaItemLive() && dur != C.TIME_UNSET && dur > 0) {
            store.saveProgress(items.get(index).key, player.getCurrentPosition(), dur);
        }
        player.stop();
        play(to);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        if (store != null) store.close();
        super.onDestroy();
    }
}
