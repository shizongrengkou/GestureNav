package com.m3h.gesturenav;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receives BOOT_COMPLETED and QUICKBOOT_POWERON to restart gesture service.
 * Uses a transparent trampoline activity to reliably start the foreground service
 * on Android 10+ without showing any UI.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "GestureBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || "android.intent.action.QUICKBOOT_POWERON".equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Log.i(TAG, "Boot event received: " + action);
            startGestureService(context);
        }
    }

    private void startGestureService(Context context) {
        // Use trampoline activity for reliable foreground service start on Android 10+
        Intent trampoline = new Intent(context, BootTrampolineActivity.class);
        trampoline.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_NO_ANIMATION
                          | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                          | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        context.startActivity(trampoline);
        Log.i(TAG, "Boot trampoline launched");
    }
}
