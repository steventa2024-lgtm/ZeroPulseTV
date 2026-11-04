package tv.calmhome.launcher.downloads;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static tv.calmhome.launcher.downloads.DownloadRecord.State.*;

public final class DownloadTests {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Download check " + (checks + 1));
        checks++;
    }
    private static void reject(Action action) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException | IOException expected) { checks++; return; }
        throw new AssertionError("Expected refusal at check " + (checks + 1));
    }
    public static void main(String[] args) throws Exception {
        URI origin = DownloadPolicy.validateUrl("https://example.test/releases/start?ticket=abc");
        check(origin.getHost().equals("example.test"));
        for (String value : new String[]{null, "", "1234", "http://example.test/a", "file:///a.apk", "content://p",
                "intent://example.test/#Intent", "https:///a", "https://user:pass@example.test/app",
                "https://example.test/a#fragment", "https://example.test:65536/a", "https://example.test:0/a",
                "https://example.test:/", "https://example.test/%0d%0a", "https://example.test/%5cfoo"})
            reject(() -> DownloadPolicy.validateUrl(value));
        check(DownloadPolicy.redirect(origin, "../safe.apk", 0).toString().equals("https://example.test/safe.apk"));
        check(DownloadPolicy.redirect(origin, "//cdn.example.test/app.apk", 3).getHost().equals("cdn.example.test"));
        reject(() -> DownloadPolicy.redirect(origin, "http://other.test/sideload", 0));
        reject(() -> DownloadPolicy.redirect(origin, "file:///tmp/app.apk", 0));
        reject(() -> DownloadPolicy.redirect(origin, "//user@cdn.example.test/app.apk", 0));
        reject(() -> DownloadPolicy.redirect(origin, null, 0));
        reject(() -> DownloadPolicy.redirect(origin, "", 0));
        reject(() -> DownloadPolicy.redirect(origin, "/loop", DownloadPolicy.MAX_REDIRECTS));
        check(DownloadPolicy.fileName(origin).equals("start"));
        check(DownloadPolicy.fileName(URI.create("https://example.test/files/%2e%2e")).equals("download.apk"));
        check(DownloadPolicy.fileName(URI.create("https://example.test/files/a%20b.apk")).equals("a_b.apk"));

        String digest = DownloadPolicy.hex(DownloadPolicy.digest().digest("abc".getBytes(StandardCharsets.UTF_8)));
        check(digest.equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        check(DownloadPolicy.normalizeChecksum(digest.toUpperCase(java.util.Locale.ROOT)).equals(digest));
        check(DownloadPolicy.normalizeChecksum(null).isEmpty());
        reject(() -> DownloadPolicy.normalizeChecksum("1122"));
        DownloadPolicy.checkDigest("", digest);
        DownloadPolicy.checkDigest(digest, digest);
        reject(() -> DownloadPolicy.checkDigest(digest, digest.substring(1)));
        DownloadPolicy.validateSize(-1, DownloadPolicy.RESERVE_BYTES);
        reject(() -> DownloadPolicy.validateSize(DownloadPolicy.MAX_BYTES + 1, Long.MAX_VALUE));
        reject(() -> DownloadPolicy.validateSize(1024, DownloadPolicy.RESERVE_BYTES));
        reject(() -> DownloadPolicy.validateSize(-1, DownloadPolicy.RESERVE_BYTES - 1));

        File local = File.createTempFile("zeropulse-download", ".apk");
        try {
            Files.write(local.toPath(), "<html>Sign in</html>".getBytes(StandardCharsets.UTF_8));
            reject(() -> DownloadPolicy.checkArchive(local));
            zip(local, "README");
            reject(() -> DownloadPolicy.checkArchive(local));
            zip(local, "AndroidManifest.xml");
            DownloadPolicy.checkArchive(local); // Android must still parse and validate the signed APK.
        } finally { Files.deleteIfExists(local.toPath()); }

        DownloadRecord r = new DownloadRecord();
        check("APK download".equals(r.displayName()));
        r.fileName = "developer.apk"; check("developer.apk".equals(r.displayName()));
        r.appName = "Parsed APK"; check("Parsed APK".equals(r.displayName()));
        r.total = -1; r.bytes = 1024; check(r.percentTransferred() == -1);
        r.total = 0; check(r.percentTransferred() == -1);
        r.total = 4096; check(r.percentTransferred() == 25);
        r.bytes = 0; check(r.percentTransferred() == 0);
        r.bytes = 4095; check(r.percentTransferred() == 99);
        r.bytes = 4096; check(r.percentTransferred() == 100);
        r.bytes = Long.MAX_VALUE; check(r.percentTransferred() == 100);
        r.attempt = 2;
        for (DownloadRecord.State s : DownloadRecord.State.values()) {
            r.state = s;
            check(r.inActiveQueue() == (s == QUEUED || s == DOWNLOADING || s == VERIFYING || s == AWAITING_APPROVAL));
            check(r.acceptsAttempt(2) == (s == QUEUED || s == DOWNLOADING || s == VERIFYING));
            check(!r.acceptsAttempt(1));
            r.sessionId = 10;
            check(r.acceptsInstallResult(10) == (s == AWAITING_APPROVAL));
            check(!r.acceptsInstallResult(9));
            check(!r.acceptsInstallResult(-1));
        }
        check(DownloadRecord.installOutcome(true, false, true) == INSTALLED);
        check(DownloadRecord.installOutcome(true, false, false) == FAILED);
        check(DownloadRecord.installOutcome(false, true, true) == CANCELED);
        check(DownloadRecord.installOutcome(false, false, true) == FAILED);
        System.out.println("Downloader: " + checks + " checks passed.");
    }
    private static void zip(File file, String entry) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            zip.putNextEntry(new ZipEntry(entry)); zip.write(1); zip.closeEntry();
        }
    }
}
