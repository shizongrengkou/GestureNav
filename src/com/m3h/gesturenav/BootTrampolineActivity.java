package com.m3h.gesturenav;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Transparent trampoline that starts EdgeOverlayService on boot,
 * then immediately finishes. No UI is ever shown.
 */
public class BootTrampolineActivity extends Activity {
    private static final String TAG = "GestureBoot";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No setContentView — nothing to draw

        Intent svc = new Intent(this, EdgeOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Log.i(TAG, "EdgeOverlayService started via trampoline");

        finish();
        // No animation for the finish transition
        overridePendingTransition(0, 0);
    }
}
