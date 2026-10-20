import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tv.calmhome.launcher.core.CommandParser;
import tv.calmhome.launcher.core.CommandParser.Kind;
import tv.calmhome.launcher.core.CommandParser.Named;
import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.Greeting;
import tv.calmhome.launcher.core.WallpaperCatalog;

/**
 * JVM unit tests for the launcher's pure logic (no Android needed). Run by qa/run-tests.ps1.
 * Usage: java CoreTests <path to launcher/assets/wallpapers/credits.tsv>
 */
public class CoreTests {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }

    static <T> void eq(String name, T expected, T actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(name + (ok ? "" : "  expected=" + expected + " actual=" + actual), ok);
    }

    public static void main(String[] args) throws Exception {
        dock();
        greeting();
        catalog(args.length > 0 ? args[0] : null);
        commands();
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void dock() {
        // Migration from Calm Home 1.0: key absent -> the same default order Calm Home showed
        eq("dock: absent key gives Calm Home default", DockList.DEFAULT, DockList.parse(null));
        eq("dock: default has the 7 favourites", 7, DockList.parse(null).size());
        eq("dock: first is Netflix", "com.netflix.ninja", DockList.parse(null).get(0));
        // A dock saved by Calm Home 1.0 (TextUtils.join(",")) round-trips unchanged
        String saved = "com.netflix.ninja,com.hulu.livingroomplus,com.google.android.youtube.tv";
        eq("dock: Calm Home format round-trips", saved, DockList.serialize(DockList.parse(saved)));
        eq("dock: empty string = user emptied dock", 0, DockList.parse("").size());
        eq("dock: blanks and duplicates dropped", Arrays.asList("a", "b"), DockList.parse("a,,b, a ,"));

        List<String> d = Arrays.asList("a", "x", "b", "c");          // x is not installed
        List<String> vis = DockList.visible(d, p -> !p.equals("x"));
        eq("dock: hidden apps filtered from view", Arrays.asList("a", "b", "c"), vis);
        eq("dock: move right skips hidden slot", Arrays.asList("b", "x", "a", "c"), DockList.move(d, vis, "a", 1));
        eq("dock: move left at start is a no-op", d, DockList.move(d, vis, "a", -1));
        eq("dock: move right at end is a no-op", d, DockList.move(d, vis, "c", 1));
        eq("dock: remove keeps others in order", Arrays.asList("a", "x", "c"), DockList.remove(d, "b"));
        eq("dock: add appends", Arrays.asList("a", "x", "b", "c", "z"), DockList.add(d, "z"));
        eq("dock: add existing is a no-op", d, DockList.add(d, "b"));
        eq("dock: hidden entry keeps its stored slot", "x", DockList.remove(d, "b").get(1));
    }

    static void greeting() {
        eq("greeting 05:00", "Good Morning", Greeting.forHour(5));
        eq("greeting 11:59", "Good Morning", Greeting.forHour(11));
        eq("greeting 12:00", "Good Afternoon", Greeting.forHour(12));
        eq("greeting 17:00", "Good Evening", Greeting.forHour(17));
        eq("greeting 21:00", "Good Evening", Greeting.forHour(21));
        eq("greeting 22:00", "Good Night", Greeting.forHour(22));
        eq("greeting 00:00", "Good Night", Greeting.forHour(0));
        eq("greeting: next-hour delay from :00", 3600000L, Greeting.millisToNextHour(0));
        eq("greeting: next-hour delay from :59:59", 1000L, Greeting.millisToNextHour(3599000));
    }

    static void catalog(String tsvPath) throws Exception {
        WallpaperCatalog legacy = WallpaperCatalog.parse(Arrays.asList(
                "wp01.jpg\tPlace A\tPhoto: X, CC BY-SA 4.0", "", "bad line"));
        eq("catalog: Calm Home 3-column format parses", 1, legacy.all().size());
        eq("catalog: legacy entries land in Nature", Collections.singletonMap("Nature", 1), legacy.categories());
        eq("catalog: caption keeps the credit", "Place A   ·   Photo: X, CC BY-SA 4.0", legacy.all().get(0).caption());

        WallpaperCatalog c = WallpaperCatalog.parse(Arrays.asList(
                "a.jpg\tA\tPhoto: 1\tNature,Mountains", "b.jpg\tB\tPhoto: 2\tNature,Ocean,Mountains",
                "c.jpg\tC\tPhoto: 3\tNature,Desert", "z.jpg\tZ\tOriginal artwork: ZeroPulse TV\tZeroPulse"));
        Map<String, Integer> cats = c.categories();
        eq("catalog: only non-empty categories, in order", Arrays.asList("Nature", "Mountains", "Ocean", "Desert", "ZeroPulse"),
                new ArrayList<>(cats.keySet()));
        eq("catalog: Mountains count", Integer.valueOf(2), cats.get("Mountains"));
        eq("catalog: artwork stays out of the Nature rotation", 3, c.inCategory("Nature").size());
        eq("catalog: ZeroPulse collection", 1, c.inCategory("ZeroPulse").size());
        eq("catalog: unknown category falls back to all", 4, c.inCategory("Space").size());
        eq("catalog: byFile", "B", c.byFile("b.jpg").place);

        eq("interval: default 60", 60, WallpaperCatalog.DEFAULT_INTERVAL);
        eq("interval: 60 -> next 300", 300, WallpaperCatalog.nextInterval(60, 1));
        eq("interval: static wraps to 30", 30, WallpaperCatalog.nextInterval(0, 1));
        eq("interval: 30 back wraps to static", 0, WallpaperCatalog.nextInterval(30, -1));
        eq("interval: labels", "5 minutes", WallpaperCatalog.intervalLabel(300));
        check("interval: 45 s is not offered", !WallpaperCatalog.isValidInterval(45));

        if (tsvPath != null) {
            List<String> lines = Files.readAllLines(Paths.get(tsvPath), StandardCharsets.UTF_8);
            WallpaperCatalog real = WallpaperCatalog.parse(lines);
            eq("bundled: 9 Wikimedia photos + signature images parse", lines.stream().filter(l -> !l.trim().isEmpty()).count(),
                    (long) real.all().size());
            boolean credited = true;
            for (WallpaperCatalog.Entry e : real.all()) {
                credited &= e.credit.startsWith("Photo: ") || e.credit.startsWith("Original artwork");
                credited &= Files.exists(Paths.get(tsvPath).getParent().resolve(e.file));
            }
            check("bundled: every wallpaper has a credit and its file exists", credited);
        }
    }

    static void commands() {
        List<Named> apps = Arrays.asList(new Named("com.netflix.ninja", "Netflix"),
                new Named("com.google.android.youtube.tv", "YouTube"),
                new Named("com.google.android.youtube.tvunplugged", "YouTube TV"),
                new Named("com.disney.disneyplus", "Disney+"));
        List<Named> inputs = Arrays.asList(new Named("hdmi1", "HDMI 1"), new Named("hdmi2", "HDMI 2"),
                new Named("tuner", "Antenna TV"));
        eq("cmd: open netflix", "com.netflix.ninja", CommandParser.parse("Open Netflix", apps, inputs).target);
        eq("cmd: bare app name", "com.netflix.ninja", CommandParser.parse("netflix", apps, inputs).target);
        eq("cmd: exact beats prefix (youtube)", "com.google.android.youtube.tv", CommandParser.parse("youtube", apps, inputs).target);
        eq("cmd: youtube tv", "com.google.android.youtube.tvunplugged", CommandParser.parse("launch YouTube TV", apps, inputs).target);
        eq("cmd: disney plus symbol", "com.disney.disneyplus", CommandParser.parse("open disney+", apps, inputs).target);
        eq("cmd: switch to hdmi 2", "hdmi2", CommandParser.parse("switch to HDMI 2", apps, inputs).target);
        eq("cmd: antenna", Kind.SWITCH_INPUT, CommandParser.parse("antenna", apps, inputs).kind);
        eq("cmd: home", Kind.HOME, CommandParser.parse("go home", apps, inputs).kind);
        eq("cmd: all apps", Kind.ALL_APPS, CommandParser.parse("All Apps", apps, inputs).kind);
        eq("cmd: please is ignored", Kind.SETTINGS, CommandParser.parse("settings please", apps, inputs).kind);
        eq("cmd: not installed app is UNKNOWN", Kind.UNKNOWN, CommandParser.parse("open plex", apps, inputs).kind);
        eq("cmd: 2-letter fragment does not match", Kind.UNKNOWN, CommandParser.parse("ne", apps, inputs).kind);
        eq("cmd: empty is UNKNOWN", Kind.UNKNOWN, CommandParser.parse("   ", apps, inputs).kind);
    }
}
