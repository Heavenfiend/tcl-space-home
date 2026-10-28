package local.tcl.vpnguard;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public final class GuardService extends Service {
    private static final String TARGET = "llc.itdev.incy";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean armed;
    private long reconnectAt;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
                Network network = connectivity.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
                boolean vpn = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                if (vpn) {
                    if (!armed) Log.i("TclVpnGuard", "VPN detected; background UI protection active");
                    armed = true;
                    // TCL force-stops the entire package when its cached UI process remains alive.
                    // This standard API removes background processes and preserves its foreground VPN.
                    getSystemService(ActivityManager.class).killBackgroundProcesses(TARGET);
                } else if (armed) {
                    ApplicationInfo app = getPackageManager().getApplicationInfo(TARGET, 0);
                    if ((app.flags & ApplicationInfo.FLAG_STOPPED) != 0) {
                        long now = SystemClock.elapsedRealtime();
                        if (now >= reconnectAt) {
                            Intent reconnect = new Intent(TARGET + ".CONNECT");
                            reconnect.setComponent(new ComponentName(TARGET, TARGET + ".receiver.VpnIntentReceiver"));
                            reconnect.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            sendBroadcast(reconnect);
                            reconnectAt = now + 20000;
                            Log.i("TclVpnGuard", "Restoring INCY after package force-stop");
                        }
                    } else if (SystemClock.elapsedRealtime() >= reconnectAt) {
                        // An ordinary disconnect must stay disconnected.
                        armed = false;
                        Log.i("TclVpnGuard", "VPN disconnected normally; no reconnect requested");
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                armed = false;
            } catch (RuntimeException e) {
                Log.w("TclVpnGuard", "Protection check failed: " + e.getClass().getSimpleName());
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("guard", "Защита VPN", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "guard")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Защита INCY включена")
                .setContentText("Защита от очистки фоновых приложений TCL")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Выключить защиту", stop).build()).build();
        startForeground(1, notification);
        Log.i("TclVpnGuard", "Protection service started");
        handler.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            getSharedPreferences("guard", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        getSharedPreferences("guard", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        Log.i("TclVpnGuard", "Protection service stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
