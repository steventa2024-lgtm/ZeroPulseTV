package tv.calmhome.launcher.downloads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class InstallReceiver extends BroadcastReceiver {
    static final String RESULT = "tv.calmhome.launcher.INSTALL_RESULT";
    @Override public void onReceive(Context context, Intent intent) {
        if (RESULT.equals(intent.getAction())) Downloads.get(context).installer.result(intent);
    }
}
