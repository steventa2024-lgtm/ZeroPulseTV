package tv.calmhome.launcher.toolbox;

import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Modal Liquid Glass panel displayed when a Downloader shortcode or URL resolves to
 * an HTML web directory instead of a direct APK binary. Extracts clickable {@code .apk} links.
 */
public final class WebDirectoryModalPanel extends Panel {

    public interface ApkSelectListener {
        void onApkSelected(String apkUrl);
    }

    private final String pageUrl;
    private final ApkSelectListener listener;
    private LinearLayout listBox;
    private TextView statusText;
    private View firstFocus;
    private View card;

    public WebDirectoryModalPanel(Host host, String pageUrl, ApkSelectListener listener) {
        super(host);
        this.pageUrl = pageUrl;
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

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(t.dp(24), t.dp(20), t.dp(24), t.dp(20));

        TextView title = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Web Directory Detected");
        col.addView(title);

        TextView urlView = t.text(c, Theme.T_CAPTION, Theme.PULSE_CYAN, Theme.FONT_REGULAR);
        urlView.setText(pageUrl);
        urlView.setSingleLine(true);
        urlView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        ulp.topMargin = t.dp(4);
        col.addView(urlView, ulp);

        statusText = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        statusText.setText("Scanning page for downloadable .apk installers…");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        slp.topMargin = t.dp(10);
        col.addView(statusText, slp);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(c);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(240));
        scLp.topMargin = t.dp(10);
        col.addView(scroll, scLp);

        View closeBtn = buildRowButton("Close Directory", true, v -> host.close(this));
        firstFocus = closeBtn;
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(42));
        clp.topMargin = t.dp(12);
        col.addView(closeBtn, clp);

        pane.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        card = pane;
        scrim.addView(pane, new FrameLayout.LayoutParams(t.dp(540), Widgets.WRAP, Gravity.CENTER));

        ToolboxEngine.get(c).extractApkLinks(pageUrl, this::renderLinks);
        return scrim;
    }

    private void renderLinks(List<String> links, String error) {
        if (!c.getResources().getConfiguration().toString().isEmpty() && listBox == null) return;
        listBox.removeAllViews();
        if (error != null) {
            statusText.setText("Could not inspect web directory: " + error);
            return;
        }
        if (links.isEmpty()) {
            statusText.setText("No direct .apk links were found on this page.");
            return;
        }
        statusText.setText("Found " + links.size() + " APK installer" + (links.size() == 1 ? "" : "s") + ". Select one to install:");
        View firstLink = null;
        for (int i = 0; i < links.size(); i++) {
            final String apkUrl = links.get(i);
            String segment = Uri.parse(apkUrl).getLastPathSegment();
            String label = (segment == null || segment.trim().isEmpty()) ? apkUrl : segment;
            View row = buildRowButton(label + "   ·   " + apkUrl, false, v -> {
                host.close(this);
                listener.onApkSelected(apkUrl);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(44));
            lp.topMargin = i == 0 ? 0 : t.dp(4);
            listBox.addView(row, lp);
            if (firstLink == null) firstLink = row;
        }
        if (firstLink != null) {
            firstFocus = firstLink;
            firstLink.requestFocus();
        }
    }

    private View buildRowButton(String label, boolean capsule, View.OnClickListener click) {
        TextView tv = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(capsule ? Gravity.CENTER : (Gravity.START | Gravity.CENTER_VERTICAL));
        tv.setPadding(t.dp(14), t.dp(8), t.dp(14), t.dp(8));
        if (t.liquid && t.kit != null) {
            ZeroPulseGlassRow row = w.glassButton(tv,
                    capsule ? GraphiteGlassSurface.Shape.CAPSULE : GraphiteGlassSurface.Shape.CARD,
                    capsule, 1f);
            row.setContentDescription(label);
            row.setOnClickListener(click);
            return row;
        }
        tv.setBackground(t.glass(capsule ? Theme.R_PILL : Theme.R_PANEL, 0.85f));
        w.focusable(tv, capsule ? Theme.R_PILL : Theme.R_PANEL, null, true);
        tv.setOnClickListener(click);
        return tv;
    }

    @Override
    public void focusFirst() {
        if (firstFocus != null) firstFocus.requestFocus();
    }
}
