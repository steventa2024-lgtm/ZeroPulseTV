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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import tv.calmhome.launcher.assistant.AssistantActivity;
import tv.calmhome.launcher.core.DockList;
import tv.calmhome.launcher.core.Greeting;
import tv.calmhome.launcher.core.WallpaperCatalog;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.WallpaperStage;
import tv.calmhome.launcher.screens.AboutPanel;
import tv.calmhome.launcher.screens.AppsPanel;
import tv.calmhome.launcher.screens.InputsPanel;
import tv.calmhome.launcher.screens.MenuPanel;
import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.screens.SettingsPanel;
import tv.calmhome.launcher.screens.WallpapersPanel;
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

    /** Intent extra: open a screen on arrival ("apps", "inputs", "wallpapers", "settings[:category]", "about"). */
    public static final String EXTRA_OPEN = "zp_open";

    static final int DOCK_COLUMNS = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Theme theme;
    private Widgets w;
    private Apps apps;
    private WallpaperStage stage;

    private FrameLayout root, mainUi;
    private View dim;
    private LinearLayout dock, clockBox, greetingBox;
    private HorizontalScrollView dockScroll;
    private TextView greeting, credit;
    private View navAssistant, chipAssistant;
    private int tileW, tileH;
    private final List<Panel> panels = new ArrayList<>();
    private BroadcastReceiver packageReceiver;

    private final Runnable greetingTick = new Runnable() {
        @Override public void run() {
            updateGreeting();
            handler.postDelayed(this, Greeting.millisToNextHour(System.currentTimeMillis() % 3600000L) + 1000);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        theme = new Theme(this, prefs);
        w = new Widgets(this, theme);
        apps = new Apps(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.DEEP_BLACK));
        buildHome();
        setContentView(root);
        buildDock();
        root.post(this::focusFirstDockTile);

        packageReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                buildDock();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        handleOpenExtra(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        stage.start();
        handler.removeCallbacks(greetingTick);
        greetingTick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (panels.isEmpty() && root.findFocus() == null) focusFirstDockTile();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stage.stop();
        handler.removeCallbacks(greetingTick);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        stage.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleOpenExtra(intent)) return;
        // Home pressed while already home: close everything and return to the first app.
        closeAll();
        dockScroll.smoothScrollTo(0, 0);
        focusFirstDockTile();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Android 12 (this TV) still routes Back here; Home itself ignores Back.
        if (panels.isEmpty()) return;
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
            case "apps": open(new AppsPanel(this)); break;
            case "inputs": open(new InputsPanel(this)); break;
            case "wallpapers": open(new WallpapersPanel(this)); break;
            case "settings": open(new SettingsPanel(this, parts.length > 1 ? parts[1] : null)); break;
            case "about": open(new AboutPanel(this)); break;
            default: return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Home layout

    private void buildHome() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        ImageView wallA = wallpaperView(), wallB = wallpaperView();
        root.addView(wallA, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        root.addView(wallB, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        stage = new WallpaperStage(this, prefs, wallA, wallB, this::onWallpaper);
        stage.setFadeMs(theme.fadeMs(1500));

        dim = new View(this);
        root.addView(dim, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        root.addView(topShade, new FrameLayout.LayoutParams(Widgets.MATCH, theme.rawDp(150), Gravity.TOP));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE0000000, 0x8C000000, 0x00000000}));
        root.addView(bottomShade, new FrameLayout.LayoutParams(Widgets.MATCH, theme.rawDp(300), Gravity.BOTTOM));

        mainUi = new FrameLayout(this);
        mainUi.setClipChildren(false);
        root.addView(mainUi, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        int sx = theme.safeX(), sy = theme.safeY();

        // Area A: logo + navigation + clock
        FrameLayout top = new FrameLayout(this);
        top.setClipChildren(false);
        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setClipChildren(false);
        left.addView(new PulseLogo(this, theme.dp(30)));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(theme.dp(5), theme.dp(5), theme.dp(5), theme.dp(5));
        nav.setBackground(theme.glass(Theme.R_PILL + 3, 0.55f));
        nav.addView(w.navItem("Home", true, v -> { closeAll(); focusFirstDockTile(); }));
        nav.addView(w.navItem("Apps", false, v -> open(new AppsPanel(this))));
        nav.addView(w.navItem("Inputs", false, v -> open(new InputsPanel(this))));
        navAssistant = w.navItem("Assistant", false, v -> openAssistant());
        nav.addView(navAssistant);
        nav.addView(w.navItem("Settings", false, v -> open(new SettingsPanel(this, null))));
        LinearLayout.LayoutParams nl = w.wrap();
        nl.leftMargin = theme.dp(28);
        left.addView(nav, nl);
        top.addView(left, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));

        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        theme.style(time, Theme.T_CLOCK, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        theme.overPhoto(time);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, MMM d");
        date.setFormat24Hour("EEE, d MMM");
        theme.style(date, Theme.T_DATE, Theme.withAlpha(Theme.SOFT_WHITE, 0.85f), Theme.FONT_REGULAR);
        theme.overPhoto(date);
        // Fixed-width, right-aligned column: on the TV the content-sized clock was cut off ("7:0") after updates.
        for (TextView tv : new TextView[]{time, date}) {
            tv.setGravity(Gravity.END);
            tv.setSingleLine(true);
        }
        clockBox.addView(time, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        dl.topMargin = theme.dp(4);
        clockBox.addView(date, dl);
        top.addView(clockBox, new FrameLayout.LayoutParams(theme.dp(220), Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.TOP);
        tl.setMargins(sx, sy, sx, 0);
        mainUi.addView(top, tl);

        // Area C: ambient greeting
        greetingBox = new LinearLayout(this);
        greetingBox.setOrientation(LinearLayout.VERTICAL);
        greetingBox.setGravity(Gravity.CENTER_HORIZONTAL);
        // Full-width, centred, single-line text: on the TV the content-sized greeting showed only "Good".
        greeting = theme.text(this, Theme.T_GREETING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        theme.overPhoto(greeting);
        greeting.setGravity(Gravity.CENTER_HORIZONTAL);
        greeting.setSingleLine(true);
        updateGreeting();
        greetingBox.addView(greeting, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        TextView tagline = theme.text(this, Theme.T_TAGLINE, Theme.withAlpha(Theme.SOFT_WHITE, 0.88f), Theme.FONT_REGULAR);
        tagline.setText(Greeting.TAGLINE);
        tagline.setLetterSpacing(0.06f);
        tagline.setGravity(Gravity.CENTER_HORIZONTAL);
        tagline.setSingleLine(true);
        theme.overPhoto(tagline);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        gl.topMargin = theme.dp(8);
        greetingBox.addView(tagline, gl);
        PulseLogo mark = new PulseLogo(this, theme.dp(18)).name("");
        LinearLayout.LayoutParams ml = w.wrap();
        ml.topMargin = theme.dp(8);
        greetingBox.addView(mark, ml);
        FrameLayout.LayoutParams gbl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.CENTER_HORIZONTAL);
        gbl.topMargin = Math.round(theme.screenH() * 0.30f);
        mainUi.addView(greetingBox, gbl);

        // Areas D + E + footer, stacked from the bottom safe margin
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);

        float availDp = (theme.screenW() - 2f * sx) / getResources().getDisplayMetrics().density;
        tileW = Math.round((availDp - (DOCK_COLUMNS - 1) * Theme.GAP) / DOCK_COLUMNS);
        tileH = Math.round(tileW * 9f / 16f);
        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setClipChildren(false);
        // Side padding = safe margin, so a focused (scaled) end tile is never cut off by the screen edge.
        dockScroll.setPadding(sx, theme.dp(10), sx, 0);
        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dockScroll.addView(dock);
        bottom.addView(dockScroll, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        LinearLayout chips = new LinearLayout(this);
        chips.setClipChildren(false);
        chips.setPadding(sx, 0, sx, 0);
        addChip(chips, Glyph.Kind.GRID, "All Apps", false, v -> open(new AppsPanel(this)));
        addChip(chips, Glyph.Kind.INPUTS, "Inputs", false, v -> open(new InputsPanel(this)));
        chipAssistant = addChip(chips, Glyph.Kind.ASSISTANT, "Assistant", true, v -> openAssistant());
        addChip(chips, Glyph.Kind.IMAGE, "Wallpapers", false, v -> open(new WallpapersPanel(this)));
        addChip(chips, Glyph.Kind.GEAR, "Settings", false, v -> open(new SettingsPanel(this, null)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Widgets.MATCH, theme.dp(42));
        cl.topMargin = theme.dp(8);
        bottom.addView(chips, cl);

        FrameLayout footer = new FrameLayout(this);
        footer.setPadding(sx, 0, sx, 0);
        credit = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.72f), Theme.FONT_REGULAR);
        theme.overPhoto(credit);
        footer.addView(credit, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout powered = new LinearLayout(this);
        powered.setGravity(Gravity.CENTER_VERTICAL);
        TextView pw = theme.text(this, Theme.T_CAPTION, Theme.withAlpha(Theme.SOFT_WHITE, 0.6f), Theme.FONT_REGULAR);
        pw.setText("Powered by ZeroPulse AI");
        powered.addView(pw);
        footer.addView(powered, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        fl.topMargin = theme.dp(14);
        bottom.addView(footer, fl);

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.BOTTOM);
        bl.bottomMargin = Math.round(sy * 0.7f);
        mainUi.addView(bottom, bl);

        applyHomePrefs();
    }

    private View addChip(LinearLayout row, Glyph.Kind icon, String text, boolean accent, View.OnClickListener click) {
        LinearLayout chip = w.chip(icon, text, accent, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = theme.dp(Theme.GAP);
        row.addView(chip, lp);
        return chip;
    }

    private ImageView wallpaperView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private void onWallpaper(WallpaperCatalog.Entry e) {
        // The credit is always shown: CC BY-SA requires attribution wherever the photo is displayed.
        credit.setText(e.caption());
    }

    private void updateGreeting() {
        greeting.setText(Greeting.forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
    }

    /** Applies preferences that change Home without rebuilding it. */
    private void applyHomePrefs() {
        greetingBox.setVisibility(prefs.showGreeting() ? View.VISIBLE : View.GONE);
        clockBox.setVisibility(prefs.showClock() ? View.VISIBLE : View.INVISIBLE);
        dim.setBackgroundColor(Color.argb(Math.round(2.55f * prefs.dimPercent()), 0, 0, 0));
        int a = prefs.assistantEnabled() ? View.VISIBLE : View.GONE;
        navAssistant.setVisibility(a);
        chipAssistant.setVisibility(a);
    }

    // ------------------------------------------------------------------ dock

    void buildDock() {
        // Package broadcasts (many at boot) rebuild the dock; removing the tiles would drop focus onto the
        // first remaining focusable view, so remember whether focus belongs in the dock.
        View focused = root.findFocus();
        boolean focusInDock = focused == null || isInside(focused, dock);
        dock.removeAllViews();
        for (String pkg : DockList.visible(prefs.dock(), apps::usable)) {
            View tile = w.appTile(apps.banner(pkg), apps.icon(pkg), apps.label(pkg), tileW, tileH, prefs.labelsAlways(),
                    v -> launchApp(pkg), v -> { dockMenu(pkg); return true; });
            LinearLayout.LayoutParams lp = w.wrap();
            if (dock.getChildCount() > 0) lp.leftMargin = theme.dp(Theme.GAP);
            dock.addView(tile, lp);
        }
        if (dock.getChildCount() == 0) {
            View tile = w.appTile(null, new Glyph(Glyph.Kind.GRID, Theme.SOFT_WHITE), "Add apps", tileW, tileH, true,
                    v -> open(new AppsPanel(this)), null);
            dock.addView(tile, w.wrap());
        }
        Widgets.cardOf(dock.getChildAt(0)).setFocusedByDefault(true);
        // Left/Right stop at the ends of the dock (otherwise Right on the last app jumped to the Settings chip).
        View first = Widgets.cardOf(dock.getChildAt(0)), last = Widgets.cardOf(dock.getChildAt(dock.getChildCount() - 1));
        for (View v : new View[]{first, last}) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
        if (panels.isEmpty() && focusInDock) focusFirstDockTile();
    }

    private void focusFirstDockTile() {
        if (dock.getChildCount() > 0) Widgets.cardOf(dock.getChildAt(0)).requestFocus();
    }

    private void launchApp(String pkg) {
        if (!apps.launch(pkg)) toast("Can't open " + apps.label(pkg) + " right now");
    }

    private void dockMenu(final String pkg) {
        final List<String> visible = DockList.visible(prefs.dock(), apps::usable);
        final int i = visible.indexOf(pkg);
        MenuPanel m = new MenuPanel(this, apps.label(pkg), "Dock app");
        if (i > 0) m.item("Move left", () -> moveInDock(pkg, -1));
        if (i >= 0 && i < visible.size() - 1) m.item("Move right", () -> moveInDock(pkg, 1));
        m.item("Remove from dock", () -> {
            prefs.setDock(DockList.remove(prefs.dock(), pkg));
            buildDock();
            focusFirstDockTile();
            toast(apps.label(pkg) + " removed from the dock (the app is still installed)");
        });
        final Intent info = apps.appInfoIntent(pkg);
        if (apps.resolves(info)) m.item("App info", () -> apps.start(info));
        open(m);
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
        View v = p.view();
        if (panels.isEmpty()) {
            mainUi.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            mainUi.animate().alpha(p.isModal() ? 0.6f : 0f).setDuration(theme.fadeMs(180)).start();
        } else {
            View below = panels.get(panels.size() - 1).view();
            ((ViewGroup) below).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        p.setReturnFocus(root.findFocus());
        panels.add(p);
        v.setAlpha(0f);
        root.addView(v, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        v.animate().alpha(1f).setDuration(theme.fadeMs(180)).start();
        v.post(() -> p.focusFirst());
    }

    @Override
    public void close(Panel p) {
        int i = panels.indexOf(p);
        if (i < 0) return;
        panels.remove(i);
        root.removeView(p.view());
        p.onClosed();
        View back = p.returnFocus();
        if (panels.isEmpty()) {
            mainUi.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mainUi.animate().alpha(1f).setDuration(theme.fadeMs(180)).start();
            applyHomePrefs();
        } else {
            Panel top = panels.get(panels.size() - 1);
            ((ViewGroup) top.view()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            top.onUncovered();
        }
        if (back != null && back.isAttachedToWindow() && back.requestFocus()) return;
        if (panels.isEmpty()) focusFirstDockTile();
        else panels.get(panels.size() - 1).focusFirst();
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
    public void openAssistant() {
        if (!prefs.assistantEnabled()) {
            toast("The Assistant is turned off in Settings");
            return;
        }
        startActivity(new Intent(this, AssistantActivity.class));
    }

    @Override
    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
