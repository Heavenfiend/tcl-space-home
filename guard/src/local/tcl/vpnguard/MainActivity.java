package local.tcl.vpnguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private Button toggle;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(70, 30, 70, 30);
        layout.setBackgroundColor(Color.rgb(16, 22, 31));
        TextView title = new TextView(this);
        title.setText("INCY · защита на TCL");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(18);
        status.setTextColor(Color.rgb(167, 219, 197));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 16, 0, 16);
        layout.addView(status);
        TextView detail = new TextView(this);
        detail.setText("Помогает сохранить VPN после выхода из INCY.\nНастройки и подписки INCY не меняет.\nПосле включения VPN откройте YouTube как обычно.\nЗащита запускается после перезагрузки TCL.\nПеред принудительной остановкой INCY в настройках выключите защиту.");
        detail.setTextSize(16);
        detail.setGravity(Gravity.CENTER);
        detail.setTextColor(Color.rgb(205, 213, 224));
        layout.addView(detail);
        toggle = new Button(this);
        toggle.setOnClickListener(v -> {
            boolean enabled = getSharedPreferences("guard", MODE_PRIVATE).getBoolean("enabled", false);
            if (enabled) startService(new Intent(this, GuardService.class).setAction("STOP"));
            else startForegroundService(new Intent(this, GuardService.class));
            getSharedPreferences("guard", MODE_PRIVATE).edit().putBoolean("enabled", !enabled).apply();
            refresh();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(420, 64);
        params.topMargin = 24;
        layout.addView(toggle, params);
        Button close = new Button(this);
        close.setText("Вернуться к просмотру");
        close.setOnClickListener(v -> finish());
        layout.addView(close, new LinearLayout.LayoutParams(420, 64));
        setContentView(layout);
        if (getIntent().getBooleanExtra("enable", false)) {
            getSharedPreferences("guard", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
            startForegroundService(new Intent(this, GuardService.class));
        }
        refresh();
        toggle.requestFocus();
    }

    private void refresh() {
        boolean enabled = getSharedPreferences("guard", MODE_PRIVATE).getBoolean("enabled", false);
        status.setText(enabled ? "Защита включена" : "Защита выключена");
        toggle.setText(enabled ? "Выключить защиту" : "Включить защиту");
    }
}
