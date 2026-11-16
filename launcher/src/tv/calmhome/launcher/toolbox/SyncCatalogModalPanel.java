package tv.calmhome.launcher.toolbox;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executors;

import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Modal Liquid Glass panel to fetch and cache a remote HTTPS {@code zeropulse_toolbox.json} catalog.
 */
public final class SyncCatalogModalPanel extends Panel {

    public interface SyncListener {
        void onSynced(ToolboxCatalog.Catalog catalog, String message, boolean error);
    }

    private final SyncListener listener;
    private EditText urlInput;
    private View syncButton;
    private View card;

    public SyncCatalogModalPanel(Host host, SyncListener listener) {
        super(host);
        this.listener = listener;
    }

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    public View enterMotionView() {
        return card;
    }

    @Override
    protected View build() {
        boolean glass = t.liquid && t.kit != null;
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, glass ? 0.56f : 0.74f));

        FrameLayout pane = new FrameLayout(c);
        pane.setClipChildren(false);
        pane.setBackground(glass
                ? t.glass(Theme.R_PANEL, 0.9f)
                : t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.BORDER, 1, Theme.R_PANEL));

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(t.dp(24), t.dp(20), t.dp(24), t.dp(20));

        TextView title = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Sync Remote Toolbox Catalog");
        box.addView(title);

        TextView sub = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        sub.setText("Enter an HTTPS URL pointing to a zeropulse_toolbox.json file to refresh app shortcodes without reinstalling.");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        slp.topMargin = t.dp(6);
        box.addView(sub, slp);

        urlInput = new EditText(c);
        t.style(urlInput, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setHint("https://example.com/zeropulse_toolbox.json");
        urlInput.setHintTextColor(Theme.MUTED_GRAY);
        urlInput.setPadding(t.dp(10), t.dp(8), t.dp(10), t.dp(8));
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                triggerSync();
                return true;
            }
            return false;
        });

        View field = glass ? w.glassField(urlInput, null, null) : null;
        if (field == null) {
            urlInput.setBackground(t.glass(Theme.R_PILL, 0.85f));
            field = urlInput;
        }
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(Widgets.MATCH, glass ? t.dp(40) : Widgets.WRAP);
        flp.topMargin = t.dp(14);
        box.addView(field, flp);

        LinearLayout actions = new LinearLayout(c);
        actions.setClipChildren(false);
        View cancelBtn = pillButton("Cancel", v -> host.close(this));
        syncButton = pillButton("Sync Catalog", v -> triggerSync());
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, t.dp(38), 1f);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, t.dp(38), 1.4f);
        b2.leftMargin = t.dp(10);
        actions.addView(cancelBtn, b1);
        actions.addView(syncButton, b2);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        alp.topMargin = t.dp(16);
        box.addView(actions, alp);

        pane.addView(box, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        card = pane;
        scrim.addView(pane, new FrameLayout.LayoutParams(t.dp(480), Widgets.WRAP, Gravity.CENTER));
        return scrim;
    }

    private void triggerSync() {
        final String rawUrl = urlInput.getText().toString().trim();
        if (rawUrl.isEmpty()) {
            host.toast("Enter an HTTPS catalog JSON URL.");
            return;
        }
        host.toast("Syncing remote catalog…");
        Executors.newSingleThreadExecutor().execute(() -> {
            final ToolboxCatalog.SyncResult res = ToolboxCatalog.syncFromRemote(c, rawUrl);
            urlInput.post(() -> {
                host.close(this);
                listener.onSynced(res.catalog, res.message, res.error);
            });
        });
    }

    private View pillButton(String label, View.OnClickListener click) {
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

    @Override
    public void focusFirst() {
        if (syncButton != null) syncButton.requestFocus();
    }
}
