package tv.calmhome.launcher;

import android.graphics.Color;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.GlassKit;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/**
 * Dev-only renders of every main screen of 3.0.0-beta.7 for the visual audit (fixture apps, no TV inputs, Robolectric's renderer:
 * SIMULATED, not the TV; fonts and blur differ from the TCL). Writes PNGs and layout-bounds.txt to ZP_RENDER_DIR; skipped unless set.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class UiRefineRenderTest {

    private static void settle(MainActivity h) throws Exception {
        for (int i = 0; i < 4; i++) {
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
            HomeTestSupport.layoutTv(h);
        }
        HomeTestSupport.pumpUntil(h, () -> {
            GlassKit kit = h.theme().kit;
            return kit.renderedSurfaceCount() == kit.surfaceCount();
        }, 150);
        HomeTestSupport.pump(h, 6);
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
    }

    private static MainActivity home(int extra) throws Exception {
        List<String> dock = new ArrayList<>();
        for (String[] app : MockTvApps.DEFAULTS) {
            MockTvApps.install(app[0], app[1], true, app[1].startsWith("YouTube") ? MockTvApps.pictureBanner()
                    : MockTvApps.logoBanner(Color.rgb(24, 24, 28), Color.rgb(230, 230, 235)));
            dock.add(app[0]);
        }
        for (int i = 0; i < extra; i++) {
            String pkg = "com.extra.app" + i;
            MockTvApps.install(pkg, "Extra " + (i + 1), true, MockTvApps.logoBanner(Color.rgb(30, 40, 60), Color.rgb(230, 230, 235)));
            dock.add(pkg);
        }
        new Prefs(HomeTestSupport.app()).setDock(dock);
        MainActivity h = HomeTestSupport.startHome(true);
        settle(h);
        return h;
    }

    @Test public void renderEveryMainScreen() throws Exception {
        Assume.assumeTrue("set ZP_RENDER_DIR to render", System.getenv("ZP_RENDER_DIR") != null);
        MainActivity h = home(0);
        LayoutRenderTest.save("01-home", h.getWindow().getDecorView());
        LayoutRenderTest.bounds(h);
        HomeTestSupport.dpad(h, View.FOCUS_RIGHT);
        HomeTestSupport.dpad(h, View.FOCUS_RIGHT);
        settle(h);
        LayoutRenderTest.save("02-home-tile3-focused", h.getWindow().getDecorView());
        HomeTestSupport.dpad(h, View.FOCUS_DOWN);
        settle(h);
        LayoutRenderTest.save("03-home-quick-action-focused", h.getWindow().getDecorView());

        String[][] screens = {{"Apps", "04-apps"}, {"Downloads", "05-downloads"}, {"Inputs", "06-inputs"}, {"Settings", "07-settings-appearance"}};
        for (String[] s : screens) {
            h.navigate(s[0]);
            settle(h);
            LayoutRenderTest.save(s[1], h.getWindow().getDecorView());
        }
        // Settings > Features (glass switches, a danger action)
        View features = HomeTestSupport.withDescription(h, "Features");
        if (features != null) {
            features.requestFocus();
            settle(h);
            LayoutRenderTest.save("08-settings-features", h.getWindow().getDecorView());
        }
        h.navigate("Home");
        settle(h);
        h.onNewIntent(new android.content.Intent(h, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "wallpapers"));
        settle(h);
        LayoutRenderTest.save("09-wallpapers", h.getWindow().getDecorView());
        h.navigate("Home");
        settle(h);
        ZeroPulseGlassAppTile first = HomeTestSupport.glassTiles(h).get(0);
        first.requestFocus();
        first.performLongClick();
        settle(h);
        LayoutRenderTest.save("10-home-context-menu", h.getWindow().getDecorView());
    }

    @Test public void renderTheCarouselWithNineFavorites() throws Exception {
        Assume.assumeTrue("set ZP_RENDER_DIR to render", System.getenv("ZP_RENDER_DIR") != null);
        MainActivity h = home(2);
        for (int i = 0; i < 7; i++) HomeTestSupport.dpad(h, View.FOCUS_RIGHT);
        settle(h);
        LayoutRenderTest.save("11-home-carousel-eighth-focused", h.getWindow().getDecorView());
        HomeTestSupport.dpad(h, View.FOCUS_RIGHT);
        settle(h);
        LayoutRenderTest.save("12-home-carousel-last-focused", h.getWindow().getDecorView());
    }
}
