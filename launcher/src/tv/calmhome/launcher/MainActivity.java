package tv.calmhome.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.DownloadSummary;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.HeroState;
import tv.calmhome.launcher.data.DownloadWatcher;
import tv.calmhome.launcher.ui.HeroView;
import tv.calmhome.launcher.core.Greeting;
import tv.calmhome.launcher.core.HomeShelf;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.core.GlassMaterial;
import tv.calmhome.launcher.data.AppArt;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.screens.AboutPanel;
import tv.calmhome.launcher.screens.AppsPanel;
import tv.calmhome.launcher.screens.DownloadsPanel;
import tv.calmhome.launcher.downloads.Downloads;
import tv.calmhome.launcher.ui.Navigation;
import tv.calmhome.launcher.screens.InputsPanel;
import tv.calmhome.launcher.screens.MenuPanel;
import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.screens.SettingsPanel;
import tv.calmhome.launcher.screens.WallpapersPanel;
import tv.calmhome.launcher.ui.AdaptiveScrimView;
import tv.calmhome.launcher.ui.FramedImageView;
import tv.calmhome.launcher.streaming.StreamingActivity;
import tv.calmhome.launcher.web.WebActivity;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse TV Home ("Cinematic Overlay"). This class name is the TV's Home component
 * (tv.calmhome.launcher/.MainActivity) and must not be renamed, or the Home setting breaks.
 *
 * Layout on the 960x540 dp canvas, inside 5% safe margins:
 *   top bar (logo · navigation pill · clock) / greeting / dock / quick-action chips / photo credit.
 */
public class MainActivity extends Activity implements Panel.Host {

    /**
     * Intent extra: open a screen on arrival ("apps", "inputs", "wallpapers", "settings[:category]", "about",
     * "streaming", "web"). Modules that are switched off are refused.
     */
    public static final String EXTRA_OPEN = "zp_open";

    static final int DOCK_COLUMNS = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Theme theme;
    private Widgets w;
    private Apps apps;
    private AppArt art;
    /** Update dots come only from here; no authorised source exists, so none is shown (see AppUpdates). */
    private final tv.calmhome.launcher.data.AppUpdates updates = tv.calmhome.launcher.data.AppUpdates.NONE;
    private String lastLaunched;
    private HeroView hero;
    private tv.calmhome.launcher.data.UiSounds sounds;
    private final tv.calmhome.launcher.core.KonamiDetector konami = new tv.calmhome.launcher.core.KonamiDetector();
    private View wash;                                       // Liquid Glass's thin wash over the wallpaper (time-of-day tint)
    private View[] walls = new View[0];                      // the wallpaper layers (wallpaper depth)
    private final HeroState heroState = new HeroState();
    private DownloadWatcher downloadWatcher;
    private DownloadSummary downloadSummary = DownloadSummary.IDLE;
    private int featuredIndex;
    private final Runnable heroTick = new Runnable() {
        @Override public void run() {
            updateHero();
            handler.postDelayed(this, 1000);
        }
    };
    private WallpaperStage stage;
    private AdaptiveScrimView scrim;               // Liquid Glass only
    private int plannedDim = -1;

    private FrameLayout root, mainUi;
    private View dim, graphite;
    private boolean started;
    private LinearLayout recentRow;                // graphite Home only
    private List<String> shownRecent;
    private TextView shelfTitle;
    private FrameLayout footer;
    private boolean showingPhoto;
    private HomeShelf shelf = HomeShelf.compute(java.util.Collections.<String>emptyList(), java.util.Collections.<String>emptyList(),
            java.util.Collections.<String>emptyList(), java.util.Collections.<String>emptySet(), p -> false, 0);
    private LinearLayout dock, greetingBox, bottomBlock, allAppsDrawer, allAppsRow;
    private Navigation navigation;
    private HorizontalScrollView dockScroll;
    private android.widget.ScrollView allAppsScroll;
    private android.animation.ObjectAnimator allAppsScrollAnim;
    private TextView greeting, credit;
    private boolean drawerOpen;
    private View dockReturnFocus;
    private int tileW, tileH;
    private int tilePx, tileHpx, rowExtraPx;       // Liquid Glass: the tile row in whole pixels
    private final List<Panel> panels = new ArrayList<>();
    private BroadcastReceiver packageReceiver;

    /** Liquid Glass's crash guard is cleared when Home has drawn its first frame and stayed up for 8 seconds. */
    private final Runnable disarmLiquidGuard = new Runnable() {
        @Override public void run() {
            prefs.setLiquidStarting(false);
            android.util.Log.i("ZeroPulseGlass", "Home up for 8 s. " + theme.kit.diagnostics());
        }
    };

    private final Runnable greetingTick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            applyTimeTint();
            handler.postDelayed(this, Greeting.millisToNextHour(System.currentTimeMillis() % 3600000L) + 1000);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pickerCategory = state.getString("apkPickerCategory");
        prefs = new Prefs(this);
        // The Assistant (JARVIS) was removed: delete what it stored, once, on a worker (only its own files and keys; see the class).
        tv.calmhome.launcher.data.LegacyAssistantCleanup.runOnceInBackground(this);
        // Liquid Glass is optional. If the guard is still armed, the last Liquid Glass start never finished: turn it off.
        boolean liquid = prefs.liquidGlass(), fellBack = false;
        if (liquid && prefs.liquidStarting()) {
            prefs.setLiquidGlass(false);
            prefs.setLiquidStarting(false);
            liquid = false;
            fellBack = true;
        } else if (liquid) {
            prefs.setLiquidStarting(true);
        }
        theme = new Theme(this, prefs, liquid);
        android.util.Log.i("ZeroPulseGlass", "Home look: " + (liquid ? "Liquid Glass (" + (prefs.liquidGlassIsExplicit() ? "chosen" : "default") + ")"
                : fellBack ? "graphite (Liquid Glass failed to start last time and was turned off)" : "graphite (Liquid Glass is off)"));
        w = new Widgets(this, theme);
        apps = new Apps(this);
        art = new AppArt(this, apps);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.DEEP_BLACK));
        buildHome();
        setContentView(root);
        if (theme.liquid) theme.kit.setAnchor(root);               // the glass clock and navigation measure from the screen
        if (BuildConfig.STANDALONE_ZPAPPS) {
            mainUi.setVisibility(View.GONE);
            mainUi.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        } else {
            buildDock();
            buildAllAppsCarousel();
        }
        sounds = new tv.calmhome.launcher.data.UiSounds(this, prefs);
        downloadWatcher = new DownloadWatcher(this, new DownloadWatcher.Listener() {
            @Override public void onSummary(DownloadSummary s) { onDownloads(s); }

            @Override public void onFinished(String name, boolean ok) { onDownloadFinished(name, ok); }
        });
        if (fellBack) toast("Liquid Glass was turned off because it did not start last time");
        if (liquid) {
            root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    handler.postDelayed(disarmLiquidGuard, 8000);
                    return true;
                }
            });
        }
        if (!BuildConfig.STANDALONE_ZPAPPS) {
            root.post(() -> { if (panels.isEmpty()) focusFirstDockTile(); });
        }
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (!BuildConfig.STANDALONE_ZPAPPS) {
                updateShelfHint(now);
            }
            updateParallax(now);
            if (was != null && now != null && panels.isEmpty()) sounds.play(tv.calmhome.launcher.data.UiSounds.Kind.FOCUS);
        });

        packageReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i.getData() != null) {
                    apps.invalidate(i.getData().getSchemeSpecificPart());
                    art.invalidate(i.getData().getSchemeSpecificPart());           // an updated app may ship new artwork
                }
                w.clearLogoCache();
                if (!BuildConfig.STANDALONE_ZPAPPS) {
                    buildDock();
                    buildAllAppsCarousel();
                    refreshRecents(true);
                }
                for (Panel panel : panels) panel.onPackagesChanged();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        if (!handleOpenExtra(getIntent()) && state != null && state.getString("restoreSection") != null) {
            handleOpenExtra(new Intent().putExtra(EXTRA_OPEN, state.getString("restoreSection")));
        }
        if (BuildConfig.STANDALONE_ZPAPPS && panels.isEmpty()) {
            open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        syncBackdrop();
        refreshRecents(false);
        handler.removeCallbacks(greetingTick);
        greetingTick.run();
        downloadWatcher.start();
        handler.removeCallbacks(heroTick);
        heroTick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (panels.isEmpty() && root.findFocus() == null) restoreLaunchFocus();
        for (Panel panel : new ArrayList<>(panels)) panel.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        stage.stop();
        handler.removeCallbacks(greetingTick);
        handler.removeCallbacks(heroTick);
        downloadWatcher.stop();                                    // no queue reads while Home is not on screen
        handler.removeCallbacks(disarmLiquidGuard);
        if (theme.liquid) prefs.setLiquidStarting(false);          // leaving Home is not a crash
    }

    @Override
    protected void onDestroy() {
        releaseHeld();                                             // a screen held mid-transition is closed properly too
        for (Panel panel : panels) panel.onClosed();
        unregisterReceiver(packageReceiver);
        handler.removeCallbacks(disarmLiquidGuard);
        if (theme.liquid) {
            prefs.setLiquidStarting(false);
            theme.kit.shutdown();
        }
        stage.shutdown();
        downloadWatcher.shutdown();
        sounds.release();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleOpenExtra(intent)) return;
        if (BuildConfig.STANDALONE_ZPAPPS) {
            while (panels.size() > 1) close(panels.get(panels.size() - 1));
            if (panels.isEmpty()) open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this));
            return;
        }
        // Home pressed while already home: close everything and return to the first app.
        closeAll();
        closeAllAppsDrawer(false);
        dockScroll.smoothScrollTo(0, 0);
        focusFirstDockTile();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (BuildConfig.STANDALONE_ZPAPPS) {
            if (panels.isEmpty()) { finish(); return; }
            Panel top = panels.get(panels.size() - 1);
            if (top.onBack()) return;
            if (panels.size() > 1) close(top);
            else if (!(top instanceof tv.calmhome.launcher.toolbox.ZpAppsPanel)) navigate("Toolbox");
            else finish();
            return;
        }
        // Android 12 (this TV) still routes Back here; Home itself ignores Back (except closing an open bottom carousel).
        if (panels.isEmpty()) {
            if (drawerOpen) closeAllAppsDrawer(true);
            return;
        }
        Panel top = panels.get(panels.size() - 1);
        if (!top.onBack()) close(top);
    }

    private boolean handleOpenExtra(Intent intent) {
        String open = intent == null ? null : intent.getStringExtra(EXTRA_OPEN);
        if (open == null) return false;
        intent.removeExtra(EXTRA_OPEN);
        closeAll();
        String[] parts = open.split(":", 2);
        switch (parts[0]) {
            case "home": focusFirstDockTile(); break;
            // The Assistant (JARVIS) was removed in 3.0.0-beta.7: an old shortcut or saved route to it lands on Home.
            case "assistant": focusFirstDockTile(); break;
            case "apps": open(new AppsPanel(this)); break;
            case "downloads":
                open(new DownloadsPanel(this));
                if (parts.length > 1 && "library".equals(parts[1])) open(DownloadsPanel.library(this));
                break;
            case "zpapps": open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this)); break;
            case "downloader": open(new tv.calmhome.launcher.screens.DownloaderPanel(this)); break;
            case "inputs": open(new InputsPanel(this)); break;
            case "wallpapers": open(new WallpapersPanel(this)); break;
            case "wallpaper-info": open(new tv.calmhome.launcher.screens.WallpaperInfoPanel(this)); break;
            case "settings": open(new SettingsPanel(this, parts.length > 1 ? parts[1] : null)); break;
            case "about": open(new AboutPanel(this)); break;
            case "streaming": openModule(Features.Module.STREAMING); break;
            case "web": openModule(Features.Module.WEB); break;
            default: return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Home layout

    private void buildHome() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        FramedImageView wallA = wallpaperView(), wallB = wallpaperView();
        root.addView(wallA, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        root.addView(wallB, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        FramedImageView frost = wallpaperView();
        root.addView(frost, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        walls = new View[]{wallA, wallB, frost};
        stage = new WallpaperStage(this, prefs, wallA, wallB, frost, this::onWallpaper);
        stage.setFadeMs(theme.fadeMs(1500));
        if (theme.liquid) {
            stage.setBackdropSink(blurred -> theme.backdrop.set(blurred, prefs.dimPercent()));
            stage.setSceneSink(wallpaper -> theme.kit.setWallpaper(wallpaper, prefs.dimPercent()));
            stage.setContrastSink(plan -> scrim.setPlan(plan, theme.fadeMs(900)));
        }

        if (theme.liquid) {
            wash = new View(this);
            wash.setBackgroundColor(LiquidTokens.WASH);
            root.addView(wash, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        }
        dim = new View(this);
        root.addView(dim, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        if (theme.liquid) {
            // The adaptive contrast layer: only as dark as the wallpaper behind each band of the screen needs (see ContrastProbe).
            scrim = new AdaptiveScrimView(this);
            root.addView(scrim, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        } else {
            View topShade = new View(this);
            topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
            root.addView(topShade, new FrameLayout.LayoutParams(Widgets.MATCH, theme.rawDp(150), Gravity.TOP));
            View bottomShade = new View(this);
            bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE0000000, 0x8C000000, 0x00000000}));
            root.addView(bottomShade, new FrameLayout.LayoutParams(Widgets.MATCH, theme.rawDp(300), Gravity.BOTTOM));
        }

        graphite = new View(this);
        graphite.setBackground(new tv.calmhome.launcher.ui.GraphiteBackdrop());
        root.addView(graphite, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));

        mainUi = new FrameLayout(this);
        mainUi.setClipChildren(false);
        root.addView(mainUi, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        int sx = theme.safeX(), sy = theme.safeY();

        navigation = new Navigation(this, "Home");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.TOP);
        tl.setMargins(sx, sy, sx, 0);
        mainUi.addView(navigation, tl);

        // Area C: ambient greeting
        greetingBox = new LinearLayout(this);
        greetingBox.setOrientation(LinearLayout.VERTICAL);
        greetingBox.setGravity(Gravity.START);
        // Full-width, centred, single-line text: on the TV the content-sized greeting showed only "Good".
        greeting = theme.text(this, Theme.T_GREETING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        theme.overPhoto(greeting);
        greeting.setGravity(theme.liquid ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        greeting.setSingleLine(true);
        if (theme.liquid) theme.style(greeting, Theme.T_GREETING, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        updateGreeting();
        greetingBox.addView(greeting, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        TextView tagline = theme.text(this, Theme.T_TAGLINE, Theme.withAlpha(Theme.SOFT_WHITE, 0.88f), Theme.FONT_REGULAR);
        tagline.setText(theme.liquid ? "Relax  ·  Watch  ·  Explore" : "Your TV  ·  Android " + android.os.Build.VERSION.RELEASE);
        tagline.setLetterSpacing(0.06f);
        tagline.setGravity(theme.liquid ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        if (theme.liquid) theme.style(tagline, Theme.T_TAGLINE, Theme.withAlpha(Theme.SOFT_WHITE, 0.82f), Theme.FONT_LIGHT);
        tagline.setSingleLine(true);
        theme.overPhoto(tagline);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        gl.topMargin = theme.dp(8);
        greetingBox.addView(tagline, gl);

        FrameLayout.LayoutParams gbl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.CENTER_HORIZONTAL);
        // Liquid Glass: the greeting sits lower (31 % of the height, was 25 %), closing the empty band above the app row.
        gbl.setMargins(sx, Math.round(theme.screenH() * (theme.liquid ? .31f : .19f)), sx, 0);
        // The centre of Home: the greeting exactly as it was, in a region of its own size that can also show a live download,
        // a short notice or (when turned on) a featured app, without moving anything else (see HeroView, HeroState).
        hero = new HeroView(this, theme, greetingBox);
        mainUi.addView(hero, gbl);

        // Areas D + E + footer, stacked from the bottom safe margin
        LinearLayout bottom = new LinearLayout(this);
        bottomBlock = bottom;
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);

        // Liquid Glass Home is one clean row of apps: the "Recently opened" shelf (an empty placeholder for most viewers) is
        // gone from it. The history is still recorded and is listed under All Apps > Recent.
        if (!theme.liquid) {
            bottom.addView(shelfLabel("Recently opened", sx));
            recentRow = new LinearLayout(this);
            recentRow.setGravity(Gravity.CENTER_VERTICAL);
            recentRow.setClipChildren(false);
            recentRow.setPadding(sx, 0, sx, 0);
            bottom.addView(recentRow, new LinearLayout.LayoutParams(Widgets.MATCH, theme.dp(35)));
        }
        shelfTitle = shelfLabel("Favorites", sx);
        bottom.addView(shelfTitle);
        float availDp = (theme.screenW() - 2f * sx) / getResources().getDisplayMetrics().density / theme.scale;
        tileW = Math.round((availDp - (DOCK_COLUMNS - 1) * Theme.GAP) / DOCK_COLUMNS);
        tileH = Math.round(tileW * 9f / 16f);
        if (theme.liquid) {
            // Liquid Glass: whole pixels, so seven tiles and their gaps end exactly where the five quick actions below end,
            // and tiles a little taller than 16:9 (see GlassMaterial.TILE_ASPECT).
            int[] row = GlassMaterial.rowPx(theme.screenW() - 2 * sx, DOCK_COLUMNS, theme.dp(Theme.GAP));
            tilePx = row[0];
            rowExtraPx = row[1];
            tileHpx = Math.max(1, Math.round(tilePx / GlassMaterial.TILE_ASPECT));
            tileH = Math.round(tileHpx / theme.pxPerDp());
        }
        tv.calmhome.launcher.ui.SafeMarginScrollView carousel = new tv.calmhome.launcher.ui.SafeMarginScrollView(this);
        // Focus scrolling keeps the focused tile inside the safe margin, gliding there (the Favorites carousel); instant with reduced motion.
        carousel.setCarousel(!theme.reducedMotion, tv.calmhome.launcher.core.MotionTokens.CAROUSEL_MS);
        dockScroll = carousel;
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setClipChildren(false);
        // Side padding = safe margin, so a focused (scaled) end tile is never cut off by the screen edge.
        dockScroll.setPadding(sx, theme.dp(10), sx, 0);
        dock = new LinearLayout(this);
        if (!theme.liquid) dock.setBackground(theme.glass(Theme.R_PANEL, .52f));       // Liquid Glass tiles sit on the scene itself
        dock.setClipChildren(false);
        dockScroll.addView(dock);
        bottom.addView(dockScroll, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        if (theme.liquid) {
            bottom.addView(buildPeekHandle(), peekHandleParams());
        }

        footer = new FrameLayout(this);
        footer.setPadding(sx, 0, sx, 0);
        credit = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.72f), Theme.FONT_REGULAR);
        theme.overPhoto(credit);
        footer.addView(credit, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout powered = new LinearLayout(this);
        powered.setGravity(Gravity.CENTER_VERTICAL);
        TextView pw = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.6f), Theme.FONT_REGULAR);
        pw.setText("ZeroPulse TV  ·  Local launcher");
        powered.addView(pw);
        if (theme.liquid) powered.setVisibility(View.GONE);          // the photo credit stays (the licences need it); the slogan goes
        footer.addView(powered, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        fl.topMargin = theme.dp(10);
        bottom.addView(footer, fl);

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM);
        bl.bottomMargin = sy;
        mainUi.addView(bottom, bl);

        buildAllAppsDrawer(sx, sy);

        applyHomePrefs();
    }

    private LinearLayout.LayoutParams peekHandleParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = theme.dp(14);
        lp.bottomMargin = theme.dp(16);
        return lp;
    }

    /** Subtle, ultra-slim non-focusable glass handle at the bottom edge hinting at the All Apps & ZPAPPS drawer below. */
    private View buildPeekHandle() {
        final tv.calmhome.launcher.ui.ZeroPulseGlassTileBackground bg = new tv.calmhome.launcher.ui.ZeroPulseGlassTileBackground(
                this, theme.kit, tv.calmhome.launcher.ui.GraphiteGlassSurface.Shape.CAPSULE, theme.dp(Widgets.QUICK_ACTION_DP));
        bg.pinMaterialHeight(theme.dp(Widgets.QUICK_ACTION_DP));
        bg.setPresence(0.65f);
        FrameLayout handle = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                bg.measure(
                        MeasureSpec.makeMeasureSpec(Math.max(1, getMeasuredWidth()), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(1, getMeasuredHeight()), MeasureSpec.EXACTLY));
            }
        };
        handle.setFocusable(false);
        handle.setClickable(false);
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        handle.addView(bg, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        TextView cue = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.80f), Theme.FONT_MEDIUM);
        cue.setText("⌄  All Apps & ZPAPPS");
        cue.setSingleLine(true);
        cue.setPadding(theme.dp(14), theme.dp(3), theme.dp(14), theme.dp(4));
        handle.addView(cue, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.CENTER));
        return handle;
    }

    private void buildAllAppsDrawer(int sx, int sy) {
        allAppsDrawer = new LinearLayout(this);
        allAppsDrawer.setOrientation(LinearLayout.VERTICAL);
        allAppsDrawer.setClipChildren(false);
        allAppsDrawer.setClipToPadding(false);
        allAppsDrawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        allAppsDrawer.setVisibility(View.INVISIBLE);
        allAppsDrawer.setAlpha(0f);
        allAppsDrawer.setTranslationY(offscreenDrawerY());
        allAppsDrawer.setPadding(sx, 0, sx, 0);

        FrameLayout header = new FrameLayout(this);
        header.setPadding(0, 0, 0, theme.dp(10));
        TextView title = theme.text(this, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("All Apps & ZPAPPS");
        header.addView(title, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView hint = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.72f), Theme.FONT_REGULAR);
        hint.setText("↓↑ Scroll carousel  ·  OK opens  ·  ↑ at top or Back returns Home");
        header.addView(hint, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        allAppsDrawer.addView(header, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        android.widget.ScrollView scroller = new android.widget.ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        allAppsScroll = scroller;

        allAppsRow = new LinearLayout(this);
        allAppsRow.setOrientation(LinearLayout.VERTICAL);
        allAppsRow.setClipChildren(false);
        allAppsRow.setClipToPadding(false);
        allAppsRow.setPadding(0, theme.dp(16), 0, theme.dp(120));
        int carouselW = Math.min(theme.screenW() - 2 * sx, theme.dp(720));
        android.widget.FrameLayout.LayoutParams rowLp = new android.widget.FrameLayout.LayoutParams(carouselW, Widgets.WRAP, Gravity.CENTER_HORIZONTAL);
        allAppsScroll.addView(allAppsRow, rowLp);
        allAppsDrawer.addView(allAppsScroll, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));

        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH);
        dlp.setMargins(0, theme.dp(92) + sy, 0, sy);
        mainUi.addView(allAppsDrawer, dlp);
    }

    private TextView shelfLabel(String label, int sx) {
        TextView v = theme.text(this, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        v.setText(label); v.setPadding(sx, theme.dp(4), sx, theme.dp(3));
        return v;
    }

    private void refreshRecents(boolean force) {
        if (recentRow == null) return;                       // Liquid Glass Home has no recents shelf
        List<String> visible = DockList.visible(prefs.recentlyOpened(), apps::usable);
        if (!force && visible.equals(shownRecent)) return;
        shownRecent = visible;
        Object focused = recentRow.findFocus() == null ? null : recentRow.findFocus().getTag();
        recentRow.removeAllViews();
        if (visible.isEmpty()) {
            TextView empty = theme.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            empty.setText("Apps you open from ZeroPulse will appear here.");
            recentRow.addView(empty);
        }
        for (String pkg : visible) {
            TextView app = w.navItem(apps.label(pkg), false, v -> launchApp(pkg));
            app.setBackground(theme.glass(Theme.R_PILL, .8f));
            app.setTag(pkg);
            app.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.graphics.drawable.Drawable icon = apps.icon(pkg);
            if (icon != null) {
                icon.setBounds(0, 0, theme.dp(17), theme.dp(17));
                app.setCompoundDrawablesRelative(icon, null, null, null);
                app.setCompoundDrawablePadding(theme.dp(4));
            }
            int width = (theme.screenW() - 2 * theme.safeX() - theme.dp(25)) / 6;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, Widgets.WRAP);
            if (recentRow.getChildCount() > 0) lp.leftMargin = theme.dp(5);
            recentRow.addView(app, lp);
        }
        if (focused != null) {
            View restored = recentRow.findViewWithTag(focused);
            if (restored != null) restored.requestFocus();
        }
    }

    private void syncBackdrop() {
        Panel base = null;
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (!panels.get(i).isModal()) { base = panels.get(i); break; }
        }
        boolean obscured = false;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            p.view().setVisibility(obscured && p.view() != held ? View.INVISIBLE : View.VISIBLE);
            if (!p.isModal()) obscured = true;
        }
        mainUi.setVisibility(obscured && held != mainUi ? View.INVISIBLE : View.VISIBLE);
        mainUi.setAlpha(panels.isEmpty() || held == mainUi ? 1f : .6f);
        boolean photo = theme.liquid || (base == null ? prefs.usePhotoBackdrop() : base instanceof WallpapersPanel);
        graphite.setVisibility(photo ? View.GONE : View.VISIBLE);
        showingPhoto = photo;
        applyCredit();
        stage.setFrosted(photo && base != null);
        if (photo && started) stage.start(); else stage.stop();
    }

    private FramedImageView wallpaperView() {
        return new FramedImageView(this);                 // fills the screen: a screen-shaped window centred on the picture's focal point
    }

    private void onWallpaper(WallpaperCatalog.Entry e) {
        // Liquid Glass Home shows no credit line for any wallpaper (homeCredit() is empty): the credit, licence and what was changed
        // are on the Wallpaper information screen, one step from Home (Wallpapers > Information) and from About. The graphite
        // Home keeps the caption it always had.
        credit.setText(theme.liquid ? e.homeCredit() : e.caption());
        applyCredit();
        for (Panel panel : panels) panel.updateCredit(e.caption());
    }

    /** The credit line is shown over a photo, and only when there is something to credit; Liquid Glass's footer then collapses. */
    private void applyCredit() {
        boolean showCredit = showingPhoto && credit.length() > 0;
        credit.setVisibility(showCredit ? View.VISIBLE : View.GONE);
        if (theme.liquid) footer.setVisibility(showCredit ? View.VISIBLE : View.GONE);      // nothing else lives in Liquid Glass's footer
    }

    private void updateGreeting() {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(tv.calmhome.launcher.core.SystemTime.now());
        greeting.setText(Greeting.forHour(now.get(Calendar.HOUR_OF_DAY)));
    }

    // ------------------------------------------------------------------ ambient: tint, wallpaper depth, the secret

    /** The wash's very small time-of-day shift (Liquid Glass only; Settings > Appearance > Time-of-day tint). */
    private void applyTimeTint() {
        if (wash == null) return;
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(tv.calmhome.launcher.core.SystemTime.now());
        wash.setBackgroundColor(prefs.timeOfDayTint() ? tv.calmhome.launcher.core.TimeTint.wash(LiquidTokens.WASH, now.get(Calendar.HOUR_OF_DAY))
                : LiquidTokens.WASH);
    }

    /**
     * Wallpaper depth (off by default; Settings > Appearance): the wallpaper drifts a few dp away from where focus is. Only the
     * wallpaper layers move, as a transform, and they are enlarged by just enough to cover the drift (no blank edge); the glass is
     * not re-rendered for it, so while depth is on the glass's refraction can lag the drift by up to MotionTokens.PARALLAX_X_DP dp.
     * That trade-off is why it is a choice and not the default.
     */
    private void updateParallax(View focused) {
        boolean on = theme.liquid && prefs.wallpaperDepth() && !theme.reducedMotion && walls.length > 0;
        if (!on && (walls.length == 0 || (walls[0].getTranslationX() == 0f && walls[0].getTranslationY() == 0f && walls[0].getScaleX() == 1f))) return;  // off: nothing to do
        float density = getResources().getDisplayMetrics().density;
        float tx = 0f, ty = 0f;
        if (on && focused != null) {
            int[] at = new int[2];
            focused.getLocationInWindow(at);
            float cx = (at[0] + focused.getWidth() / 2f) / Math.max(1, root.getWidth()) - 0.5f;
            float cy = (at[1] + focused.getHeight() / 2f) / Math.max(1, root.getHeight()) - 0.5f;
            tx = -2f * cx * tv.calmhome.launcher.core.MotionTokens.PARALLAX_X_DP * density;
            ty = -2f * cy * tv.calmhome.launcher.core.MotionTokens.PARALLAX_Y_DP * density;
        }
        float over = on ? 1f + 2f * tv.calmhome.launcher.core.MotionTokens.PARALLAX_X_DP * density / Math.max(1, root.getWidth()) : 1f;
        for (View v : walls) {
            v.setScaleX(over);
            v.setScaleY(over);
            v.animate().translationX(tx).translationY(ty).setDuration(on ? tv.calmhome.launcher.core.MotionTokens.PARALLAX_MS : 0).start();
        }
    }

    /** For tests: Liquid Glass's wash layer. */
    View washLayer() {
        return wash;
    }

    /** For tests: the interface sounds. */
    tv.calmhome.launcher.data.UiSounds sounds() {
        return sounds;
    }

    /** For tests: the wallpaper layers. */
    View[] wallpaperLayers() {
        return walls;
    }

    /**
     * Watches Home's remote presses for the secret (Up, Up, Down, Down, Left, Right, Left, Right). It only observes: every key still
     * moves focus exactly as before. Not while a panel is open or text is being typed.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            View f = root == null ? null : root.findFocus();
            if (!panels.isEmpty() || f instanceof android.widget.EditText) {
                konami.reset();
            } else {
                tv.calmhome.launcher.core.KonamiDetector.Key k;
                switch (e.getKeyCode()) {
                    case android.view.KeyEvent.KEYCODE_DPAD_UP: k = tv.calmhome.launcher.core.KonamiDetector.Key.UP; break;
                    case android.view.KeyEvent.KEYCODE_DPAD_DOWN: k = tv.calmhome.launcher.core.KonamiDetector.Key.DOWN; break;
                    case android.view.KeyEvent.KEYCODE_DPAD_LEFT: k = tv.calmhome.launcher.core.KonamiDetector.Key.LEFT; break;
                    case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: k = tv.calmhome.launcher.core.KonamiDetector.Key.RIGHT; break;
                    default: k = tv.calmhome.launcher.core.KonamiDetector.Key.OTHER; break;
                }
                if (konami.press(k, e.getEventTime())) secretFound();
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_DOWN && !drawerOpen && f != null && isInside(f, dock)) {
                    openAllAppsDrawer();
                    return true;
                }
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP && drawerOpen && f != null && isInside(f, allAppsDrawer)) {
                    View firstRow = allAppsRow != null && allAppsRow.getChildCount() > 0 ? allAppsRow.getChildAt(0) : null;
                    if (firstRow != null && (f == firstRow || (firstRow instanceof ViewGroup && isInside(f, (ViewGroup) firstRow)))) {
                        closeAllAppsDrawer(true);
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private int secrets;

    /** The secret: a ripple across the wallpaper (unless motion is reduced), a short notice and a soft chime. Then all is as it was. */
    private void secretFound() {
        secrets++;
        if (!theme.reducedMotion) {
            tv.calmhome.launcher.ui.RippleView ripple = new tv.calmhome.launcher.ui.RippleView(this);
            root.addView(ripple, root.indexOfChild(mainUi), new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));   // over the wallpaper, under Home
            ripple.play();
        }
        postNotice("ZeroPulse Secret Unlocked ⚡");
        sounds.play(tv.calmhome.launcher.data.UiSounds.Kind.SUCCESS);
    }

    /** For tests: how many times the secret was found. */
    int secretsFound() {
        return secrets;
    }

    // ------------------------------------------------------------------ contextual hint

    private boolean shelfHint;
    static final String SHELF_HINT = "Favorites  ·  hold OK to open, see app info or uninstall";

    /**
     * While a favorite has focus, the row's own title says what OK held does (a crossfade of the same one-line label: nothing moves,
     * no button changes meaning). It goes back to "Favorites" when focus leaves the row.
     */
    private void updateShelfHint(View focused) {
        boolean want = focused != null && shelf.mode == HomeShelf.Mode.FAVORITES && isInside(focused, dock);
        if (want == shelfHint) return;
        shelfHint = want;
        final String text = want ? SHELF_HINT : "Favorites";
        int half = tv.calmhome.launcher.core.MotionTokens.of(tv.calmhome.launcher.core.MotionTokens.CONTEXT_FADE_MS / 2, theme.reducedMotion);
        shelfTitle.animate().cancel();
        if (half == 0) { shelfTitle.setText(text); shelfTitle.setAlpha(1f); return; }
        shelfTitle.animate().alpha(0f).setDuration(half).withEndAction(() -> {
            shelfTitle.setText(text);
            shelfTitle.animate().alpha(1f).setDuration(half).start();
        }).start();
    }

    /** For tests: the Favorites row's title. */
    TextView shelfTitle() {
        return shelfTitle;
    }

    // ------------------------------------------------------------------ the centre of Home (hero) and live downloads

    /** A real download sample: the centre and the Downloads destination follow it. */
    void onDownloads(DownloadSummary s) {
        downloadSummary = s;
        if (s.isActive()) hero.setDownload(s);
        navigation.setDownloadProgress(s.isActive() ? s.aggregate : Float.NaN);
        updateHero();
    }

    /** A download that was moving has finished or failed: a short notice in the centre, and a brief acknowledgment on Downloads. */
    void onDownloadFinished(String name, boolean ok) {
        heroState.post(ok ? "Downloaded  ·  " + name : "Download failed  ·  " + name, android.os.SystemClock.uptimeMillis());
        navigation.acknowledgeDownload(ok);
        sounds.play(ok ? tv.calmhome.launcher.data.UiSounds.Kind.SUCCESS : tv.calmhome.launcher.data.UiSounds.Kind.ERROR);
        updateHero();
    }

    /** A short notice in the centre of Home (a finished download, a secret found). */
    public void postNotice(String text) {
        heroState.post(text, android.os.SystemClock.uptimeMillis());
        updateHero();
    }

    private void updateHero() {
        boolean featuredAllowed = prefs.featuredOnHome() && !shelf.packages.isEmpty();
        if (!heroState.update(android.os.SystemClock.uptimeMillis(), downloadSummary.isActive(), featuredAllowed)) return;
        HeroState.Kind k = heroState.kind();
        if (k == HeroState.Kind.NOTIFICATION) hero.setNotice(heroState.notice());
        if (k == HeroState.Kind.FEATURED) pickFeatured();
        hero.showKind(k);
    }

    /** The next featured app: an installed favorite with its own artwork, in turn. Never an app that is not installed. */
    private void pickFeatured() {
        List<String> pkgs = shelf.packages;
        if (pkgs.isEmpty()) { hero.setFeatured(null, null); return; }
        String pkg = pkgs.get(Math.floorMod(featuredIndex++, pkgs.size()));
        int h = Math.max(theme.dp(40), Math.round(hero.getHeight() * 0.86f));
        AppArt.Art a = art.get(pkg, Math.round(h * GlassMaterial.TILE_ASPECT), h);
        hero.setFeatured(a.isGenuine() ? a : null, apps.label(pkg));
    }

    /** For tests that feed simulated download samples: stops reading the real queue so it cannot overwrite them. */
    void pauseDownloadWatcherForTests() {
        downloadWatcher.stop();
    }

    /** For tests: the centre of Home. */
    public HeroView hero() {
        return hero;
    }

    /** Applies preferences that change Home without rebuilding it. */
    private void applyHomePrefs() {
        hero.setGreetingEnabled(prefs.showGreeting());            // the region stays (a download can still show there); the words go
        applyTimeTint();
        if (root != null) updateParallax(root.findFocus());
        navigation.refresh(this);
        navigation.showSelected();                                 // Home again: its header marks Home, not the screen it last opened
        // What's New: a discreet dot on Settings until this version's notes have been opened (Settings > About > What's new).
        navigation.setWhatsNewBadge(tv.calmhome.launcher.core.WhatsNew.unseen(BuildConfig.VERSION_NAME, prefs.whatsNewSeen()));
        syncBackdrop();
        if (theme.liquid) {
            theme.backdrop.setDim(prefs.dimPercent());
            theme.kit.setDim(prefs.dimPercent());
            if (plannedDim != prefs.dimPercent()) {            // the viewer's own dim already does part of the scrim's work: re-plan
                plannedDim = prefs.dimPercent();
                stage.refreshOptics();
            }
        }
        dim.setBackgroundColor(Color.argb(Math.round(2.55f * prefs.dimPercent()), 0, 0, 0));
    }

    // ------------------------------------------------------------------ dock

    void buildDock() {
        // Package broadcasts (many at boot) rebuild the dock; removing the tiles would drop focus onto the
        // first remaining focusable view, so remember whether focus belongs in the dock.
        View focused = root.findFocus();
        boolean focusInDock = focused == null || isInside(focused, dock);
        Object focusedPackage = focused != null && focusInDock ? focused.getTag() : null;
        dock.removeAllViews();
        shelf = shelf();
        shelfTitle.setText(shelf.mode == HomeShelf.Mode.SUGGESTIONS ? "Suggested apps  ·  hold OK to add one to Favorites"
                : shelf.mode == HomeShelf.Mode.EMPTY ? "Apps" : "Favorites");
        shelfHint = false;                                          // the title was just reset; the next focus move re-applies the hint
        final boolean suggestions = shelf.mode == HomeShelf.Mode.SUGGESTIONS;
        for (String pkg : shelf.packages) {
            View.OnLongClickListener menu = v -> { if (suggestions) suggestionMenu(pkg); else dockMenu(pkg); return true; };
            View.OnClickListener open = v -> launchApp(pkg);
            View tile = theme.liquid
                    ? w.glassAppTilePx(pkg, art.get(pkg, tilePx, tileHpx), apps.label(pkg), tilePx + (dock.getChildCount() < rowExtraPx ? 1 : 0), tileHpx,
                            prefs.labelsAlways(), open, menu)
                    : w.appTile(pkg, apps.banner(pkg), apps.icon(pkg), apps.label(pkg), tileW, tileH, prefs.labelsAlways(), open, menu);
            LinearLayout.LayoutParams lp = w.wrap();
            if (dock.getChildCount() > 0) lp.leftMargin = theme.dp(Theme.GAP);
            dock.addView(tile, lp);
            Widgets.cardOf(tile).setTag(pkg);
            View card = Widgets.cardOf(tile);
            if (card instanceof tv.calmhome.launcher.ui.ZeroPulseGlassAppTile) {
                ((tv.calmhome.launcher.ui.ZeroPulseGlassAppTile) card).setUpdateAvailable(updates.updateAvailable(pkg));
            }
        }
        final boolean modulesFollow = !Features.homeEntries(prefs).isEmpty();
        if (dock.getChildCount() == 0 && !modulesFollow) {
            // Nothing is saved and nothing is launchable: say so (never a tile for an app that is not installed).
            View tile = theme.liquid
                    ? w.glassNotice("No apps to show yet", "Nothing launchable is installed on this TV. Open All Apps once you install one.",
                            (tileW + Theme.GAP) * DOCK_COLUMNS - Theme.GAP, tileH, v -> open(new AppsPanel(this)))
                    : w.appTile(null, new Glyph(Glyph.Kind.GRID, Theme.SOFT_WHITE), "Add apps", tileW, tileH, true,
                            v -> open(new AppsPanel(this)), null);
            dock.addView(tile, w.wrap());
        }
        // Optional modules sit after the apps, only while switched on (Settings → Features).
        for (final Features.Module m : Features.homeEntries(prefs)) {
            Glyph g = new Glyph(m == Features.Module.STREAMING ? Glyph.Kind.PLAY : Glyph.Kind.GLOBE, Theme.PULSE_CYAN);
            View.OnClickListener openModule = v -> openModule(m);
            View.OnLongClickListener moduleMenu = v -> { moduleMenu(m); return true; };
            View tile = theme.liquid
                    ? w.glassAppTile(null, null, g, m.tile, tileW, tileH, prefs.labelsAlways(), openModule, moduleMenu)
                    : w.appTile(null, g, m.tile, tileW, tileH, prefs.labelsAlways(), openModule, moduleMenu);
            Widgets.cardOf(tile).setContentDescription(m.title);
            LinearLayout.LayoutParams lp = w.wrap();
            lp.leftMargin = theme.dp(Theme.GAP);
            dock.addView(tile, lp);
        }
        Widgets.cardOf(dock.getChildAt(0)).setFocusedByDefault(true);
        // Left/Right stop at the ends of the dock (otherwise Right on the last app jumped to the Settings chip).
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) cards.add(Widgets.cardOf(dock.getChildAt(i)));
        Widgets.lockRowEnds(cards);
        if (panels.isEmpty() && focusInDock) {
            View restored = focusedPackage == null ? null : dock.findViewWithTag(focusedPackage);
            if (restored == null || !restored.requestFocus()) focusFirstDockTile();
        }
    }

    /**
     * What the app row shows: the saved favorites, or, only if there are none, real installed apps as suggestions (recently
     * opened ones first, then TV apps, then the rest). Reading this never writes a preference.
     */
    private HomeShelf shelf() {
        List<String> stored = prefs.dock();
        if (!DockList.visible(stored, apps::usable).isEmpty()) {
            return HomeShelf.compute(stored, java.util.Collections.<String>emptyList(), java.util.Collections.<String>emptyList(),
                    java.util.Collections.<String>emptySet(), apps::usable, DOCK_COLUMNS);
        }
        return HomeShelf.compute(stored, prefs.recentlyOpened(), apps.all(), apps.leanbackPackages(), apps::usable, DOCK_COLUMNS);
    }

    /** What the row shows now (for tests and diagnostics). */
    public HomeShelf homeShelf() {
        return shelf;
    }

    /** The file of the wallpaper on screen, or null before the first one has loaded (for tests and diagnostics). */
    public String currentWallpaperFile() {
        return stage.current() == null ? null : stage.current().file;
    }

    /** The wallpaper stage (for tests and diagnostics). */
    public WallpaperStage wallpaperStage() {
        return stage;
    }

    /** A suggested app (not saved): the only things to do are add it to Favorites or look at its info. */
    private void suggestionMenu(final String pkg) {
        MenuPanel m = new MenuPanel(this, apps.label(pkg), "Suggested app");
        m.item("Add to Favorites", () -> {
            prefs.setDock(DockList.add(prefs.dock(), pkg));
            buildDock();
            View added = dock.findViewWithTag(pkg);
            if (added != null) added.requestFocus();
            toast(apps.label(pkg) + " added to Favorites");
        });
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) m.item("App info", () -> apps.start(info));
        open(m);
    }

    private void focusFirstDockTile() {
        if (dock.getChildCount() > 0) Widgets.cardOf(dock.getChildAt(0)).requestFocus();
    }

    private void buildAllAppsCarousel() {
        if (allAppsRow == null) return;
        View focused = root == null ? null : root.findFocus();
        Object focusedTag = focused != null && isInside(focused, allAppsRow) ? focused.getTag() : null;
        allAppsRow.removeAllViews();

        final int rowH = theme.dp(68);
        final int thumbW = theme.dp(88);
        final int thumbH = theme.dp(50);

        View zpRow = buildVerticalCarouselRow(
                "zpapps",
                zpAppsCarouselArt(thumbW, thumbH),
                getDrawable(R.drawable.zpapps_logo_source),
                "ZPAPPS Toolbox",
                "20 TV Apps  ·  Shortcodes  ·  Essentials  ·  Cache Cleaner",
                "Open",
                rowH, thumbW, thumbH,
                v -> open(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this)),
                null);
        allAppsRow.addView(zpRow, new LinearLayout.LayoutParams(Widgets.MATCH, rowH));

        java.util.Set<String> favs = new java.util.HashSet<>(DockList.visible(prefs.dock(), apps::usable));
        for (String pkg : apps.all()) {
            if (!apps.usable(pkg)) continue;
            boolean inDock = favs.contains(pkg);
            View appRow = buildVerticalCarouselRow(
                    pkg,
                    theme.liquid ? art.get(pkg, thumbW, thumbH) : null,
                    theme.liquid ? null : (apps.banner(pkg) != null ? apps.banner(pkg) : apps.icon(pkg)),
                    apps.label(pkg),
                    inDock ? "Favorite on Home  ·  Hold OK for options" : "Installed TV App  ·  Hold OK for options",
                    inDock ? "Favorite ★" : "Open",
                    rowH, thumbW, thumbH,
                    v -> launchApp(pkg),
                    v -> { appCarouselMenu(pkg); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, rowH);
            lp.topMargin = theme.dp(8);
            allAppsRow.addView(appRow, lp);
        }

        List<View> rows = new ArrayList<>();
        for (int i = 0; i < allAppsRow.getChildCount(); i++) rows.add(allAppsRow.getChildAt(i));
        for (View r : rows) {
            if (r.getId() == View.NO_ID) r.setId(View.generateViewId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        if (!rows.isEmpty()) {
            rows.get(0).setNextFocusUpId(rows.get(0).getId());
            rows.get(rows.size() - 1).setNextFocusDownId(rows.get(rows.size() - 1).getId());
        }
        for (int i = 0; i + 1 < rows.size(); i++) {
            View cur = rows.get(i), next = rows.get(i + 1);
            cur.setNextFocusDownId(next.getId());
            next.setNextFocusUpId(cur.getId());
        }
        setDrawerGlassPresence(drawerOpen ? 1f : 0f);
        if (drawerOpen) {
            View restored = focusedTag == null ? null : allAppsRow.findViewWithTag(focusedTag);
            if (restored == null || !restored.requestFocus()) focusFirstCarouselTile();
        }
    }

    private View buildVerticalCarouselRow(
            String tag,
            tv.calmhome.launcher.data.AppArt.Art appArt,
            android.graphics.drawable.Drawable fallbackDrawable,
            String title,
            String subtitle,
            String badge,
            int rowH, int thumbW, int thumbH,
            View.OnClickListener click,
            View.OnLongClickListener longClick) {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(theme.dp(14), theme.dp(8), theme.dp(18), theme.dp(8));

        FrameLayout thumbFrame = new FrameLayout(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(0x40141A24);
        thumbBg.setCornerRadius(theme.dp(10));
        thumbFrame.setBackground(thumbBg);
        thumbFrame.setClipToOutline(true);
        if (appArt != null) {
            tv.calmhome.launcher.ui.TileArtView tvArt = new tv.calmhome.launcher.ui.TileArtView(this, appArt);
            thumbFrame.addView(tvArt, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            if (fallbackDrawable != null) iv.setImageDrawable(fallbackDrawable);
            thumbFrame.addView(iv, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        }
        inner.addView(thumbFrame, new LinearLayout.LayoutParams(thumbW, thumbH));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = theme.text(this, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        titleView.setText(title);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(titleView, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        TextView subView = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.70f), Theme.FONT_REGULAR);
        subView.setText(subtitle);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        subLp.topMargin = theme.dp(2);
        labels.addView(subView, subLp);

        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        labelsLp.leftMargin = theme.dp(14);
        labelsLp.rightMargin = theme.dp(12);
        inner.addView(labels, labelsLp);

        TextView badgeView = theme.text(this, Theme.T_CAPTION,
                "Open".equals(badge) ? Theme.withAlpha(Theme.SOFT_WHITE, 0.72f) : Theme.PULSE_CYAN,
                Theme.FONT_MEDIUM);
        badgeView.setText(badge);
        badgeView.setSingleLine(true);
        inner.addView(badgeView, new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP));

        final View rowView;
        if (theme.liquid && theme.kit != null) {
            tv.calmhome.launcher.ui.ZeroPulseGlassRow row = new tv.calmhome.launcher.ui.ZeroPulseGlassRow(
                    this, theme.kit, tv.calmhome.launcher.ui.GraphiteGlassSurface.Shape.CAPSULE,
                    theme.reducedMotion ? 0 : tv.calmhome.launcher.core.LiquidTokens.FOCUS_MS, false, 1.02f);
            row.content().addView(inner, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
            rowView = row;
        } else {
            FrameLayout box = new FrameLayout(this);
            box.setFocusable(true);
            box.setClickable(true);
            box.setBackground(theme.glass(Theme.R_PANEL, .55f));
            box.addView(inner, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
            rowView = box;
        }
        rowView.setTag(tag);
        rowView.setContentDescription(title);
        rowView.setOnClickListener(click);
        if (longClick != null) rowView.setOnLongClickListener(longClick);
        rowView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && drawerOpen) centerVerticalCarouselItem(v);
        });
        return rowView;
    }

    private void centerVerticalCarouselItem(View item) {
        if (allAppsScroll == null || item == null) return;
        int viewportH = allAppsScroll.getHeight();
        if (viewportH <= 0) {
            allAppsScroll.post(() -> centerVerticalCarouselItem(item));
            return;
        }
        int itemCenter = item.getTop() + item.getHeight() / 2;
        int targetY = Math.max(0, itemCenter - viewportH / 2);
        if (allAppsScrollAnim != null) allAppsScrollAnim.cancel();
        int dur = tv.calmhome.launcher.core.MotionTokens.of(tv.calmhome.launcher.core.MotionTokens.CAROUSEL_MS, theme.reducedMotion);
        if (dur == 0) {
            allAppsScroll.scrollTo(0, targetY);
        } else {
            allAppsScrollAnim = android.animation.ObjectAnimator.ofInt(allAppsScroll, "scrollY", targetY);
            allAppsScrollAnim.setDuration(dur);
            allAppsScrollAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
            allAppsScrollAnim.start();
        }
    }

    private tv.calmhome.launcher.data.AppArt.Art zpAppsCarouselArt(int wPx, int hPx) {
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.zpapps_logo_source);
        if (bmp != null) {
            return tv.calmhome.launcher.data.AppArt.fromIcon(
                    new android.graphics.drawable.BitmapDrawable(getResources(), bmp), "zpapps", wPx, hPx);
        }
        return tv.calmhome.launcher.data.AppArt.monogram("ZPAPPS", wPx, hPx);
    }

    private void appCarouselMenu(final String pkg) {
        boolean inDock = DockList.visible(prefs.dock(), apps::usable).contains(pkg);
        MenuPanel m = new MenuPanel(this, apps.label(pkg), inDock ? "Favorite" : "Installed app");
        m.item("Open", () -> launchApp(pkg));
        if (inDock) {
            m.item("Remove from Favorites", () -> {
                prefs.setDock(DockList.remove(prefs.dock(), pkg));
                buildDock();
                toast(apps.label(pkg) + " removed from Favorites");
            });
        } else {
            m.item("Add to Favorites", () -> {
                prefs.setDock(DockList.add(prefs.dock(), pkg));
                buildDock();
                toast(apps.label(pkg) + " added to Favorites");
            });
        }
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) {
            m.item("App info", () -> apps.start(info));
            if (apps.userRemovable(pkg)) m.item("Uninstall… (App info asks to confirm)", () -> apps.start(info), true);
        }
        open(m);
    }

    private void setDrawerGlassPresence(float value) {
        if (allAppsRow == null) return;
        for (int i = 0; i < allAppsRow.getChildCount(); i++) {
            View child = allAppsRow.getChildAt(i);
            if (child instanceof tv.calmhome.launcher.ui.ZeroPulseGlassRow) {
                tv.calmhome.launcher.ui.ZeroPulseGlassRow row = (tv.calmhome.launcher.ui.ZeroPulseGlassRow) child;
                row.setSurfacePresence(value <= 0.001f ? 0f : (row.hasFocus() ? 1f : 0f));
            }
        }
    }

    private float offscreenDrawerY() {
        return Math.max(theme.dp(320), Math.round(theme.screenH() * 0.55f));
    }

    private void focusFirstCarouselTile() {
        if (allAppsRow != null && allAppsRow.getChildCount() > 0) {
            allAppsRow.getChildAt(0).requestFocus();
        }
    }

    void openAllAppsDrawer() {
        if (allAppsDrawer == null || drawerOpen) return;
        View focused = root == null ? null : root.findFocus();
        dockReturnFocus = focused != null && isInside(focused, dock) ? focused : null;
        drawerOpen = true;
        allAppsDrawer.animate().cancel();
        hero.animate().cancel();
        bottomBlock.animate().cancel();
        stage.setFrosted(true);
        bottomBlock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        allAppsDrawer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        allAppsDrawer.setVisibility(View.VISIBLE);
        int dur = tv.calmhome.launcher.core.MotionTokens.of(tv.calmhome.launcher.core.MotionTokens.DRAWER_SLIDE_MS, theme.reducedMotion);
        float homeLift = -theme.dp(220);
        if (dur == 0) {
            allAppsDrawer.setTranslationY(0f);
            allAppsDrawer.setAlpha(1f);
            hero.setTranslationY(homeLift);
            hero.setAlpha(0f);
            bottomBlock.setTranslationY(homeLift);
            bottomBlock.setAlpha(0f);
        } else {
            allAppsDrawer.setTranslationY(offscreenDrawerY());
            allAppsDrawer.setAlpha(0f);
            allAppsDrawer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
            hero.animate()
                    .translationY(homeLift)
                    .alpha(0f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
            bottomBlock.animate()
                    .translationY(homeLift)
                    .alpha(0f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
        }
        updateShelfHint(null);
        focusFirstCarouselTile();
        setDrawerGlassPresence(1f);
        if (allAppsScroll != null) allAppsScroll.scrollTo(0, 0);
    }

    void closeAllAppsDrawer(boolean restoreDockFocus) {
        if (allAppsDrawer == null || !drawerOpen) return;
        drawerOpen = false;
        allAppsDrawer.animate().cancel();
        hero.animate().cancel();
        bottomBlock.animate().cancel();
        if (panels.isEmpty()) stage.setFrosted(false);
        allAppsDrawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        bottomBlock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (restoreDockFocus) {
            if (dockReturnFocus == null || !dockReturnFocus.isAttachedToWindow() || !dockReturnFocus.requestFocus()) {
                focusFirstDockTile();
            }
        }
        dockReturnFocus = null;
        int dur = tv.calmhome.launcher.core.MotionTokens.of(tv.calmhome.launcher.core.MotionTokens.DRAWER_SLIDE_MS, theme.reducedMotion);
        if (dur == 0) {
            allAppsDrawer.setTranslationY(offscreenDrawerY());
            allAppsDrawer.setAlpha(0f);
            allAppsDrawer.setVisibility(View.INVISIBLE);
            setDrawerGlassPresence(0f);
            hero.setTranslationY(0f);
            hero.setAlpha(1f);
            bottomBlock.setTranslationY(0f);
            bottomBlock.setAlpha(1f);
        } else {
            allAppsDrawer.animate()
                    .translationY(offscreenDrawerY())
                    .alpha(0f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        if (!drawerOpen) {
                            allAppsDrawer.setVisibility(View.INVISIBLE);
                            setDrawerGlassPresence(0f);
                        }
                    })
                    .start();
            hero.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
            bottomBlock.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(dur)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    /** For tests: true while the bottom All Apps & ZPAPPS carousel drawer is open. */
    boolean isAllAppsDrawerOpen() {
        return drawerOpen;
    }

    /** For tests: the bottom All Apps & ZPAPPS carousel container. */
    LinearLayout allAppsDrawer() {
        return allAppsDrawer;
    }

    /** For tests: the horizontal row of tiles inside the All Apps & ZPAPPS carousel. */
    LinearLayout allAppsRow() {
        return allAppsRow;
    }

    private void launchApp(String pkg) {
        lastLaunched = pkg;
        sounds.play(tv.calmhome.launcher.data.UiSounds.Kind.SELECT);
        if (!apps.launch(pkg)) toast("Can't open " + apps.label(pkg) + " right now");
    }

    /** Back from an app: focus returns to the tile that launched it (Android usually keeps it; a rebuilt row would not). */
    private void restoreLaunchFocus() {
        if (lastLaunched != null) {
            for (int i = 0; i < dock.getChildCount(); i++) {
                View card = Widgets.cardOf(dock.getChildAt(i));
                if (lastLaunched.equals(card.getTag())) { card.requestFocus(); return; }
            }
        }
        focusFirstDockTile();
    }

    private void dockMenu(final String pkg) {
        final List<String> visible = DockList.visible(prefs.dock(), apps::usable);
        final int i = visible.indexOf(pkg);
        MenuPanel m = new MenuPanel(this, apps.label(pkg), "Favorite");
        // Contextual actions for the focused favorite (a menu, not a change of the bottom buttons' meaning: those always stay
        // All Apps, Downloads, Wallpapers and Inputs, so the remote never lands on a button that does something else).
        m.item("Open", () -> launchApp(pkg));
        if (i > 0) m.item("Move left", () -> moveInDock(pkg, -1));
        if (i >= 0 && i < visible.size() - 1) m.item("Move right", () -> moveInDock(pkg, 1));
        m.item("Remove from Favorites", () -> {
            prefs.setDock(DockList.remove(prefs.dock(), pkg));
            buildDock();
            focusFirstDockTile();
            toast(apps.label(pkg) + " removed from Favorites (the app is still installed)");
        });
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) {
            m.item("App info", () -> apps.start(info));
            // Uninstalling is the system's own flow: its App info page asks to confirm. This launcher never removes an app itself,
            // and never offers it for system apps or for itself. (Removing a favorite is "Remove from Favorites", above.)
            if (apps.userRemovable(pkg)) m.item("Uninstall… (App info asks to confirm)", () -> apps.start(info), true);
        }
        if (updates.updateAvailable(pkg)) m.item("Update", () -> apps.start(info));      // only with a real update source (none today)
        open(m);
    }

    private void moduleMenu(final Features.Module m) {
        open(new MenuPanel(this, m.title, "Optional module")
                .item("Open", () -> openModule(m))
                .item("Feature settings", () -> open(new SettingsPanel(this, "features"))));
    }

    private void moveInDock(String pkg, int delta) {
        List<String> visible = DockList.visible(prefs.dock(), apps::usable);
        prefs.setDock(DockList.move(prefs.dock(), visible, pkg, delta));
        buildDock();
        List<String> now = DockList.visible(prefs.dock(), apps::usable);
        int at = now.indexOf(pkg);
        if (at >= 0 && at < dock.getChildCount()) Widgets.cardOf(dock.getChildAt(at)).requestFocus();
    }

    private static boolean isInside(View v, ViewGroup g) {
        View f = v;
        while (f != null) {
            if (f == g) return true;
            f = f.getParent() instanceof View ? (View) f.getParent() : null;
        }
        return false;
    }

    // ------------------------------------------------------------------ Panel.Host

    @Override public Context context() { return this; }
    @Override public Theme theme() { return theme; }
    @Override public Widgets widgets() { return w; }
    @Override public Prefs prefs() { return prefs; }
    @Override public Apps apps() { return apps; }
    @Override public WallpaperStage stage() { return stage; }

    @Override
    public void open(Panel p) {
        releaseHeld();
        openPanel(p, root.findFocus());
    }

    private void openPanel(Panel p, View previousFocus) {
        sounds.play(tv.calmhome.launcher.data.UiSounds.Kind.WHOOSH);
        View v = p.view();
        boolean glide = glides(p);
        if (panels.isEmpty()) {
            mainUi.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            if (glide && held == null) hold(mainUi, homeMotion(), null);
        } else {
            Panel top = panels.get(panels.size() - 1);
            View below = top.view();
            ((ViewGroup) below).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            if (glide && held == null && !top.isModal() && top.navigation() != null) hold(below, top.motionViews(), null);
            if (!p.isModal() && below != held) below.setVisibility(View.INVISIBLE);
        }
        p.setReturnFocus(previousFocus);
        panels.add(p);
        syncBackdrop();
        root.addView(v, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        if (glide) {
            enterContent(p);
        } else {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(theme.fadeMs(tv.calmhome.launcher.core.MotionTokens.SCREEN_FADE_MS)).start();
            View card = p.enterMotionView();
            if (card != null && !theme.reducedMotion) {
                card.setTranslationY(theme.dp(14));
                card.animate().translationY(0f).setDuration(tv.calmhome.launcher.core.MotionTokens.MODAL_RISE_MS)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        }
        v.post(() -> {
            if (!panels.isEmpty() && panels.get(panels.size() - 1) == p) p.focusContentOrTab();
        });
    }

    // ------------------------------------------------------------------ transitions between destinations

    /**
     * The screen being left while a new destination comes in: kept on screen, its content already fading out, so that its header (the
     * glass navigation and clock, in the same place on every screen) stays put until the new screen's own header shows real glass. Then
     * the new header takes over in the same frame and this one goes (see {@link #releaseHeld}). Null when no change is in progress.
     */
    private View held;
    private List<View> heldMotion = new ArrayList<>();
    private Panel heldPanel;                     // non-null when the held screen was closed: it is removed at release
    private long heldSince;
    private final Runnable heldCheck = new Runnable() {
        @Override public void run() {
            if (held == null) return;
            Panel top = topFullPanel();
            boolean ready = top == null || top.navigation() == null || top.navigation().glassReady();
            if (ready || android.os.SystemClock.uptimeMillis() - heldSince >= tv.calmhome.launcher.core.MotionTokens.HEADER_HANDOFF_MAX_MS) releaseHeld();
            else handler.postDelayed(this, 16);
        }
    };

    /** A full screen with its own header, reached with the glass look and motion on: it glides in instead of fading in whole. */
    private boolean glides(Panel p) {
        // A screen with a backdrop of its own (Wallpapers' shade) fades in whole: a header held behind that shade would look dimmed.
        return theme.liquid && !theme.reducedMotion && !p.isModal() && p.navigation() != null && p.view().getBackground() == null;
    }

    private Panel topFullPanel() {
        for (int i = panels.size() - 1; i >= 0; i--) if (!panels.get(i).isModal()) return panels.get(i);
        return null;
    }

    /** Home's content under its header: the vertical carousel if open, otherwise the centre hero and dock block. */
    private List<View> homeMotion() {
        List<View> m = new ArrayList<>();
        if (drawerOpen && allAppsDrawer != null) {
            m.add(allAppsDrawer);
        } else {
            m.add(hero);
            m.add(bottomBlock);
        }
        return m;
    }

    private void hold(View view, List<View> motion, Panel closedPanel) {
        held = view;
        heldMotion = new ArrayList<>(motion);
        heldPanel = closedPanel;
        heldSince = android.os.SystemClock.uptimeMillis();
        for (View m : heldMotion) {
            m.animate().cancel();
            m.animate().alpha(0f).setStartDelay(0).setDuration(tv.calmhome.launcher.core.MotionTokens.CONTENT_OUT_MS).start();
        }
        handler.removeCallbacks(heldCheck);
        handler.postDelayed(heldCheck, 16);
    }

    /** The new screen's content fades in (its heading rises a few dp); its header appears when its glass is ready (see heldCheck). */
    private void enterContent(Panel p) {
        p.view().setAlpha(1f);
        if (held != null && p.navigation() != null) p.navigation().setAlpha(0f);
        for (View m : p.motionViews()) {
            m.setAlpha(0f);
            m.animate().alpha(1f).setStartDelay(tv.calmhome.launcher.core.MotionTokens.CONTENT_IN_DELAY_MS).setDuration(tv.calmhome.launcher.core.MotionTokens.CONTENT_IN_MS).start();
        }
        View heading = p.headingView();
        if (heading != null) {
            heading.setTranslationY(theme.dp(8));
            heading.animate().translationY(0f).setStartDelay(tv.calmhome.launcher.core.MotionTokens.CONTENT_IN_DELAY_MS).setDuration(tv.calmhome.launcher.core.MotionTokens.CONTENT_IN_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
    }

    /** Ends a change of destination now: the new header shows, and the screen that was left goes (or, if it stays below, hides). */
    private void releaseHeld() {
        handler.removeCallbacks(heldCheck);
        if (held == null) return;
        View was = held;
        List<View> motion = heldMotion;
        Panel closed = heldPanel;
        held = null;
        heldMotion = new ArrayList<>();
        heldPanel = null;
        Panel top = topFullPanel();
        if (top != null && top.navigation() != null) top.navigation().setAlpha(1f);
        for (View m : motion) {
            m.animate().cancel();
            m.setAlpha((drawerOpen && (m == bottomBlock || m == hero)) ? 0f : 1f);                                   // hidden behind the new screen now, ready for when it shows again
        }
        if (closed != null) {
            root.removeView(was);
            closed.onClosed();
        }
        syncBackdrop();
    }

    /** For tests: true while a change of destination is still waiting for the new header's glass. */
    boolean holdingPreviousScreen() {
        return held != null;
    }

    /**
     * Changes destination from the navigation: the screens open now are closed, except that the one being left (with its header) is held
     * until the new header is ready. Back later returns focus where the screen being left would have returned it.
     */
    private void switchTo(Panel next) {
        releaseHeld();
        next.view();                                          // built now, so it is known whether it has a header
        Panel base = topFullPanel();
        if (!glides(next) || (base != null && base.navigation() == null)) {
            closeAll();
            openPanel(next, root.findFocus());
            return;
        }
        View returnTo = base == null ? root.findFocus() : panels.get(0).returnFocus();
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel q = panels.get(i);
            if (q == base) continue;
            panels.remove(i);
            root.removeView(q.view());
            q.onClosed();
        }
        if (base != null) {
            panels.remove(base);
            ((ViewGroup) base.view()).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            hold(base.view(), base.motionViews(), base);
        }
        openPanel(next, returnTo);
    }

    @Override
    public void close(Panel p) {
        releaseHeld();
        int i = panels.indexOf(p);
        if (i < 0) return;
        panels.remove(i);
        syncBackdrop();
        root.removeView(p.view());
        p.onClosed();
        View back = p.returnFocus();
        if (panels.isEmpty()) {
            stage.setFrosted(drawerOpen);
            mainUi.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            // Back to Home from a full screen: Home's header was there all along (only hidden); its content fades back in.
            if (glides(p)) {
                for (View m : homeMotion()) {
                    m.setAlpha(0f);
                    m.animate().alpha(1f).setStartDelay(0).setDuration(tv.calmhome.launcher.core.MotionTokens.CONTENT_IN_MS).start();
                }
            }

            applyHomePrefs();
        } else {
            Panel top = panels.get(panels.size() - 1);
            top.view().setVisibility(View.VISIBLE);
            ((ViewGroup) top.view()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            top.onUncovered();
        }
        if (back != null && back.isAttachedToWindow() && back.requestFocus()) return;
        if (panels.isEmpty()) focusFirstDockTile();
        else panels.get(panels.size() - 1).focusContentOrTab();
    }

    @Override
    public void closeAll() {
        while (!panels.isEmpty()) close(panels.get(panels.size() - 1));
    }

    @Override
    public void refreshHome() {
        applyHomePrefs();
        buildDock();
        stage.reload();
    }

    @Override
    public void rebuild(String reopen) {
        if (reopen != null) getIntent().putExtra(EXTRA_OPEN, reopen);
        recreate();
    }

    @Override
    public void navigate(String tab) {
        switch (tab) {
            case "Apps": switchTo(new AppsPanel(this)); break;
            case "Downloads": switchTo(new DownloadsPanel(this)); break;
            case "Toolbox":
            case "ZPAPPS": switchTo(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this)); break;
            case "Wallpapers": switchTo(new WallpapersPanel(this)); break;
            case "Inputs": switchTo(new InputsPanel(this)); break;
            case "Settings": switchTo(new SettingsPanel(this, null)); break;
            // "Home", and any other name (the removed Assistant, a stale saved destination): Home, never a crash or an empty screen.
            default:
                if (BuildConfig.STANDALONE_ZPAPPS) {
                    switchTo(new tv.calmhome.launcher.toolbox.ZpAppsPanel(this));
                } else {
                    closeAll();
                    focusFirstDockTile();
                }
                break;
        }
    }

    private static final int PICK_APK = 713;
    private String pickerCategory;

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("apkPickerCategory", pickerCategory);
        for (int i = panels.size() - 1; i >= 0; i--) {
            String route = panels.get(i).restoreRoute();
            if (route != null) { state.putString("restoreSection", route); break; }
        }
    }

    @Override
    public void pickApk(SourceEntry.Category category) {
        pickerCategory = category == null ? null : category.name();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        android.content.pm.ResolveInfo handler = getPackageManager().resolveActivity(pick, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (handler != null && "com.android.tv.frameworkpackagestubs".equals(handler.activityInfo.packageName)) {
            pickerCategory = null;
            toast("This TV has no file picker. Enter a developer HTTPS link in Downloads.");
            return;
        }
        try { startActivityForResult(pick, PICK_APK); }
        catch (android.content.ActivityNotFoundException e) { toast("This TV has no document picker. Enter a developer HTTPS link instead."); }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_APK) return;
        String category = pickerCategory;
        pickerCategory = null;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            long id = Downloads.get(this).importFile(data.getData(), "");
            if (category != null) new SourceCatalog(this).indexFile(id, SourceEntry.Category.valueOf(category));
            if (panels.isEmpty()) open(new DownloadsPanel(this));
            open(DownloadsPanel.detail(this, id));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) { toast(e.getMessage()); }
    }

    @Override
    public void openModule(Features.Module m) {
        if (!Features.mayOpen(prefs, m)) {
            toast(m.title + " is turned off in Settings → Features");
            return;
        }
        startActivity(new Intent(this, m == Features.Module.STREAMING ? StreamingActivity.class : WebActivity.class));
    }

    @Override
    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
