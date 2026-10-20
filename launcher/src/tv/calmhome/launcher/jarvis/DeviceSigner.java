package tv.calmhome.launcher.jarvis;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * The TV's P-256 device identity. On the TV the private key lives in Android Keystore and cannot be
 * exported (see assistant/KeystoreSigner); tests use a software key.
 */
public interface DeviceSigner {

    /** X.509 SubjectPublicKeyInfo DER of the public key. */
    byte[] publicKeySpki();

    /** ECDSA P-256 / SHA-256 signature in DER form (what java.security.Signature returns). */
    byte[] signDer(byte[] data) throws GeneralSecurityException;

    /** Device id as the gateway computes it: first 32 hex characters of SHA-256(SPKI). */
    static String deviceId(byte[] spki) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(spki);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i] & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** DER ECDSA signature to IEEE P1363 (r then s, 32 bytes each), the form the gateway verifies. */
    static byte[] derToP1363(byte[] der) {
        int i = 0;
        if (der[i++] != 0x30) throw new IllegalArgumentException("not a DER sequence");
        if ((der[i++] & 0xff) == 0x81) i++;
        byte[] out = new byte[64];
        for (int part = 0; part < 2; part++) {
            if (der[i++] != 0x02) throw new IllegalArgumentException("not a DER integer");
            int n = der[i++] & 0xff;
            int start = i, count = n;
            while (count > 32 && der[start] == 0) { start++; count--; }
            if (count > 32) throw new IllegalArgumentException("integer too large");
            System.arraycopy(der, start, out, part * 32 + (32 - count), count);
            i += n;
        }
        return out;
    }
}
