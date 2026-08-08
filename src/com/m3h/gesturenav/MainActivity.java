package com.m3h.gesturenav;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.os.Build;

public class MainActivity extends Activity {

    private TextView statusText;
    private Button overlayBtn, accBtn, startStopBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();

        // Auto-request overlay permission on first run
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }

        // Auto-start gesture service on launch
        autoStartService();
    }

    private void autoStartService() {
        Intent serviceIntent = new Intent(this, EdgeOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        startStopBtn.setText("Stop Service");
    }

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(20, 40, 20, 20);
        root.setBackgroundColor(Color.parseColor("#1A1A2E"));

        TextView title = new TextView(this);
        title.setText("M3H Gesture Nav");
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 8);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(12f);
        statusText.setTextColor(Color.parseColor("#AAAAAA"));
        statusText.setPadding(0, 0, 0, 24);
        root.addView(statusText);

        overlayBtn = makeButton("Grant Overlay Permission", v -> requestOverlayPermission());
        accBtn = makeButton("Enable Accessibility", v -> openAccessibilitySettings());
        startStopBtn = makeButton("Start Service", v -> toggleService());

        root.addView(overlayBtn);
        root.addView(accBtn);
        root.addView(startStopBtn);

        TextView helpLabel = new TextView(this);
        helpLabel.setText("Gesture guide:\n"
                + "  Swipe up from bottom → Home\n"
                + "  Swipe up & hold → Recents\n"
                + "  Swipe in from sides → Back");
        helpLabel.setTextSize(11f);
        helpLabel.setTextColor(Color.parseColor("#777777"));
        helpLabel.setPadding(0, 32, 0, 0);
        root.addView(helpLabel);

        setContentView(root);
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(13f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#16213E"));
        btn.setPadding(24, 16, 24, 16);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 8, 8, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startSettingsSafely(intent);
        }
    }

    private void openAccessibilitySettings() {
        startSettingsSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    /**
     * 定制 ROM 可能缺少对应的系统设置页，直接 startActivity 会抛
     * ActivityNotFoundException 导致闪退。先解析再跳转，失败时提示用户。
     */
    private void startSettingsSafely(Intent intent) {
        try {
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "此 ROM 没有对应的系统设置页", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统设置: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(this, EdgeOverlayService.class);
        if (isServiceRunning()) {
            stopService(serviceIntent);
            startStopBtn.setText("Start Service");
            Toast.makeText(this, "Gesture service stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            startStopBtn.setText("Stop Service");
            Toast.makeText(this, "Gesture service started", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isServiceRunning() {
        // Simple flag-based approach — the button toggles state
        return "Stop Service".contentEquals(startStopBtn.getText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            sb.append("Overlay: OK  ");
        } else {
            sb.append("Overlay: NEEDED  ");
        }
        sb.append("|  Accessibility: check settings");
        statusText.setText(sb.toString());
    }
}
