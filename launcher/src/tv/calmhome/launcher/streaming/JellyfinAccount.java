package tv.calmhome.launcher.streaming;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import tv.calmhome.launcher.data.ModuleData;

/**
 * The optional Jellyfin connection, stored in ZeroPulse Streaming's own preferences ("zp_streaming"): server address,
 * user, server id and a random device id. The access token is encrypted with AES-GCM under its own Android
 * Keystore key ({@link #KEY_ALIAS}, used for nothing else); the password is never stored. "Delete Streaming
 * data" removes all of it, including the key.
 */
public final class JellyfinAccount {

    public static final String KEY_ALIAS = ModuleData.JELLYFIN_KEY_ALIAS;

    /** Where the token's secret lives; the Keystore one in the app, a plain one in tests (Robolectric has no Keystore). */
    public interface Vault {
        String seal(String token) throws GeneralSecurityException;
        String open(String sealed) throws GeneralSecurityException;
        void destroy();
    }

    private final SharedPreferences sp;
    private final Vault vault;

    public JellyfinAccount(Context c, Vault vault) {
        sp = c.getSharedPreferences(ModuleData.STREAMING_PREFS, Context.MODE_PRIVATE);
        this.vault = vault;
    }

    public JellyfinAccount(Context c) {
        this(c, new KeystoreVault());
    }

    public boolean connected() {
        return sp.getString("jf_server", null) != null && sp.getString("jf_token", null) != null;
    }

    public String server() { return sp.getString("jf_server", null); }
    public String serverName() { return sp.getString("jf_server_name", "Jellyfin"); }
    public String serverId() { return sp.getString("jf_server_id", ""); }
    public String userName() { return sp.getString("jf_user", null); }
    public String userId() { return sp.getString("jf_user_id", null); }

    public String deviceId() {
        String id = sp.getString("jf_device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString("jf_device_id", id).apply();
        }
        return id;
    }

    /** The decrypted token, or null when it can't be read (then the user signs in again). */
    public String token() {
        String sealed = sp.getString("jf_token", null);
        if (sealed == null) return null;
        try {
            return vault.open(sealed);
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    public void save(String server, String serverName, JellyfinApi.Login login) throws GeneralSecurityException {
        sp.edit()
                .putString("jf_server", server)
                .putString("jf_server_name", serverName)
                .putString("jf_server_id", login.serverId)
                .putString("jf_user", login.userName)
                .putString("jf_user_id", login.userId)
                .putString("jf_token", vault.seal(login.token))
                .apply();
    }

    public void forget() {
        sp.edit().remove("jf_server").remove("jf_server_name").remove("jf_server_id").remove("jf_user")
                .remove("jf_user_id").remove("jf_token").apply();
        vault.destroy();
    }

    /** Client for the saved connection (signed in), or null when not connected. */
    public JellyfinApi api(JellyfinApi.Transport transport) {
        String t = token();
        if (server() == null || t == null) return null;
        return new JellyfinApi(server(), deviceId(), Build.MODEL, transport).signedIn(t, userId());
    }

    // ------------------------------------------------------------------ Android Keystore

    static final class KeystoreVault implements Vault {
        private static final String STORE = "AndroidKeyStore";

        @Override
        public String seal(String token) throws GeneralSecurityException {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(true));
            byte[] ct = c.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(c.getIV()) + ":" + Base64.getEncoder().encodeToString(ct);
        }

        @Override
        public String open(String sealed) throws GeneralSecurityException {
            String[] p = sealed.split(":", 2);
            if (p.length != 2) throw new GeneralSecurityException("format");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, Base64.getDecoder().decode(p[0])));
            return new String(c.doFinal(Base64.getDecoder().decode(p[1])), StandardCharsets.UTF_8);
        }

        @Override
        public void destroy() {
            deleteKey();
        }

        static void deleteKey() {
            try {
                KeyStore ks = KeyStore.getInstance(STORE);
                ks.load(null);
                ks.deleteEntry(KEY_ALIAS);
            } catch (Exception e) {
                // No key, or no Keystore: nothing to delete.
            }
        }

        private static SecretKey key(boolean create) throws GeneralSecurityException {
            KeyStore ks = KeyStore.getInstance(STORE);
            try {
                ks.load(null);
            } catch (java.io.IOException e) {
                throw new GeneralSecurityException(e);
            }
            KeyStore.Entry e = ks.getEntry(KEY_ALIAS, null);
            if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
            if (!create) throw new GeneralSecurityException("no key");
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
            g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return g.generateKey();
        }
    }

}
