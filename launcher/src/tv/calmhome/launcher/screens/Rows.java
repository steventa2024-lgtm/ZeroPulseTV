package tv.calmhome.launcher.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Settings rows with one focus style. Toggle: OK flips. Choice: Left/Right (or OK) cycles the value.
 * Action: OK runs. Info: focusable so it can be read and scrolled to, does nothing on OK.
 */
final class Rows {

    interface IntChange {
        void set(int index);
    }

    interface BoolChange {
        void set(boolean value);
    }

    private final Context c;
    private final Theme t;

    Rows(Context c, Theme t) {
        this.c = c;
        this.t = t;
    }

    LinearLayout toggle(String label, boolean value, BoolChange change) {
        final boolean[] on = {value};
        final LinearLayout row = base(label);
        final FrameLayout sw = new FrameLayout(c);
        final View knob = new View(c);
        knob.setBackground(t.rounded(Theme.SOFT_WHITE, 9));
        sw.addView(knob, new FrameLayout.LayoutParams(t.dp(16), t.dp(16), Gravity.CENTER_VERTICAL));
        row.addView(sw, new LinearLayout.LayoutParams(t.dp(40), t.dp(22)));
        final Runnable paint = () -> {
            sw.setBackground(t.rounded(on[0] ? Theme.ELECTRIC_BLUE : Theme.BORDER, 11));
            FrameLayout.LayoutParams kl = (FrameLayout.LayoutParams) knob.getLayoutParams();
            kl.gravity = Gravity.CENTER_VERTICAL | (on[0] ? Gravity.END : Gravity.START);
            kl.leftMargin = kl.rightMargin = t.dp(3);
            knob.setLayoutParams(kl);
            row.setContentDescription(label + (on[0] ? ", on" : ", off"));
        };
        paint.run();
        row.setOnClickListener(v -> {
            on[0] = !on[0];
            paint.run();
            change.set(on[0]);
        });
        return row;
    }

    LinearLayout choice(String label, final String[] values, int index, final IntChange change) {
        final int[] i = {Math.max(0, Math.min(values.length - 1, index))};
        final LinearLayout row = base(label);
        final TextView value = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        row.addView(value);
        final Runnable paint = () -> {
            boolean f = row.hasFocus() || row.isFocused();
            value.setText(f ? "‹  " + values[i[0]] + "  ›" : values[i[0]]);
            value.setTextColor(f ? Theme.SOFT_WHITE : Theme.MUTED_GRAY);
            row.setContentDescription(label + ", " + values[i[0]]);
        };
        paint.run();
        final View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, has) -> {
            base.onFocusChange(v, has);
            paint.run();
        });
        final IntChange step = d -> {
            i[0] = ((i[0] + d) % values.length + values.length) % values.length;
            paint.run();
            change.set(i[0]);
        };
        row.setOnClickListener(v -> step.set(1));
        row.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { step.set(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { step.set(-1); return true; }
            return false;
        });
        return row;
    }

    LinearLayout action(String label, String value, boolean danger, View.OnClickListener click) {
        LinearLayout row = base(label);
        if (danger) ((TextView) row.getChildAt(0)).setTextColor(Theme.ERROR);
        TextView v = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        v.setText(value == null ? "›" : value + "   ›");
        row.addView(v);
        row.setOnClickListener(click);
        return row;
    }

    LinearLayout info(String label, String value, int valueColor) {
        LinearLayout row = base(label);
        TextView v = t.text(c, Theme.T_BODY, valueColor, Theme.FONT_REGULAR);
        v.setText(value);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1.4f));
        row.setContentDescription(label + ", " + value);
        return row;
    }

    private LinearLayout base(String label) {
        final LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(t.dp(16), t.dp(12), t.dp(16), t.dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(label);
        final TextView l = t.text(c, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has
                ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.22f), Theme.ELECTRIC_BLUE, 1.5f, 10) : null));
        return row;
    }

    static LinearLayout.LayoutParams full(Theme t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(2);
        return lp;
    }
}
