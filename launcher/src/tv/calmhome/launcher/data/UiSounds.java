package tv.calmhome.launcher.data;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.calmhome.launcher.core.UiTones;

/**
 * ZeroPulse's own interface sounds (UiSoundManager in the brief): focus tick, select, panel whoosh, success and error. They are original
 * tones synthesised on the TV (see {@link UiTones}; no downloaded or third-party sound files), written once to the app's cache and
 * loaded into a {@link SoundPool}, so nothing is decoded on a focus move.
 *
 * Off by default (Settings > Appearance > Interface sounds), because the TV may already play its own navigation sounds. When on: a
 * focus tick is never repeated faster than {@link #TICK_GAP_MS}, nothing plays while media is playing, and the volume is the viewer's.
 */
public final class UiSounds {
    public enum Kind { FOCUS, SELECT, WHOOSH, SUCCESS, ERROR }

    static final long TICK_GAP_MS = 45;

    private final Context c;
    private final Prefs prefs;
    private final AudioManager audio;
    private final Map<Kind, Integer> ids = new java.util.concurrent.ConcurrentHashMap<>();   // filled on the loader thread
    private volatile SoundPool pool;
    private boolean loading;
    private long lastTick;
    private int played;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zp-ui-sounds");
        t.setDaemon(true);
        return t;
    });

    public UiSounds(Context c, Prefs prefs) {
        this.c = c.getApplicationContext();
        this.prefs = prefs;
        this.audio = c.getSystemService(AudioManager.class);
    }

    /** Plays {@code kind} if sounds are on and nothing else should be heard instead. Cheap when off: one preference read. */
    public void play(Kind kind) {
        if (!prefs.uiSounds()) return;
        if (audio != null && audio.isMusicActive()) return;            // never over a film, a song or a live channel
        long now = SystemClock.uptimeMillis();
        if (kind == Kind.FOCUS) {
            if (now - lastTick < TICK_GAP_MS) return;                   // a held D-pad does not machine-gun ticks
            lastTick = now;
        }
        ensureLoaded();
        Integer id = ids.get(kind);
        if (pool == null || id == null) return;                         // still being prepared: the first press is silent
        float v = prefs.uiSoundVolume() / 100f * (kind == Kind.FOCUS ? 0.55f : 1f);
        pool.play(id, v, v, 1, 0, 1f);
        played++;
    }

    /** How many sounds actually started (for tests and diagnostics). */
    public int played() {
        return played;
    }

    private void ensureLoaded() {
        if (pool != null || loading) return;
        loading = true;
        final SoundPool p = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        io.execute(() -> {
            Map<Kind, File> files = new EnumMap<>(Kind.class);
            try {
                File dir = new File(c.getCacheDir(), "ui-sounds-v1");
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                for (Kind k : Kind.values()) {
                    File f = new File(dir, k.name().toLowerCase(java.util.Locale.ROOT) + ".wav");
                    if (!f.isFile()) try (FileOutputStream out = new FileOutputStream(f)) { out.write(UiTones.wav(tone(k))); }
                    files.put(k, f);
                }
            } catch (IOException e) {
                return;                                                // no sounds rather than a crash
            }
            for (Map.Entry<Kind, File> e : files.entrySet()) ids.put(e.getKey(), p.load(e.getValue().getPath(), 1));
            pool = p;
        });
    }

    private static UiTones.Tone tone(Kind k) {
        switch (k) {
            case FOCUS: return UiTones.Tone.TICK;
            case SELECT: return UiTones.Tone.THUD;
            case WHOOSH: return UiTones.Tone.WHOOSH;
            case SUCCESS: return UiTones.Tone.SUCCESS;
            default: return UiTones.Tone.ERROR;
        }
    }

    public void release() {
        io.shutdownNow();
        if (pool != null) pool.release();
        pool = null;
    }
}
