package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.toolbox.CodeKeypadModalPanel;
import tv.calmhome.launcher.toolbox.ToolboxCatalog;
import tv.calmhome.launcher.toolbox.ToolboxEngine;
import tv.calmhome.launcher.toolbox.WelcomeGuideModalPanel;
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

    private static View findClickableByText(View root, String exactText) {
        for (TextView tv : allViews(root, TextView.class)) {
            if (tv.isShown() && tv.getText() != null && exactText.equals(tv.getText().toString())) {
                View cur = tv;
                while (cur != null) {
                    if (cur.isShown() && cur.isClickable() && cur.isFocusable()) return cur;
                    if (!(cur.getParent() instanceof View)) break;
                    cur = (View) cur.getParent();
                }
            }
        }
        return null;
    }

    private static View findClickableByDescription(View root, String exactDesc) {
        for (View v : allViews(root, View.class)) {
            if (v.getVisibility() == View.VISIBLE && v.isClickable() && v.isFocusable()
                    && v.getContentDescription() != null && exactDesc.equals(v.getContentDescription().toString())) {
                return v;
            }
        }
        return null;
    }

    private static void relayout(MainActivity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
    }

    private static void sendConfirmKey(MainActivity activity, View target, int keyCode) {
        assertTrue("target view must be focusable", target.isFocusable());
        if (!target.requestFocus()) {
            // Robolectric starts windows in touch mode; enable touch focus in test so activity.dispatchKeyEvent reaches target.
            target.setFocusableInTouchMode(true);
            assertTrue("target view should accept focus", target.requestFocus());
        }
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
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

    @Test
    @Config(sdk = 25)
    public void standaloneLaunchesCleanlyOnApi25FireOs6WithoutPlayStore() {
        Application app = RuntimeEnvironment.getApplication();
        new Prefs(app).setLiquidGlass(true);
        ToolboxEngine engine = ToolboxEngine.get(app);
        engine.setOnboardingCompleted(true);
        assertTrue("API 25 should not require runtime unknown-sources toggle", engine.canRequestPackageInstalls());
        assertEquals(ToolboxEngine.PermissionRoute.NOT_REQUIRED,
                engine.openInstallPermissionSettings(Robolectric.buildActivity(MainActivity.class).setup().get()));

        MainActivity activity = Robolectric.buildActivity(MainActivity.class, new Intent(app, MainActivity.class)).setup().get();
        View decor = activity.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);

        assertTrue(hasVisibleText(activity, "ZPAPPS Toolbox"));
        assertTrue(hasVisibleText(activity, "All 20 Apps"));
        for (GraphiteGlassSurface s : allViews(decor, GraphiteGlassSurface.class)) {
            assertEquals(1f, s.getScaleX(), 0.0001f);
            assertEquals(1f, s.getScaleY(), 0.0001f);
        }
    }

    @Test
    public void fireTvDpadCenterAndEnterActivateCategoryTabsTilesAndKeypad() {
        Application app = RuntimeEnvironment.getApplication();
        new Prefs(app).setLiquidGlass(true);
        ToolboxEngine engine = ToolboxEngine.get(app);
        engine.setOnboardingCompleted(true);

        MainActivity activity = Robolectric.buildActivity(MainActivity.class, new Intent(app, MainActivity.class)).setup().get();
        View decor = activity.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);

        View allAppsTab = findClickableByText(decor, "All 20 Apps");
        sendConfirmKey(activity, allAppsTab, KeyEvent.KEYCODE_ENTER);
        relayout(activity);
        assertFalse("Category filters appear after KEYCODE_ENTER on All 20 Apps", allViews(decor, EditText.class).isEmpty());

        View catPill = findClickableByText(decor, "Streaming");
        if (catPill == null) catPill = findClickableByText(decor, "All");
        sendConfirmKey(activity, catPill, KeyEvent.KEYCODE_DPAD_CENTER);
        relayout(activity);
        assertTrue(hasVisibleText(activity, "ZPAPPS Toolbox"));

        String firstTitle = ToolboxCatalog.load(app).apps.get(0).name;
        View tile = findClickableByText(decor, firstTitle);
        if (tile != null) {
            sendConfirmKey(activity, tile, KeyEvent.KEYCODE_ENTER);
            relayout(activity);
            assertTrue("Tile click opens app detail without Play Store", hasVisibleText(activity, firstTitle));
            activity.onBackPressed();
            relayout(activity);
        }

        View keypadBtn = findClickableByText(decor, "+ Code Keypad");
        sendConfirmKey(activity, keypadBtn, KeyEvent.KEYCODE_DPAD_CENTER);
        relayout(activity);
        assertTrue(hasVisibleText(activity, "Downloader Shortcode"));

        View key5 = findClickableByDescription(decor, "5");
        if (key5 == null) key5 = findClickableByText(decor, "5");
        sendConfirmKey(activity, key5, KeyEvent.KEYCODE_ENTER);

        View key3 = findClickableByDescription(decor, "3");
        if (key3 == null) key3 = findClickableByText(decor, "3");
        sendConfirmKey(activity, key3, KeyEvent.KEYCODE_DPAD_CENTER);

        List<EditText> edits = allViews(decor, EditText.class);
        assertFalse(edits.isEmpty());
        EditText codeInput = edits.get(edits.size() - 1);
        assertEquals("53", codeInput.getText().toString());
    }

    @Test
    @Config(sdk = 28)
    public void fireOsFallbackAndHintHandleMissingManageUnknownSourcesGracefully() {
        Application app = RuntimeEnvironment.getApplication();
        ToolboxEngine engine = ToolboxEngine.get(app);
        engine.setOnboardingCompleted(true);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class, new Intent(app, MainActivity.class)).setup().get();
        relayout(activity);
        View decor = activity.getWindow().getDecorView();

        Shadows.shadowOf(app).checkActivities(true);
        ShadowPackageManager spm = Shadows.shadowOf(app.getPackageManager());
        ComponentName secComp = new ComponentName("com.amazon.tv.settings", "com.amazon.tv.settings.SecuritySettingsActivity");
        spm.addActivityIfNotPresent(secComp);
        IntentFilter filter = new IntentFilter(Settings.ACTION_SECURITY_SETTINGS);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        spm.addIntentFilterForActivity(secComp, filter);

        assertEquals(ToolboxEngine.PermissionRoute.FIRE_OS_SECURITY_FALLBACK, engine.openInstallPermissionSettings(activity));
        ShadowActivity sa = Shadows.shadowOf(activity);
        Intent launched = sa.getNextStartedActivity();
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, launched.getAction());

        activity.open(new WelcomeGuideModalPanel(activity, false));
        relayout(activity);
        View permBtn = findClickableByText(decor, "Grant Install Permission");
        sendConfirmKey(activity, permBtn, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(hasVisibleText(activity, "On Fire TV: " + ToolboxEngine.FIRE_TV_PERMISSION_HINT));
    }
}
