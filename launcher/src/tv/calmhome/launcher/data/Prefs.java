package tv.calmhome.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.WallpaperCatalog;

/**
 * All persisted launcher state. Same file ("calmhome") and same "dock" key as Calm Home 1.0, so the
 * saved dock carries over the update and survives a rollback. New ZeroPulse settings use "zp_" keys,
 * which Calm Home ignores. Nothing here ever clears or rewrites "dock" unless the user edits the dock.
 */
public final class Prefs {
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

    // ---- appearance
    public boolean showGreeting() { return sp.getBoolean("zp_show_greeting", true); }
    public void setShowGreeting(boolean v) { sp.edit().putBoolean("zp_show_greeting", v).apply(); }

    public boolean showClock() { return sp.getBoolean("zp_show_clock", true); }
    public void setShowClock(boolean v) { sp.edit().putBoolean("zp_show_clock", v).apply(); }

    public boolean reducedMotion() { return sp.getBoolean("zp_reduced_motion", false); }
    public void setReducedMotion(boolean v) { sp.edit().putBoolean("zp_reduced_motion", v).apply(); }

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

    public String wallpaperCollection() { return sp.getString("zp_wp_collection", WallpaperCatalog.ALL); }
    public void setWallpaperCollection(String v) { sp.edit().putString("zp_wp_collection", v).apply(); }

    /** File of the wallpaper the user picked (shown first; stays put when rotation is Static). */
    public String wallpaperPicked() { return sp.getString("zp_wp_picked", null); }
    public void setWallpaperPicked(String v) { sp.edit().putString("zp_wp_picked", v).apply(); }

    // ---- assistant
    public boolean assistantEnabled() { return sp.getBoolean("zp_assistant", true); }
    public void setAssistantEnabled(boolean v) { sp.edit().putBoolean("zp_assistant", v).apply(); }

    public boolean reactorAnimation() { return sp.getBoolean("zp_reactor_anim", true); }
    public void setReactorAnimation(boolean v) { sp.edit().putBoolean("zp_reactor_anim", v).apply(); }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
