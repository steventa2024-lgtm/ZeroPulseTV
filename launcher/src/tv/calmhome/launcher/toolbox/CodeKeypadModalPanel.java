package tv.calmhome.launcher.toolbox;

import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Modal Liquid Glass 3x4 D-Pad shortcode keypad and direct HTTPS URL input panel.
 */
public final class CodeKeypadModalPanel extends Panel {

    public interface SubmitListener {
        void onSubmit(String codeOrUrl);
    }

    private final SubmitListener submitListener;
    private EditText input;
    private TextView resolvedPreview;
    private View firstKey;

    public CodeKeypadModalPanel(Host host, SubmitListener submitListener) {
        super(host);
        this.submitListener = submitListener;
    }

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    public View enterMotionView() {
        return card;
    }

    private View card;

    @Override
    protected View build() {
        boolean glass = t.liquid && t.kit != null;
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, glass ? 0.54f : 0.72f));
        scrim.setClipChildren(false);
        scrim.setClipToPadding(false);

        FrameLayout pane = new FrameLayout(c);
        pane.setClipChildren(false);
        pane.setClipToPadding(false);
        pane.setBackground(glass
                ? t.glass(Theme.R_PANEL, 0.9f)
                : t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.BORDER, 1, Theme.R_PANEL));

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(t.dp(24), t.dp(20), t.dp(24), t.dp(20));

        TextView title = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Downloader Shortcode");
        box.addView(title);

        TextView sub = t.text(c, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        sub.setText("Enter a 4–8 digit aftv.news code with the D-pad or type an HTTPS APK link.");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        sl.topMargin = t.dp(4);
        box.addView(sub, sl);

        input = new EditText(c);
        t.style(input, Theme.T_BODY + 2, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setHint("e.g. 3975858 or https://...");
        input.setHintTextColor(Theme.MUTED_GRAY);
        input.setPadding(t.dp(10), t.dp(8), t.dp(10), t.dp(8));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit();
                return true;
            }
            return false;
        });

        View fieldWrap = glass ? w.glassField(input, null, null) : null;
        if (fieldWrap == null) {
            input.setBackground(t.glass(Theme.R_PILL, 0.85f));
            fieldWrap = input;
        }
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Widgets.MATCH, glass ? t.dp(40) : Widgets.WRAP);
        fl.topMargin = t.dp(12);
        box.addView(fieldWrap, fl);

        resolvedPreview = t.text(c, Theme.T_CAPTION, Theme.PULSE_CYAN, Theme.FONT_REGULAR);
        resolvedPreview.setText("Ready for code or URL");
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        pl.topMargin = t.dp(6);
        box.addView(resolvedPreview, pl);

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String resolved = ToolboxEngine.resolveInputToUrl(s.toString());
                resolvedPreview.setText(resolved == null ? "Enter 4–8 digits or a valid domain/URL" : "Target: " + resolved);
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        // Quick preset shortcodes
        LinearLayout presets = new LinearLayout(c);
        presets.setClipChildren(false);
        presets.setClipToPadding(false);
        String[][] presetList = {
                {"3975858", "AK47"},
                {"1499898", "SportzX"},
                {"54794", "SmartTube"},
                {"71893", "Stremio"}
        };
        for (int i = 0; i < presetList.length; i++) {
            final String pCode = presetList[i][0];
            String label = "#" + pCode + " " + presetList[i][1];
            View chip = glassPill(label, v -> {
                input.setText(pCode);
                input.setSelection(pCode.length());
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, t.dp(32), 1f);
            if (i > 0) clp.leftMargin = t.dp(6);
            presets.addView(chip, clp);
        }
        LinearLayout.LayoutParams preLp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        preLp.topMargin = t.dp(10);
        box.addView(presets, preLp);

        // 3x4 Numeric Keypad
        GridLayout keypad = new GridLayout(c);
        keypad.setColumnCount(3);
        keypad.setClipChildren(false);
        keypad.setClipToPadding(false);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "CLR", "0", "DEL"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            View btn = glassPill(key, v -> onKeypadPress(key));
            if (i == 0) firstKey = btn;
            GridLayout.LayoutParams klp = new GridLayout.LayoutParams();
            klp.width = t.dp(130);
            klp.height = t.dp(40);
            klp.setMargins(0, 0, (i % 3 == 2) ? 0 : t.dp(8), t.dp(8));
            keypad.addView(btn, klp);
        }
        LinearLayout.LayoutParams kplp = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        kplp.gravity = Gravity.CENTER_HORIZONTAL;
        kplp.topMargin = t.dp(12);
        box.addView(keypad, kplp);

        // Action row
        LinearLayout actions = new LinearLayout(c);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        View cancelBtn = glassPill("Cancel", v -> host.close(this));
        View goBtn = glassPill("Download & Install", v -> submit());
        LinearLayout.LayoutParams al1 = new LinearLayout.LayoutParams(0, t.dp(38), 1f);
        LinearLayout.LayoutParams al2 = new LinearLayout.LayoutParams(0, t.dp(38), 1.6f);
        al2.leftMargin = t.dp(10);
        actions.addView(cancelBtn, al1);
        actions.addView(goBtn, al2);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        actLp.topMargin = t.dp(10);
        box.addView(actions, actLp);

        pane.addView(box, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        card = pane;
        scrim.addView(pane, new FrameLayout.LayoutParams(t.dp(460), Widgets.WRAP, Gravity.CENTER));

        pane.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK) {
                host.close(this);
                return true;
            }
            return false;
        });
        return scrim;
    }

    private View glassPill(String label, View.OnClickListener click) {
        TextView tv = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        if (t.liquid && t.kit != null) {
            ZeroPulseGlassRow row = w.glassButton(tv, GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
            row.setContentDescription(label);
            row.setOnClickListener(click);
            return row;
        }
        tv.setBackground(t.glass(Theme.R_PILL, 0.85f));
        w.focusable(tv, Theme.R_PILL, null, true);
        tv.setOnClickListener(click);
        return tv;
    }

    private void onKeypadPress(String key) {
        String current = input.getText().toString();
        if ("CLR".equals(key)) {
            input.setText("");
            return;
        }
        if ("DEL".equals(key)) {
            if (!current.isEmpty()) {
                input.setText(current.substring(0, current.length() - 1));
                input.setSelection(input.getText().length());
            }
            return;
        }
        if (current.length() < 120) {
            String next = current + key;
            input.setText(next);
            input.setSelection(next.length());
        }
    }

    private void submit() {
        String raw = input.getText().toString().trim();
        if (ToolboxEngine.resolveInputToUrl(raw) == null) {
            host.toast(String.format(Locale.US, "Enter a 4–8 digit Downloader code or valid HTTPS URL."));
            return;
        }
        host.close(this);
        submitListener.onSubmit(raw);
    }

    @Override
    public void focusFirst() {
        if (firstKey != null) firstKey.requestFocus();
    }
}
