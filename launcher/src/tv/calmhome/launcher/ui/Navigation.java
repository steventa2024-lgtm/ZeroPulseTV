package tv.calmhome.launcher.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import tv.calmhome.glasscomponents.GlassClockView;
import tv.calmhome.glasscomponents.GlassTopNavView;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.SystemTime;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.screens.Panel;

/**
 * Common navigation: graphite, or, in Liquid Glass, the supplied glass components (a glass rail with a focus pill, and a glass
 * clock whose numerals are the glass). Exactly five destinations: Home, Apps, Downloads, Inputs and Settings.
 */
public final class Navigation extends LinearLayout {
    /** The destinations, in order. Standalone ZPAPPS swaps launcher-only Home/Inputs for Toolbox. */
    public static final String[] TABS = tv.calmhome.launcher.BuildConfig.STANDALONE_ZPAPPS
            ? new String[]{"Toolbox", "Downloads", "Wallpapers", "Settings"}
            : new String[]{"Home", "Apps", "Downloads", "Inputs", "Settings"};
    /**
     * Liquid Glass header: the rail's height, and the focus pill's height and width, in dp. With five destinations each pill is
     * 102 dp (it was 84 dp for six): 5 x (102 + 6) = 6 x (84 + 6), so the rail keeps exactly the width and place it had between the
     * logo and the clock, and the destinations spread evenly across it instead of leaving a gap where the Assistant was.
     */
    public static final float RAIL_DP = 56f, RAIL_BUTTON_DP = 38f, RAIL_BUTTON_WIDTH_DP = 102f;

    private LinearLayout clockBox;
    private TextView date;
    private TextView glassDate;
    private View current;
    // Liquid Glass: the supplied components
    private GlassTopNavView glassNav;
    private GlassClockView glassClock;
    private String glassSelected;
    private Consumer<String> glassOnTab;
    private final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            syncClock();
        }
    };

    public Navigation(Panel.Host host, String selected) {
        this(host.context(), host.theme(), host.widgets(), host.prefs(), selected, host::navigate);
    }

    public Navigation(Context c, Theme t, Widgets w, Prefs prefs, String selected, Consumer<String> onTab) {
        super(c);
        setGravity(Gravity.CENTER_VERTICAL); setClipChildren(false);
        if (t.liquid && t.kit != null) {
            buildGlassHeader(c, t, prefs, selected, onTab);
            return;
        }
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setClipChildren(false);
        bar.setPadding(t.dp(6), t.dp(3), t.dp(5), t.dp(3));
        bar.setBackground(t.liquid ? t.liquidCapsule(Theme.R_PILL) : t.glass(Theme.R_PILL, .83f));
        addView(bar, new LayoutParams(0, Widgets.WRAP, 1));
        FrameLayout badge = new FrameLayout(c);
        if (!t.liquid) badge.setBackground(t.raised(20, false));
        badge.addView(new PulseLogo(c, t.dp(19)).name(""), new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.CENTER));
        bar.addView(badge, new LayoutParams(t.dp(34), t.dp(34)));
        LinearLayout brand = new LinearLayout(c);
        brand.setOrientation(VERTICAL); brand.setPadding(t.dp(5), 0, t.dp(6), 0);
        TextView name = t.text(c, 15, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        name.setText("ZeroPulse TV"); name.setSingleLine();
        TextView system = t.text(c, 9, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        system.setText("Android TV"); brand.addView(name); brand.addView(system); bar.addView(brand);
        LinearLayout tabs = new LinearLayout(c);
        tabs.setGravity(Gravity.CENTER); tabs.setClipChildren(false);
        bar.addView(tabs, new LayoutParams(0, Widgets.WRAP, 1));
        String[] labels = TABS;
        Glyph.Kind[] icons = tv.calmhome.launcher.BuildConfig.STANDALONE_ZPAPPS
                ? new Glyph.Kind[]{Glyph.Kind.GRID, Glyph.Kind.DOWNLOAD, Glyph.Kind.GRID, Glyph.Kind.GEAR}
                : new Glyph.Kind[]{Glyph.Kind.HOME, Glyph.Kind.GRID, Glyph.Kind.DOWNLOAD, Glyph.Kind.INPUTS, Glyph.Kind.GEAR};
        for (int i = 0; i < labels.length; i++) {
            String tab = labels[i];
            TextView item = w.navItem(tab, tab.equals(selected), v -> onTab.accept(tab));
            if (tab.equals(selected)) { current = item; item.setFocusableInTouchMode(true); }
            if (t.screenW() - 2 * t.safeX() >= t.dp(755)) {
                Glyph glyph = new Glyph(icons[i], Theme.SOFT_WHITE);
                glyph.setBounds(0,0,t.dp(14),t.dp(14));
                item.setCompoundDrawablesRelative(glyph,null,null,null); item.setCompoundDrawablePadding(t.dp(4));
            }
            tabs.addView(item);
        }
        clockBox = new LinearLayout(c);
        clockBox.setOrientation(VERTICAL); clockBox.setGravity(Gravity.CENTER);
        if (!t.liquid) clockBox.setBackground(t.glass(Theme.R_PILL, .84f));
        TextClock time = new TextClock(c); time.setFormat12Hour("h:mm a"); time.setFormat24Hour("HH:mm");
        if (t.liquid) t.style(time, 15, LiquidTokens.CLOCK_TEXT, Theme.FONT_LIGHT);    // light and slightly translucent
        else t.style(time, 15, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        time.setGravity(Gravity.CENTER); clockBox.addView(time);
        TextClock dateClock = new TextClock(c); dateClock.setFormat12Hour("EEE, MMM d"); dateClock.setFormat24Hour("EEE, MMM d");
        date = dateClock;
        if (t.liquid) t.style(date, 9, LiquidTokens.DATE_TEXT, Theme.FONT_REGULAR);
        else t.style(date, 9, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        date.setGravity(Gravity.CENTER); clockBox.addView(date);
        LayoutParams box = new LayoutParams(t.dp(85),t.dp(39)); box.leftMargin=t.dp(12); addView(clockBox,box);
        update(prefs);
    }
    private void update(Prefs prefs) {
        clockBox.setVisibility(prefs.showClock() ? VISIBLE : GONE);
        date.setVisibility(prefs.showDate() ? VISIBLE : GONE);
    }
    public void refresh(Panel.Host host) { update(host.prefs()); }

    /**
     * Puts the selection back on this header's own destination. Activating a destination moves the glass selection to it (the kit's
     * behaviour), and Home's header stays alive under the screen it opened, so without this Home still marked that screen (say Apps)
     * after Back. Selection only: focus is not moved.
     */
    public void showSelected() {
        if (glassNav != null && glassSelected != null && !glassSelected.equals(glassNav.getSelectedId())) glassNav.setSelectedId(glassSelected);
    }

    /** The destination this header marks as selected right now (for tests); null in graphite. */
    public String selectedDestination() {
        return glassNav == null ? null : glassNav.getSelectedId();
    }
    public void focusCurrent() {
        if (glassNav != null) glassNav.requestFocusOn(glassSelected);
        else if (current != null) current.requestFocus();
    }

    /** Live download activity on the Downloads destination: 0..1 real progress, negative while working with no known size, NaN for none. */
    public void setDownloadProgress(float aggregate) {
        if (glassNav != null) glassNav.setItemProgress("Downloads", aggregate);
    }

    /** A finished (or failed) download: a brief acknowledgment on the Downloads destination. */
    public void acknowledgeDownload(boolean ok) {
        if (glassNav != null) glassNav.acknowledge("Downloads", ok);
    }

    /** What's New: a discreet dot on Settings until the notes for this version have been seen (no seventh destination). */
    public void setWhatsNewBadge(boolean on) {
        if (glassNav != null) glassNav.setItemBadge("Settings", on);
    }

    /**
     * True once every piece of this header's glass (the rail, its pills, the clock) shows real glass pixels for the current wallpaper.
     * A header that has just been built draws a plain placeholder until then; MainActivity keeps the previous header on screen until
     * this is true, so changing destination never flashes the placeholder. Graphite has no glass: always true.
     */
    public boolean glassReady() {
        return glassNav == null || ready(this);
    }

    private static boolean ready(View v) {
        if (v.getVisibility() != VISIBLE || v.getWidth() == 0 || v.getHeight() == 0) return true;      // nothing to show yet
        if (v instanceof tv.calmhome.glasscomponents.GraphiteGlassRenderer.CachedView
                && !((tv.calmhome.glasscomponents.GraphiteGlassRenderer.CachedView) v).hasMaterial()) return false;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (!ready(g.getChildAt(i))) return false;
        }
        return true;
    }

    /** The supplied glass navigation (Liquid Glass only; null in graphite). */
    public GlassTopNavView glassNav() {
        return glassNav;
    }

    // ---------------------------------------------------------------------------------------- Liquid Glass header

    /**
     * Logo, the glass rail, and the glass clock with its date: one horizontal header. The components sample the real
     * wallpaper (see {@link GlassKit}); the labels and the date stay sharp text.
     */
    private void buildGlassHeader(Context c, Theme t, Prefs prefs, String selected, Consumer<String> onTab) {
        glassSelected = selected;
        glassOnTab = onTab;
        float density = c.getResources().getDisplayMetrics().density;
        PulseLogo headerLogo = new PulseLogo(c, t.dp(20)).luminous();
        if (tv.calmhome.launcher.BuildConfig.STANDALONE_ZPAPPS) headerLogo.name("ZPAPPS").suffix("", 0);
        addView(headerLogo, new LayoutParams(Widgets.WRAP, Widgets.WRAP));      // the concept's luminous mark and wordmark
        glassNav = new GlassTopNavView(c);
        glassNav.setLabelSp(13);
        glassNav.setReducedMotion(t.reducedMotion);                     // the focus glides between destinations, unless motion is reduced
        // A slimmer rail that wraps its buttons (56 dp tall, was 72; 38 dp pills of 84 dp, were 48 and spread across the header),
        // so the header is lighter and the wallpaper shows between the rail and the clock.
        glassNav.setMetrics(RAIL_BUTTON_DP, RAIL_BUTTON_WIDTH_DP);
        LayoutParams nl = new LayoutParams(Widgets.WRAP, Math.round(RAIL_DP * density));
        nl.leftMargin = t.dp(16);
        nl.rightMargin = t.dp(16);
        addView(glassNav, nl);
        addView(new View(c), new LayoutParams(0, 1, 1f));              // the free space between the rail and the clock
        clockBox = new LinearLayout(c);
        clockBox.setOrientation(VERTICAL);
        clockBox.setGravity(Gravity.END);
        glassClock = new GlassClockView(c);
        glassClock.setDigitHeightDp(GlassKit.CLOCK_DIGIT_DP);
        clockBox.addView(glassClock, new LayoutParams(Widgets.WRAP, Widgets.WRAP));
        glassDate = new TextView(c);                      // not a TextClock: the date follows the clock's own time source
        date = glassDate;
        t.style(date, 11, LiquidTokens.DATE_TEXT, Theme.FONT_LIGHT);
        date.setGravity(Gravity.END);
        LayoutParams dl = new LayoutParams(Widgets.WRAP, Widgets.WRAP);
        dl.gravity = Gravity.END;
        clockBox.addView(date, dl);
        addView(clockBox, new LayoutParams(Widgets.WRAP, Widgets.WRAP));
        setGlassItems();
        t.kit.bindNav(glassNav);
        t.kit.bindClock(glassClock);
        glassClock.setTimeSource(SystemTime::now);
        glassClock.setOnMinuteChange(this::syncClock);
        glassClock.setAutoUpdate(true);                  // at each minute boundary, while attached
        syncClock();
        update(prefs);
    }

    /** The five destinations, in the app's order, with the real action behind each. */
    private void setGlassItems() {
        List<GlassTopNavView.Item> items = new ArrayList<>();
        for (String tab : TABS) items.add(new GlassTopNavView.Item(tab, tab));
        glassNav.setItems(items, id -> glassOnTab.accept(id));
        glassNav.setSelectedId(glassSelected);
    }

    /** The clock follows the system: the 12/24-hour setting, the time zone and the minute, also after a resume. */
    private void syncClock() {
        if (glassClock == null) return;
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(SystemTime.now());                            // the system clock (a test may show another time, see SystemTime)
        glassClock.setUse24Hour(DateFormat.is24HourFormat(getContext()));
        glassClock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        syncDate(now);
    }

    /** The date line under the glass clock follows the same time source as the clock. */
    private void syncDate(Calendar now) {
        if (glassDate != null) glassDate.setText(DateFormat.format("EEE, MMM d", now));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (glassClock == null) return;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        getContext().registerReceiver(timeChanged, f);
        syncClock();
    }

    @Override protected void onDetachedFromWindow() {
        if (glassClock != null) {
            try {
                getContext().unregisterReceiver(timeChanged);
            } catch (IllegalArgumentException notRegistered) { /* never attached */ }
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) syncClock();
    }
}
