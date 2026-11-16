package tv.calmhome.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.Features;

/**
 * An update from 2.1 keeps the dock and settings; module switches and deletes touch nothing else (not even another feature's file,
 * such as the old JARVIS pairing, which only {@link LegacyAssistantCleanup} removes).
 */
@RunWith(RobolectricTestRunner.class)
public class PreservationTest {

    private final Context c = RuntimeEnvironment.getApplication();
    private static final List<String> DOCK = Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv", "com.tcl.tv");

    /** The preference files exactly as ZeroPulse 2.1 leaves them. */
    private void write21State() {
        c.getSharedPreferences("calmhome", Context.MODE_PRIVATE).edit()
                .putString("dock", DockList.serialize(DOCK))
                .putBoolean("zp_assistant", false)
                .putInt("zp_dim", 30)
                .putInt("zp_ui_scale", 2)
                .putString("zp_wp_picked", "dolomites.jpg")
                .commit();
        c.getSharedPreferences("zp_jarvis", Context.MODE_PRIVATE).edit()
                .putString("host", "pc.example:18800")
                .putString("pin", "pin-fixture")
                .putString("token_ct", "ciphertext-fixture")
                .putString("token_iv", "iv-fixture")
                .commit();
    }

    private Map<String, ?> snapshot(String file) {
        return new HashMap<>(c.getSharedPreferences(file, Context.MODE_PRIVATE).getAll());
    }

    @Test public void upgradeFrom21KeepsDockSettingsAndPairing_modulesStartOff() {
        write21State();
        Map<String, ?> jarvis = snapshot("zp_jarvis");
        Prefs p = new Prefs(c);
        assertEquals(DOCK, p.dock());
        assertEquals(30, p.dimPercent());
        assertEquals(2, p.uiScaleIndex());
        assertEquals("dolomites.jpg", p.wallpaperPicked());
        assertFalse(p.enabled(Features.Module.STREAMING));
        assertFalse(p.enabled(Features.Module.WEB));
        assertEquals(jarvis, snapshot("zp_jarvis"));
    }

    @Test public void switchingModulesOnlyWritesTheirOwnKeys() {
        write21State();
        Map<String, Object> before = new HashMap<>(snapshot("calmhome"));
        Map<String, ?> jarvis = snapshot("zp_jarvis");
        Prefs p = new Prefs(c);
        p.setEnabled(Features.Module.STREAMING, true);
        p.setEnabled(Features.Module.WEB, true);
        p.setSectionEnabled(Features.Section.SPORTS, false);
        p.setEnabled(Features.Module.STREAMING, false);
        Map<String, Object> after = new HashMap<>(snapshot("calmhome"));
        after.keySet().removeIf(k -> k.startsWith("zp_feature_") || k.startsWith("zp_stream_"));
        assertEquals(before, after);
        assertEquals(jarvis, snapshot("zp_jarvis"));
        // Off again, but the user's sub-choice survives for next time.
        assertFalse(p.sectionEnabled(Features.Section.SPORTS));
    }

    @Test public void deletingModuleDataLeavesLauncherAndPairingAlone() throws Exception {
        write21State();
        c.getSharedPreferences(ModuleData.STREAMING_PREFS, Context.MODE_PRIVATE).edit().putString("s", "1").commit();
        c.getSharedPreferences(ModuleData.WEB_PREFS, Context.MODE_PRIVATE).edit().putString("w", "1").commit();
        File sdb = touch(c.getDatabasePath(ModuleData.STREAMING_DB));
        File wdb = touch(c.getDatabasePath(ModuleData.WEB_DB));
        File cookies = touch(new File(c.getDataDir(), "app_webview_web/Default/Cookies"));
        File webCache = touch(new File(c.getCacheDir(), "WebView_web/Default/HTTP Cache/data_0"));
        File guide = touch(new File(c.getCacheDir(), "streaming/guide.xml"));
        File unrelated = touch(new File(c.getFilesDir(), "keep.txt"));
        Map<String, ?> home = snapshot("calmhome"), jarvis = snapshot("zp_jarvis");

        ModuleData.deleteStreamingData(c);
        assertFalse(sdb.exists());
        assertFalse(guide.exists());
        assertFalse(prefsFile(ModuleData.STREAMING_PREFS).exists());
        assertTrue(wdb.exists());
        assertTrue(cookies.exists());
        assertTrue(prefsFile(ModuleData.WEB_PREFS).exists());

        ModuleData.deleteWebData(c);
        assertFalse(wdb.exists());
        assertFalse(cookies.exists());
        assertFalse(webCache.exists());
        assertFalse(prefsFile(ModuleData.WEB_PREFS).exists());

        assertTrue(unrelated.exists());
        assertTrue(prefsFile("calmhome").exists());
        assertTrue(prefsFile("zp_jarvis").exists());
        assertEquals(home, snapshot("calmhome"));
        assertEquals(jarvis, snapshot("zp_jarvis"));
    }

    @Test public void deleteNeverFollowsALinkOutOfTheModuleFolder() throws Exception {
        File outside = touch(new File(c.getFilesDir(), "outside/precious.txt"));
        File webDir = new File(c.getDataDir(), "app_webview_web");
        assertTrue(webDir.mkdirs() || webDir.isDirectory());
        try {
            java.nio.file.Files.createSymbolicLink(new File(webDir, "link").toPath(), outside.getParentFile().toPath());
        } catch (java.nio.file.FileSystemException noPrivilege) {
            // Windows without the symlink privilege: can't build the fixture here (runs on Linux/macOS CI).
            org.junit.Assume.assumeNoException(noPrivilege);
        }
        ModuleData.deleteWebData(c);
        assertTrue(outside.exists());
        assertFalse(webDir.exists());
    }

    private File prefsFile(String name) {
        return new File(c.getDataDir(), "shared_prefs/" + name + ".xml");
    }

    private static File touch(File f) throws Exception {
        assertTrue(f.getParentFile().mkdirs() || f.getParentFile().isDirectory());
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(1);
        }
        return f;
    }

}
