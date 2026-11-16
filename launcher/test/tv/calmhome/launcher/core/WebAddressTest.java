package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import tv.calmhome.launcher.core.WebAddress.Result.Kind;

public class WebAddressTest {

    private static WebAddress.Result in(String s) {
        return WebAddress.fromInput(s, WebAddress.Engine.DUCKDUCKGO);
    }

    @Test public void httpsAddressNavigates() {
        WebAddress.Result r = in("https://example.org/path?q=1");
        assertEquals(Kind.NAVIGATE, r.kind);
        assertEquals("https://example.org/path?q=1", r.url);
    }

    @Test public void bareHostGetsHttpsNotHttp() {
        assertEquals("https://example.org", in("example.org").url);
        assertEquals("https://example.org/a/b", in("  example.org/a/b ").url);
        assertEquals("https://example.org:8443/x", in("example.org:8443/x").url);
    }

    @Test public void wordsAreSearched() {
        WebAddress.Result r = in("weather in lisbon");
        assertEquals(Kind.SEARCH, r.kind);
        assertEquals("https://duckduckgo.com/?q=weather+in+lisbon", r.url);
        assertEquals(Kind.SEARCH, in("single").kind);
        assertEquals("https://www.google.com/search?q=a%26b%3Dc",
                WebAddress.fromInput("a&b=c", WebAddress.Engine.GOOGLE).url);
    }

    @Test public void httpIsRefusedNeverRewritten() {
        assertEquals(Kind.BLOCKED_HTTP, in("http://example.org").kind);
        assertEquals(Kind.BLOCKED_HTTP, in("HTTP://EXAMPLE.ORG").kind);
        assertEquals(Kind.BLOCKED_HTTP, WebAddress.check("http://example.org/redirect-target").kind);
    }

    @Test public void otherSchemesAreRefused() {
        for (String s : new String[]{"file:///data/data/tv.calmhome.launcher/shared_prefs/zp_jarvis.xml",
                "content://settings/system", "javascript:alert(1)", "intent://x#Intent;component=a/b;end",
                "data:text/html,hi", "ftp://example.org", "chrome://settings", "about:config"}) {
            assertEquals(s, Kind.BLOCKED_SCHEME, in(s).kind);
        }
    }

    @Test public void localAndPrivateAddressesAreBlocked() {
        for (String h : new String[]{"https://localhost", "https://localhost:8080/", "https://127.0.0.1",
                "https://127.1.2.3", "https://10.0.0.1", "https://172.16.0.1", "https://172.31.255.255",
                "https://192.168.4.33:18800", "https://169.254.1.1", "https://100.64.0.1", "https://0.0.0.0",
                "https://[::1]/", "https://[fd00::1]", "https://[fe80::1]", "https://[::ffff:192.168.1.1]",
                "https://router.local", "https://nas.lan", "https://tv.home.arpa", "https://2130706433",
                "https://0x7f000001", "https://0177.0.0.1", "https://127.1", "https://printer.localhost"}) {
            assertEquals(h, Kind.BLOCKED_LOCAL, WebAddress.check(h).kind);
        }
        assertEquals(Kind.BLOCKED_LOCAL, in("192.168.4.28:5555").kind);
        assertEquals(Kind.BLOCKED_LOCAL, in("localhost").kind);
    }

    @Test public void publicAddressesAreNotMistakenForLocal() {
        for (String h : new String[]{"https://172.32.0.1", "https://8.8.8.8", "https://1.example.com",
                "https://local.example.com", "https://[2001:4860:4860::8888]", "https://192.169.0.1"}) {
            assertEquals(h, Kind.NAVIGATE, WebAddress.check(h).kind);
        }
    }

    @Test public void credentialsInUrlsAndJunkAreInvalid() {
        assertEquals(Kind.INVALID, WebAddress.check("https://user:pw@example.org").kind);
        assertEquals(Kind.INVALID, in("").kind);
        assertEquals(Kind.INVALID, in("   ").kind);
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 3000; i++) huge.append('a');
        assertEquals(Kind.INVALID, in(huge.toString()).kind);
        assertFalse(in(null).loads());
    }

    @Test public void resultLoadsOnlyForNavigateAndSearch() {
        assertTrue(in("example.org").loads());
        assertTrue(in("cats").loads());
        assertFalse(in("http://example.org").loads());
    }
}
