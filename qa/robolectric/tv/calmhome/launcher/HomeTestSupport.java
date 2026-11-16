package tv.calmhome.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;
import tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction;

/** Shared helpers for the Home suites: starting Home, walking its views, the remote's keys. */
final class HomeTestSupport {
    private HomeTestSupport() { }

    static android.app.Application app() {
        return RuntimeEnvironment.getApplication();
    }

    /** The preference file Calm Home 1.0 and ZeroPulse share (key "dock" is the favorites). */
    static SharedPreferences raw() {
        return app().getSharedPreferences("calmhome", Context.MODE_PRIVATE);
    }

    static MainActivity startHome(boolean liquid) {
        new Prefs(app()).setLiquidGlass(liquid);
        new Prefs(app()).setLiquidStarting(false);
        org.robolectric.android.controller.ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class, new Intent(app(), MainActivity.class)).setup();
        MainActivity home = controller.get();
        leaveTouchMode(home.getWindow().getDecorView());
        controller.pause().resume();            // a TV window is never in touch mode, so Home's resume asks for focus on the first tile
        layoutTv(home);                         // (a focus request made before a layout waits for the next one, as on a device)
        ShadowLooper.idleMainLooper();
        return home;
    }

    /** A TV is driven by a D-pad, so its window is not in touch mode (Robolectric's starts in it, where focus cannot move). */
    static void leaveTouchMode(View anyViewInTheWindow) {
        try {
            Object root = View.class.getMethod("getViewRootImpl").invoke(anyViewInTheWindow.getRootView());
            java.lang.reflect.Method leave = root.getClass().getDeclaredMethod("ensureTouchMode", boolean.class);
            leave.setAccessible(true);
            leave.invoke(root, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void collect(View v, List<View> out, Class<?> type) {
        if (type.isInstance(v)) out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out, type);
    }

    static <T extends View> List<T> all(Activity home, Class<T> type) {
        List<View> found = new ArrayList<>();
        collect(home.getWindow().getDecorView(), found, type);
        List<T> typed = new ArrayList<>();
        for (View v : found) typed.add(type.cast(v));
        return typed;
    }

    /** Home's glass app tiles, left to right (an empty-state notice is a tile with no package tag). */
    static List<ZeroPulseGlassAppTile> glassTiles(Activity home) {
        return all(home, ZeroPulseGlassAppTile.class);
    }

    static List<ZeroPulseGlassQuickAction> glassActions(Activity home) {
        return all(home, ZeroPulseGlassQuickAction.class);
    }

    static List<GraphiteGlassSurface> surfaces(Activity home) {
        return all(home, GraphiteGlassSurface.class);
    }

    /** The package tags of the tiles on Home, left to right, in either look (a tag on a focusable, clickable card). */
    static List<String> favoritePackages(Activity home) {
        List<String> out = new ArrayList<>();
        for (FrameLayout card : all(home, FrameLayout.class)) {
            if (card.getTag() instanceof String && card.isFocusable() && card.isClickable()) out.add((String) card.getTag());
        }
        return out;
    }

    /**
     * Lets the wallpaper stage, the glass engine's worker and the renderer's worker finish. The glass starts its work from a
     * pre-draw callback; Robolectric's looper may not run a real traversal, so the callback is fired the way a frame would.
     */
    static void pump(Activity home, int rounds) throws InterruptedException {
        View decor = home.getWindow().getDecorView();
        for (int i = 0; i < rounds; i++) {
            ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            firePreDraw(decor);
            Thread.sleep(40);
        }
        ShadowLooper.idleMainLooper();
    }

    /** Pumps until {@code done} holds or {@code maxRounds} frames have passed; returns whether it held. */
    static boolean pumpUntil(Activity home, java.util.concurrent.Callable<Boolean> done, int maxRounds) throws Exception {
        for (int i = 0; i < maxRounds; i++) {
            if (done.call()) return true;
            pump(home, 1);
        }
        return done.call();
    }

    static void firePreDraw(View decor) {
        try {
            android.view.ViewTreeObserver.class.getMethod("dispatchOnPreDraw").invoke(decor.getViewTreeObserver());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Lays the window out at the TV's 1920 x 1080 again (after a margin changed, say). */
    static void layoutTv(Activity home) {
        View decor = home.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
    }

    /** A view drawn into a bitmap, as the user would see it. */
    static android.graphics.Bitmap draw(View v) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(Math.max(1, v.getWidth()), Math.max(1, v.getHeight()), android.graphics.Bitmap.Config.ARGB_8888);
        v.draw(new android.graphics.Canvas(b));
        return b;
    }

    /** Mean RGB brightness (0..255) of the opaque pixels of a bitmap, and how many there were. */
    static double meanBrightness(android.graphics.Bitmap b) {
        long sum = 0, n = 0;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                int p = b.getPixel(x, y);
                if ((p >>> 24) < 200) continue;
                sum += ((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255);
                n += 3;
            }
        }
        return n == 0 ? 0 : sum / (double) n;
    }

    /** Mean absolute channel difference between two same-size bitmaps, over pixels that are opaque in both. */
    static double meanDifference(android.graphics.Bitmap a, android.graphics.Bitmap b) {
        long sum = 0, n = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                if ((p >>> 24) < 200 || (q >>> 24) < 200) continue;
                sum += Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
                n += 3;
            }
        }
        return n == 0 ? 0 : sum / (double) n;
    }

    /** Moves focus the way the remote's D-pad does (the focus search the window runs for a direction key). */
    static boolean dpad(Activity home, int direction) {
        View focused = focus(home);
        if (focused == null) return false;
        View next = focused.focusSearch(direction);
        if (next == null || next == focused) return false;
        return next.requestFocus(direction);
    }

    /** The focused view. (Robolectric's Activity.getCurrentFocus() answers null for a window that never got window focus.) */
    static View focus(Activity home) {
        return home.getWindow().getDecorView().findFocus();
    }

    static boolean hasText(Activity home, String startsWith) {
        for (TextView tv : all(home, TextView.class)) {
            if (tv.getText() != null && tv.getText().toString().startsWith(startsWith) && tv.getVisibility() == View.VISIBLE) return true;
        }
        return false;
    }

    static View withDescription(Activity home, String description) {
        ArrayList<View> found = new ArrayList<>();
        home.getWindow().getDecorView().findViewsWithText(found, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return found.isEmpty() ? null : found.get(0);
    }

    static void press(Activity home, int keyCode) {
        home.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        home.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        ShadowLooper.idleMainLooper();
    }

    static String focusedLabel(Activity home) {
        View f = focus(home);
        if (f == null) return null;
        if (f.getTag() instanceof String) return (String) f.getTag();
        return f.getContentDescription() == null ? f.getClass().getSimpleName() : f.getContentDescription().toString();
    }
}
