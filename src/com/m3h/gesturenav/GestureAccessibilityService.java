package com.m3h.gesturenav;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：手势能力的载体，同时拥有三条手势 overlay 窗口。
 *
 * 为什么窗口必须由本服务添加？
 * 该 ROM 在打开系统设置等关键 UI 时会调用 setHideNonSystemOverlays(true)，
 * 把所有 TYPE_APPLICATION_OVERLAY（非系统窗口）强制隐藏，导致手势失效。
 * TYPE_ACCESSIBILITY_OVERLAY 属于系统窗口类型，不受该机制影响；
 * 但它要求调用方持有 BIND_ACCESSIBILITY_SERVICE 权限，只有本服务满足。
 *
 * 引擎与窗口的完整生命周期都在这里管理：服务连接时创建，销毁时清理，
 * 因此无障碍服务被系统重建（打开设置页触发）后会自动恢复，不会残留死引用。
 */
public class GestureAccessibilityService extends AccessibilityService {
    private static final String TAG = "GestureOverlay";

    private static GestureAccessibilityService instance;

    private GestureEngine engine;
    private WindowManager wm;
    private View bottomOverlay, leftOverlay, rightOverlay;

    public static GestureAccessibilityService getInstance() { return instance; }

    /** 手势引擎（可能在 EdgeOverlayService 之前启动时尚未创建，返回 null） */
    public GestureEngine getEngine() { return engine; }

    @Override
    public void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 屏幕参数在窗口添加前确定
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        GestureConfig.density = dm.density;
        GestureConfig.screenWidth = dm.widthPixels;
        GestureConfig.screenHeight = dm.heightPixels;
        Log.i(TAG, String.format("Screen: %dx%d, density=%.2f", dm.widthPixels, dm.heightPixels, dm.density));

        engine = new GestureEngine(this);
        addEdgeOverlays();
        Log.i(TAG, "Gesture engine + overlays ready");
        CrashLogger.log(this, "Accessibility connected: " + dm.widthPixels + "x"
                + dm.heightPixels + " density=" + dm.density);

        // 通知前台服务（若有）手势能力已就绪
        EdgeOverlayService.onAccessibilityReady(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        CrashLogger.log(this, "Accessibility destroyed, removing overlays");
        removeEdgeOverlays();
        engine = null;
        instance = null;
        EdgeOverlayService.onAccessibilityDisconnected();
        super.onDestroy();
    }

    // ── 三条手势条（底部 / 左 / 右）──────────────────────────────────────────
    private void addEdgeOverlays() {
        int bw = GestureConfig.bottomEdgePx();
        int lw = GestureConfig.leftEdgePx();
        int rw = GestureConfig.rightEdgePx();
        int sw = GestureConfig.screenWidth;
        int sh = GestureConfig.screenHeight;

        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                      | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // 系统窗口类型：免疫 ROM 的 setHideNonSystemOverlays 强制隐藏
        int overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

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

    private void removeEdgeOverlays() {
        try {
            if (bottomOverlay != null) { wm.removeView(bottomOverlay); bottomOverlay = null; }
            if (leftOverlay != null)   { wm.removeView(leftOverlay);   leftOverlay = null; }
            if (rightOverlay != null)  { wm.removeView(rightOverlay);  rightOverlay = null; }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Overlay already removed: " + e.getMessage());
        }
    }

    // ── 边缘手势条（与 EdgeOverlayService 中同一套逻辑，仅宿主变化）─────────────
    private static class EdgeStripView extends View {
        private final int edge; // 0=bottom, 1=left, 2=right
        private GestureEngine engine;
        private float startRawX, startRawY;
        private long downTime;
        private boolean tracking, holdPhase, dispatched;

        private static final float TAP_MAX_DIST_DP = 10f;
        private static final long TAP_MAX_DURATION_MS = 200;

        private boolean showFeedback = false;
        private boolean gestureTriggered = false;
        private float fingerX, fingerY;
        private float swipeDistance = 0f;

        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int ARROW_COLOR = 0x99FFFFFF;

        EdgeStripView(GestureAccessibilityService ctx, int edge) {
            super(ctx);
            this.edge = edge;
            setBackgroundColor(0x00000000);

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void bindEngine(GestureEngine e) { this.engine = e; }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (engine == null) {
                Log.w(TAG, "edge=" + edge + " touch dropped: engine null");
                return false;
            }
            float rx = event.getRawX();
            float ry = event.getRawY();
            long evTime = event.getEventTime();

            int[] loc = new int[2];
            getLocationOnScreen(loc);
            fingerX = rx - loc[0];
            fingerY = ry - loc[1];

            Log.i(TAG, "touch edge=" + edge + " act=" + event.getActionMasked()
                    + " (" + rx + "," + ry + ") track=" + tracking);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startRawX = rx; startRawY = ry;
                    downTime = evTime; tracking = true;
                    holdPhase = false; dispatched = false;
                    showFeedback = false; gestureTriggered = false;
                    swipeDistance = 0f;
                    claimAccepted = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!tracking) return false;
                    float dx = rx - startRawX;
                    float dy = ry - startRawY;
                    long elapsed = evTime - downTime;

                    float moveDist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (edge == 0) {
                        boolean upwardIntent = dy < -GestureConfig.dp(4f);
                        boolean downwardIntent = dy > GestureConfig.dp(6f);

                        if (!claimAccepted) {
                            if (downwardIntent || (moveDist > GestureConfig.dp(14f) && !upwardIntent)) {
                                tracking = false; hide();
                                return false;
                            }
                            if (upwardIntent && moveDist > GestureConfig.dp(4f)) {
                                claimAccepted = true;
                            } else {
                                return true;
                            }
                        }

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
                    } else {
                        float dir = (edge == 1) ? dx : -dx;
                        float distIn = Math.abs(dx);
                        boolean inwardIntent = dir > GestureConfig.dp(3f);
                        boolean outwardIntent = dir < -GestureConfig.dp(5f);

                        if (!claimAccepted) {
                            if (outwardIntent || (moveDist > GestureConfig.dp(14f) && !inwardIntent)) {
                                tracking = false; hide();
                                return false;
                            }
                            if (inwardIntent && moveDist > GestureConfig.dp(3f)) {
                                claimAccepted = true;
                            } else {
                                return true;
                            }
                        }

                        swipeDistance = distIn;
                        showFeedback = true;
                        progress = Math.min(1f, distIn / GestureConfig.sideSwipeMin());
                        if (distIn > GestureConfig.sideSwipeMin()) {
                            gestureTriggered = true;
                            engine.back(); dispatched = true;
                        }
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!tracking) return false;
                    float ddx = rx - startRawX;
                    float ddy = ry - startRawY;
                    long dur = evTime - downTime;
                    tracking = false;

                    if (edge == 0 && !dispatched && claimAccepted) {
                        float dist = Math.abs(ddy);
                        if (dist > GestureConfig.swipeUpMin() || ddy < -GestureConfig.dp(18f)) {
                            gestureTriggered = true;
                            engine.home();
                        }
                    }
                    if ((edge == 1 || edge == 2) && !dispatched && claimAccepted) {
                        float dist = Math.abs(ddx);
                        float dir = (edge == 1) ? ddx : -ddx;
                        if (dir > 0 && dist > GestureConfig.sideSwipeMin() * 0.7f) {
                            gestureTriggered = true;
                            engine.back();
                        }
                    }

                    if (showFeedback) {
                        postDelayed(() -> hide(), 250);
                    }
                    holdPhase = false; dispatched = false;
                    claimAccepted = false;
                    return true;

                default:
                    return false;
            }
        }

        private float progress = 0f;
        private boolean claimAccepted = false;

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

        private void drawBottomPill(Canvas canvas, int w, int h, float p) {
            float pillW = w * 0.28f;
            float pillH = GestureConfig.dp(3f);
            float pillY = Math.max(pillH, Math.min(h - pillH, fingerY));
            float pillX = (w - pillW) / 2f;

            int alpha = (int)(180 * p);

            linePaint.setColor(0xFFFFFF);
            linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(pillH);
            canvas.drawRoundRect(
                pillX, pillY - pillH / 2f,
                pillX + pillW, pillY + pillH / 2f,
                pillH, pillH, linePaint);
        }

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
}
