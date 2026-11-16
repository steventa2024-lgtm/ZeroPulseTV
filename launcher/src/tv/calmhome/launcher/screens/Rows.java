package tv.calmhome.launcher.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Settings rows with one focus style. Toggle: OK flips. Choice: Left and Right navigate; OK starts a change (the value shows
 * its arrows), Left and Right then step it, and OK, Up, Down or Back finish. Action: OK runs. Info: focusable so it can be read
 * and scrolled to, does nothing on OK.
 *
 * <p>In Liquid Glass every row is one {@link ZeroPulseGlassRow}: the approved glass, finish and focus crossfade that Home's tiles use,
 * with no outline and no second background. A list row's glass is there while it has focus; a card (the Wallpapers controls) shows
 * its glass all the time. Graphite keeps its own outline.
 */
final class Rows {

    interface IntChange {
        void set(int index);
    }

    interface BoolChange {
        void set(boolean value);
    }

    /** A row: the view that takes focus and clicks, and the line of label and value inside it. */
    private static final class Row {
        final View host;
        final LinearLayout line;

        Row(View host, LinearLayout line) {
            this.host = host;
            this.line = line;
        }
    }

    private final Context c;
    private final Theme t;
    private final boolean glass, idleGlass;

    /** List rows: glass on focus. */
    Rows(Context c, Theme t) {
        this(c, t, false);
    }

    /** {@code idleGlass} true makes cards that show their glass all the time (the Wallpapers controls). */
    Rows(Context c, Theme t, boolean idleGlass) {
        this.c = c;
        this.t = t;
        this.glass = t.liquid && t.kit != null;
        this.idleGlass = idleGlass;
    }

    View toggle(String label, boolean value, BoolChange change) {
        final boolean[] on = {value};
        final Row r = base(label);
        final View host = r.host;
        if (glass) {
            // Liquid Glass: the glass switch (graphite capsule, pearl knob that slides). It changes only when OK is pressed on its row.
            final tv.calmhome.launcher.ui.GlassSwitch sw = new tv.calmhome.launcher.ui.GlassSwitch(c);
            sw.setReducedMotion(t.reducedMotion);
            sw.setChecked(on[0], false);
            r.line.addView(sw, new LinearLayout.LayoutParams(t.dp(38), t.dp(21)));
            host.setContentDescription(label + (on[0] ? ", on" : ", off"));
            host.setOnClickListener(v -> {
                on[0] = !on[0];
                sw.setChecked(on[0], true);
                host.setContentDescription(label + (on[0] ? ", on" : ", off"));
                change.set(on[0]);
            });
            return host;
        }
        final FrameLayout sw = new FrameLayout(c);
        final View knob = new View(c);
        knob.setBackground(t.rounded(Theme.SOFT_WHITE, 9));
        sw.addView(knob, new FrameLayout.LayoutParams(t.dp(16), t.dp(16), Gravity.CENTER_VERTICAL));
        r.line.addView(sw, new LinearLayout.LayoutParams(t.dp(40), t.dp(22)));
        final Runnable paint = () -> {
            sw.setBackground(t.rounded(on[0] ? Theme.ELECTRIC_BLUE : Theme.BORDER, 11));
            FrameLayout.LayoutParams kl = (FrameLayout.LayoutParams) knob.getLayoutParams();
            kl.gravity = Gravity.CENTER_VERTICAL | (on[0] ? Gravity.END : Gravity.START);
            kl.leftMargin = kl.rightMargin = t.dp(3);
            knob.setLayoutParams(kl);
            host.setContentDescription(label + (on[0] ? ", on" : ", off"));
        };
        paint.run();
        host.setOnClickListener(v -> {
            on[0] = !on[0];
            paint.run();
            change.set(on[0]);
        });
        return host;
    }

    /** The row that is being changed right now (at most one), and how to put it back to simply focused. */
    private Runnable stopEditing;

    /**
     * Ends a change in progress (a Back press does this before it closes anything). True if one was in progress.
     */
    boolean endEditing() {
        if (stopEditing == null) return false;
        Runnable stop = stopEditing;
        stopEditing = null;
        stop.run();
        return true;
    }

    /**
     * A value with a short list of choices. <b>Left and Right navigate</b> like everywhere else on the screen: a row never
     * changes its value because a direction key was pressed on the way to another control. <b>OK starts a change</b> (the value
     * shows its arrows, {@code ‹ value ›}): Left and Right then step the value, and OK, Up, Down, Back or moving away keep it
     * and finish. (3005 changed the value on every Left or Right, so reaching Dim overlay or the framing controls from the side
     * changed them, and they were reachable only by going Down from a preview.)
     */
    View choice(String label, final String[] values, int index, final IntChange change) {
        return choice(label, values, index, false, change);
    }

    /**
     * As {@link #choice(String, String[], int, IntChange)}; with {@code applyOnFinish} the change is applied once, when the change
     * ends and only if the value moved, instead of on every step (for settings that rebuild the whole screen).
     */
    View choice(String label, final String[] values, int index, final boolean applyOnFinish, final IntChange change) {
        final int[] i = {Math.max(0, Math.min(values.length - 1, index))};
        final int[] started = {i[0]};
        final boolean[] changing = {false};
        final Row r = base(label);
        final View host = r.host;
        final TextView value = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        r.line.addView(value);
        final Runnable paint = () -> {
            boolean f = host.hasFocus() || host.isFocused();
            value.setText(changing[0] ? "‹  " + values[i[0]] + "  ›" : values[i[0]]);
            value.setTextColor(f ? Theme.SOFT_WHITE : Theme.MUTED_GRAY);
            host.setContentDescription(label + ", " + values[i[0]] + (changing[0] ? ", changing: Left and Right change it, OK keeps it" : ""));
        };
        paint.run();
        final Runnable stop = () -> {
            if (!changing[0]) return;
            changing[0] = false;
            paint.run();
            if (applyOnFinish && i[0] != started[0]) {
                started[0] = i[0];
                change.set(i[0]);
            }
        };
        final View.OnFocusChangeListener base = host.getOnFocusChangeListener();
        host.setOnFocusChangeListener((v, has) -> {
            if (base != null) base.onFocusChange(v, has);
            if (!has) {
                if (stopEditing == stop) stopEditing = null;
                stop.run();
            }
            paint.run();
        });
        final IntChange step = d -> {
            i[0] = ((i[0] + d) % values.length + values.length) % values.length;
            paint.run();
            if (!applyOnFinish) {
                started[0] = i[0];
                change.set(i[0]);
            }
        };
        host.setOnClickListener(v -> {
            if (changing[0]) {
                endEditing();
                return;
            }
            endEditing();                                  // another row may have been left mid-change
            changing[0] = true;
            stopEditing = stop;
            paint.run();
        });
        host.setOnKeyListener((v, keyCode, e) -> {
            if (!changing[0]) return false;                // not changing: every direction key is navigation
            if (e.getAction() != KeyEvent.ACTION_DOWN) return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_BACK;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { step.set(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { step.set(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_BACK) { endEditing(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) endEditing();   // keep the value, then move on
            return false;
        });
        return host;
    }

    View action(String label, String value, boolean danger, View.OnClickListener click) {
        Row r = base(label);
        if (danger) ((TextView) r.line.getChildAt(0)).setTextColor(Theme.ERROR);
        TextView v = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        v.setText(value == null ? "›" : value + "   ›");
        r.line.addView(v);
        r.host.setOnClickListener(click);
        return r.host;
    }

    View info(String label, String value, int valueColor) {
        Row r = base(label);
        TextView v = t.text(c, Theme.T_BODY, valueColor, Theme.FONT_REGULAR);
        v.setText(value);
        v.setGravity(Gravity.END);
        r.line.addView(v, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1.4f));
        r.host.setContentDescription(label + ", " + value);
        return r.host;
    }

    private Row base(String label) {
        final LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        final TextView l = t.text(c, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        l.setText(label);
        line.addView(l, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        if (glass) {
            ZeroPulseGlassRow host = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CARD, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, idleGlass);
            line.setPadding(t.dp(16), t.dp(11), t.dp(16), t.dp(11));
            host.content().addView(line, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP, Gravity.CENTER_VERTICAL));
            host.setContentDescription(label);
            return new Row(host, line);
        }
        line.setPadding(t.dp(16), t.dp(12), t.dp(16), t.dp(12));
        line.setFocusable(true);
        line.setClickable(true);
        line.setContentDescription(label);
        line.setOnFocusChangeListener((v, has) -> v.setBackground(has
                ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.22f), Theme.ELECTRIC_BLUE, 1.5f, 10) : null));
        return new Row(line, line);
    }

    static LinearLayout.LayoutParams full(Theme t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(2);
        return lp;
    }
}
