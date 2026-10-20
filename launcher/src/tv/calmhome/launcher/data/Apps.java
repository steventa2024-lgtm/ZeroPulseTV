package tv.calmhome.launcher.data;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installed, launchable apps. Uses the TV (LEANBACK_LAUNCHER) and regular (LAUNCHER) entry points that
 * the manifest's <queries> makes visible; no broad package permissions. Only apps actually installed
 * and enabled are ever offered.
 */
public final class Apps {
    private final Context c;
    private final PackageManager pm;
    private final Map<String, String> labelCache = new HashMap<>();

    public Apps(Context c) {
        this.c = c;
        this.pm = c.getPackageManager();
    }

    public Intent launchIntent(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    /** Installed, enabled, and has a launch entry point. */
    public boolean usable(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return ai.enabled && launchIntent(pkg) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean installed(String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** All launchable apps except this launcher, sorted by name. */
    public List<String> all() {
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = ri.activityInfo.packageName;
                if (!p.equals(c.getPackageName()) && !found.containsKey(p)) found.put(p, ri);
            }
        }
        List<String> out = new ArrayList<>(found.keySet());
        out.sort((a, b) -> label(a).compareToIgnoreCase(label(b)));
        return out;
    }

    public String label(String pkg) {
        String l = labelCache.get(pkg);
        if (l != null) return l;
        try {
            l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            l = pkg;
        }
        labelCache.put(pkg, l);
        return l;
    }

    /** The app's 16:9 TV banner if it has one. */
    public Drawable banner(String pkg) {
        try {
            Intent i = launchIntent(pkg);
            if (i != null && i.getComponent() != null) {
                Drawable d = pm.getActivityBanner(i.getComponent());
                if (d != null) return d;
            }
            return pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    public Drawable icon(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /** Starts the app. Returns false (and starts nothing) if it can't be launched. */
    public boolean launch(String pkg) {
        Intent i = launchIntent(pkg);
        return i != null && start(i);
    }

    public boolean start(Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    public Intent appInfoIntent(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
    }

    /** True if some activity on the TV handles the intent (used to hide shortcuts the TV doesn't support). */
    public boolean resolves(Intent i) {
        return pm.resolveActivity(i, 0) != null;
    }
}
