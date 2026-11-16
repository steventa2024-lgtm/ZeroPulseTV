package tv.calmhome.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.DockList;

/**
 * The one-time removal of what the Assistant (JARVIS) stored: exactly its own files, keys and two switches, nothing else, and
 * only marked done when it all worked.
 */
@RunWith(RobolectricTestRunner.class)
public class LegacyAssistantCleanupTest {

    private final Context c = RuntimeEnvironment.getApplication();
    private static final List<String> DOCK = Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv");

    /** A fake Keystore holding the given aliases. */
    private static final class FakeKeys implements LegacyAssistantCleanup.Keys {
        final Set<String> aliases;
        final List<String> asked = new ArrayList<>();
        boolean broken;

        FakeKeys(String... aliases) {
            this.aliases = new HashSet<>(Arrays.asList(aliases));
        }

        @Override public boolean delete(String alias) throws Exception {
            asked.add(alias);
            if (broken) throw new java.security.KeyStoreException("not ready");
            return aliases.remove(alias);
        }
    }

    /** The data a TV with ai9, a paired PC, a ChatGPT sign-in and Streaming turned on would hold. */
    private void writeAi9State() {
        c.getSharedPreferences("calmhome", Context.MODE_PRIVATE).edit()
                .putString("dock", DockList.serialize(DOCK))
                .putBoolean("zp_assistant", true)
                .putBoolean("zp_reactor_anim", false)
                .putInt("zp_dim", 20)
                .putString("zp_wp_picked", "zp02.jpg")
                .putBoolean("zp_feature_streaming", true)
                .commit();
        c.getSharedPreferences("zp_jarvis", Context.MODE_PRIVATE).edit().putString("host", "pc.example").putString("token_ct", "x").commit();
        c.getSharedPreferences("tv_ai_provider_prefs", Context.MODE_PRIVATE).edit().putString("encrypted-key", "encrypted-value").commit();
        c.getSharedPreferences("zp_ai_models", Context.MODE_PRIVATE).edit().putString("chatgpt", "a-model").commit();
        c.getSharedPreferences("zp_chat_v1", Context.MODE_PRIVATE).edit().putString("turns", "[]").commit();
        c.getSharedPreferences("assistant_voice", Context.MODE_PRIVATE).edit().putBoolean("speakReplies", true).commit();
        c.getSharedPreferences(ModuleData.STREAMING_PREFS, Context.MODE_PRIVATE).edit().putString("source", "kept").commit();
    }

    private Map<String, ?> snapshot(String file) {
        return new HashMap<>(c.getSharedPreferences(file, Context.MODE_PRIVATE).getAll());
    }

    private File prefsFile(String name) {
        return new File(c.getDataDir(), "shared_prefs/" + name + ".xml");
    }

    @Test public void removesExactlyTheAssistantsDataAndKeepsEverythingElse() throws Exception {
        writeAi9State();
        File unrelated = new File(c.getFilesDir(), "keep.txt");
        assertTrue(unrelated.getParentFile().mkdirs() || unrelated.getParentFile().isDirectory());
        try (FileOutputStream o = new FileOutputStream(unrelated)) { o.write(1); }
        Map<String, ?> streaming = snapshot(ModuleData.STREAMING_PREFS);
        FakeKeys keys = new FakeKeys("zp_jarvis_device", "zp_jarvis_token", "zp_ai_master", ModuleData.JELLYFIN_KEY_ALIAS);

        LegacyAssistantCleanup.Result r = LegacyAssistantCleanup.run(c, keys);

        assertTrue(r.done);
        assertEquals(5, r.files);
        assertEquals(3, r.aliases);
        assertEquals(2, r.keys);
        for (String f : new String[]{"zp_jarvis", "tv_ai_provider_prefs", "zp_ai_models", "zp_chat_v1", "assistant_voice"}) {
            assertFalse(f + " is deleted", prefsFile(f).exists());
            assertTrue(f + " reads empty", snapshot(f).isEmpty());
        }
        assertEquals("only the three Assistant keys were asked for", Arrays.asList("zp_jarvis_device", "zp_jarvis_token", "zp_ai_master"), keys.asked);
        assertTrue("the Jellyfin key is untouched", keys.aliases.contains(ModuleData.JELLYFIN_KEY_ALIAS));

        Prefs p = new Prefs(c);
        assertEquals(DOCK, p.dock());
        assertEquals(20, p.dimPercent());
        assertEquals("zp02.jpg", p.wallpaperPicked());
        Map<String, ?> home = snapshot("calmhome");
        assertFalse(home.containsKey("zp_assistant"));
        assertFalse(home.containsKey("zp_reactor_anim"));
        assertEquals(true, home.get("zp_feature_streaming"));
        assertEquals(streaming, snapshot(ModuleData.STREAMING_PREFS));
        assertTrue(unrelated.exists());
    }

    @Test public void runsOnceThenDoesNothing() {
        writeAi9State();
        FakeKeys keys = new FakeKeys("zp_jarvis_device");
        assertTrue(LegacyAssistantCleanup.run(c, keys).done);
        // Something called zp_jarvis appearing later (it cannot: no code writes it any more) is not looked at again.
        c.getSharedPreferences("zp_jarvis", Context.MODE_PRIVATE).edit().putString("host", "again").commit();
        keys.asked.clear();
        LegacyAssistantCleanup.Result again = LegacyAssistantCleanup.run(c, keys);
        assertEquals(0, again.files + again.aliases + again.keys);
        assertTrue(keys.asked.isEmpty());
    }

    @Test public void aKeystoreThatIsNotReadyIsTriedAgainNextTime() {
        writeAi9State();
        FakeKeys keys = new FakeKeys("zp_jarvis_device", "zp_ai_master");
        keys.broken = true;
        LegacyAssistantCleanup.Result first = LegacyAssistantCleanup.run(c, keys);
        assertFalse("not marked done", first.done);
        assertEquals("the files went anyway", 5, first.files);
        keys.broken = false;
        LegacyAssistantCleanup.Result second = LegacyAssistantCleanup.run(c, keys);
        assertTrue(second.done);
        assertEquals(2, second.aliases);
        assertTrue(keys.aliases.isEmpty());
    }

    @Test public void aTvThatNeverUsedTheAssistantLosesNothing() {
        c.getSharedPreferences("calmhome", Context.MODE_PRIVATE).edit().putString("dock", DockList.serialize(DOCK)).commit();
        Map<String, Object> before = new HashMap<>(snapshot("calmhome"));
        LegacyAssistantCleanup.Result r = LegacyAssistantCleanup.run(c, new FakeKeys());
        assertTrue(r.done);
        assertEquals(0, r.files + r.aliases + r.keys);
        Map<String, Object> after = new HashMap<>(snapshot("calmhome"));
        after.remove(LegacyAssistantCleanup.DONE_KEY);
        assertEquals(before, after);
    }
}
