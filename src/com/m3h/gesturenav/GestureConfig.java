package com.m3h.gesturenav;

public class GestureConfig {
    // Edge trigger zones in dp
    // Bottom strip kept thin: only the very edge must capture the gesture so it
    // doesn't sit on top of apps' bottom nav bars / tab bars / action icons.
    // On the 0.75 density target 14dp ≈ 11px.
    public static final int BOTTOM_EDGE_HEIGHT_DP = 14;
    public static final int LEFT_EDGE_WIDTH_DP = 8;
    public static final int RIGHT_EDGE_WIDTH_DP = 8;

    // Gesture thresholds
    public static final float SWIPE_UP_THRESHOLD_DP = 24f;      // min distance for Home
    public static final float SWIPE_HOLD_THRESHOLD_DP = 40f;   // distance before hold triggers Recents
    public static final float HOLD_TIME_MS = 260f;             // pause at hold point to trigger Recents
    public static final float SIDE_SWIPE_THRESHOLD_DP = 16f;   // min distance for Back
    public static final float VELOCITY_THRESHOLD_DP_PER_MS = 0.55f; // min velocity for fast swipe

    // These get filled at runtime from display metrics
    public static float density = 1f;
    public static int screenWidth = 0;
    public static int screenHeight = 0;

    public static float dp(float dp) { return dp * density; }

    public static int bottomEdgePx() { return (int) dp(BOTTOM_EDGE_HEIGHT_DP); }
    public static int leftEdgePx()   { return (int) dp(LEFT_EDGE_WIDTH_DP); }
    public static int rightEdgePx()  { return (int) dp(RIGHT_EDGE_WIDTH_DP); }
    public static float swipeUpMin() { return dp(SWIPE_UP_THRESHOLD_DP); }
    public static float holdDist()   { return dp(SWIPE_HOLD_THRESHOLD_DP); }
    public static float sideSwipeMin() { return dp(SIDE_SWIPE_THRESHOLD_DP); }
    public static float veloMin()    { return dp(VELOCITY_THRESHOLD_DP_PER_MS); }
}
