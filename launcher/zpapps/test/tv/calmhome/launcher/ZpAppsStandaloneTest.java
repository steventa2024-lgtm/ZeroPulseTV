package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.toolbox.CodeKeypadModalPanel;
import tv.calmhome.launcher.toolbox.ToolboxEngine;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class ZpAppsStandaloneTest {

    private static <T extends View> List<T> allViews(View root, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) out.addAll(allViews(g.getChildAt(i), type));
        }
        return out;
    }

    private static boolean hasVisibleText(MainActivity a, String prefix) {
        for (TextView tv : allViews(a.getWindow().getDecorView(), TextView.class)) {
            if (tv.getVisibility() == View.VISIBLE && tv.getText() != null && tv.getText().toString().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void manifestAndBuildConfigIdentifyStandaloneZpAppsWithoutHomeCategory() {
        assertEquals("tv.zeropulse.zpapps", BuildConfig.APPLICATION_ID);
        assertEquals(1001, BuildConfig.VERSION_CODE);
        assertEquals("1.0.0", BuildConfig.VERSION_NAME);
        assertTrue(BuildConfig.STANDALONE_ZPAPPS);

        Application app = RuntimeEnvironment.getApplication();
        PackageManager pm = app.getPackageManager();

        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
        List<ResolveInfo> launcherMatches = pm.queryIntentActivities(launcher, 0);
        assertFalse("ZPAPPS must advertise standard LAUNCHER entry", launcherMatches.isEmpty());

        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(app.getPackageName());
        List<ResolveInfo> tvMatches = pm.queryIntentActivities(leanback, 0);
        assertFalse("ZPAPPS must advertise LEANBACK_LAUNCHER entry", tvMatches.isEmpty());

        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.getPackageName());
        List<ResolveInfo> homeMatches = pm.queryIntentActivities(home, 0);
        assertTrue("Standalone ZPAPPS must NEVER register CATEGORY_HOME", homeMatches.isEmpty());
    }

    @Test
    public void standaloneLaunchesIntoZpAppsWithUnscaledGlassAndCleanBackExit() {
        Application app = RuntimeEnvironment.getApplication();
        new Prefs(app).setLiquidGlass(true);
        ToolboxEngine.get(app).setOnboardingCompleted(true);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class, new Intent(app, MainActivity.class)).setup().get();
        View decor = activity.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);

        assertTrue(hasVisibleText(activity, "ZPAPPS Toolbox"));
        assertTrue(hasVisibleText(activity, "Discover"));
        assertTrue(hasVisibleText(activity, "All 20 Apps"));
        assertTrue(hasVisibleText(activity, "+ Code Keypad"));
        assertTrue(hasVisibleText(activity, "Purge APK Cache"));

        for (GraphiteGlassSurface s : allViews(decor, GraphiteGlassSurface.class)) {
            assertEquals("glass primitives must never scaleX", 1f, s.getScaleX(), 0.0001f);
            assertEquals("glass primitives must never scaleY", 1f, s.getScaleY(), 0.0001f);
        }

        activity.open(new CodeKeypadModalPanel(activity, code -> { }));
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
        assertTrue(hasVisibleText(activity, "Downloader Shortcode"));

        activity.onBackPressed();
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
        assertFalse("first Back closes modal only", activity.isFinishing());
        assertFalse(hasVisibleText(activity, "Downloader Shortcode"));
        assertTrue(hasVisibleText(activity, "ZPAPPS Toolbox"));

        activity.onBackPressed();
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
        assertTrue("Back on root ZPAPPS exits cleanly", activity.isFinishing());
    }
}
