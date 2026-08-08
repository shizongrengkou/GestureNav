package com.m3h.gesturenav;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

/**
 * 前台服务：保持进程存活 + 常驻通知 + 手势健康看门狗。
 *
 * 手势条 overlay 窗口已迁移到 GestureAccessibilityService 中
 * （使用 TYPE_ACCESSIBILITY_OVERLAY，免疫该 ROM 的 setHideNonSystemOverlays
 * 强制隐藏机制）。本服务负责：
 *   1. 前台通知（防止进程被回收，开机链路不变）
 *   2. 看门狗：无障碍服务断开时记录事件、提示恢复通知、必要时自杀触发
 *      系统重绑（START_STICKY 重启进程 → AccessibilityManagerService 自动重绑，
 *      实测 kill -9 后 ~10s 内恢复）。
 *
 * 为什么需要看门狗：Android 11 对崩溃过的无障碍服务不自动重绑
 * （防崩溃循环），一旦进程死亡且未被拉起，手势会静默失效。
 */
public class EdgeOverlayService extends Service {
    private static final String TAG = "GestureOverlay";
    private static final String CHANNEL_ID = "gesture_nav_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int RECOVERY_NOTIFICATION_ID = 2003;

    // ── 看门狗参数 ──────────────────────────────────────────────────────────
    private static final long WATCHDOG_INTERVAL_MS = 30_000;
    /** 断开超过此时长才提示恢复通知（避免短暂重连抖动打扰） */
    private static final long NOTIFY_AFTER_MS = 60_000;
    /** 断开超过此时长且未被系统自动恢复，自杀触发重绑 */
    private static final long AUTO_KILL_AFTER_MS = 120_000;
    /** 自杀冷却：10 分钟内最多一次，防重启后仍断开导致无限循环 */
    private static final long AUTO_KILL_COOLDOWN_MS = 600_000;

    private static EdgeOverlayService instance;

    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private long disconnectedSince = 0;
    private long lastAutoKill = 0;
    private boolean recoveryShown = false;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            try {
                checkAccessibilityHealth();
            } catch (Exception e) {
                Log.w(TAG, "watchdog tick failed: " + e);
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    public static EdgeOverlayService getInstance() { return instance; }

    /** 无障碍服务连接成功：手势能力就绪（窗口与引擎已在无障碍服务侧建好） */
    public static void onAccessibilityReady(android.accessibilityservice.AccessibilityService accService) {
        Log.i(TAG, "Accessibility connected, gesture engine ready");
    }

    /** 无障碍服务断开（如设置页触发重建）：引擎会在重连时自动重建 */
    public static void onAccessibilityDisconnected() {
        Log.w(TAG, "Accessibility disconnected, waiting for reconnect");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForeground(NOTIFICATION_ID, buildNotification());
        CrashLogger.log(this, "EdgeOverlayService created");
        watchdogHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);

        // 若无障碍已先连接（开机时通常先于本服务），确认引擎状态
        GestureAccessibilityService acc = GestureAccessibilityService.getInstance();
        if (acc != null && acc.getEngine() != null) {
            Log.i(TAG, "Gesture engine already running (from accessibility service)");
        } else {
            Log.w(TAG, "AccessibilityService not yet connected, waiting for callback");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 引擎生命周期由无障碍服务管理；这里仅确保前台状态
        GestureAccessibilityService acc = GestureAccessibilityService.getInstance();
        if (acc != null && acc.getEngine() != null) {
            Log.i(TAG, "Engine alive (onStartCommand)");
        } else {
            Log.w(TAG, "Engine not ready yet (onStartCommand)");
        }
        return START_STICKY;
    }

    // ── 看门狗 ──────────────────────────────────────────────────────────────
    private void checkAccessibilityHealth() {
        GestureAccessibilityService acc = GestureAccessibilityService.getInstance();
        boolean ok = acc != null && acc.getEngine() != null;
        long now = System.currentTimeMillis();

        if (ok) {
            if (disconnectedSince != 0) {
                CrashLogger.log(this, "Accessibility reconnected after "
                        + (now - disconnectedSince) + "ms");
            }
            disconnectedSince = 0;
            if (recoveryShown) {
                getNotificationManager().cancel(RECOVERY_NOTIFICATION_ID);
                recoveryShown = false;
            }
            return;
        }

        // 断开。先确认用户没有在系统设置里主动关闭无障碍
        if (disconnectedSince == 0) disconnectedSince = now;
        long elapsed = now - disconnectedSince;
        boolean enabledInSettings = isAccessibilityEnabledInSettings();
        CrashLogger.log(this, "Accessibility disconnected " + elapsed
                + "ms, settingsEnabled=" + enabledInSettings);

        if (!enabledInSettings) {
            // 用户主动关闭：不打扰、不自救
            if (recoveryShown) {
                getNotificationManager().cancel(RECOVERY_NOTIFICATION_ID);
                recoveryShown = false;
            }
            return;
        }

        if (elapsed >= NOTIFY_AFTER_MS && !recoveryShown) {
            recoveryShown = true;
            getNotificationManager().notify(RECOVERY_NOTIFICATION_ID,
                    buildRecoveryNotification());
            CrashLogger.log(this, "Recovery notification shown");
        }

        if (elapsed >= AUTO_KILL_AFTER_MS && now - lastAutoKill > AUTO_KILL_COOLDOWN_MS) {
            lastAutoKill = now;
            CrashLogger.log(this, "Auto-heal: killing own process to force rebind");
            // 自杀后系统按 START_STICKY 重启本服务，同时 AccessibilityManagerService
            // 会重新绑定无障碍服务（实测 ~10s 内完成）
            Process.killProcess(Process.myPid());
        }
    }

    private boolean isAccessibilityEnabledInSettings() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null
                && enabled.contains(GestureAccessibilityService.class.getName());
    }

    // ── Notification ─────────────────────────────────────────────────────────
    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Gesture Navigation", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Edge gestures active");
            ch.setShowBadge(false);
            NotificationManager nm = getNotificationManager();
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

    /** 手势中断提示：点击打开应用（进程拉起后系统会自动重绑无障碍服务） */
    private Notification buildRecoveryNotification() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("手势服务已中断")
                .setContentText("点击打开应用即可自动恢复手势")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        watchdogHandler.removeCallbacks(watchdog);
        instance = null;
        CrashLogger.log(this, "EdgeOverlayService destroyed");
        super.onDestroy();
    }
}
