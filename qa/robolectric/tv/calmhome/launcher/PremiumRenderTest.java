package tv.calmhome.launcher;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import tv.calmhome.launcher.core.DownloadSummary;
import tv.calmhome.launcher.ui.ZeroPulseGlassAppTile;

/** Dev-only renders of the new states (SIMULATED data and renderer, not the TV). Skipped unless ZP_RENDER_DIR is set. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
public class PremiumRenderTest {

    @Test public void renderTheNewStates() throws Exception {
        Assume.assumeTrue("dev-only: set ZP_RENDER_DIR", System.getenv("ZP_RENDER_DIR") != null);
        MainActivity h = LayoutRenderTest.renderedHome();
        h.pauseDownloadWatcherForTests();
        ZeroPulseGlassAppTile netflix = null;
        for (ZeroPulseGlassAppTile t : HomeTestSupport.glassTiles(h)) if ("com.netflix.ninja".equals(t.getTag())) netflix = t;
        (netflix != null ? netflix : HomeTestSupport.glassTiles(h).get(0)).requestFocus();
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        LayoutRenderTest.save("motion-netflix-focused-glow", h.getWindow().getDecorView());

        h.onDownloads(DownloadSummary.of(Collections.singletonList(new DownloadSummary.Item(1, "Simulated download", DownloadSummary.Status.DOWNLOADING,
                42L * 1024 * 1024, 100L * 1024 * 1024)), null, 0));
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        LayoutRenderTest.save("motion-download-in-centre-SIMULATED", h.getWindow().getDecorView());

        h.onDownloads(DownloadSummary.IDLE);
        h.onDownloadFinished("Simulated download", true);
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        LayoutRenderTest.save("motion-download-finished-notice-SIMULATED", h.getWindow().getDecorView());

        ShadowLooper.idleMainLooper(6_000, TimeUnit.MILLISECONDS);
        h.open(new tv.calmhome.launcher.screens.WhatsNewPanel(h));
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        LayoutRenderTest.save("motion-whats-new", h.getWindow().getDecorView());
    }
}
