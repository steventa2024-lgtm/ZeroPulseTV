package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowToast;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.streaming.StreamingActivity;
import tv.calmhome.launcher.web.WebActivity;

/** Switched-off modules have no entry point and refuse to open; switched-on ones appear on Home and open. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34})
public class FeatureGateTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private static boolean has(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return !out.isEmpty();
    }

    private static View find(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return out.isEmpty() ? null : out.get(0);
    }

    @Test public void freshInstallHomeHasNoModuleTiles() {
        MainActivity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = home.getWindow().getDecorView();
        assertFalse(has(root, Features.Module.STREAMING.title));
        assertFalse(has(root, Features.Module.WEB.title));
        assertNull(shadowOf(home).getNextStartedActivity());
    }

    @Test public void enabledModulesAppearOnHomeAndOpen() {
        Prefs p = new Prefs(c);
        p.setEnabled(Features.Module.STREAMING, true);
        p.setEnabled(Features.Module.WEB, true);
        MainActivity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = home.getWindow().getDecorView();
        View streaming = find(root, Features.Module.STREAMING.title);
        View web = find(root, Features.Module.WEB.title);
        assertNotNull(streaming);
        assertNotNull(web);
        streaming.performClick();
        assertEquals(StreamingActivity.class.getName(), shadowOf(home).getNextStartedActivity().getComponent().getClassName());
        web.performClick();
        assertEquals(WebActivity.class.getName(), shadowOf(home).getNextStartedActivity().getComponent().getClassName());
    }

    @Test public void onlyTheEnabledModuleAppears() {
        new Prefs(c).setEnabled(Features.Module.WEB, true);
        View root = Robolectric.buildActivity(MainActivity.class).setup().get().getWindow().getDecorView();
        assertTrue(has(root, Features.Module.WEB.title));
        assertFalse(has(root, Features.Module.STREAMING.title));
    }

    @Test public void directOpenIsRefusedWhileOff() {
        MainActivity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        home.openModule(Features.Module.STREAMING);
        home.openModule(Features.Module.WEB);
        assertNull(shadowOf(home).getNextStartedActivity());
        assertTrue(ShadowToast.getTextOfLatestToast().contains("turned off"));

        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "web");
        MainActivity viaExtra = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        assertNull(shadowOf(viaExtra).getNextStartedActivity());
    }

    @Test public void moduleScreensCloseThemselvesWhileOff() {
        StreamingActivity s = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        assertTrue(s.isFinishing());
        assertTrue(ShadowToast.getTextOfLatestToast().contains("ZeroPulse Streaming is turned off"));
        WebActivity w = Robolectric.buildActivity(WebActivity.class).setup().get();
        assertTrue(w.isFinishing());
        assertTrue(ShadowToast.getTextOfLatestToast().contains("ZeroPulse Web is turned off"));
    }

    @Test public void streamingClosesWhenSwitchedOffWhileAway() {
        Prefs p = new Prefs(c);
        p.setEnabled(Features.Module.STREAMING, true);
        org.robolectric.android.controller.ActivityController<StreamingActivity> ctl =
                Robolectric.buildActivity(StreamingActivity.class).setup();
        assertFalse(ctl.get().isFinishing());
        ctl.pause().stop();
        p.setEnabled(Features.Module.STREAMING, false);
        ctl.restart().start().resume();
        assertTrue(ctl.get().isFinishing());
    }

    @Test public void streamingListsOnlyEnabledSections() {
        Prefs p = new Prefs(c);
        p.setEnabled(Features.Module.STREAMING, true);
        p.setSectionEnabled(Features.Section.MOVIES, false);
        StreamingActivity s = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        View root = s.getWindow().getDecorView();
        assertFalse(s.isFinishing());
        assertTrue(has(root, "Live TV"));
        assertTrue(has(root, "TV Shows"));
        assertTrue(has(root, "Sports"));
        assertTrue(has(root, "Sources"));
        assertFalse(has(root, "Movies"));
    }
}
