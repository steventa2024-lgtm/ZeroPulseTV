package tv.calmhome.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV's physical inputs (HDMI/AV passthrough and TCL's own tuner) via the public TvInputManager API.
 * Switching uses the same intents Calm Home used on this TV: a passthrough channel URI for HDMI,
 * TCL's TV app for the tuner. No privileged permissions.
 */
public final class TvInputs {

    public enum State { CONNECTED, STANDBY, DISCONNECTED, UNKNOWN }

    public static final class Source {
        public final String id, name;
        /** Name the connected device reports over HDMI-CEC (e.g. "Fire TV Stick"), or null. */
        public final String deviceName;
        public final State state;
        public final boolean tuner;
        public final Intent intent;

        Source(String id, String name, String deviceName, State state, boolean tuner, Intent intent) {
            this.id = id;
            this.name = name;
            this.deviceName = deviceName;
            this.state = state;
            this.tuner = tuner;
            this.intent = intent;
        }

        /** Second line on the card: the CEC device name when known, otherwise the reported state. */
        public String detail() {
            if (tuner) return "Live TV";
            if (state == State.DISCONNECTED) return "Nothing connected";
            if (!TextUtils.isEmpty(deviceName)) return deviceName;
            if (state == State.STANDBY) return "Standby";
            if (state == State.CONNECTED) return "Connected";
            return "";
        }
    }

    private TvInputs() { }

    public static List<Source> list(Context c) {
        List<Source> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        PackageManager pm = c.getPackageManager();
        List<TvInputInfo> inputs = tim.getTvInputList();
        Map<String, String> childNames = new HashMap<>();
        for (TvInputInfo info : inputs) {
            if (info.getParentId() != null) {
                CharSequence l = info.loadLabel(c);
                if (!TextUtils.isEmpty(l)) childNames.put(info.getParentId(), l.toString());
            }
        }
        List<Source> tuners = new ArrayList<>(), hdmi = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            if (info.getParentId() != null) continue;
            boolean ownTuner = info.getType() == TvInputInfo.TYPE_TUNER
                    && "com.tcl.tvinput".equals(info.getServiceInfo().packageName);
            if (!info.isPassthroughInput() && !ownTuner) continue;   // apps can register "inputs" too; skip those
            String name = name(c, info);
            if (ownTuner) {
                Intent i = pm.getLaunchIntentForPackage("com.tcl.tv");
                if (i != null) tuners.add(new Source(info.getId(), name, null, State.UNKNOWN, true, i));
            } else {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()));
                hdmi.add(new Source(info.getId(), name, childNames.get(info.getId()), state(tim, info.getId()), false, i));
            }
        }
        hdmi.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        out.addAll(tuners);
        out.addAll(hdmi);
        return out;
    }

    private static String name(Context c, TvInputInfo info) {
        CharSequence l = info.loadCustomLabel(c);
        if (TextUtils.isEmpty(l)) l = info.loadLabel(c);
        return l == null ? info.getId() : l.toString();
    }

    private static State state(TvInputManager tim, String id) {
        try {
            switch (tim.getInputState(id)) {
                case TvInputManager.INPUT_STATE_CONNECTED: return State.CONNECTED;
                case TvInputManager.INPUT_STATE_CONNECTED_STANDBY: return State.STANDBY;
                case TvInputManager.INPUT_STATE_DISCONNECTED: return State.DISCONNECTED;
                default: return State.UNKNOWN;
            }
        } catch (RuntimeException e) {
            return State.UNKNOWN;
        }
    }
}
