package tv.calmhome.launcher.downloads;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Rules shared by direct links, owner shortcuts and every redirect hop. */
public final class DownloadPolicy {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final long RESERVE_BYTES = 16L * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;

    public static URI validateUrl(String value) {
        try {
            if (value == null || value.length() > 8192) throw new URISyntaxException("", "");
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getRawUserInfo() != null || uri.getRawFragment() != null ||
                    uri.getPort() > 65535 || uri.getPort() == 0 || uri.getRawAuthority().endsWith(":") ||
                    uri.toASCIIString().matches("(?i).*%(?:0[0-9a-f]|1[0-9a-f]|7f|5c).*")) {
                throw new URISyntaxException("", "");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Enter an HTTPS link without a username, password or fragment.");
        }
    }

    static URI redirect(URI uri, String location, int hops) throws IOException {
        if (hops >= MAX_REDIRECTS || location == null || location.trim().isEmpty())
            throw new IOException("Invalid redirect or too many redirects.");
        try {
            return validateUrl(uri.resolve(location).toASCIIString());
        } catch (IllegalArgumentException e) {
            throw new IOException("The source redirected to an unsafe destination.");
        }
    }

    public static String normalizeChecksum(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String hex = value.trim().toLowerCase(Locale.ROOT);
        if (!hex.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 must have 64 hexadecimal digits.");
        return hex;
    }

    static String fileName(URI uri) {
        String path = uri.getPath();
        if (path == null) return "download.apk";
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isEmpty() || name.startsWith(".") ? "download.apk" : name.substring(0, Math.min(100, name.length()));
    }

    static void validateSize(long bytes, long free) throws IOException {
        if (bytes > MAX_BYTES) throw new IOException("APK exceeds the 512 MiB limit.");
        if (free < RESERVE_BYTES || bytes >= 0 && bytes > free - RESERVE_BYTES)
            throw new IOException("Not enough free storage. Remove downloads and retry.");
    }

    static void checkArchive(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("AndroidManifest.xml") == null) throw new IOException("Missing APK manifest.");
        } catch (java.util.zip.ZipException e) {
            throw new IOException("The downloaded file is not an APK archive.");
        }
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(2 * bytes.length);
        for (byte value : bytes) out.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return out.toString();
    }

    static void checkDigest(String expected, String actual) throws IOException {
        if (!expected.isEmpty() && !expected.equals(actual))
            throw new IOException("SHA-256 differs from the supplied publisher checksum. Installation blocked.");
    }

    private DownloadPolicy() {}
}
