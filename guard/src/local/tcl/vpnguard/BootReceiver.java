package local.tcl.vpnguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && context.getSharedPreferences("guard", Context.MODE_PRIVATE).getBoolean("enabled", false)) {
            context.startForegroundService(new Intent(context, GuardService.class));
        }
    }
}
