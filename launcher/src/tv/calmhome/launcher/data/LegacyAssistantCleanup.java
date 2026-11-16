package tv.calmhome.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.KeyStore;

/**
 * Removes what the Assistant (JARVIS) left on the TV, once, after the update that removed it (3.0.0-beta.7).
 *
 * <p>The list is exact: these are the only preference files, preference keys and Android Keystore entries that the removed
 * {@code assistant}, {@code jarvis}, {@code ai} and {@code auth} packages ever wrote (checked against the 3.0.0-beta.6-ai9
 * source). Nothing is matched by pattern and nothing else is touched: the dock, settings, wallpapers, downloads, the optional
 * modules' data and the Jellyfin key ({@code zp_jellyfin_token}) stay exactly as they are.
 *
 * <ul>
 *   <li>{@code zp_jarvis.xml}: the JARVIS PC pairing (address, certificate pin, the encrypted device token).</li>
 *   <li>{@code tv_ai_provider_prefs.xml}: the AI Accounts sign-ins (encrypted with Jetpack Security; the keysets live in the same
 *       file), including the ChatGPT/Codex token.</li>
 *   <li>{@code zp_ai_models.xml}, {@code zp_chat_v1.xml}, {@code assistant_voice.xml}: the remembered model, the local chat history,
 *       the "speak replies" switch.</li>
 *   <li>Keystore: {@code zp_jarvis_device} (the device identity), {@code zp_jarvis_token} (the token key), {@code zp_ai_master}
 *       (the AI Accounts master key).</li>
 *   <li>In the launcher's own {@code calmhome} preferences, only the two Assistant switches {@code zp_assistant} and
 *       {@code zp_reactor_anim}.</li>
 * </ul>
 *
 * Runs on a worker thread. It is marked done only when every step succeeded (or had nothing to do), so a Keystore that was not
 * ready is simply tried again at the next start. Like the rest of the data package it logs nothing.
 */
public final class LegacyAssistantCleanup {
    /** Set in the {@code calmhome} preferences once the cleanup has finished. */
    static final String DONE_KEY = "zp_assistant_cleanup_v1";
    static final String[] PREFERENCE_FILES = {"zp_jarvis", "tv_ai_provider_prefs", "zp_ai_models", "zp_chat_v1", "assistant_voice"};
    static final String[] KEYSTORE_ALIASES = {"zp_jarvis_device", "zp_jarvis_token", "zp_ai_master"};
    static final String[] LAUNCHER_KEYS = {"zp_assistant", "zp_reactor_anim"};
    private static final String LAUNCHER_FILE = "calmhome";

    /** What a run did (for tests and the log). */
    public static final class Result {
        public final int files, aliases, keys;
        public final boolean done;

        Result(int files, int aliases, int keys, boolean done) {
            this.files = files;
            this.aliases = aliases;
            this.keys = keys;
            this.done = done;
        }
    }

    /** Where Keystore entries are removed; replaceable in tests (Robolectric has no Android Keystore). */
    public interface Keys {
        /** Deletes the entry if it exists. Returns true if one was deleted. Throws if the store cannot be read. */
        boolean delete(String alias) throws Exception;
    }

    static final Keys ANDROID_KEYSTORE = alias -> {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(alias)) return false;
        ks.deleteEntry(alias);
        return true;
    };

    private LegacyAssistantCleanup() { }

    /** Starts the cleanup on its own thread if it has not finished before. Never blocks or throws. */
    public static void runOnceInBackground(Context context) {
        final Context app = context.getApplicationContext();
        if (launcherPrefs(app).getBoolean(DONE_KEY, false)) return;
        Thread t = new Thread(() -> {
            try {
                run(app, ANDROID_KEYSTORE);
            } catch (RuntimeException e) {
                // Not marked done: it runs again at the next start.
            }
        }, "assistant-cleanup");
        t.setDaemon(true);
        t.start();
    }

    /** The cleanup itself, synchronously (tests call this). */
    public static Result run(Context context, Keys keys) {
        SharedPreferences launcher = launcherPrefs(context);
        if (launcher.getBoolean(DONE_KEY, false)) return new Result(0, 0, 0, true);
        boolean ok = true;
        int files = 0, aliases = 0, removedKeys = 0;
        for (String name : PREFERENCE_FILES) {
            if (!exists(context, name)) continue;
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();   // also empties a cached instance
            if (context.deleteSharedPreferences(name)) files++;
            else ok = false;
        }
        for (String alias : KEYSTORE_ALIASES) {
            try {
                if (keys.delete(alias)) aliases++;
            } catch (Exception e) {
                ok = false;                                     // the Keystore was not readable now: try again next start
            }
        }
        SharedPreferences.Editor edit = launcher.edit();
        for (String key : LAUNCHER_KEYS) {
            if (launcher.contains(key)) {
                edit.remove(key);
                removedKeys++;
            }
        }
        if (ok) edit.putBoolean(DONE_KEY, true);
        edit.commit();
        return new Result(files, aliases, removedKeys, ok);
    }

    private static boolean exists(Context c, String name) {
        java.io.File dir = new java.io.File(c.getApplicationInfo().dataDir, "shared_prefs");
        return new java.io.File(dir, name + ".xml").exists() || !c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().isEmpty();
    }

    private static SharedPreferences launcherPrefs(Context c) {
        return c.getSharedPreferences(LAUNCHER_FILE, Context.MODE_PRIVATE);
    }
}
