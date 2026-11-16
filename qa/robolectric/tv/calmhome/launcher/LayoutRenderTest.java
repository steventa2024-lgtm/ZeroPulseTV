package tv.calmhome.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Dev-only renders of Home for the before/after layout comparison (fixture apps, Robolectric's renderer: SIMULATED, not the TV). Uses only
 * APIs that the earlier build also has, so the same file renders both trees, and writes where every major region is (layout-bounds.txt)
 * so the comparison is measured, not eyeballed. Skipped unless ZP_RENDER_DIR is set.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class LayoutRenderTest {

    static final String NL = System.lineSeparator();

    static void save(String name, View v) throws Exception {
        File d = new File(System.getenv("ZP_RENDER_DIR"));
        d.mkdirs();
        Bitmap b = HomeTestSupport.draw(v);
        try (FileOutputStream out = new FileOutputStream(new File(d, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static String box(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return at[0] + "," + at[1] + " " + v.getWidth() + "x" + v.getHeight();
    }

    /** Where every major region is on screen, one line each: the before/after layout comparison diffs these files. */
    static void bounds(MainActivity h) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("nav ").append(box(HomeTestSupport.all(h, tv.calmhome.glasscomponents.GlassTopNavView.class).get(0))).append(NL);
        out.append("clock ").append(box(HomeTestSupport.all(h, tv.calmhome.glasscomponents.GlassClockView.class).get(0))).append(NL);
        for (android.widget.TextView tv : HomeTestSupport.all(h, android.widget.TextView.class)) {
            String t = String.valueOf(tv.getText());
            if (t.startsWith("Good ") || t.startsWith("Relax")) out.append("text '").append(t).append("' ").append(box(tv)).append(NL);
        }
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        for (int i = 0; i < tiles.size(); i++) out.append("tile ").append(i).append(' ').append(box(tiles.get(i))).append(NL);
        List<tv.calmhome.launcher.ui.ZeroPulseGlassQuickAction> actions = HomeTestSupport.glassActions(h);
        for (int i = 0; i < actions.size(); i++) out.append("action ").append(i).append(' ').append(box(actions.get(i))).append(NL);
        File f = new File(System.getenv("ZP_RENDER_DIR"), "layout-bounds.txt");
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(out.toString().getBytes("UTF-8")); }
    }

    static MainActivity renderedHome() throws Exception {
        for (String[] app : MockTvApps.DEFAULTS) {
            MockTvApps.install(app[0], app[1], true, app[1].startsWith("YouTube") ? MockTvApps.pictureBanner()
                    : MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
        }
        MainActivity h = HomeTestSupport.startHome(true);
        HomeTestSupport.pumpUntil(h, () -> {
            GlassKit kit = h.theme().kit;
            return kit.surfaceCount() > 0 && kit.renderedSurfaceCount() == kit.surfaceCount();
        }, 150);
        return h;
    }

    @Test public void renderHomeForTheLayoutComparison() throws Exception {
        Assume.assumeTrue("dev-only: set ZP_RENDER_DIR", System.getenv("ZP_RENDER_DIR") != null);
        MainActivity h = renderedHome();
        List<ZeroPulseGlassAppTile> tiles = HomeTestSupport.glassTiles(h);
        View action = HomeTestSupport.glassActions(h).get(0);
        action.requestFocus();                                   // nothing in the app row focused: the plain layout
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        save("home-row-unfocused", h.getWindow().getDecorView());
        bounds(h);
        tiles.get(3).requestFocus();
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        save("home-tile4-focused", h.getWindow().getDecorView());
    }
}
