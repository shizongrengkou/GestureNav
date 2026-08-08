package com.m3h.gesturenav;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局未捕获异常处理 + 关键生命周期事件落盘。
 *
 * 背景：目标 ROM 不写 data_app_crash 到 dropbox，logcat 循环覆盖后崩溃
 * 完全无法取证（2026-08-08 曾因服务崩溃导致手势静默失效，根因不可查）。
 * 这里把崩溃堆栈和关键事件追加到 filesDir/events.log，崩溃复发时可用
 * `adb shell run-as com.m3h.gesturenav cat files/events.log` 取回分析。
 */
public final class CrashLogger {
    private static final String TAG = "GestureCrash";
    private static final String FILE_NAME = "events.log";
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static Context appContext;
    private static Thread.UncaughtExceptionHandler prevHandler;

    private CrashLogger() {}

    /** 在 Application.onCreate 调用一次；会链式保留系统默认处理器。 */
    public static synchronized void install(Context ctx) {
        if (appContext != null) return;
        appContext = ctx.getApplicationContext();

        prevHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger::onUncaught);
        log(ctx, "CrashLogger installed, sdk=" + Build.VERSION.SDK_INT
                + " model=" + Build.MODEL + " build=" + Build.DISPLAY);
    }

    /** 追加一条带时间戳的事件（线程安全，多进程场景下允许交错）。 */
    public static void log(Context ctx, String msg) {
        synchronized (CrashLogger.class) {
            FileWriter fw = null;
            try {
                File f = new File(appContextOr(ctx).getFilesDir(), FILE_NAME);
                fw = new FileWriter(f, true);
                fw.write(TS.format(new Date()) + " " + msg + "\n");
            } catch (Exception e) {
                Log.w(TAG, "write log failed: " + e);
            } finally {
                try { if (fw != null) fw.close(); } catch (Exception ignored) {}
            }
        }
        Log.i(TAG, msg);
    }

    private static Context appContextOr(Context ctx) {
        return appContext != null ? appContext : ctx.getApplicationContext();
    }

    private static void onUncaught(Thread thread, Throwable t) {
        log(null, "CRASH thread=" + thread.getName() + " tid=" + thread.getId());
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            log(null, "  " + cur);
            StackTraceElement[] st = cur.getStackTrace();
            int max = Math.min(st.length, 30);
            for (int i = 0; i < max; i++) {
                log(null, "    at " + st[i]);
            }
            if (st.length > max) {
                log(null, "    ... (" + (st.length - max) + " more)");
            }
        }
        if (prevHandler != null) {
            prevHandler.uncaughtException(thread, t);
        }
    }
}
