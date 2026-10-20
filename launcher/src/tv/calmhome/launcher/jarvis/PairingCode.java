package tv.calmhome.launcher.jarvis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The 16-character code shown by `openpulse tv invite` on the PC:  SSSS-SSSS-CCCC-CCCC.
 * The first 8 characters are the one-time secret that redeems the invitation; the last 8 are
 * HMAC-SHA256(secret, "zeropulse-tv-pin|v1|" + certificate SHA-256) cut to 40 bits. The TV checks the
 * certificate it receives during the TLS handshake against the check part and refuses it — before
 * sending anything — if they differ. Mirrors packages/gateway/src/tvlink/pairing-code.ts in OpenPulse;
 * both are tested against the same vector.
 */
public final class PairingCode {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public final String secret;
    public final String check;

    private PairingCode(String secret, String check) {
        this.secret = secret;
        this.check = check;
    }

    /** Parses what the viewer typed; null unless it is exactly 16 code characters. */
    public static PairingCode parse(String typed) {
        String n = normalise(typed);
        if (n.length() != 16) return null;
        for (int i = 0; i < n.length(); i++) if (ALPHABET.indexOf(n.charAt(i)) < 0) return null;
        return new PairingCode(n.substring(0, 8), n.substring(8));
    }

    /** Uppercase, no spaces or dashes, look-alikes mapped (O→0, I/L→1). */
    public static String normalise(String typed) {
        if (typed == null) return "";
        return typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "")
                .replace('O', '0').replace('I', '1').replace('L', '1');
    }

    /** True if the certificate (by SHA-256 hex) is the one the PC showed this code for. */
    public boolean matches(String fingerprintHex) {
        return MessageDigest.isEqual(check.getBytes(StandardCharsets.US_ASCII),
                checkFor(secret, fingerprintHex).getBytes(StandardCharsets.US_ASCII));
    }

    public static String checkFor(String secret, String fingerprintHex) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(normalise(secret).getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            byte[] d = mac.doFinal(("zeropulse-tv-pin|v1|" + fingerprintHex.toLowerCase(Locale.ROOT))
                    .getBytes(StandardCharsets.US_ASCII));
            return encode40(d);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** First 40 bits of the bytes as 8 Crockford base32 characters. */
    static String encode40(byte[] b) {
        long v = 0;
        for (int i = 0; i < 5; i++) v = (v << 8) | (b[i] & 0xff);
        StringBuilder sb = new StringBuilder(8);
        for (int i = 7; i >= 0; i--) sb.append(ALPHABET.charAt((int) ((v >> (i * 5)) & 31)));
        return sb.toString();
    }

    /** The short code the TV shows for itself; `openpulse tv invite` shows the same one before approving. */
    public static String tvCode(String deviceId) {
        String s = deviceId.substring(0, 8).toUpperCase(Locale.ROOT);
        return s.substring(0, 4) + "-" + s.substring(4);
    }
}
