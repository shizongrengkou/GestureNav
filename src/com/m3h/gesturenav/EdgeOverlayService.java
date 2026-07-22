package com.m3h.gesturenav;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class EdgeOverlayService extends Service {
    private static final String TAG = "GestureOverlay";
    private static final String CHANNEL_ID = "gesture_nav_channel";
    private static final int NOTIFICATION_ID = 2001;

    private static EdgeOverlayService instance;

    private WindowManager wm;
    private GestureEngine engine;
    private View bottomOverlay, leftOverlay, rightOverlay;

    public static void onAccessibilityReady(AccessibilityService accService) {
        if (instance != null && instance.engine == null) {
            instance.engine = new GestureEngine(accService);
            instance.startEdgeOverlays();
            Log.i(TAG, "Gesture engine started (via accessibility callback)");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForeground(NOTIFICATION_ID, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        GestureConfig.density = dm.density;
        GestureConfig.screenWidth = dm.widthPixels;
        GestureConfig.screenHeight = dm.heightPixels;

        Log.i(TAG, String.format("Screen: %dx%d, density=%.2f", dm.widthPixels, dm.heightPixels, dm.density));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (engine == null) {
            AccessibilityService accService = GestureAccessibilityService.getInstance();
            if (accService != null) {
                engine = new GestureEngine(accService);
                startEdgeOverlays();
                Log.i(TAG, "Gesture engine started");
            } else {
                Log.w(TAG, "AccessibilityService not yet connected, waiting for callback");
            }
        }
        return START_STICKY;
    }

    private void startEdgeOverlays() {
        int bw = GestureConfig.bottomEdgePx();
        int lw = GestureConfig.leftEdgePx();
        int rw = GestureConfig.rightEdgePx();
        int sw = GestureConfig.screenWidth;
        int sh = GestureConfig.screenHeight;

        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                      | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams bp = new WindowManager.LayoutParams(sw, bw, overlayType, baseFlags, PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.BOTTOM | Gravity.START;
        bottomOverlay = new EdgeStripView(this, 0);
        ((EdgeStripView) bottomOverlay).bindEngine(engine);
        wm.addView(bottomOverlay, bp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(lw, sh, overlayType, baseFlags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        leftOverlay = new EdgeStripView(this, 1);
        ((EdgeStripView) leftOverlay).bindEngine(engine);
        wm.addView(leftOverlay, lp);

        WindowManager.LayoutParams rp = new WindowManager.LayoutParams(rw, sh, overlayType, baseFlags, PixelFormat.TRANSLUCENT);
        rp.gravity = Gravity.RIGHT | Gravity.TOP;
        rightOverlay = new EdgeStripView(this, 2);
        ((EdgeStripView) rightOverlay).bindEngine(engine);
        wm.addView(rightOverlay, rp);

        Log.i(TAG, String.format("Edge strips: bottom=%dpx left=%dpx right=%dpx", bw, lw, rw));
    }

    // ── Edge strip with finger-following arrow feedback ──────────────────────
    private static class EdgeStripView extends View {
        private final int edge; // 0=bottom, 1=left, 2=right
        private GestureEngine engine;
        private float startRawX, startRawY;
        private long downTime;
        private boolean tracking, holdPhase, dispatched;

        // Tap detection
        private static final float TAP_MAX_DIST_DP = 10f;
        private static final long TAP_MAX_DURATION_MS = 200;

        // Feedback state
        private boolean showFeedback = false;
        private boolean gestureTriggered = false;
        private float fingerX, fingerY;      // finger position in this view's coords
        private float swipeDistance = 0f;     // how far the finger has moved

        // Drawing
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Colors — pure white, no tint
        private static final int ARROW_COLOR = 0x99FFFFFF;   // 60% white

        EdgeStripView(Context ctx, int edge) {
            super(ctx);
            this.edge = edge;
            setBackgroundColor(0x00000000);

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void bindEngine(GestureEngine e) { this.engine = e; }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (engine == null) return false;
            float rx = event.getRawX();
            float ry = event.getRawY();
            long evTime = event.getEventTime();

            // Convert to view-local coordinates
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            fingerX = rx - loc[0];
            fingerY = ry - loc[1];

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startRawX = rx; startRawY = ry;
                    downTime = evTime; tracking = true;
                    holdPhase = false; dispatched = false;
                    showFeedback = false; gestureTriggered = false;
                    swipeDistance = 0f;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!tracking) return false;
                    float dx = rx - startRawX;
                    float dy = ry - startRawY;
                    long elapsed = evTime - downTime;

                    float moveDist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (moveDist < GestureConfig.dp(TAP_MAX_DIST_DP) && elapsed < TAP_MAX_DURATION_MS) {
                        return true;
                    }

                    if (edge == 0) {
                        // Bottom: swipe up = home / hold = recents
                        if (dy < 0) {
                            swipeDistance = Math.abs(dy);
                            showFeedback = true;
                            progress = Math.min(1f, swipeDistance / GestureConfig.swipeUpMin());

                            if (!holdPhase && !dispatched && elapsed > GestureConfig.HOLD_TIME_MS
                                && swipeDistance > GestureConfig.holdDist()
                                && swipeDistance < GestureConfig.holdDist() + GestureConfig.dp(30f)) {
                                holdPhase = true;
                                gestureTriggered = true;
                                engine.recents(); dispatched = true;
                            }
                            invalidate();
                        } else if (dy > GestureConfig.dp(10f)) {
                            tracking = false; hide();
                        }
                    } else {
                        // Left / right: swipe inward = back
                        float distIn = Math.abs(dx);
                        float dir = (edge == 1) ? dx : -dx;
                        if (dir > 0 && !dispatched) {
                            swipeDistance = distIn;
                            showFeedback = true;
                            progress = Math.min(1f, distIn / GestureConfig.sideSwipeMin());
                            if (distIn > GestureConfig.sideSwipeMin()) {
                                gestureTriggered = true;
                                engine.back(); dispatched = true;
                            }
                            invalidate();
                        } else if (dir < -GestureConfig.dp(8f)) {
                            tracking = false; hide();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!tracking) return false;
                    float ddx = rx - startRawX;
                    float ddy = ry - startRawY;
                    long dur = evTime - downTime;
                    tracking = false;

                    float totalDist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                    if (totalDist < GestureConfig.dp(TAP_MAX_DIST_DP) && dur < TAP_MAX_DURATION_MS) {
                        hide();
                        return false; // tap passthrough
                    }

                    if (edge == 0 && !dispatched) {
                        float dist = Math.abs(ddy);
                        if (dist > GestureConfig.swipeUpMin() || ddy < -GestureConfig.dp(20f)) {
                            gestureTriggered = true;
                            engine.home();
                        }
                    }
                    if ((edge == 1 || edge == 2) && !dispatched) {
                        float dist = Math.abs(ddx);
                        float dir = (edge == 1) ? ddx : -ddx;
                        if (dir > 0 && dist > GestureConfig.sideSwipeMin() * 0.7f) {
                            gestureTriggered = true;
                            engine.back();
                        }
                    }

                    // Fade out
                    if (showFeedback) {
                        postDelayed(() -> hide(), 250);
                    }
                    holdPhase = false; dispatched = false;
                    return true;

                default:
                    return false;
            }
        }

        private float progress = 0f;

        private void hide() {
            showFeedback = false; progress = 0f; swipeDistance = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!showFeedback || progress <= 0f) return;

            int w = getWidth();
            int h = getHeight();
            float p = Math.min(1f, progress);

            if (edge == 0) {
                drawBottomPill(canvas, w, h, p);
            } else if (edge == 1) {
                drawSideArrow(canvas, w, h, p, true);
            } else {
                drawSideArrow(canvas, w, h, p, false);
            }
        }

        // ── Bottom: thin pill that follows finger upward ─────────────────────
        private void drawBottomPill(Canvas canvas, int w, int h, float p) {
            float pillW = w * 0.28f;
            float pillH = GestureConfig.dp(3f);
            float pillY = Math.max(pillH, Math.min(h - pillH, fingerY));
            float pillX = (w - pillW) / 2f;

            // Alpha fades with progress
            int alpha = (int)(180 * p);

            linePaint.setColor(0xFFFFFF);
            linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(pillH);
            canvas.drawRoundRect(
                pillX, pillY - pillH / 2f,
                pillX + pillW, pillY + pillH / 2f,
                pillH, pillH, linePaint);
        }

        // ── Side: single chevron following finger ────────────────────────────
        private void drawSideArrow(Canvas canvas, int w, int h, float p, boolean isLeft) {
            float arrowY = Math.max(GestureConfig.dp(16f), Math.min(h - GestureConfig.dp(16f), fingerY));
            float arrowSize = GestureConfig.dp(8f) * Math.min(1f, p * 1.5f);
            float halfH = arrowSize * 0.55f;

            float cx = isLeft ? w - GestureConfig.dp(1f) : GestureConfig.dp(1f);

            linePaint.setColor(0xFFFFFF);
            linePaint.setAlpha((int)(140 * p));
            linePaint.setStrokeWidth(GestureConfig.dp(1.8f));

            if (isLeft) {
                canvas.drawLine(cx - arrowSize * 0.5f, arrowY - halfH,
                               cx, arrowY, linePaint);
                canvas.drawLine(cx, arrowY,
                               cx - arrowSize * 0.5f, arrowY + halfH, linePaint);
            } else {
                canvas.drawLine(cx + arrowSize * 0.5f, arrowY - halfH,
                               cx, arrowY, linePaint);
                canvas.drawLine(cx, arrowY,
                               cx + arrowSize * 0.5f, arrowY + halfH, linePaint);
            }
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Gesture Navigation", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Edge gestures active");
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Gesture Nav")
                .setContentText("Edge gestures active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi).setOngoing(true).setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        instance = null;
        if (bottomOverlay != null) { wm.removeView(bottomOverlay); bottomOverlay = null; }
        if (leftOverlay != null)   { wm.removeView(leftOverlay);   leftOverlay = null; }
        if (rightOverlay != null)  { wm.removeView(rightOverlay);  rightOverlay = null; }
        engine = null; super.onDestroy();
    }
}
