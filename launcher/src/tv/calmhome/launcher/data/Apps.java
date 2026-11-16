package tv.calmhome.launcher.data;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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

    public void invalidate(String pkg) { labelCache.remove(pkg); }

    public int category(String pkg) {
        try { return pm.getApplicationInfo(pkg, 0).category; }
        catch (PackageManager.NameNotFoundException e) { return ApplicationInfo.CATEGORY_UNDEFINED; }
    }

    /**
     * True when the viewer may remove {@code pkg} themselves: an installed app that is not part of the system image and is not this
     * launcher. System apps and updated system apps are never offered for removal.
     */
    public boolean userRemovable(String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        try {
            int flags = pm.getApplicationInfo(pkg, 0).flags;
            return (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
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

    /** Packages with a TV (leanback) launcher entry: the apps made for a TV, preferred when Home has to suggest some. */
    public java.util.Set<String> leanbackPackages() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (!ri.activityInfo.packageName.equals(c.getPackageName())) out.add(ri.activityInfo.packageName);
        }
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

    /** A piece of an app's own artwork and where it came from (for diagnostics and tests). */
    public static final class Source {
        public final Drawable drawable;
        /** "activity-banner", "application-banner", "activity-icon" or "application-icon". */
        public final String origin;

        Source(Drawable drawable, String origin) {
            this.drawable = drawable;
            this.origin = origin;
        }
    }

    /** The app's TV launch entry (else its regular launcher entry), which is where its own banner and icon are declared. */
    public ComponentName entryPoint(String pkg) {
        Intent i = launchIntent(pkg);
        return i == null ? null : i.getComponent();
    }

    /** The app's own TV banner: its launch activity's, else the application's. Null if the app declares none. */
    public Source bannerSource(String pkg) {
        try {
            ComponentName cn = entryPoint(pkg);
            if (cn != null) {
                Drawable d = pm.getActivityBanner(cn);
                if (d != null) return new Source(d, "activity-banner");
            }
            Drawable d = pm.getApplicationBanner(pkg);
            return d == null ? null : new Source(d, "application-banner");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The app's own icon: its launch activity's, else the application's, at the highest density its package offers. Null
     * when the app declares no icon at all (Android would then hand out its generic green placeholder, which is not the
     * app's artwork and is never shown as if it were).
     */
    public Source iconSource(String pkg) {
        try {
            ComponentName cn = entryPoint(pkg);
            ActivityInfo ai = cn == null ? null : pm.getActivityInfo(cn, 0);
            ApplicationInfo app = ai != null && ai.applicationInfo != null ? ai.applicationInfo : pm.getApplicationInfo(pkg, 0);
            int activityIcon = ai == null ? 0 : ai.icon, applicationIcon = app.icon;
            if (activityIcon == 0 && applicationIcon == 0) return null;
            boolean fromActivity = activityIcon != 0;
            Drawable sharp = sharpIcon(app, fromActivity ? activityIcon : applicationIcon);
            if (sharp != null) return new Source(sharp, fromActivity ? "activity-icon" : "application-icon");
            Drawable d = fromActivity ? pm.getActivityIcon(cn) : pm.getApplicationIcon(app);
            return d == null ? null : new Source(d, fromActivity ? "activity-icon" : "application-icon");
        } catch (Exception e) {
            return null;
        }
    }

    /** The icon resource at extra-extra-extra-high density, so it is scaled down to a tile (sharp) rather than up (soft). */
    private Drawable sharpIcon(ApplicationInfo app, int resId) {
        try {
            android.content.res.Resources res = pm.getResourcesForApplication(app);
            return res.getDrawableForDensity(resId, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (Exception e) {
            return null;
        }
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
        if (i == null || !start(i)) return false;
        new Prefs(c).recordOpened(pkg);
        return true;
    }

    public boolean start(Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
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

    /** Android TV's placeholders for apps a TV doesn't ship (documents, media, ...): they close at once. */
    static final String TV_STUBS = "com.android.tv.frameworkpackagestubs";

    /**
     * True when a real app handles {@code i}: not just Android TV's stub, which resolves but closes immediately
     * (seen on the TCL for the document picker). Used so that no button opens nothing.
     */
    public static boolean hasRealHandler(Context c, Intent i) {
        android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && !TV_STUBS.equals(r.activityInfo.packageName);
    }
}
