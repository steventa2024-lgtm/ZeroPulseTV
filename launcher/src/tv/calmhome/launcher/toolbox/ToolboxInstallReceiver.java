package tv.calmhome.launcher.toolbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Non-exported receiver for {@link android.content.pm.PackageInstaller} callbacks
 * originating from {@link ToolboxEngine}.
 */
public final class ToolboxInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_RESULT = tv.calmhome.launcher.BuildConfig.APPLICATION_ID + ".toolbox.INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_INSTALL_RESULT.equals(intent.getAction())) {
            ToolboxEngine.get(context).onInstallBroadcast(intent);
        }
    }
}
