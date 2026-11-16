package tv.calmhome.launcher;

import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

/**
 * Installed TV apps for the Robolectric suites: real PackageManager entries (a launcher or leanback entry point, a label,
 * an icon, optionally a TV banner), so Home's discovery, icons and launch intents run against what a TV really offers
 * instead of an empty fixture. Nothing here is a ZeroPulse tile: it only populates the (fake) device.
 */
final class MockTvApps {
    private MockTvApps() { }

    private static int nextBannerId = 0x7f0e0000, nextIconId = 0x7f0f0000;

    /** Every fixture picture carries this tag, so a screenshot of fixture data can never be taken for a real app's artwork. */
    static final String TAG = "MOCK";

    /** The packages of Calm Home's seven default favorites, with the labels a TV shows for them. */
    static final String[][] DEFAULTS = {
            {"com.netflix.ninja", "Netflix"},
            {"com.amazon.amazonvideo.livingroom", "Prime Video"},
            {"com.disney.disneyplus", "Disney+"},
            {"com.hulu.livingroomplus", "Hulu"},
            {"com.google.android.youtube.tv", "YouTube"},
            {"com.google.android.youtube.tvunplugged", "YouTube TV"},
            {"com.google.android.youtube.tvmusic", "YouTube Music"}};

    /** Installs an app. {@code banner} (may be null) is the TV banner; {@code leanback} gives it a TV launcher entry. */
    static void install(String pkg, String label, boolean leanback, Drawable banner) {
        install(pkg, label, leanback, banner, true);
    }

    /**
     * As above; {@code withIcon} false installs an app that declares no icon resource at all (the real Android case that
     * leaves only a generic placeholder), so the pipeline's monogram fallback can be exercised.
     */
    static void install(String pkg, String label, boolean leanback, Drawable banner, boolean withIcon) {
        ShadowPackageManager spm = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.name = pkg;
        ai.enabled = true;
        ai.nonLocalizedLabel = label;
        ComponentName main = new ComponentName(pkg, pkg + ".MainActivity");
        ActivityInfo activity = new ActivityInfo();
        activity.packageName = pkg;
        activity.name = main.getClassName();
        activity.applicationInfo = ai;
        activity.enabled = true;
        activity.exported = true;
        activity.nonLocalizedLabel = label;
        if (banner != null) {
            int id = nextBannerId++;
            ai.banner = id;
            activity.banner = id;
            spm.addDrawableResolution(pkg, id, banner);
        }
        int iconId = 0;
        if (withIcon) {
            iconId = nextIconId++;
            ai.icon = iconId;
            activity.icon = iconId;
            spm.addDrawableResolution(pkg, iconId, icon(pkg.hashCode()));
        }
        PackageInfo info = new PackageInfo();
        info.packageName = pkg;
        info.versionName = "1.0";
        info.applicationInfo = ai;
        spm.installPackage(info);
        ActivityInfo registered = spm.addActivityIfNotPresent(main);
        registered.nonLocalizedLabel = label;
        registered.enabled = true;
        registered.exported = true;
        registered.banner = activity.banner;
        registered.icon = iconId;
        registered.applicationInfo = ai;
        spm.addOrUpdateActivity(registered);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        spm.addIntentFilterForActivity(main, filter);
        if (withIcon) spm.setApplicationIcon(pkg, icon(pkg.hashCode()));
    }

    static void install(String pkg, String label, boolean leanback) {
        install(pkg, label, leanback, null);
    }

    static void uninstall(String pkg) {
        ShadowPackageManager spm = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        ComponentName main = new ComponentName(pkg, pkg + ".MainActivity");
        spm.clearIntentFilterForActivity(main);
        spm.removeActivity(main);
        spm.removePackage(pkg);
    }

    /** The broadcast Android sends when a package is installed or removed (Home rebuilds its row on it). */
    static void broadcast(String action, String pkg) {
        Context c = RuntimeEnvironment.getApplication();
        c.sendBroadcast(new Intent(action, Uri.fromParts("package", pkg, null)));
        ShadowLooper.idleMainLooper();
    }

    // ------------------------------------------------------------------ artwork

    /** A TV banner as the stores make them: a logo on one plain backdrop, 320 x 180. */
    static Drawable logoBanner(int backdrop, int ink) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(backdrop);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink);
        canvas.drawRoundRect(90, 62, 230, 118, 14, 14, p);
        tag(canvas, 320, 180);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** A TV banner that is a picture, not a logo on a plain backdrop. */
    static Drawable pictureBanner() {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 180; y++) {
            for (int x = 0; x < 320; x++) b.setPixel(x, y, Color.rgb((x * 5 + y) & 255, (y * 3 + x * 2) & 255, ((x ^ y) * 4) & 255));
        }
        tag(new Canvas(b), 320, 180);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    static Drawable icon(int seed) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(Color.rgb(60 + Math.abs(seed % 150), 80 + Math.abs((seed >> 3) % 120), 110 + Math.abs((seed >> 6) % 100)));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        canvas.drawCircle(48, 48, 22, p);
        tag(canvas, 96, 96);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** A neutral, obviously synthetic TV banner: a soft diagonal blend, one plain emblem and the MOCK tag. No brand look-alike. */
    static Drawable neutralBanner(int hue, int emblem) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        float[] a = {hue % 360, 0.55f, 0.42f}, c = {(hue + 40) % 360, 0.60f, 0.16f};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 320, 180, Color.HSVToColor(a), Color.HSVToColor(c), android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 320, 180, p);
        p.setShader(null);
        p.setColor(0xE6FFFFFF);
        switch (emblem % 4) {
            case 0: canvas.drawCircle(160, 90, 34, p); break;
            case 1: canvas.drawRoundRect(124, 56, 196, 124, 12, 12, p); break;
            case 2: {
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(160, 52); tri.lineTo(198, 122); tri.lineTo(122, 122); tri.close();
                canvas.drawPath(tri, p);
                break;
            }
            default: canvas.drawRect(118, 80, 202, 100, p); canvas.drawRect(150, 48, 170, 132, p);
        }
        tag(canvas, 320, 180);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** A neutral, obviously synthetic square icon (an opaque square, like a plain launcher icon) with the MOCK tag. */
    static Drawable neutralIcon(int hue, int emblem) {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        float[] top = {hue % 360, 0.55f, 0.55f}, bottom = {(hue + 30) % 360, 0.65f, 0.25f};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, 256, Color.HSVToColor(top), Color.HSVToColor(bottom), android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 256, 256, p);
        p.setShader(null);
        p.setColor(0xE6FFFFFF);
        if (emblem % 2 == 0) canvas.drawCircle(128, 124, 56, p); else canvas.drawRoundRect(72, 68, 184, 180, 22, 22, p);
        tag(canvas, 256, 256);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** The small MOCK tag in a picture's lower-right corner. */
    private static void tag(Canvas canvas, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(Math.max(8, h * 0.075f));
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xB3000000);
        canvas.drawText(TAG, w - h * 0.04f + 1, h - h * 0.05f + 1, p);
        p.setColor(0xF2FFFFFF);
        canvas.drawText(TAG, w - h * 0.04f, h - h * 0.05f, p);
    }
}
