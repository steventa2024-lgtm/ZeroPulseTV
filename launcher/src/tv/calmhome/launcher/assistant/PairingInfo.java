package tv.calmhome.launcher.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Whether the TV is paired with a PC, for Settings. Reads only the non-secret entries of zp_jarvis.xml:
 * no network, no Keystore, no token. Live connection state exists only while the Assistant is open.
 */
public final class PairingInfo {

    private PairingInfo() { }

    /** "192.168.4.33:18800" when paired, otherwise null. */
    public static String pairedPc(Context c) {
        SharedPreferences sp = c.getSharedPreferences(KeystoreCredentials.FILE, Context.MODE_PRIVATE);
        String host = sp.getString("host", null);
        if (host == null || sp.getString("pin", null) == null || sp.getString("token_ct", null) == null) return null;
        return host + ":" + sp.getInt("port", 18800);
    }
}
