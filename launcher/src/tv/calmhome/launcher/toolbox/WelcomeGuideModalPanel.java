package tv.calmhome.launcher.toolbox;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.calmhome.launcher.R;
import tv.calmhome.launcher.screens.Panel;
import tv.calmhome.launcher.ui.GraphiteGlassSurface;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;
import tv.calmhome.launcher.ui.ZeroPulseGlassRow;

/**
 * Modal Liquid Glass First-Launch Landing and 3-Step Interactive D-Pad Guided Tour for ZPAPPS.
 */
public final class WelcomeGuideModalPanel extends Panel {

    private int step = 0; // 0 = Welcome landing, 1..3 = 3-step interactive D-Pad tour
    private TextView permBadge;
    private View permBtn, primaryBtn;
    private LinearLayout contentContainer;
    private View card;

    public WelcomeGuideModalPanel(Host host, boolean startTourDirectly) {
        super(host);
        this.step = startTourDirectly ? 1 : 0;
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
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, glass ? 0.58f : 0.78f));

        FrameLayout pane = new FrameLayout(c);
        pane.setClipChildren(false);
        pane.setBackground(glass
                ? t.glass(Theme.R_PANEL, 0.9f)
                : t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.BORDER, 1, Theme.R_PANEL));

        contentContainer = new LinearLayout(c);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setClipChildren(false);
        contentContainer.setPadding(t.dp(28), t.dp(24), t.dp(28), t.dp(24));
        pane.addView(contentContainer, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        card = pane;
        scrim.addView(pane, new FrameLayout.LayoutParams(t.dp(620), Widgets.WRAP, Gravity.CENTER));

        renderStep();
        return scrim;
    }

    public void refreshPermissionState() {
        if (step != 0 || permBadge == null) return;
        boolean granted = ToolboxEngine.get(c).canRequestPackageInstalls();
        permBadge.setText(granted ? "Ready to Install Apps" : "Install Permission Needed");
        permBadge.setTextColor(granted ? 0xFF68BC71 : 0xFFFFB74D);
        if (permBtn != null) permBtn.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        refreshPermissionState();
    }

    private void renderStep() {
        contentContainer.removeAllViews();
        if (step == 0) {
            buildWelcome();
        } else {
            buildTourStep(step);
        }
    }

    private void buildWelcome() {
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(c);
        Bitmap rawLogo = BitmapFactory.decodeResource(c.getResources(), R.drawable.zpapps_logo_source);
        if (rawLogo != null) logo.setImageBitmap(rawLogo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(t.dp(54), t.dp(54)));

        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(t.dp(14), 0, 0, 0);
        TextView h1 = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h1.setText("ZPAPPS  ·  ZeroPulse TV Toolbox");
        titles.addView(h1);
        TextView h2 = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        h2.setText("20 curated Android TV apps, any Downloader shortcode, and automatic APK storage cleanup.");
        titles.addView(h2);
        header.addView(titles, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        contentContainer.addView(header);

        // 3-column feature highlights
        LinearLayout cols = new LinearLayout(c);
        cols.setClipChildren(false);
        String[][] features = {
                {"Curated TV Hub", "Sports, IPTV, 4K VOD, players & anti-popup tools organized by category."},
                {"Any Shortcode", "Built-in D-pad numeric keypad resolves Downloader codes & HTML directories."},
                {"Auto Storage Cleanup", "Instant Delete APK prompt after system install keeps TV storage clean."}
        };
        for (int i = 0; i < features.length; i++) {
            LinearLayout cardBox = new LinearLayout(c);
            cardBox.setOrientation(LinearLayout.VERTICAL);
            cardBox.setPadding(t.dp(12), t.dp(10), t.dp(12), t.dp(10));
            cardBox.setBackground(t.glass(Theme.R_PANEL, 0.45f));
            TextView ft = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            ft.setText(features[i][0]);
            cardBox.addView(ft);
            TextView fd = t.text(c, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            fd.setText(features[i][1]);
            LinearLayout.LayoutParams fdlp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            fdlp.topMargin = t.dp(4);
            cardBox.addView(fd, fdlp);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
            if (i > 0) clp.leftMargin = t.dp(10);
            cols.addView(cardBox, clp);
        }
        LinearLayout.LayoutParams colsLp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        colsLp.topMargin = t.dp(16);
        contentContainer.addView(cols, colsLp);

        // Interactive Install Permission Status Card
        LinearLayout permCard = new LinearLayout(c);
        permCard.setOrientation(LinearLayout.HORIZONTAL);
        permCard.setGravity(Gravity.CENTER_VERTICAL);
        permCard.setPadding(t.dp(14), t.dp(12), t.dp(14), t.dp(12));
        permCard.setBackground(t.glass(Theme.R_PANEL, 0.55f));
        permBadge = t.text(c, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        permCard.addView(permBadge, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        permBtn = pillButton("Grant Install Permission", v -> {
            if (c instanceof Activity) {
                ToolboxEngine.get(c).openInstallPermissionSettings((Activity) c);
            }
        });
        permCard.addView(permBtn, new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(36)));
        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        pclp.topMargin = t.dp(14);
        contentContainer.addView(permCard, pclp);
        refreshPermissionState();

        // Action row
        LinearLayout row = new LinearLayout(c);
        row.setClipChildren(false);
        View skipBtn = pillButton("Skip Intro", v -> {
            ToolboxEngine.get(c).setOnboardingCompleted(true);
            host.close(this);
        });
        primaryBtn = pillButton("Launch Toolbox & Tour", v -> {
            ToolboxEngine.get(c).setOnboardingCompleted(true);
            step = 1;
            renderStep();
            focusFirst();
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, t.dp(40), 1f);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, t.dp(40), 1.7f);
        b2.leftMargin = t.dp(12);
        row.addView(skipBtn, b1);
        row.addView(primaryBtn, b2);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        rlp.topMargin = t.dp(18);
        contentContainer.addView(row, rlp);
    }

    private void buildTourStep(int num) {
        String title;
        String desc;
        if (num == 1) {
            title = "Step 1 of 3  ·  Browse Curated Categories";
            desc = "Use the TV D-pad on Discover Shelves or switch to Toolbox Grid to filter 20 verified apps across Live TV & Sports, IPTV Players, Media Players, Movies & VOD, and Utilities. Hold OK on any tile for App Info or Uninstall.";
        } else if (num == 2) {
            title = "Step 2 of 3  ·  Keypad & Any Shortcode";
            desc = "Select '+ Code' in the top toolbar to enter any 4–8 digit Downloader shortcode on a TV remote keypad, paste an HTTPS link, or automatically pick .apk files out of HTML web directories.";
        } else {
            title = "Step 3 of 3  ·  Essentials Trio & Safe Cleanup";
            desc = "Select 'Essentials (3)' to batch install AdGuard TV, Wuffy Player, and VLC one after another. Whenever you return from the Android installer, ZPAPPS offers to delete leftover .apk cache files automatically.";
        }

        TextView k = t.text(c, Theme.T_CAPTION, Theme.PULSE_CYAN, Theme.FONT_MEDIUM);
        k.setText("INTERACTIVE D-PAD GUIDE");
        contentContainer.addView(k);

        TextView h = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        hlp.topMargin = t.dp(6);
        contentContainer.addView(h, hlp);

        TextView bodyText = t.text(c, Theme.T_BODY + 1, Theme.withAlpha(Theme.SOFT_WHITE, 0.86f), Theme.FONT_REGULAR);
        bodyText.setText(desc);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        blp.topMargin = t.dp(12);
        contentContainer.addView(bodyText, blp);

        LinearLayout row = new LinearLayout(c);
        row.setClipChildren(false);
        View skipBtn = pillButton("Finish Guide", v -> {
            ToolboxEngine.get(c).setOnboardingCompleted(true);
            host.close(this);
        });
        primaryBtn = pillButton(num < 3 ? "Next Step  →" : "Start Using ZPAPPS", v -> {
            if (step < 3) {
                step++;
                renderStep();
                focusFirst();
            } else {
                ToolboxEngine.get(c).setOnboardingCompleted(true);
                host.close(this);
            }
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, t.dp(40), 1f);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, t.dp(40), 1.5f);
        b2.leftMargin = t.dp(12);
        row.addView(skipBtn, b1);
        row.addView(primaryBtn, b2);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        rlp.topMargin = t.dp(22);
        contentContainer.addView(row, rlp);
    }

    private View pillButton(String label, View.OnClickListener click) {
        TextView tv = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(t.dp(14), t.dp(6), t.dp(14), t.dp(6));
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
        if (primaryBtn != null) primaryBtn.requestFocus();
    }
}
