package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;

import tv.calmhome.launcher.core.HttpsFetch.Reason;

/** Redirect, size and error policy, driven by a fake server (no network). */
public class HttpsFetchTest {

    /** url -> {status, location or body}. */
    private final Map<String, Object[]> routes = new HashMap<>();
    private final List<String> opened = new ArrayList<>();

    private final HttpsFetch.Opener fake = url -> {
        opened.add(url.toString());
        final Object[] r = routes.get(url.toString());
        if (r == null) throw new java.net.UnknownHostException("no route");
        if (r[0] instanceof IOException) throw (IOException) r[0];
        return new HttpsFetch.Response() {
            @Override public int status() { return (Integer) r[0]; }
            @Override public String header(String name) { return "Location".equals(name) ? (String) r[1] : null; }
            @Override public InputStream body() { return new ByteArrayInputStream(((String) r[1]).getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() { }
        };
    };

    private Reason fails(String url, int max) {
        try {
            HttpsFetch.text(url, max, fake);
        } catch (HttpsFetch.FetchException e) {
            return e.reason;
        }
        fail("expected a failure");
        return null;
    }

    @Test public void fetchesHttpsAndFollowsHttpsRedirects() throws Exception {
        routes.put("https://a.example.org/list.m3u", new Object[]{302, "/moved.m3u"});
        routes.put("https://a.example.org/moved.m3u", new Object[]{301, "https://cdn.example.org/final.m3u"});
        routes.put("https://cdn.example.org/final.m3u", new Object[]{200, "#EXTM3U\n"});
        assertEquals("#EXTM3U\n", HttpsFetch.text("https://a.example.org/list.m3u", 1000, fake));
        assertEquals(3, opened.size());
    }

    @Test public void refusesHttpAndDowngradesWithoutRequestingThem() {
        assertEquals(Reason.HTTP_NOT_SUPPORTED, fails("http://a.example.org/list.m3u", 1000));
        assertEquals(0, opened.size());
        routes.put("https://a.example.org/list.m3u", new Object[]{302, "http://a.example.org/plain.m3u"});
        assertEquals(Reason.HTTPS_DOWNGRADE, fails("https://a.example.org/list.m3u", 1000));
        for (String u : opened) assertFalse(u, u.startsWith("http:"));
    }

    @Test public void stopsRedirectLoops() {
        routes.put("https://a.example.org/x", new Object[]{302, "https://a.example.org/x"});
        assertEquals(Reason.TOO_MANY_REDIRECTS, fails("https://a.example.org/x", 1000));
        assertEquals(HttpsFetch.MAX_REDIRECTS + 1, opened.size());
    }

    @Test public void mapsStatusesAndFailures() throws Exception {
        routes.put("https://a.example.org/401", new Object[]{401, ""});
        routes.put("https://a.example.org/403", new Object[]{403, ""});
        routes.put("https://a.example.org/404", new Object[]{404, ""});
        routes.put("https://a.example.org/500", new Object[]{500, ""});
        routes.put("https://a.example.org/tls", new Object[]{new SSLHandshakeException("bad cert")});
        assertEquals(Reason.ACCESS_DENIED, fails("https://a.example.org/401", 10));
        assertEquals(Reason.ACCESS_DENIED, fails("https://a.example.org/403", 10));
        assertEquals(Reason.NOT_FOUND, fails("https://a.example.org/404", 10));
        assertEquals(Reason.HTTP_STATUS, fails("https://a.example.org/500", 10));
        assertEquals(Reason.TLS, fails("https://a.example.org/tls", 10));
        assertEquals(Reason.NETWORK, fails("https://nowhere.example.org/", 10));
        assertEquals(Reason.INVALID_URL, fails("ftp://a.example.org/", 10));
        assertEquals(Reason.INVALID_URL, fails("https:///nohost", 10));
    }

    @Test public void enforcesTheSizeLimit() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append('x');
        routes.put("https://a.example.org/big", new Object[]{200, big.toString()});
        assertEquals(Reason.TOO_LARGE, fails("https://a.example.org/big", 4096));
    }

    @Test public void errorMessagesNeverContainTheUrl() {
        routes.put("https://a.example.org/list.m3u?token=SECRET", new Object[]{403, ""});
        try {
            HttpsFetch.text("https://a.example.org/list.m3u?token=SECRET", 10, fake);
            fail();
        } catch (HttpsFetch.FetchException e) {
            assertFalse(e.getMessage().contains("SECRET"));
            assertFalse(e.getMessage().contains("example.org"));
        }
    }

}
