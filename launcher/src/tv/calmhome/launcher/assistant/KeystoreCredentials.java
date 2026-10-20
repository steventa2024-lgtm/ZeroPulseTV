package tv.calmhome.launcher.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import tv.calmhome.launcher.jarvis.DeviceSigner;
import tv.calmhome.launcher.jarvis.JarvisSession;

/**
 * Where the TV keeps its JARVIS pairing.
 *  - Device identity: an EC P-256 key generated inside Android Keystore (alias zp_jarvis_device). The
 *    private key never leaves the Keystore; the app can only ask it to sign.
 *  - Device token: encrypted with AES-256-GCM under a second Keystore key (zp_jarvis_token) and stored
 *    in the private preferences file zp_jarvis.xml together with the PC address and certificate pin.
 * The app has allowBackup="false" and data-extraction rules excluding everything, so neither the file
 * nor the keys are copied off the TV. Nothing here is ever logged.
 */
final class KeystoreCredentials implements JarvisSession.CredentialStore {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String DEVICE_KEY = "zp_jarvis_device";
    private static final String TOKEN_KEY = "zp_jarvis_token";
    static final String FILE = "zp_jarvis";

    private final SharedPreferences sp;

    KeystoreCredentials(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Override
    public JarvisSession.Pairing load() {
        String host = sp.getString("host", null);
        String fp = sp.getString("pin", null);
        String ct = sp.getString("token_ct", null);
        String iv = sp.getString("token_iv", null);
        if (host == null || fp == null || ct == null || iv == null) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, tokenKey(false), new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
            String token = new String(c.doFinal(Base64.getDecoder().decode(ct)), StandardCharsets.UTF_8);
            return new JarvisSession.Pairing(host, sp.getInt("port", 18800), fp, token);
        } catch (Exception e) {
            return null;   // key gone or data damaged: treat as not paired
        }
    }

    @Override
    public void save(JarvisSession.Pairing p) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, tokenKey(true));
            byte[] ct = c.doFinal(p.token.getBytes(StandardCharsets.UTF_8));
            sp.edit()
                    .putString("host", p.host)
                    .putInt("port", p.port)
                    .putString("pin", p.fingerprint)
                    .putString("token_ct", Base64.getEncoder().encodeToString(ct))
                    .putString("token_iv", Base64.getEncoder().encodeToString(c.getIV()))
                    .commit();
        } catch (Exception e) {
            throw new IllegalStateException("could not protect the device token", e);
        }
    }

    @Override
    public void clear() {
        String lastHost = lastHost();
        int lastPort = lastPort();
        sp.edit().clear().commit();
        if (lastHost != null) rememberHost(lastHost, lastPort);   // the address is not a secret
        try {
            KeyStore ks = keyStore();
            ks.deleteEntry(TOKEN_KEY);
            ks.deleteEntry(DEVICE_KEY);
        } catch (Exception ignored) {
            // nothing stored
        }
    }

    /** Last address used, to prefill the pairing form (not secret). */
    String lastHost() {
        return sp.getString("last_host", null);
    }

    void rememberHost(String host, int port) {
        sp.edit().putString("last_host", host).putInt("last_port", port).apply();
    }

    int lastPort() {
        return sp.getInt("last_port", 18800);
    }

    @Override
    public DeviceSigner signer() throws GeneralSecurityException {
        KeyStore ks = keyStore();
        if (!ks.containsAlias(DEVICE_KEY)) {
            KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
            g.initialize(new KeyGenParameterSpec.Builder(DEVICE_KEY, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
            g.generateKeyPair();
        }
        final PrivateKey key = (PrivateKey) ks.getKey(DEVICE_KEY, null);
        final byte[] spki = ks.getCertificate(DEVICE_KEY).getPublicKey().getEncoded();
        return new DeviceSigner() {
            @Override
            public byte[] publicKeySpki() {
                return spki.clone();
            }

            @Override
            public byte[] signDer(byte[] data) throws GeneralSecurityException {
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initSign(key);
                s.update(data);
                return s.sign();
            }
        };
    }

    /** "Secure hardware", "Keystore (software)" or null if there is no key yet. Reported as Android says. */
    @SuppressWarnings("deprecation")
    String keyProtection() {
        try {
            KeyStore ks = keyStore();
            if (!ks.containsAlias(DEVICE_KEY)) return null;
            PrivateKey key = (PrivateKey) ks.getKey(DEVICE_KEY, null);
            KeyInfo info = KeyFactory.getInstance(key.getAlgorithm(), KEYSTORE).getKeySpec(key, KeyInfo.class);
            boolean hw = Build.VERSION.SDK_INT >= 31
                    ? info.getSecurityLevel() != KeyProperties.SECURITY_LEVEL_SOFTWARE
                    : info.isInsideSecureHardware();
            return hw ? "Secure hardware" : "Keystore (software)";
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey tokenKey(boolean create) throws GeneralSecurityException {
        KeyStore ks = keyStore();
        if (!ks.containsAlias(TOKEN_KEY)) {
            if (!create) throw new GeneralSecurityException("no token key");
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            g.init(new KeyGenParameterSpec.Builder(TOKEN_KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return g.generateKey();
        }
        return (SecretKey) ks.getKey(TOKEN_KEY, null);
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            return ks;
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
