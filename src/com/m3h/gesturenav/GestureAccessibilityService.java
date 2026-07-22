package com.m3h.gesturenav;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class GestureAccessibilityService extends AccessibilityService {
    private static GestureAccessibilityService instance;

    public static GestureAccessibilityService getInstance() { return instance; }

    @Override
    public void onServiceConnected() {
        instance = this;
        EdgeOverlayService.onAccessibilityReady(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
