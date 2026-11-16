package tv.calmhome.launcher.screens;

import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/** Common scrolling content, field and focus behavior for the downloader screens. */
abstract class DownloadPage extends Panel {
    final LinearLayout body;
    View first;
    DownloadPage(Host host) {
        super(host);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(t.dp(5), t.dp(3), t.dp(5), t.dp(7));
    }
    View screen(String title) {
        ScrollView scroll = new ScrollView(c);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        // Keep a tall document inside its viewport, including above the separate wallpaper credit.
        scroll.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        scroll.setClipToOutline(true);
        scroll.setSmoothScrollingEnabled(!t.reducedMotion);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.addView(body);
        return frame(title, scroll, .57f);
    }
    TextView text(String value, float size, boolean heading) {
        TextView v = t.text(c, size, heading ? Theme.SOFT_WHITE : Theme.MUTED_GRAY,
                heading ? Theme.FONT_MEDIUM : Theme.FONT_REGULAR);
        v.setText(value);
        return v;
    }

    static void updateText(TextView view, String value) {
        if (!android.text.TextUtils.equals(view.getText(), value)) view.setText(value);
    }
    LinearLayout column() {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
        col.setBackground(t.glass(Theme.R_PANEL, .85f));
        return col;
    }
    LinearLayout.LayoutParams spaced(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(gap);
        return lp;
    }
    /**
     * A button. In Liquid Glass it is a capsule of the approved glass (the shared renderer, its finish and focus crossfade) around the
     * label, with no outline; in graphite, the label on its graphite pill. Use {@link #labelOf} to change its text.
     */
    View action(String label, Runnable click) {
        TextView v = text(label, Theme.T_NAV, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        tv.calmhome.launcher.ui.ZeroPulseGlassRow glass = w.glassButton(v, tv.calmhome.launcher.ui.GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
        if (glass != null) {
            glass.setContentDescription(label);
            glass.setOnClickListener(view -> click.run());
            return glass;
        }
        v.setBackground(t.glass(Theme.R_PILL, .84f));
        w.focusable(v, Theme.R_PILL, null, true);
        v.setOnClickListener(view -> click.run());
        return v;
    }

    /** The label inside a button made by {@link #action}. */
    static TextView labelOf(View action) {
        if (action instanceof TextView) return (TextView) action;
        return (TextView) ((tv.calmhome.launcher.ui.ZeroPulseGlassRow) action).content().getChildAt(0);
    }

    /** Changes a button's label (and what it says to accessibility). */
    static void setLabel(View action, String label) {
        labelOf(action).setText(label);
        action.setContentDescription(label);
    }

    /** A list-row button: its label starts at the left. */
    static void alignStart(View action) {
        labelOf(action).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }

    /** A small button (a header's or a row's own action): less padding round the label. */
    void compact(View action) {
        labelOf(action).setPadding(t.dp(10), t.dp(3), t.dp(10), t.dp(3));
    }
    EditText field(String hint, int inputType, int maxLength) {
        EditText v = new EditText(c);
        t.style(v, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        v.setSingleLine(true);
        v.setInputType(inputType);
        v.setHint(hint);
        v.setHintTextColor(Theme.MUTED_GRAY);
        v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        v.setPadding(t.dp(14), t.dp(8), t.dp(14), t.dp(8));
        v.setBackground(t.glass(Theme.R_PILL, .82f));
        v.setOnFocusChangeListener((view, focused) -> view.setForeground(w.ring(focused, Theme.R_PILL)));
        return v;
    }
    void showError(TextView v, Exception e) {
        v.setText(e.getMessage() == null ? "Unable to complete this action." : e.getMessage());
        v.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    void dismissKeyboard() {
        android.view.inputmethod.InputMethodManager ime =
                c.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (ime != null) ime.hideSoftInputFromWindow(body.getWindowToken(), 0);
    }
    @Override public void focusFirst() { if (first != null) first.requestFocus(); }
}
