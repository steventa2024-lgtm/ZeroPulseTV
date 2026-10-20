package tv.calmhome.launcher.jarvis;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.X509TrustManager;

/**
 * Trust decision for the TV link, made inside the TLS handshake (before any request is sent).
 * There is no CA and no fallback: a server certificate is accepted only if
 *  - pinned mode: its SHA-256 equals the fingerprint saved when the TV was paired, or
 *  - pairing mode: it matches the check half of the code typed from the PC screen (see PairingCode).
 * Anything else throws, and the connection never carries data.
 */
public final class PinnedTrust implements X509TrustManager {

    /** The server presented a different certificate than the pinned one. */
    public static final class Mismatch extends CertificateException {
        private static final long serialVersionUID = 1L;
        public final String observed;
        Mismatch(String message, String observed) {
            super(message);
            this.observed = observed;
        }
    }

    private final String pinned;
    private final PairingCode code;
    private volatile String observed;

    private PinnedTrust(String pinned, PairingCode code) {
        this.pinned = pinned;
        this.code = code;
    }

    public static PinnedTrust pinned(String fingerprintHex) {
        if (fingerprintHex == null || !fingerprintHex.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("pinned fingerprint must be 64 lowercase hex characters");
        return new PinnedTrust(fingerprintHex, null);
    }

    public static PinnedTrust pairing(PairingCode code) {
        if (code == null) throw new IllegalArgumentException("pairing code required");
        return new PinnedTrust(null, code);
    }

    /** SHA-256 of the certificate the server presented (set once the handshake checked it). */
    public String observed() {
        return observed;
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) throw new CertificateException("no server certificate");
        String fp = sha256Hex(chain[0].getEncoded());
        if (pinned != null) {
            if (!MessageDigest.isEqual(pinned.getBytes(), fp.getBytes()))
                throw new Mismatch("PC certificate does not match the paired one", fp);
        } else if (!code.matches(fp)) {
            throw new Mismatch("PC certificate does not match the pairing code", fp);
        }
        observed = fp;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("client certificates are not used");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
