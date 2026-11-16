package tv.calmhome.launcher.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.http.SslError;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowWebView;
import org.robolectric.util.ReflectionHelpers;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.Prefs;

/**
 * Browser behaviour that can be checked without a real WebView engine (Robolectric's WebView records calls but
 * renders nothing). Real page loading, rendering and remote interaction need a device: not covered here.
 */
@RunWith(RobolectricTestRunner.class)
public class WebActivityTest {

    private final Context c = RuntimeEnvironment.getApplication();

    @Before public void webOnWithProvider() {
        new Prefs(c).setEnabled(Features.Module.WEB, true);
        PackageInfo provider = new PackageInfo();
        provider.packageName = "com.google.android.webview";
        provider.versionName = "test";
        ShadowWebView.setCurrentWebViewPackage(provider);
    }

    private static <T extends View> T first(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T t = first(g.getChildAt(i), type);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    private static void type(WebActivity a, String text) {
        EditText address = first(a.getWindow().getDecorView(), EditText.class);
        address.setText(text);
        address.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
    }

    @Test public void startsWithoutLoadingAnything_andHardenedSettings() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        WebView web = first(a.getWindow().getDecorView(), WebView.class);
        ShadowWebView sw = shadowOf(web);
        assertNull("no network request until the user asks", sw.getLastLoadedUrl());
        WebSettings s = web.getSettings();
        assertFalse(s.getAllowFileAccess());
        assertFalse(s.getAllowContentAccess());
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.getMixedContentMode());
        assertFalse(s.getJavaScriptCanOpenWindowsAutomatically());
        assertTrue(s.getMediaPlaybackRequiresUserGesture());
        assertTrue(allText(a.getWindow().getDecorView()).contains("Start browsing"));
    }

    @Test public void noJavascriptBridgeIsExposed() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        ShadowWebView sw = shadowOf(first(a.getWindow().getDecorView(), WebView.class));
        for (String name : new String[]{"jarvis", "JARVIS", "android", "Android", "zeropulse", "ZeroPulse", "native", "bridge", "app"}) {
            assertNull(name, sw.getJavascriptInterface(name));
        }
    }

    @Test public void typedAddressLoadsHttpsAndSearchUsesEngine() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        ShadowWebView sw = shadowOf(first(a.getWindow().getDecorView(), WebView.class));
        type(a, "example.org");
        assertEquals("https://example.org", sw.getLastLoadedUrl());
        type(a, "remote friendly browser");
        assertEquals("https://duckduckgo.com/?q=remote+friendly+browser", sw.getLastLoadedUrl());
    }

    @Test public void httpAndLocalAddressesAreRefusedWithAnExplanation() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        ShadowWebView sw = shadowOf(first(a.getWindow().getDecorView(), WebView.class));
        type(a, "http://example.org");
        assertNull(sw.getLastLoadedUrl());
        assertTrue(allText(a.getWindow().getDecorView()).contains("HTTP pages aren't supported"));
        type(a, "192.168.4.33:18800");
        assertNull(sw.getLastLoadedUrl());
        assertTrue(allText(a.getWindow().getDecorView()).contains("Local addresses are blocked"));
    }

    @SuppressWarnings("deprecation")
    @Test public void certificateErrorsAreAlwaysCancelled() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        WebView web = first(a.getWindow().getDecorView(), WebView.class);
        SslErrorHandler handler = ReflectionHelpers.callConstructor(SslErrorHandler.class);
        android.net.http.SslCertificate cert = new android.net.http.SslCertificate("bad.example", "Untrusted CA", "", "");
        SslError error = new SslError(SslError.SSL_UNTRUSTED, cert, "https://bad.example");
        shadowOf(web).getWebViewClient().onReceivedSslError(web, handler, error);
        assertTrue(shadowOf(handler).wasCancelCalled());
        assertFalse(shadowOf(handler).wasProceedCalled());
        assertTrue(allText(a.getWindow().getDecorView()).contains("certificate wasn't accepted"));
    }

    @Test public void httpsToHttpRedirectAndLocalSubresourcesAreBlocked() {
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        WebView web = first(a.getWindow().getDecorView(), WebView.class);
        android.webkit.WebViewClient client = shadowOf(web).getWebViewClient();
        assertTrue(client.shouldOverrideUrlLoading(web, request("http://example.org/after-redirect", true)));
        assertTrue(client.shouldOverrideUrlLoading(web, request("https://10.0.0.1/admin", true)));
        assertFalse(client.shouldOverrideUrlLoading(web, request("https://example.org/next", true)));
        assertEquals(403, client.shouldInterceptRequest(web, request("http://cdn.example.org/a.js", false)).getStatusCode());
        assertEquals(403, client.shouldInterceptRequest(web, request("https://127.0.0.1:18795/", false)).getStatusCode());
        assertEquals(403, client.shouldInterceptRequest(web, request("https://127.1/", false)).getStatusCode());
        assertEquals(403, client.shouldInterceptRequest(web, request("https://2130706433/", false)).getStatusCode());
        assertNull(client.shouldInterceptRequest(web, request("https://cdn.example.org/a.js", false)));
    }

    @Test public void missingWebViewProviderShowsAnHonestState() {
        ShadowWebView.setCurrentWebViewPackage(null);
        WebActivity a = Robolectric.buildActivity(WebActivity.class).setup().get();
        assertNull(first(a.getWindow().getDecorView(), WebView.class));
        assertTrue(allText(a.getWindow().getDecorView()).contains("Web isn't available on this TV"));
    }

    private static android.webkit.WebResourceRequest request(final String url, final boolean mainFrame) {
        return new android.webkit.WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return android.net.Uri.parse(url); }
            @Override public boolean isForMainFrame() { return mainFrame; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        };
    }

}
