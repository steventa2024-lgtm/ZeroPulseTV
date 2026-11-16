package tv.calmhome.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.WallpaperCatalog;

/**
 * All persisted launcher state. Same file ("calmhome") and same "dock" key as Calm Home 1.0, so the
 * saved dock carries over the update and survives a rollback. New ZeroPulse settings use "zp_" keys,
 * which Calm Home ignores. Nothing here ever clears or rewrites "dock" unless the user edits the dock.
 * 2.2 only adds keys (zp_feature_*, zp_stream_*), so 2.1 can still read this file after a rollback.
 */
public final class Prefs implements Features.State {
    private static final String FILE = "calmhome";
    private static final String DOCK = "dock";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- dock (Calm Home format)
    public List<String> dock() {
        return DockList.parse(sp.getString(DOCK, null));
    }

    public void setDock(List<String> dock) {
        sp.edit().putString(DOCK, DockList.serialize(dock)).apply();
    }

    /** Back to the original seven favourites (removes the key, exactly like a fresh Calm Home). */
    public void resetDock() {
        sp.edit().remove(DOCK).apply();
    }

    public boolean labelsAlways() { return sp.getBoolean("zp_labels_always", false); }
    public void setLabelsAlways(boolean v) { sp.edit().putBoolean("zp_labels_always", v).apply(); }

    public List<String> recentlyOpened() { return DockList.parse(sp.getString("zp_recent_apps", "")); }
    public void recordOpened(String pkg) {
        List<String> updated = tv.calmhome.launcher.core.AppLibrary.record(recentlyOpened(), pkg);
        sp.edit().putString("zp_recent_apps", DockList.serialize(updated)).apply();
    }

    public boolean usePhotoBackdrop() {
        return sp.getBoolean("zp_photo_backdrop", sp.contains("zp_wp_picked") || sp.contains("zp_wp_collection"));
    }
    public void setPhotoBackdrop(boolean enabled) { sp.edit().putBoolean("zp_photo_backdrop", enabled).apply(); }

    // ---- Liquid Glass and its crash guard: the guard is armed (synchronously) before Home starts in Liquid Glass and cleared
    // once Home has stayed up; if it is found armed, the last start never finished.
    /**
     * Liquid Glass Home is on unless the viewer (or the crash guard) has turned it off: with no stored choice it is on, so
     * an upgrade from a build that never offered it shows the approved look, while an explicit "off" is always kept.
     */
    public boolean liquidGlass() { return sp.getBoolean(LIQUID_KEY, true); }
    /** True if Liquid Glass was chosen (on or off) rather than left at its default; for diagnostics. */
    public boolean liquidGlassIsExplicit() { return sp.contains(LIQUID_KEY); }
    private static final String LIQUID_KEY = "zp_liquid_glass";
    public void setLiquidGlass(boolean v) { sp.edit().putBoolean("zp_liquid_glass", v).commit(); }
    public boolean liquidStarting() { return sp.getBoolean("zp_liquid_starting", false); }
    public void setLiquidStarting(boolean v) { sp.edit().putBoolean("zp_liquid_starting", v).commit(); }

    // ---- appearance
    public boolean showGreeting() { return sp.getBoolean("zp_show_greeting", true); }
    public void setShowGreeting(boolean v) { sp.edit().putBoolean("zp_show_greeting", v).apply(); }

    public boolean showClock() { return sp.getBoolean("zp_show_clock", true); }
    public void setShowClock(boolean v) { sp.edit().putBoolean("zp_show_clock", v).apply(); }

    public boolean showDate() { return sp.getBoolean("zp_show_date", true); }
    public void setShowDate(boolean value) { sp.edit().putBoolean("zp_show_date", value).apply(); }

    public boolean reducedMotion() { return sp.getBoolean("zp_reduced_motion", false); }
    public void setReducedMotion(boolean v) { sp.edit().putBoolean("zp_reduced_motion", v).apply(); }

    // Premium interactions (3.0.0-beta.6 motion work). Each optional effect has its own switch; the defaults keep Home as approved.
    /** The focused tile's ambient brand glow and lift shadow (on by default; off for a plainer, cheaper focus). */
    public boolean focusGlow() { return sp.getBoolean("zp_focus_glow", true); }
    public void setFocusGlow(boolean v) { sp.edit().putBoolean("zp_focus_glow", v).apply(); }
    /** A featured installed app takes a turn in the centre of Home (off by default: the greeting is the default). */
    public boolean featuredOnHome() { return sp.getBoolean("zp_featured_home", false); }
    public void setFeaturedOnHome(boolean v) { sp.edit().putBoolean("zp_featured_home", v).apply(); }
    /** The wallpaper drifts slightly with focus (off by default: see WallpaperParallax for the glass trade-off). */
    public boolean wallpaperDepth() { return sp.getBoolean("zp_wallpaper_depth", false); }
    public void setWallpaperDepth(boolean v) { sp.edit().putBoolean("zp_wallpaper_depth", v).apply(); }
    /** The wash shifts very slightly with the time of day (on by default). */
    public boolean timeOfDayTint() { return sp.getBoolean("zp_time_tint", true); }
    public void setTimeOfDayTint(boolean v) { sp.edit().putBoolean("zp_time_tint", v).apply(); }
    /** ZeroPulse's own interface sounds (off by default: the TV may already play its own navigation sounds). */
    public boolean uiSounds() { return sp.getBoolean("zp_ui_sounds", false); }
    public void setUiSounds(boolean v) { sp.edit().putBoolean("zp_ui_sounds", v).apply(); }
    /** Interface sound volume, 0..100. */
    public int uiSoundVolume() { return clamp(sp.getInt("zp_ui_sound_volume", 35), 0, 100); }
    public void setUiSoundVolume(int v) { sp.edit().putInt("zp_ui_sound_volume", clamp(v, 0, 100)).apply(); }
    /** The last version whose What's New the viewer saw, or null. */
    public String whatsNewSeen() { return sp.getString("zp_whats_new_seen", null); }
    public void setWhatsNewSeen(String version) { sp.edit().putString("zp_whats_new_seen", version).apply(); }

    /** 0 low, 1 medium, 2 high. */
    public int focusIntensity() { return clamp(sp.getInt("zp_focus_intensity", 1), 0, 2); }
    public void setFocusIntensity(int v) { sp.edit().putInt("zp_focus_intensity", clamp(v, 0, 2)).apply(); }

    /** 0 subtle, 1 standard, 2 vivid. */
    public int accentIntensity() { return clamp(sp.getInt("zp_accent_intensity", 1), 0, 2); }
    public void setAccentIntensity(int v) { sp.edit().putInt("zp_accent_intensity", clamp(v, 0, 2)).apply(); }

    public static final float[] UI_SCALES = {0.9f, 1f, 1.1f};
    public static final String[] UI_SCALE_NAMES = {"Compact", "Default", "Large"};

    public int uiScaleIndex() { return clamp(sp.getInt("zp_ui_scale", 1), 0, 2); }
    public float uiScale() { return UI_SCALES[uiScaleIndex()]; }
    public void setUiScaleIndex(int v) { sp.edit().putInt("zp_ui_scale", clamp(v, 0, 2)).apply(); }

    /** Extra darkening over the wallpaper, percent 0-60. */
    public int dimPercent() { return clamp(sp.getInt("zp_dim", 10), 0, 60); }
    public void setDimPercent(int v) { sp.edit().putInt("zp_dim", clamp(v, 0, 60)).apply(); }

    // ---- wallpapers
    public int wallpaperInterval() {
        int v = sp.getInt("zp_wp_interval", WallpaperCatalog.DEFAULT_INTERVAL);
        return WallpaperCatalog.isValidInterval(v) ? v : WallpaperCatalog.DEFAULT_INTERVAL;
    }
    public void setWallpaperInterval(int v) { sp.edit().putInt("zp_wp_interval", v).apply(); }

    /** The collection rotated through. Never chosen: the Liquid Glass wallpapers while Liquid Glass is on, the nature photos otherwise. */
    public String wallpaperCollection() {
        return sp.getString("zp_wp_collection", liquidGlass() ? WallpaperCatalog.LIQUID : WallpaperCatalog.ALL);
    }
    public void setWallpaperCollection(String v) { sp.edit().putString("zp_wp_collection", v).apply(); }

    /**
     * The viewer's own framing of one wallpaper as {x, y, zoom}: the focal point (0..1 of the picture) and a zoom of 1 or more.
     * Never set: the catalog's own focal point for the picture and no zoom. Stored per picture, so it survives rotation and restarts.
     */
    public float[] wallpaperFrame(String file, float defaultX, float defaultY) {
        return tv.calmhome.launcher.core.WallpaperFraming.parseFrame(sp.getString("zp_wp_frame:" + file, null), defaultX, defaultY);
    }
    public void setWallpaperFrame(String file, float x, float y, float zoom) {
        sp.edit().putString("zp_wp_frame:" + file, tv.calmhome.launcher.core.WallpaperFraming.formatFrame(x, y, zoom)).apply();
    }
    public void resetWallpaperFrame(String file) { sp.edit().remove("zp_wp_frame:" + file).apply(); }
    public boolean hasWallpaperFrame(String file) { return sp.contains("zp_wp_frame:" + file); }

    /** File of the wallpaper the user picked (shown first; stays put when rotation is Static). */
    public String wallpaperPicked() { return sp.getString("zp_wp_picked", null); }
    public void setWallpaperPicked(String v) { sp.edit().putString("zp_wp_picked", v).putBoolean("zp_photo_backdrop", true).apply(); }

    // ---- optional modules (2.2): off until the user turns them on, also after an update from 2.1.
    // Written with commit(): ZeroPulse Web runs in its own process and reads this file when it starts.
    @Override public boolean enabled(Features.Module m) {
        return sp.getBoolean(featureKey(m), false);
    }

    public void setEnabled(Features.Module m, boolean v) {
        sp.edit().putBoolean(featureKey(m), v).commit();
    }

    @Override public boolean sectionEnabled(Features.Section s) {
        return sp.getBoolean("zp_stream_" + s.key, true);
    }

    public void setSectionEnabled(Features.Section s, boolean v) {
        sp.edit().putBoolean("zp_stream_" + s.key, v).commit();
    }

    private static String featureKey(Features.Module m) {
        return m == Features.Module.STREAMING ? "zp_feature_streaming" : "zp_feature_web";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
