package tv.calmhome.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.Prefs;

/** Settings → Features, driven the way the remote does it: focus on the switch, then OK. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(qualifiers = "w960dp-h540dp-land-xhdpi")   // the TCL: 1920x1080 at xhdpi
public class SettingsFeaturesTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    private static View find(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return out.isEmpty() ? null : out.get(0);
    }

    /** Found on the TCL: switching a module on rebuilt the rows under the wrong category. */
    @Test public void switchingAModuleKeepsTheFeaturesCategory() {
        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "settings:features");
        MainActivity home = Robolectric.buildActivity(MainActivity.class, open).setup().get();
        View root = home.getWindow().getDecorView();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        View toggle = find(root, "ZeroPulse Streaming, off");
        StringBuilder blockers = new StringBuilder();
        for (android.view.ViewParent p = toggle.getParent(); p instanceof ViewGroup; p = p.getParent()) {
            if (((ViewGroup) p).getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) blockers.append(p).append(' ');
        }
        assertTrue("blocked by " + blockers, toggle.requestFocus());
        toggle.performClick();
        String text = allText(root);
        assertTrue(new Prefs(c).enabled(Features.Module.STREAMING));
        assertTrue(text, text.contains("Live TV"));
        assertTrue(text.contains("Open ZeroPulse Streaming"));
        assertFalse("no Appearance rows mixed in", text.contains("Show clock"));
        assertTrue(find(root, "ZeroPulse Streaming, on").isFocused());

        find(root, "ZeroPulse Streaming, on").performClick();
        text = allText(root);
        assertFalse(new Prefs(c).enabled(Features.Module.STREAMING));
        assertFalse(text.contains("Show clock"));
        assertFalse(text.contains("Open ZeroPulse Streaming"));
    }
}
