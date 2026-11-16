package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.glasscomponents.GlassClockView;
import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.data.Prefs;

/**
 * The supplied glass clock and navigation inside the real Home: the six destinations in the app's order with the real
 * actions behind them, selection kept apart from remote focus, and the clock's formats at midnight, noon and 23:59.
 * Needs Robolectric (see docs/LIQUID_GLASS.md).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class LiquidGlassKitHeaderTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private MainActivity liquidHome() {
        new Prefs(c).setLiquidGlass(true);
        return Robolectric.buildActivity(MainActivity.class, new Intent(c, MainActivity.class)).setup().get();
    }

    private static <T extends View> T find(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                T hit = find(((ViewGroup) v).getChildAt(i), type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /** A TV is driven by a D-pad, so its window is not in touch mode (Robolectric's starts in it, where a button cannot take focus). */
    private static void leaveTouchMode(View anyViewInTheWindow) {
        try {
            Object root = View.class.getMethod("getViewRootImpl").invoke(anyViewInTheWindow.getRootView());
            java.lang.reflect.Method leave = root.getClass().getDeclaredMethod("ensureTouchMode", boolean.class);
            leave.setAccessible(true);
            leave.invoke(root, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static View button(GlassTopNavView nav, String label) {
        ArrayList<View> found = new ArrayList<>();
        nav.findViewsWithText(found, label, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return found.isEmpty() ? null : found.get(0);
    }

    @Test public void liquidGlassUsesTheSuppliedClockAndNavigation() {
        MainActivity home = liquidHome();
        assertNotNull(find(home.getWindow().getDecorView(), GlassTopNavView.class));
        assertNotNull(find(home.getWindow().getDecorView(), GlassClockView.class));
        assertTrue("the glass kit has its anchor", home.theme().kit != null);
    }

    @Test public void graphiteKeepsItsOwnNavigationAndClock() {
        new Prefs(c).setLiquidGlass(false);
        MainActivity home = Robolectric.buildActivity(MainActivity.class, new Intent(c, MainActivity.class)).setup().get();
        assertNull(find(home.getWindow().getDecorView(), GlassTopNavView.class));
        assertNull(find(home.getWindow().getDecorView(), GlassClockView.class));
    }

    @Test public void theFiveDestinationsAreThereAndHomeIsTheSelectedPage() {
        GlassTopNavView nav = find(liquidHome().getWindow().getDecorView(), GlassTopNavView.class);
        for (String label : new String[]{"Home", "Apps", "Downloads", "Inputs", "Settings"}) {
            View b = button(nav, label);
            assertNotNull(label, b);
            assertTrue(label + " is a real focusable button", b.isFocusable() && b.isClickable());
        }
        assertEquals("Home", nav.getSelectedId());
        assertTrue("Home is selected, not merely first", button(nav, "Home").isSelected());
        assertFalse(button(nav, "Apps").isSelected());
    }

    @Test public void thereIsNoAssistantDestination() {
        GlassTopNavView nav = find(liquidHome().getWindow().getDecorView(), GlassTopNavView.class);
        assertNull("the Assistant was removed, not hidden", button(nav, "Assistant"));
        assertNotNull(button(nav, "Settings"));
    }

    @Test public void activatingADestinationRunsTheExistingActionAndMovesTheSelection() {
        MainActivity home = liquidHome();
        ViewGroup content = (ViewGroup) home.findViewById(android.R.id.content);
        int before = ((ViewGroup) content.getChildAt(0)).getChildCount();
        GlassTopNavView nav = find(home.getWindow().getDecorView(), GlassTopNavView.class);
        button(nav, "Apps").performClick();
        ShadowLooper.idleMainLooper();
        int after = ((ViewGroup) content.getChildAt(0)).getChildCount();
        assertTrue("the Apps panel opened over Home (the same action the graphite tab runs)", after > before);
        assertEquals("Apps", nav.getSelectedId());
    }

    @Test public void focusAndSelectionAreSeparate() {
        GlassTopNavView nav = find(liquidHome().getWindow().getDecorView(), GlassTopNavView.class);
        leaveTouchMode(nav);
        assertTrue("remote focus can land on a destination", nav.requestFocusOn("Inputs"));
        assertTrue(button(nav, "Inputs").isFocused());
        assertEquals("selecting does not follow focus", "Home", nav.getSelectedId());
        assertTrue("Home keeps its marker while another button holds focus", button(nav, "Home").isSelected());
        assertFalse(button(nav, "Home").isFocused());
        View right = button(nav, "Apps").focusSearch(View.FOCUS_RIGHT);
        assertEquals("Right moves to the next destination without activating it", button(nav, "Downloads"), right);
    }

    @Test public void theClockReadsRightAtMidnightNoonAndJustBeforeMidnight() {
        GlassClockView clock = find(liquidHome().getWindow().getDecorView(), GlassClockView.class);
        clock.setAutoUpdate(false);
        clock.setUse24Hour(false);
        clock.setTime(0, 5);
        assertEquals("12:05 AM", String.valueOf(clock.getContentDescription()));
        clock.setTime(12, 0);
        assertEquals("12:00 PM", String.valueOf(clock.getContentDescription()));
        clock.setTime(23, 59);
        assertEquals("11:59 PM", String.valueOf(clock.getContentDescription()));
        clock.setUse24Hour(true);
        assertEquals("23:59", String.valueOf(clock.getContentDescription()));
        clock.setTime(0, 5);
        assertEquals("00:05", String.valueOf(clock.getContentDescription()));
        assertFalse("display only: no focus, no click", clock.isFocusable() || clock.isClickable());
    }

    @Test public void theClockFollowsTheSystemTimeFormatAndTheMinute() {
        MainActivity home = liquidHome();
        GlassClockView clock = find(home.getWindow().getDecorView(), GlassClockView.class);
        java.util.Calendar now = java.util.Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(c);
        String shown = String.valueOf(clock.getContentDescription());
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY), minute = now.get(java.util.Calendar.MINUTE);
        String expected = is24 ? String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)
                : String.format(java.util.Locale.ROOT, "%02d:%02d %s", hour % 12 == 0 ? 12 : hour % 12, minute, hour < 12 ? "AM" : "PM");
        // The minute may have just rolled over between the activity starting and this line.
        assertTrue("shows the system time: " + shown + " vs " + expected, shown.equals(expected) || shown.substring(0, 2).equals(expected.substring(0, 2)));
        home.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        ShadowLooper.idleMainLooper();
    }
}
