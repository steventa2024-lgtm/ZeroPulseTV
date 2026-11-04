package tv.calmhome.launcher.downloads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Repairs a transfer inserted just before process death but not yet registered with JobScheduler. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) Downloads.get(context);
    }
}
