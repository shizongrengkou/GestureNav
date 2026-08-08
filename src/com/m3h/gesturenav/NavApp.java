package com.m3h.gesturenav;

import android.app.Application;

/**
 * Application 入口：进程创建时（无论从 Activity、服务还是广播拉起）
 * 先装好全局崩溃捕获，保证任何崩溃都能落盘留证。
 */
public class NavApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
