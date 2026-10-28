package com.mihir.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;

final class VpnStatusIndicator extends TextView {
    private final ConnectivityManager connectivity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Network> vpns = new HashSet<>();
    private final GradientDrawable background = new GradientDrawable();
    private ConnectivityManager.NetworkCallback callback;
    private Boolean displayed;
    private boolean hasDisplay;

    VpnStatusIndicator(Context context) {
        super(context);
        connectivity = context.getSystemService(ConnectivityManager.class);
        setTextSize(12);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setIncludeFontPadding(false);
        setPadding(dp(12), 0, dp(12), 0);
        setFocusable(false);
        setClickable(false);
        background.setCornerRadius(dp(24));
        setBackground(background);
        show(null);
    }

    void start() {
        if (callback != null || connectivity == null) return;
        vpns.clear();
        ConnectivityManager.NetworkCallback listener = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (callback != this) return;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpns.add(network);
                else vpns.remove(network);
                show(!vpns.isEmpty());
            }

            @Override public void onLost(Network network) {
                if (callback != this) return;
                vpns.remove(network);
                show(!vpns.isEmpty());
            }
        };
        callback = listener;
        try {
            // Default NetworkRequest excludes VPN; explicitly listen for its transport instead.
            NetworkRequest request = new NetworkRequest.Builder().clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build();
            connectivity.registerNetworkCallback(request, listener, handler);
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpns.add(network);
            }
            show(!vpns.isEmpty());
        } catch (RuntimeException e) {
            stop();
            show(null);
            Log.w("TvHomeVpn", "Cannot observe VPN: " + e.getClass().getSimpleName());
        }
    }

    void stop() {
        ConnectivityManager.NetworkCallback listener = callback;
        callback = null;
        if (listener != null) try { connectivity.unregisterNetworkCallback(listener); }
        catch (RuntimeException e) { Log.w("TvHomeVpn", "Cannot remove VPN observer: " + e.getClass().getSimpleName()); }
        vpns.clear();
    }

    private void show(Boolean active) {
        if (hasDisplay && (displayed == null ? active == null : displayed.equals(active))) return;
        hasDisplay = true;
        displayed = active;
        boolean connected = Boolean.TRUE.equals(active);
        int color = active == null ? 0xffcbd5e1 : connected ? 0xff8cf0ba : 0xffffa7a7;
        setText(active == null ? "●  VPN: ?" : connected ? "●  VPN: ВКЛ" : "●  VPN: ВЫКЛ");
        setTextColor(color);
        background.setColor(active == null ? 0x99192230 : connected ? 0xb3103028 : 0xb3352025);
        background.setStroke(dp(1), active == null ? 0x665f728a : connected ? 0x8875d8a3 : 0x888b5059);
        setContentDescription(active == null ? "Состояние VPN недоступно"
                : connected ? "VPN подключён" : "VPN отключён");
        Log.i("TvHomeVpn", "VPN transport: " + (active == null ? "unknown" : connected ? "connected" : "disconnected"));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
