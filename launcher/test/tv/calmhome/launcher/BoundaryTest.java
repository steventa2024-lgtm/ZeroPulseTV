package tv.calmhome.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Static checks on the source tree (plain JVM, no Android): module code stays in its packages, Home and the
 * launcher code never touch WebView or the player, no page gets a bridge, and the manifest exports nothing new.
 */
public class BoundaryTest {

    private static final Path SRC = Paths.get("src", "tv", "calmhome", "launcher");

    private static List<Path> java() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Source without comments and string literals (folder names, explanations), so only real use counts. */
    private static String code(Path p) throws IOException {
        // Match literals and comments together so URLs inside Java strings cannot begin a comment.
        return read(p).replaceAll("\"(?:\\\\.|[^\"\\\\\\r\\n]++)*+\"|'(?:\\\\.|[^'\\\\\\r\\n]++)*+'|//[^\\r\\n]*|/\\*(?s:.*?)\\*/", "\"\"");
    }

    private static String pkg(Path p) {
        return SRC.relativize(p).toString().replace('\\', '/');
    }

    @Test public void webViewOnlyInsideTheWebModule() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : java()) {
            String s = code(p);
            if ((s.contains("android.webkit") || s.contains("WebView")) && !pkg(p).startsWith("web/")) offenders.add(pkg(p));
        }
        assertEquals("WebView used outside web/", new ArrayList<String>(), offenders);
    }

    @Test public void playerOnlyInsideTheStreamingModule() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : java()) if (code(p).contains("androidx.media3") && !pkg(p).startsWith("streaming/")) offenders.add(pkg(p));
        assertEquals(new ArrayList<String>(), offenders);
    }

    @Test public void noJavascriptBridgeAnywhere() throws IOException {
        for (Path p : java()) assertFalse(pkg(p), read(p).contains("addJavascriptInterface"));
    }

    @Test public void certificateErrorsNeverProceed() throws IOException {
        for (Path p : java()) {
            String s = read(p);
            assertFalse(pkg(p), s.contains(".proceed()"));
            assertFalse(pkg(p), s.contains("X509TrustManager"));       // the only custom trust (JARVIS's pinned link) went with the Assistant
        }
    }

    /** 3.0.0-beta.7 removed the Assistant (JARVIS) completely: its packages, activity, AI accounts and voice are gone, not hidden. */
    @Test public void theAssistantIsGoneNotHidden() throws IOException {
        for (Path p : java()) {
            String k = pkg(p);
            assertFalse(k, k.startsWith("assistant/") || k.startsWith("jarvis/") || k.startsWith("ai/") || k.startsWith("auth/"));
            String s = code(p);
            for (String gone : new String[]{"AssistantActivity", "JarvisSession", "openAssistant", "assistantEnabled", "AiAccountsDialog",
                    "CredentialManager", "CodexSubscriptionAuth", "RecognizerIntent", "TextToSpeech", "SpeechRecognizer"}) {
                assertFalse(k + " still mentions " + gone, s.contains(gone));
            }
        }
        String manifest = new String(Files.readAllBytes(Paths.get("AndroidManifest.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(manifest.contains("AssistantActivity"));
        assertFalse(manifest.contains("RECORD_AUDIO"));
        assertFalse(manifest.contains("RECOGNIZE_SPEECH"));
        assertFalse(manifest.contains("TTS_SERVICE"));
        String gradle = new String(Files.readAllBytes(Paths.get("build.gradle")), java.nio.charset.StandardCharsets.UTF_8);
        for (String dep : new String[]{"com.google.zxing", "org.nanohttpd", "androidx.security", "play-services-auth"}) {
            assertFalse(dep, gradle.contains("implementation '" + dep));
        }
    }

    @Test public void noLoggingInModuleCode() throws IOException {
        Pattern log = Pattern.compile("\\bLog\\.[vdiwe]\\(|System\\.(out|err)\\.print|printStackTrace\\(");
        for (Path p : java()) {
            String k = pkg(p);
            if (k.startsWith("web/") || k.startsWith("streaming/") || k.startsWith("core/") || k.startsWith("data/")) {
                assertFalse(k, log.matcher(read(p)).find());
            }
        }
    }

    @Test public void manifestKeepsHomeAndExportsNothingNew() throws IOException {
        String m = read(Paths.get("AndroidManifest.xml"));
        assertTrue(m.contains("android:name=\".MainActivity\""));
        assertTrue(m.contains("android.intent.category.HOME"));
        assertFalse("no package attribute: the ID lives in build.gradle", m.contains("package="));
        for (String forbidden : new String[]{"<provider", "usesCleartextTraffic=\"true\"",
                "networkSecurityConfig", "android:allowBackup=\"true\"", "QUERY_ALL_PACKAGES", "READ_EXTERNAL_STORAGE",
                "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION",
                "SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE", "BIND_ACCESSIBILITY_SERVICE"}) {
            assertFalse(forbidden, m.contains(forbidden));
        }
        assertTrue(m.contains("android:usesCleartextTraffic=\"false\""));
        assertTrue(m.contains("android:permission=\"android.permission.BIND_JOB_SERVICE\""));
        Matcher r = Pattern.compile("<receiver\\b([^>]*)>", Pattern.DOTALL).matcher(m);
        while (r.find()) assertTrue(r.group(1).contains("android:exported=\"false\""));
        // Every activity except Home is internal.
        Matcher a = Pattern.compile("<activity([^>]*)>|<activity([^>]*)/>", Pattern.DOTALL).matcher(m);
        TreeSet<String> exported = new TreeSet<>();
        while (a.find()) {
            String attrs = a.group(1) != null ? a.group(1) : a.group(2);
            if (attrs.contains("android:exported=\"true\"")) exported.add(attrs.replaceAll("(?s).*android:name=\"([^\"]+)\".*", "$1"));
            assertTrue("exported must be explicit", attrs.contains("android:exported="));
        }
        assertEquals("[.MainActivity]", exported.toString());
        Matcher perm = Pattern.compile("<uses-permission android:name=\"([^\"]+)\"").matcher(m);
        TreeSet<String> perms = new TreeSet<>();
        while (perm.find()) perms.add(perm.group(1));
        assertEquals("[android.permission.ACCESS_NETWORK_STATE, android.permission.INTERNET, android.permission.RECEIVE_BOOT_COMPLETED, android.permission.REQUEST_INSTALL_PACKAGES]", perms.toString());
    }

    @Test public void homeComponentIsUnchanged() throws IOException {
        assertTrue(Files.exists(SRC.resolve("MainActivity.java")));
        String gradle = read(Paths.get("build.gradle"));
        assertTrue(gradle.contains("applicationId = 'tv.calmhome.launcher'"));
        assertTrue(gradle.contains("namespace = 'tv.calmhome.launcher'"));
        Matcher v = Pattern.compile("versionCode = (\\d+)").matcher(gradle);
        assertTrue(v.find());
        assertTrue("must stay above every released versionCode (1, 20, 21)", Integer.parseInt(v.group(1)) > 21);
        assertTrue(gradle.contains("minSdk = 26"));
        assertTrue(gradle.contains("targetSdk = 34"));
    }
}
