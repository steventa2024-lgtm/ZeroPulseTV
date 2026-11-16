package tv.calmhome.launcher.screens;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import tv.calmhome.launcher.aftv.DownloaderStore;
import tv.calmhome.launcher.aftv.OfficialDownloaderLauncher;
import tv.calmhome.launcher.aftv.OfficialDownloaderLauncher.Outcome;
import tv.calmhome.launcher.core.DownloaderCodes;
import tv.calmhome.launcher.downloads.DownloadPolicy;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * The official Downloader by AFTVnews app, from the Download Center: open it, or get it from the TV's Play Store; send one of the
 * owner's AFTVnews codes into it; and, for a TV with no Play Store, install it from an APK link the owner trusts through the same
 * reviewed install as every other APK here. It is built from the Download Center's own parts, so it has the same glass and focus.
 */
public final class DownloaderPanel extends DownloadPage {
    private final DownloaderStore store;
    private TextView status, message;
    private View primary;
    private LinearLayout codeList, apkCard;
    private EditText codeField, nameField, urlField, shaField;
    private boolean installed;

    public DownloaderPanel(Host host) {
        super(host);
        store = new DownloaderStore(c);
    }

    @Override public String restoreRoute() { return "downloader"; }

    @Override protected View build() {
        LinearLayout card = column();
        card.addView(text("Downloader by AFTVnews", Theme.T_TITLE, true));
        status = text("", Theme.T_BODY, false);
        card.addView(status, spaced(4));
        primary = action("", this::openOrInstall);
        first = primary;
        card.addView(primary, spaced(8));
        message = text("", Theme.T_LABEL, false);
        message.setVisibility(View.GONE);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(message, spaced(6));
        body.addView(card);

        LinearLayout codes = column();
        codes.addView(text("Your codes", Theme.T_TITLE, true));
        codes.addView(text("Select a code to send it into Downloader as aftv.news/<code>.", Theme.T_LABEL, false), spaced(2));
        codeList = new LinearLayout(c);
        codeList.setOrientation(LinearLayout.VERTICAL);
        codeList.setClipChildren(false);
        codeList.setClipToPadding(false);
        codes.addView(codeList, spaced(6));
        LinearLayout add = new LinearLayout(c);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setClipChildren(false);
        codeField = field("Code (digits)", InputType.TYPE_CLASS_NUMBER, DownloaderCodes.MAX_LABEL);
        codeField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(16)});
        nameField = field("Name (optional)", InputType.TYPE_CLASS_TEXT, DownloaderCodes.MAX_LABEL);
        add.addView(codeField, new LinearLayout.LayoutParams(0, Widgets.WRAP, 2));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Widgets.WRAP, 3);
        nl.leftMargin = t.dp(8);
        add.addView(nameField, nl);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
        sl.leftMargin = t.dp(8);
        add.addView(action("Save code", this::saveCode), sl);
        codes.addView(add, spaced(8));
        body.addView(codes, spaced(8));

        apkCard = column();
        apkCard.addView(text("Install from an APK link", Theme.T_TITLE, true));
        apkCard.addView(text("AFTVnews publishes Downloader on Google Play and the Amazon Appstore, and offers no direct APK link of its "
                + "own. If this TV has no Play Store, paste a link and the SHA-256 that you trust. You review it, and Android asks "
                + "before anything is installed.", Theme.T_LABEL, false), spaced(2));
        urlField = field("HTTPS link to the APK", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 8192);
        urlField.setText(store.apkUrl());
        apkCard.addView(urlField, spaced(8));
        shaField = field("SHA-256 (64 hexadecimal digits, recommended)", InputType.TYPE_CLASS_TEXT, 64);
        shaField.setText(store.apkSha256());
        apkCard.addView(shaField, spaced(6));
        apkCard.addView(action("Review and install", this::reviewApk), spaced(8));
        body.addView(apkCard, spaced(8));

        refresh();
        return screen("Downloader");
    }

    // ------------------------------------------------------------------------------------------------ state

    private void refresh() {
        installed = OfficialDownloaderLauncher.isInstalled(c, OfficialDownloaderLauncher.DOWNLOADER);
        status.setText(installed ? "Installed on this TV (" + OfficialDownloaderLauncher.DOWNLOADER + ")."
                : "Not installed on this TV. Get it from the Play Store, or install it from an APK link below.");
        String label = installed ? "Open Downloader" : "Get Downloader from the Play Store";
        setLabel(primary, label);
        apkCard.setVisibility(installed ? View.GONE : View.VISIBLE);
        rebuildCodes();
    }

    private void rebuildCodes() {
        View focused = codeList.findFocus();
        String focusedCode = focused == null ? null : String.valueOf(focused.getTag());
        codeList.removeAllViews();
        List<DownloaderCodes.Entry> list = store.codes();
        if (list.isEmpty()) {
            codeList.addView(text("No codes saved yet.", Theme.T_LABEL, false));
            return;
        }
        for (DownloaderCodes.Entry e : list) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClipChildren(false);
            View send = action(e.code + (e.label.isEmpty() ? "" : "   ·   " + e.label), () -> sendCode(e.code));
            alignStart(send);
            send.setContentDescription("Send code " + e.code + " to Downloader");
            send.setTag(e.code);
            row.addView(send, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
            View remove = action("Remove", () -> removeCode(e.code));
            remove.setContentDescription("Remove code " + e.code);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP);
            rl.leftMargin = t.dp(8);
            row.addView(remove, rl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
            if (codeList.getChildCount() > 0) lp.topMargin = t.dp(5);
            codeList.addView(row, lp);
        }
        if (focusedCode != null) {
            View again = codeList.findViewWithTag(focusedCode);
            if (again != null) again.requestFocus();
        }
    }

    private void say(String text, boolean problem) {
        message.setText(text);
        message.setTextColor(problem ? Theme.WARNING : Theme.SOFT_WHITE);
        message.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------------------------------------- actions

    private void openOrInstall() {
        Outcome o = OfficialDownloaderLauncher.openOrInstallDownloader(c);
        say(OfficialDownloaderLauncher.message(o, null), o == Outcome.STORE_UNAVAILABLE || o == Outcome.FAILED);
        if (o == Outcome.STORE_UNAVAILABLE) urlField.requestFocus();
    }

    private void sendCode(String code) {
        Outcome o = OfficialDownloaderLauncher.sendCode(c, code);
        say(OfficialDownloaderLauncher.message(o, code), o == Outcome.STORE_UNAVAILABLE || o == Outcome.FAILED
                || o == Outcome.INVALID_CODE);
    }

    private void saveCode() {
        try {
            store.addCode(codeField.getText().toString(), nameField.getText().toString());
            String saved = codeField.getText().toString().trim();
            codeField.setText("");
            nameField.setText("");
            dismissKeyboard();
            rebuildCodes();
            View row = codeList.findViewWithTag(saved);
            if (row != null) row.requestFocus();
            say("Saved code " + saved + ".", false);
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "That code could not be saved." : e.getMessage(), true);
            codeField.requestFocus();
        }
    }

    private void removeCode(String code) {
        store.removeCode(code);
        rebuildCodes();
        if (codeList.findFocus() == null) codeField.requestFocus();
        say("Removed code " + code + ".", false);
    }

    private void reviewApk() {
        try {
            String url = DownloadPolicy.validateUrl(urlField.getText().toString()).toASCIIString();
            String sha = DownloadPolicy.normalizeChecksum(shaField.getText().toString());
            store.setApkSource(url, sha);
            dismissKeyboard();
            say("", false);
            host.open(new SourceReviewPanel(host, url, sha, "Downloader by AFTVnews"));
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Check the link and checksum." : e.getMessage(), true);
            urlField.requestFocus();
        }
    }

    @Override public void onUncovered() { refresh(); }

    @Override public void onPackagesChanged() { if (status != null) refresh(); }
}
