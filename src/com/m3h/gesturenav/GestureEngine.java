package com.m3h.gesturenav;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Path;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class GestureEngine {
    private static final String TAG = "GestureEngine";
    private final AccessibilityService service;
    private final Vibrator vibrator;

    public GestureEngine(AccessibilityService service) {
        this.service = service;
        this.vibrator = (Vibrator) service.getSystemService(service.VIBRATOR_SERVICE);
    }

    public void back() {
        vibrate(12);
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        Log.d(TAG, "Back dispatched");
    }

    public void home() {
        vibrate(16);
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        Log.d(TAG, "Home dispatched");
    }

    public void recents() {
        vibrate(20);
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
        Log.d(TAG, "Recents dispatched");
    }

    private void vibrate(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }
}
