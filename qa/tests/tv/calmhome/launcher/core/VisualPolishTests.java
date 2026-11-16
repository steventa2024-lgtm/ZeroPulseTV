package tv.calmhome.launcher.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * The visual-polish pass, the pure parts: how artwork sits in a tile, how any wallpaper fills any screen, the adaptive
 * contrast layer measured over the real bundled wallpapers, and the wallpaper manifest (the five supplied files byte for
 * byte, every credit reachable). Usage: java VisualPolishTests {@code <launcher/assets/wallpapers>}
 */
public final class VisualPolishTests {
    private static int checks;
    private static File wallpapers;

    /** SHA-256 of the five wallpapers as the owner supplied them: the bundled copies must be those exact bytes. */
    private static final String[][] SUPPLIED = {
            {"lg01.jpg", "1d893f483e8f7c647beeba5f3aaaca06a7b537bd59ec334e363d748993a5eb8d"},
            {"lg02.jpg", "909a8bdade2fe3bbb09312551a2fa6d6d22e894d1e5df0f3974c76ae4a44a760"},
            {"lg03.jpg", "5ad3c62d236b4b4edce8b13c44039ac7d808c33e2c76548b0b7ef3f4f2c7e57d"},
            {"lg04.jpg", "da907033c3af863dae68ad4c2370b82e9f38a0e84ebc7524faf9c2f77d44b5b0"},
            {"lg05.jpg", "319fc5af3a73d3cd15b5a4318a7720e02229c31723ab987d1dda585c7f88ebf0"}};

    public static void main(String[] args) throws Exception {
        wallpapers = new File(args.length > 0 ? args[0] : "launcher/assets/wallpapers");
        File sources = new File(args.length > 1 ? args[1] : "launcher/src");
        tileArt();
        framing();
        contrastMath();
        contrastOverBundledWallpapers();
        manifest();
        rowGeometry();
        productionHasNoFixturesOrHardcodedTimes(sources);
        System.out.println("Visual polish checks: " + checks + " passed");
    }

    /**
     * Fixture data lives only in the test sources: nothing in the app refers to a mock app, a Robolectric class or a baked-in
     * test time, so a screenshot's fixture can never be a production behaviour.
     */
    private static void productionHasNoFixturesOrHardcodedTimes(File sources) throws Exception {
        List<File> files = new ArrayList<>();
        collect(sources, files);
        check(files.size() > 60, "the app's sources were found: " + files.size() + " files under " + sources);
        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            for (String banned : new String[]{"MockTvApps", "org.robolectric", "HomeTestSupport", "mock.tv.", "setCurrentTimeMillis", "SystemClock.setCurrentTime"}) {
                check(!text.contains(banned), f.getName() + " does not contain test-only '" + banned + "'");
            }
            if (!f.getName().equals("SystemTime.java")) {
                check(!text.contains("useForTests"), f.getName() + " never replaces the clock (only SystemTime defines that seam, and only tests call it)");
            }
        }
        systemTime();
        String clock = new String(Files.readAllBytes(new File(sources, "tv/calmhome/glasscomponents/GlassClockView.java").toPath()), StandardCharsets.UTF_8);
        check(!clock.contains("hour=10") && !clock.contains("minute=8") && !clock.contains("\"10:08\""), "the glass clock has no baked-in 10:08 placeholder");
        check(clock.contains("Calendar.getInstance()") && clock.contains("System::currentTimeMillis"), "the glass clock reads the system clock by default");
        String nav = new String(Files.readAllBytes(new File(sources, "tv/calmhome/launcher/ui/Navigation.java").toPath()), StandardCharsets.UTF_8);
        check(nav.contains("Calendar.getInstance()") && nav.contains("DateFormat.is24HourFormat"), "the header follows the system time and the 12/24-hour setting");
        check(!nav.matches("(?s).*setTime\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\).*"), "the header never sets a literal time");
    }

    /** The time the launcher shows is the system clock unless a test replaces it, and the replacement can always be undone. */
    private static void systemTime() {
        check(SystemTime.isSystemClock(), "the default time source is the system clock");
        long before = System.currentTimeMillis(), shown = SystemTime.now(), after = System.currentTimeMillis();
        check(shown >= before && shown <= after, "and it reads the wall clock now, not a stored time");
        SystemTime.useForTests(() -> 1_000L);
        try {
            check(SystemTime.now() == 1_000L && !SystemTime.isSystemClock(), "a test can show another time");
        } finally {
            SystemTime.reset();
        }
        check(SystemTime.isSystemClock() && SystemTime.now() >= before, "reset brings the system clock back");
        SystemTime.useForTests(() -> 5L);
        SystemTime.useForTests(null);
        check(SystemTime.isSystemClock(), "a null source also restores it");
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else if (k.getName().endsWith(".java")) out.add(k);
        }
    }

    // ---------------------------------------------------------------------------------------------- artwork in a tile

    private static void tileArt() {
        check(TileArt.TILE_ASPECT == 1.6f, "tiles are 16:10");
        for (int tileH : new int[]{62, 69, 100, 137, 180}) {
            int tileW = Math.round(tileH * TileArt.TILE_ASPECT);
            int[] box = TileArt.artBox(tileW, tileH);
            float aspect = box[0] / (float) box[1];
            check(Math.abs(aspect - TileArt.TILE_ASPECT) < 0.06f, "the artwork box keeps the tile's shape at tile height " + tileH + ": box aspect " + aspect);
            check(box[0] < tileW && box[1] < tileH, "a hairline of glass is left around the artwork at " + tileH);
            int rim = TileArt.margin(tileH);
            check(rim >= TileArt.RIM_MIN_PX && rim <= Math.max(TileArt.RIM_MIN_PX, Math.round(tileH * 0.03f)), "the rim is a hairline, not a frame: " + rim + " px at " + tileH);
            check(box[0] >= Math.round(tileW * 0.94f) && box[1] >= Math.round(tileH * 0.94f), "the artwork fills at least 94 % of the tile at " + tileH + ": " + Arrays.toString(box));
        }
        check(TileArt.covers(320, 180, 189, 106), "a 320 x 180 TV banner fills a 16:9 box");
        check(TileArt.covers(640, 360, 189, 106), "so does a larger 16:9 banner");
        check(TileArt.covers(320, 200, 189, 106), "a 16:10 banner is cropped a sliver (within 12 %)");
        check(!TileArt.covers(400, 400, 189, 106), "a square picture is letter-boxed, not cropped to a band");
        check(!TileArt.covers(0, 0, 189, 106), "an empty picture covers nothing");
        int[] filled = TileArt.bannerSize(320, 180, 189, 106, false);
        check(filled[0] == 189 && filled[1] == 106, "a covering banner is drawn at the box's size");
        int[] boxed = TileArt.bannerSize(400, 400, 189, 106, false);
        check(boxed[1] == 106 && boxed[0] == 106, "a square is scaled uniformly to the box's height: " + Arrays.toString(boxed));
        // Carrying a plain icon's own backdrop to the edges of a wide tile (flat or smoothly shaded: yes; a glyph on nothing or a logo running off the edge: no).
        int n = 40;
        int[] flat = new int[n * n], shaded = new int[n * n], glyph = new int[n * n], bleed = new int[n * n], roundedFlat = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                flat[y * n + x] = 0xFF1E88E5;
                int g = 40 + y * 3;                                                  // a smooth top-to-bottom gradient
                shaded[y * n + x] = 0xFF000000 | (g << 16) | (g << 8) | 200;
                glyph[y * n + x] = (x > 10 && x < 30 && y > 10 && y < 30) ? 0xFFFFFFFF : 0;
                bleed[y * n + x] = (y % 8 < 4 && (x == 0 || x == n - 1)) ? 0xFFFFFFFF : 0xFF1E88E5;      // a logo cut by the edge: hard steps down the edge
                double dx = Math.max(0, Math.abs(x - (n - 1) / 2.0) - (n / 2.0 - 8)), dy = Math.max(0, Math.abs(y - (n - 1) / 2.0) - (n / 2.0 - 8));
                roundedFlat[y * n + x] = dx * dx + dy * dy <= 64 ? 0xFF1E88E5 : 0;      // a rounded square with see-through corners
            }
        }
        check(TileArt.extendsToEdges(flat, n, n, true), "a flat backdrop is carried to the sides of a wide tile");
        check(TileArt.extendsToEdges(shaded, n, n, true), "so is a smooth gradient");
        check(TileArt.extendsToEdges(roundedFlat, n, n, true), "a rounded square with see-through corners counts (the corners are left out of the check)");
        check(!TileArt.extendsToEdges(glyph, n, n, true), "a bare glyph on nothing has no backdrop to carry");
        check(!TileArt.extendsToEdges(bleed, n, n, true), "a logo cut by the edge is not smeared across the tile");
        check(!TileArt.extendsToEdges(null, n, n, true) && !TileArt.extendsToEdges(flat, 4, 4, true), "nothing and tiny pictures are refused");
        TileArt.squareOff(roundedFlat, n, n, true);
        boolean allOpaque = true;
        for (int y = 0; y < n; y++) for (int i : new int[]{0, n - 1}) if ((roundedFlat[y * n + i] >>> 24) != 255) allOpaque = false;
        check(allOpaque, "squaring off gives the see-through corners the backdrop's colour, so nothing smears when the edges are carried on");
        check(roundedFlat[0] == 0xFF1E88E5 && roundedFlat[n - 1] == 0xFF1E88E5, "in the backdrop's own colour: " + Integer.toHexString(roundedFlat[0]));
        int[] logo = TileArt.bannerSize(320, 180, 189, 106, true);
        check(logo[0] <= Math.round(189 * TileArt.LOGO_SHARE) + 1, "a bare logo is held inside the glass: " + Arrays.toString(logo));
        check(Math.abs(logo[0] / (float) logo[1] - 320f / 180f) < 0.03f, "and keeps its shape");
        int side = TileArt.iconSide(220, 137);
        check(side == Math.round(137 * TileArt.ICON_SHARE), "an icon is " + TileArt.ICON_SHARE + " of the tile height: " + side);
        check(side + 2 * TileArt.margin(137) <= 137, "and fits inside the frame");
        check(TileArt.ICON_SHARE >= 0.66f, "a bare glyph icon is shown large, not as a small mark in a big tile");
        check(TileArt.iconSide(40, 20) >= 1, "a degenerate tile still gets a positive icon side");
        check(TileArt.iconRadius(100) == 22f, "an opaque square icon gets a 22 % corner");
        check(TileArt.bannerRadius(137) > 2f && TileArt.bannerRadius(137) < GlassMaterial.radius(137, false), "banner corners are concentric with the glass's");
        check(TileArt.monogram("Netflix").equals("N"), "one word: its first letter");
        check(TileArt.monogram("Prime Video").equals("PV"), "two words: both initials");
        check(TileArt.monogram("the great app of all").equals("TG"), "never more than two letters");
        check(TileArt.monogram("  ").equals("?") && TileArt.monogram(null).equals("?"), "nothing to go on: a question mark");
        check(TileArt.monogram("éclair").equals("É"), "letters, not bytes");
        int[] opaque = new int[100], clear = new int[100], half = new int[100];
        Arrays.fill(opaque, 0xFF102030);
        Arrays.fill(half, 0xFF102030);
        for (int i = 0; i < 20; i++) half[i] = 0x00000000;
        check(!TileArt.mostlyTransparent(opaque, 100), "a banner with its own backdrop is opaque");
        check(TileArt.mostlyTransparent(clear, 100), "a bare logo has see-through pixels");
        check(TileArt.mostlyTransparent(half, 100), "20 % see-through counts");
        check(!TileArt.mostlyTransparent(null, 0), "nothing is not transparent");
        int[] square = new int[24 * 24], round = new int[24 * 24];
        Arrays.fill(square, 0xFFFFFFFF);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) round[y * 24 + x] = (x - 11.5) * (x - 11.5) + (y - 11.5) * (y - 11.5) < 121 ? 0xFFFFFFFF : 0;
        check(TileArt.opaqueCorners(square, 24, 24), "a plain square icon is given rounded corners");
        check(!TileArt.opaqueCorners(round, 24, 24), "an icon with a shape of its own is left alone");
    }

    // ---------------------------------------------------------------------------------------------- wallpaper framing

    private static void framing() {
        int[] same = WallpaperFraming.crop(1920, 1080, 16f / 9f, 0.5f, 0.5f);
        check(same[0] == 0 && same[1] == 0 && same[2] == 1920 && same[3] == 1080, "a 16:9 picture on a 16:9 screen loses nothing: " + Arrays.toString(same));
        int[] wide = WallpaperFraming.crop(2400, 1000, 16f / 9f, 0.5f, 0.5f);
        check(wide[3] == 1000 && wide[2] == 1778 && wide[0] == 311, "a wider picture loses width, centred: " + Arrays.toString(wide));
        int[] left = WallpaperFraming.crop(2400, 1000, 16f / 9f, 0f, 0.5f);
        check(left[0] == 0, "a focal point at the left edge keeps the left edge");
        int[] right = WallpaperFraming.crop(2400, 1000, 16f / 9f, 1f, 0.5f);
        check(right[0] + right[2] == 2400, "and at the right edge, the right edge: no gap is ever shown");
        int[] tall = WallpaperFraming.crop(1000, 1000, 16f / 9f, 0.5f, 0.5f);
        check(tall[2] == 1000 && tall[3] == 563, "a square picture loses height: " + Arrays.toString(tall));
        int[] top = WallpaperFraming.crop(1000, 1000, 16f / 9f, 0.5f, 0f);
        check(top[1] == 0, "a focal point at the top keeps the top");
        int[] zoomed = WallpaperFraming.crop(1920, 1080, 16f / 9f, 0.5f, 0.5f, 1.2f);
        check(zoomed[2] == 1600 && zoomed[3] == 900 && zoomed[0] == 160 && zoomed[1] == 90, "zoom 1.2 shows 1/1.2 of the picture, centred: " + Arrays.toString(zoomed));
        int[] zoomedLeft = WallpaperFraming.crop(1920, 1080, 16f / 9f, 0f, 0f, 1.4f);
        check(zoomedLeft[0] == 0 && zoomedLeft[1] == 0, "a zoomed window slides to the corner the focal point names");
        for (float z : WallpaperFraming.ZOOMS) {
            for (float fx : new float[]{0f, 0.3f, 0.5f, 1f}) {
                int[] c = WallpaperFraming.crop(1024, 572, 16f / 9f, fx, 0.5f, z);
                check(c[0] >= 0 && c[1] >= 0 && c[0] + c[2] <= 1024 && c[1] + c[3] <= 572, "the window stays inside the picture at zoom " + z + ", focus " + fx);
                check(Math.abs(c[2] / (float) c[3] - 16f / 9f) < 0.02f, "and has the screen's shape");
            }
        }
        float[] m = WallpaperFraming.matrix(1024, 572, 1920, 1080, 0.5f, 0.5f);
        check(m[0] >= 1920f / 1024f - 1e-3f, "the matrix scale covers the view: " + m[0]);
        check(m[1] <= 0f && m[2] <= 0f, "and never leaves a gap at the top or left");
        check(m[1] + 1024 * m[0] >= 1920f - 0.5f && m[2] + 572 * m[0] >= 1080f - 0.5f, "or at the bottom or right");
        float[] ident = WallpaperFraming.matrix(1920, 1080, 1920, 1080, 0.5f, 0.5f);
        check(Math.abs(ident[0] - 1f) < 1e-6f && ident[1] == 0f && ident[2] == 0f, "an exact fit is the identity");
        check(WallpaperFraming.sampleSize(1024, 572, 1920, 1080) == 1, "a small picture is never decoded smaller");
        check(WallpaperFraming.sampleSize(2752, 1536, 1920, 1080) == 1, "2752 x 1536 on a 1080p screen is decoded whole (half would be too small)");
        check(WallpaperFraming.sampleSize(7680, 4320, 1920, 1080) == 4, "an 8K original is decoded at quarter size for 1080p");
        check(WallpaperFraming.sampleSize(7680, 4320, 3840, 2160) == 2, "and half size for 4K");
        check(WallpaperFraming.sampleSize(0, 0, 1920, 1080) == 1, "an unreadable size is left alone");
        check(WallpaperFraming.isUpscaled(1024, 572, 1920, 1080) && !WallpaperFraming.isUpscaled(2752, 1536, 1920, 1080), "which pictures are enlarged on screen");
        check(WallpaperFraming.nudge(0.5f, 1) == 0.6f && WallpaperFraming.nudge(0.5f, -1) == 0.4f, "one step is a tenth");
        check(WallpaperFraming.nudge(1f, 1) == 1f && WallpaperFraming.nudge(0f, -1) == 0f, "the ends stop");
        float v = 0.5f;
        for (int i = 0; i < 20; i++) v = WallpaperFraming.nudge(v, 1);
        check(v == 1f, "twenty steps right end at 1.0 with no drift");
        float[] p = WallpaperFraming.parse("0.25, 0.75");
        check(p[0] == 0.25f && p[1] == 0.75f, "a stored focal point reads back");
        check(Arrays.equals(WallpaperFraming.parse("nonsense"), new float[]{0.5f, 0.5f}), "garbage is the centre");
        check(Arrays.equals(WallpaperFraming.parse(null), new float[]{0.5f, 0.5f}), "so is nothing");
        check(WallpaperFraming.parse("9,-4")[0] == 1f && WallpaperFraming.parse("9,-4")[1] == 0f, "out-of-range values are clamped");
        float[] f = WallpaperFraming.parseFrame(WallpaperFraming.formatFrame(0.3f, 0.6f, 1.2f), 0.5f, 0.5f);
        check(f[0] == 0.3f && f[1] == 0.6f && Math.abs(f[2] - 1.2f) < 1e-6f, "a stored framing round-trips");
        float[] fd = WallpaperFraming.parseFrame("junk", 0.4f, 0.6f);
        check(fd[0] == 0.4f && fd[1] == 0.6f && fd[2] == 1f, "an unreadable framing falls back to the catalog's default at zoom 1");
        check(WallpaperFraming.parseFrame("0.5,0.5,9", 0.5f, 0.5f)[2] == WallpaperFraming.ZOOMS[WallpaperFraming.ZOOMS.length - 1], "zoom is clamped");
    }

    // ---------------------------------------------------------------------------------------------- adaptive contrast

    private static void contrastMath() {
        near(0f, ContrastProbe.luminance(0xFF000000), 1e-6f, "black has no luminance");
        near(1f, ContrastProbe.luminance(0xFFFFFFFF), 1e-4f, "white has all of it");
        check(ContrastProbe.whiteContrast(0f) > 20f, "white on black is 21:1");
        near(1f, ContrastProbe.whiteContrast(1f), 1e-4f, "white on white is 1:1");
        near(4.5f, ContrastProbe.whiteContrast(ContrastProbe.maxLuminance(4.5f)), 1e-3f, "maxLuminance inverts whiteContrast");
        check(ContrastProbe.alphaFor(0.02f, 3.6f, 0f) == 0f, "a dark picture needs no extra darkening");
        float a1 = ContrastProbe.alphaFor(0.45f, 3.6f, 0f), a2 = ContrastProbe.alphaFor(0.8f, 3.6f, 0f);
        check(a1 > 0f && a2 > a1, "a brighter picture needs more: " + a1 + " then " + a2);
        check(ContrastProbe.alphaFor(1f, 4.5f, 0f) <= ContrastProbe.CAP, "but never more than the cap");
        check(ContrastProbe.alphaFor(0.45f, 3.6f, 0.4f) < a1, "the viewer's own dim already does part of the work");
        float lum = 0.45f, a = ContrastProbe.alphaFor(lum, 3.6f, 0f);
        check(ContrastProbe.whiteContrast(ContrastProbe.afterScrim(lum, a, 0f)) >= 3.6f - 0.02f, "the alpha it asks for reaches the contrast it was asked for");
        float[] white = new float[ContrastProbe.BANDS];
        int[] allWhite = new int[ContrastProbe.GRID_W * ContrastProbe.GRID_H];
        Arrays.fill(allWhite, 0xFFFFFFFF);
        float[] plan = ContrastProbe.plan(allWhite, ContrastProbe.GRID_W, ContrastProbe.GRID_H, 0f);
        for (int b = 0; b < ContrastProbe.BANDS; b++) check(plan[b] == ContrastProbe.CAP, "a white picture is darkened to the cap in band " + b);
        int[] allBlack = new int[allWhite.length];
        Arrays.fill(allBlack, 0xFF000000);
        float[] dark = ContrastProbe.plan(allBlack, ContrastProbe.GRID_W, ContrastProbe.GRID_H, 0f);
        check(Arrays.equals(dark, ContrastProbe.FLOOR), "a black picture gets only the floor");
        check(Arrays.equals(ContrastProbe.plan(null, 0, 0, 0f), ContrastProbe.FLOOR), "no picture yet: the floor");
        float prev = -1f;
        boolean smooth = true;
        float[] custom = {0.14f, 0.4f, 0.16f, 0.3f};
        for (int i = 0; i <= 200; i++) {
            float y = i / 200f, v = ContrastProbe.alphaAt(custom, y);
            if (prev >= 0 && Math.abs(v - prev) > 0.04f) smooth = false;      // at most 0.04 of alpha per 0.5 % of the height, even across a 0.26 step
            prev = v;
            check(v >= Math.min(Math.min(custom[0], custom[1]), Math.min(custom[2], custom[3])) - 1e-4f && v <= 0.4f + 1e-4f, "the scrim stays between its bands' values at " + y);
        }
        check(smooth, "the scrim has no visible seam between bands");
        check(ContrastProbe.alphaAt(custom, 0.1f) == 0.14f && ContrastProbe.alphaAt(custom, 0.4f) == 0.4f && ContrastProbe.alphaAt(custom, 0.75f) == 0.16f && ContrastProbe.alphaAt(custom, 0.97f) == 0.3f,
                "each band keeps its own alpha across the band");
        for (int b = 0; b < ContrastProbe.BANDS - 1; b++) check(ContrastProbe.BAND[b + 1][0] > ContrastProbe.BAND[b][1], "bands " + b + " and " + (b + 1) + " leave a gap to ramp across");
    }

    /** Every bundled wallpaper, framed as the screen frames it and measured: white text stays readable, and nothing is buried. */
    private static void contrastOverBundledWallpapers() throws Exception {
        File[] jpgs = wallpapers.listFiles((d, n) -> n.endsWith(".jpg"));
        check(jpgs != null && jpgs.length >= 13, "the wallpaper folder is readable: " + (jpgs == null ? 0 : jpgs.length) + " pictures");
        for (File f : jpgs) {
            BufferedImage img = ImageIO.read(f);
            check(img != null, f.getName() + " decodes");
            int[] c = WallpaperFraming.crop(img.getWidth(), img.getHeight(), 16f / 9f, 0.5f, 0.5f);
            int[] grid = new int[ContrastProbe.GRID_W * ContrastProbe.GRID_H];
            for (int y = 0; y < ContrastProbe.GRID_H; y++) {
                for (int x = 0; x < ContrastProbe.GRID_W; x++) {
                    // the mean of a small block, as a downscale would give
                    int x0 = c[0] + x * c[2] / ContrastProbe.GRID_W, x1 = Math.max(x0 + 1, c[0] + (x + 1) * c[2] / ContrastProbe.GRID_W);
                    int y0 = c[1] + y * c[3] / ContrastProbe.GRID_H, y1 = Math.max(y0 + 1, c[1] + (y + 1) * c[3] / ContrastProbe.GRID_H);
                    long r = 0, g = 0, b = 0, n = 0;
                    for (int yy = y0; yy < y1 && yy < img.getHeight(); yy += Math.max(1, (y1 - y0) / 4)) {
                        for (int xx = x0; xx < x1 && xx < img.getWidth(); xx += Math.max(1, (x1 - x0) / 4)) {
                            int p = img.getRGB(xx, yy);
                            r += (p >> 16) & 255;
                            g += (p >> 8) & 255;
                            b += p & 255;
                            n++;
                        }
                    }
                    grid[y * ContrastProbe.GRID_W + x] = 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
            float[] plan = ContrastProbe.plan(grid, ContrastProbe.GRID_W, ContrastProbe.GRID_H, 0.10f);
            StringBuilder line = new StringBuilder();
            for (int band = 0; band < ContrastProbe.BANDS; band++) {
                int y0 = Math.round(ContrastProbe.BAND[band][0] * ContrastProbe.GRID_H), y1 = Math.max(y0 + 1, Math.round(ContrastProbe.BAND[band][1] * ContrastProbe.GRID_H));
                float[] lum = new float[ContrastProbe.GRID_W * (y1 - y0)];
                int n = 0;
                for (int y = y0; y < y1; y++) for (int x = 0; x < ContrastProbe.GRID_W; x++) lum[n++] = ContrastProbe.luminance(grid[y * ContrastProbe.GRID_W + x]);
                float bright = ContrastProbe.percentile(lum, n, ContrastProbe.PERCENTILE);
                float contrast = ContrastProbe.contrastWith(plan, band, bright, 0.10f);
                boolean capped = plan[band] >= ContrastProbe.CAP - 1e-4f;
                check(contrast >= ContrastProbe.TARGET[band] - 0.05f || capped,
                        f.getName() + " band " + band + ": white text reaches " + ContrastProbe.TARGET[band] + ":1 or the scrim is at its cap (got " + contrast + ", alpha " + plan[band] + ")");
                line.append(String.format(java.util.Locale.ROOT, " %.2f", plan[band]));
            }
            if (f.getName().startsWith("lg")) {
                for (int band = 0; band < ContrastProbe.BANDS; band++) {
                    check(plan[band] <= 0.30f, f.getName() + " is not buried: band " + band + " is darkened " + plan[band]);
                }
            }
            System.out.println("  scrim " + f.getName() + ":" + line);
        }
    }

    // ---------------------------------------------------------------------------------------------- the wallpaper manifest

    private static void manifest() throws Exception {
        List<String> lines = Files.readAllLines(new File(wallpapers, "credits.tsv").toPath(), StandardCharsets.UTF_8);
        WallpaperCatalog cat = WallpaperCatalog.parse(lines);
        Set<String> listed = new HashSet<>();
        for (WallpaperCatalog.Entry e : cat.all()) {
            listed.add(e.file);
            check(new File(wallpapers, e.file).isFile(), e.file + " is bundled");
            check(e.homeCredit().isEmpty(), e.file + ": Home carries no credit line");
            check(e.caption().length() > 4 && !e.place.isEmpty(), e.file + " has a title and a credit for the information screen");
        }
        File[] jpgs = wallpapers.listFiles((d, n) -> n.endsWith(".jpg"));
        for (File f : jpgs) check(listed.contains(f.getName()), f.getName() + " is in the manifest (no unlisted picture is bundled)");

        List<WallpaperCatalog.Entry> liquid = cat.inCategory(WallpaperCatalog.LIQUID);
        check(liquid.size() == 5, "the Liquid Glass collection is the five supplied pictures: " + liquid.size());
        check(cat.categories().keySet().iterator().next().equals("Liquid Glass"), "and it is the first collection on the Wallpapers screen");
        for (String[] s : SUPPLIED) {
            check(sha256(new File(wallpapers, s[0])).equals(s[1]), s[0] + " is the exact file the owner supplied (SHA-256)");
            WallpaperCatalog.Entry e = cat.byFile(s[0]);
            check(e != null && e.categories.contains(WallpaperCatalog.LIQUID), s[0] + " is in the Liquid Glass collection");
            check(!e.requiresAttribution() && e.credit.startsWith("Supplied artwork"), s[0] + " is credited as supplied artwork, with no invented author");
            check(!e.license.isEmpty() && !e.note.isEmpty(), s[0] + " carries its provenance and size on the information screen");
        }
        check(cat.byFile("lg01.jpg").categories.contains(WallpaperCatalog.TWILIGHT), "the twilight photograph is also in the Twilight collection");

        int photos = 0;
        for (WallpaperCatalog.Entry e : cat.all()) {
            if (!e.requiresAttribution()) continue;
            photos++;
            check(e.license.startsWith("CC BY-SA "), e.file + " names its licence: " + e.license);
            check(e.licenseUrl().startsWith("https://creativecommons.org/licenses/by-sa/"), e.file + " gives the licence's address: " + e.licenseUrl());
            check(e.note.contains("https://commons.wikimedia.org/wiki/File:"), e.file + " links its source");
            check(e.note.contains("Cropped to 16:9") , e.file + " says what was changed (CC BY-SA asks for it)");
            check(e.credit.startsWith("Photo: "), e.file + " names the photographer in its credit");
        }
        check(photos == 9, "all nine CC BY-SA photographs are still credited: " + photos);
        check(cat.byFile("wp04.jpg").licenseUrl().equals("https://creativecommons.org/licenses/by-sa/4.0/"), "the Chile photograph's licence address");
        check(cat.byFile("wp02.jpg").licenseUrl().equals("https://creativecommons.org/licenses/by-sa/3.0/"), "and a 3.0 one's");
        // legacy three-column manifests still parse, and a ragged line does not break the reader
        WallpaperCatalog old = WallpaperCatalog.parse(Arrays.asList("a.jpg\tPlace\tPhoto: X, CC BY-SA 4.0", "broken", "", "# comment"));
        check(old.all().size() == 1 && old.byFile("a.jpg").categories.get(0).equals(WallpaperCatalog.ALL) && !old.byFile("a.jpg").hasFocus(), "a Calm Home 1.0 manifest still reads");
        WallpaperCatalog focus = WallpaperCatalog.parse(Arrays.asList("b.jpg\tPlace\tOriginal artwork: X\tZeroPulse\t0.30,0.60\tOurs\tNote"));
        check(focus.byFile("b.jpg").focusX == 0.3f && focus.byFile("b.jpg").focusY == 0.6f && focus.byFile("b.jpg").hasFocus(), "a catalog focal point is read");
        // decode the supplied pictures: sizes as recorded in the notes
        BufferedImage small = ImageIO.read(new File(wallpapers, "lg02.jpg")), big = ImageIO.read(new File(wallpapers, "lg05.jpg"));
        check(small.getWidth() == 1024 && small.getHeight() == 572, "lg02 is 1024 x 572, as the note says");
        check(big.getWidth() == 2752 && big.getHeight() == 1536, "lg05 is 2752 x 1536, as the note says");
        check(cat.byFile("lg02.jpg").note.contains("1024 x 572") && cat.byFile("lg05.jpg").note.contains("2752 x 1536"), "each note states its own size");
    }

    private static void rowGeometry() {
        for (int[] c : new int[][]{{1920 - 192, 7, 28}, {3840 - 384, 7, 56}, {1280 - 128, 7, 19}, {1000, 5, 10}}) {
            int[] r = GlassMaterial.rowPx(c[0], c[1], c[2]);
            int total = c[1] * r[0] + r[1] + (c[1] - 1) * c[2];
            check(total == c[0], "a row of " + c[1] + " tiles adds up to " + c[0] + " px exactly (got " + total + ")");
            check(r[1] >= 0 && r[1] < c[1], "the leftover pixels are fewer than the tiles");
        }
        check(GlassMaterial.rowPx(10, 0, 5)[0] >= 1, "zero columns is treated as one");
        check(GlassMaterial.tileHeightDp(111) == 69 && GlassMaterial.tileHeightDp(1) >= 1, "tile height from width");
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static String sha256(File f) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void near(float expected, float actual, float tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        checks++;
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
}
