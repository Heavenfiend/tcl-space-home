package local.tcl.vpnguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StartGuardReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("local.tcl.vpnguard.action.START_ENABLED_GUARD".equals(intent.getAction())) {
            GuardService.startIfEnabled(context, GuardService.CHECK_TRANSITION);
        }
    }
}
