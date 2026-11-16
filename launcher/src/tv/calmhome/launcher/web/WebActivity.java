package tv.calmhome.launcher.web;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks2;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import tv.calmhome.launcher.ModuleActivity;
import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.TabSet;
import tv.calmhome.launcher.core.WebAddress;
import tv.calmhome.launcher.data.ModuleData;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse Web: Android System WebView with native, remote-friendly controls. Runs in its own ":web" process
 * with its own WebView data folder, and that process ends when the browser closes, so browsing memory is
 * returned and nothing of the browser runs while you are on Home. The process boundary is about memory and
 * data separation only: it shares the app's identity, so the protection for the launcher's settings and app files
 * is that web pages get no bridge into the app at all (no JavaScript interface, no file or content access).
 *
 * Policy: HTTPS only (navigations, redirects and subresources); local-network and loopback addresses blocked by
 * URL; certificate errors always cancelled; camera, microphone, location and other site permissions denied.
 */
public class WebActivity extends ModuleActivity {

    private final TabSet tabs = new TabSet();
    private final WebAddress.Engine engine = WebAddress.Engine.DUCKDUCKGO;

    private WebView web;
    private EditText address;
    private TextView reloadStop;
    private LinearLayout tabStrip;
    private ProgressBar progress;
    private FrameLayout page;
    private View state;
    private boolean loading;

    @Override
    protected Features.Module module() {
        return Features.Module.WEB;
    }

    @Override
    protected void onModuleCreate(Bundle saved) {
        // Must run before this process creates any WebView: keeps the browser's cookies, cache and storage in
        // their own folder (app_webview_web), which "Delete Web data" removes.
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                WebView.setDataDirectorySuffix(ModuleData.WEB_DATA_SUFFIX);
            } catch (IllegalStateException alreadySet) {
                // Same process re-created the activity: the suffix is already in effect.
            }
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Theme.DEEP_BLACK);
        col.addView(toolbar(), new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setPadding(theme.dp(16), theme.dp(4), theme.dp(16), theme.dp(6));
        strip.addView(tabStrip);
        col.addView(strip, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.INVISIBLE);
        col.addView(progress, new LinearLayout.LayoutParams(Widgets.MATCH, theme.dp(3)));

        page = new FrameLayout(this);
        col.addView(page, new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f));
        setContentView(col);

        if (!createWebView()) {
            showState(Glyph.Kind.GLOBE, Theme.WARNING, "Web isn't available on this TV",
                    "This TV's Android System WebView is missing, turned off or not compatible, so ZeroPulse Web "
                            + "can't open pages. ZeroPulse doesn't replace or change system components; check for a "
                            + "WebView update in the TV's app store.");
            address.setEnabled(false);
            return;
        }
        tabs.open(null);
        renderTabs();
        showState(Glyph.Kind.GLOBE, Theme.HOLO_BLUE, "Start browsing",
                "Type an address or a search above (OK opens the keyboard). HTTPS sites only in this beta.\n\n"
                        + "Remote: Down moves into the page, Back goes back a page or returns here, Menu returns here.");
        address.requestFocus();
    }

    // ------------------------------------------------------------------ UI

    private View toolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(theme.dp(16), theme.dp(12), theme.dp(16), theme.dp(4));
        bar.addView(w.navItem("Back", false, v -> { if (web != null && web.canGoBack()) web.goBack(); }));
        bar.addView(w.navItem("Forward", false, v -> { if (web != null && web.canGoForward()) web.goForward(); }));
        reloadStop = w.navItem("Reload", false, v -> {
            if (web == null) return;
            if (loading) web.stopLoading();
            else if (tabs.active() != null && tabs.active().url != null) web.reload();
        });
        bar.addView(reloadStop);

        address = new EditText(this);
        theme.style(address, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        address.setHint("Search or type an HTTPS address");
        address.setHintTextColor(Theme.MUTED_GRAY);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        address.setPadding(theme.dp(16), theme.dp(9), theme.dp(16), theme.dp(9));
        address.setBackground(theme.outlined(Theme.PANEL_NAVY, Theme.BORDER, 1, Theme.R_PILL));
        address.setOnFocusChangeListener((v, has) -> v.setBackground(has
                ? theme.outlined(Theme.PANEL_NAVY, Theme.ELECTRIC_BLUE, 2, Theme.R_PILL)
                : theme.outlined(Theme.PANEL_NAVY, Theme.BORDER, 1, Theme.R_PILL)));
        address.setOnEditorActionListener((v, actionId, e) -> {
            boolean enter = e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                go(address.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        al.leftMargin = theme.dp(10);
        al.rightMargin = theme.dp(10);
        bar.addView(address, al);

        bar.addView(w.navItem("New tab", false, v -> newTab()));
        bar.addView(w.navItem("Close tab", false, v -> closeTab()));
        bar.addView(w.navItem("Exit", false, v -> finish()));
        return bar;
    }

    private void renderTabs() {
        tabStrip.removeAllViews();
        for (final TabSet.Tab t : tabs.all()) {
            boolean active = t == tabs.active();
            String label = t.title != null && !t.title.isEmpty() ? t.title : (t.url != null ? host(t.url) : "New tab");
            if (label.length() > 28) label = label.substring(0, 27) + "…";
            TextView chip = w.navItem(label, active, v -> selectTab(t));
            chip.setContentDescription((active ? "Current tab: " : "Tab: ") + label);
            tabStrip.addView(chip);
        }
    }

    private void showState(Glyph.Kind icon, int color, String title, String body) {
        hideState();
        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(Theme.DEEP_BLACK);
        View card = w.stateCard(icon, color, title, body);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(theme.dp(620), Widgets.WRAP, Gravity.CENTER);
        holder.addView(card, cl);
        state = holder;
        page.addView(holder, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
    }

    private void hideState() {
        if (state != null) page.removeView(state);
        state = null;
    }

    // ------------------------------------------------------------------ navigation

    private void go(String typed) {
        WebAddress.Result r = WebAddress.fromInput(typed, engine);
        if (!r.loads()) {
            explain(r.kind, typed);
            return;
        }
        load(r.url);
    }

    private void load(String url) {
        if (web == null) return;
        hideState();
        TabSet.Tab t = tabs.active();
        if (t != null) {
            t.url = url;
            t.title = null;
        }
        address.setText(url);
        web.loadUrl(url);
        web.requestFocus();
    }

    private void explain(WebAddress.Result.Kind kind, String what) {
        switch (kind) {
            case BLOCKED_HTTP:
                showState(Glyph.Kind.INFO, Theme.WARNING, "HTTP pages aren't supported",
                        "This beta opens HTTPS pages only, so this page wasn't loaded. ZeroPulse doesn't fall back to "
                                + "an unencrypted connection or quietly change the address.");
                break;
            case BLOCKED_LOCAL:
                showState(Glyph.Kind.INFO, Theme.WARNING, "Local addresses are blocked",
                        "ZeroPulse Web doesn't open this TV, your router or other devices on your home network.");
                break;
            case BLOCKED_SCHEME:
                showState(Glyph.Kind.INFO, Theme.WARNING, "This link isn't a web page",
                        "Links that open other apps or files aren't supported in this development build.");
                break;
            default:
                showState(Glyph.Kind.INFO, Theme.WARNING, "That isn't a valid address", "Check the address and try again.");
                break;
        }
    }

    private void newTab() {
        if (web == null) return;
        if (tabs.open(null) == null) {
            Toast.makeText(this, "You can have up to " + TabSet.MAX_TABS + " tabs open", Toast.LENGTH_SHORT).show();
            return;
        }
        web.stopLoading();
        web.loadUrl("about:blank");
        address.setText("");
        renderTabs();
        showState(Glyph.Kind.GLOBE, Theme.HOLO_BLUE, "New tab", "Type an address or a search above.");
        address.requestFocus();
    }

    private void closeTab() {
        TabSet.Tab t = tabs.active();
        if (t == null) return;
        tabs.close(t.id);
        if (tabs.size() == 0) {
            finish();
            return;
        }
        selectTab(tabs.active());
    }

    /** Only the active tab has a live page: switching reloads that tab's address in the one WebView. */
    private void selectTab(TabSet.Tab t) {
        if (web == null || t == null) return;
        tabs.select(t.id);
        renderTabs();
        web.stopLoading();
        if (t.url == null) {
            web.loadUrl("about:blank");
            address.setText("");
            showState(Glyph.Kind.GLOBE, Theme.HOLO_BLUE, "New tab", "Type an address or a search above.");
            address.requestFocus();
        } else {
            WebAddress.Result r = WebAddress.check(t.url);
            if (r.loads()) load(t.url);
            else explain(r.kind, t.url);
        }
    }

    private static String host(String url) {
        try {
            String h = new java.net.URI(url).getHost();
            return h != null ? h : url;
        } catch (java.net.URISyntaxException e) {
            return url;
        }
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private boolean createWebView() {
        PackageInfo provider = WebView.getCurrentWebViewPackage();
        if (provider == null) return false;
        try {
            web = new WebView(this);
        } catch (RuntimeException missingOrBroken) {
            web = null;
            return false;
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);                      // most sites need it; no bridge is ever exposed
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);                 // target=_blank opens in this tab
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSaveFormData(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setBackgroundColor(Theme.DEEP_BLACK);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, disposition, mime, length) -> Toast.makeText(this,
                "Downloads aren't available in this development build yet", Toast.LENGTH_LONG).show());
        page.addView(web, 0, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        return true;
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            // Links and redirects (including HTTPS → HTTP downgrades) pass through here.
            if (!req.isForMainFrame()) return false;
            WebAddress.Result r = WebAddress.check(req.getUrl().toString());
            if (r.loads()) return false;
            view.stopLoading();
            explain(r.kind, req.getUrl().toString());
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            String scheme = req.getUrl().getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return null;
            // Fail closed: anything that isn't a plain public HTTPS URL is refused (HTTP, local, unparseable).
            if (!WebAddress.check(req.getUrl().toString()).loads()) {
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked by ZeroPulse Web",
                        Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            setLoading(true);
            if (url != null && !url.equals("about:blank")) {
                TabSet.Tab t = tabs.active();
                if (t != null) t.url = url;
                if (!address.hasFocus()) address.setText(url);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            setLoading(false);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();                                   // never proceed past a certificate error
            showState(Glyph.Kind.INFO, Theme.ERROR, "The site's certificate wasn't accepted",
                    "The connection to " + host(error.getUrl()) + " isn't secure (certificate problem "
                            + error.getPrimaryError() + "). ZeroPulse never continues past certificate errors.");
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
            if (!req.isForMainFrame()) return;
            setLoading(false);
            showState(Glyph.Kind.INFO, Theme.ERROR, "This page couldn't be loaded",
                    host(req.getUrl().toString()) + ": " + error.getDescription()
                            + "\n\nCheck the TV's internet connection and the address, then press Reload.");
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The page's renderer crashed or was stopped for memory. Drop this WebView (it can't be reused) and
            // keep the browser open with an explanation instead of crashing.
            page.removeView(view);
            view.destroy();
            web = null;
            showState(Glyph.Kind.INFO, Theme.ERROR, "The page stopped working",
                    "The web page " + (detail.didCrash() ? "crashed" : "was closed to free memory")
                            + ". Close this browser and open it again to continue.");
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            progress.setProgress(p);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            TabSet.Tab t = tabs.active();
            if (t != null) {
                t.title = title;
                renderTabs();
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();                                     // camera, microphone, MIDI, protected media
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> files, FileChooserParams params) {
            Toast.makeText(WebActivity.this, "File uploads aren't available in this development build yet",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void setLoading(boolean on) {
        loading = on;
        progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        reloadStop.setText(on ? "Stop" : "Reload");
    }

    // ------------------------------------------------------------------ keys and lifecycle

    /** Back in the page: previous page, else up to the address bar. Back in the browser controls: leave. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        boolean inPage = web != null && f != null && isInside(f, page);
        if (inPage && web.canGoBack()) web.goBack();
        else if (inPage) address.requestFocus();
        else super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            address.requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static boolean isInside(View v, ViewGroup g) {
        for (View f = v; f != null; f = f.getParent() instanceof View ? (View) f.getParent() : null) if (f == g) return true;
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (web != null && level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) web.pauseTimers();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (web != null) web.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            page.removeView(web);
            web.destroy();
            web = null;
        }
        boolean leaving = isFinishing();
        super.onDestroy();
        // Leaving the browser ends its process: every tab and all WebView memory are released, and the next
        // start reads the feature switch fresh.
        if (leaving) Process.killProcess(Process.myPid());
    }
}
