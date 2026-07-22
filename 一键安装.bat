@echo off
chcp 65001 >nul
echo ============================================
echo   M3H 手势导航 — 一键安装
echo ============================================
echo.

:: Check adb
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 adb，请先安装 Android Platform Tools
    echo 下载地址: https://developer.android.com/tools/releases/platform-tools
    pause
    exit /b 1
)

:: Check device
echo [1/6] 检查设备连接...
adb devices | findstr /r "device$" >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到设备，请确认 USB 调试已开启
    pause
    exit /b 1
)
echo       设备已连接

:: Install APK
echo [2/6] 安装 APK...
adb install -r "%~dp0GestureNav.apk" 2>nul
if %errorlevel% neq 0 (
    echo       尝试卸载旧版后重装...
    adb uninstall com.m3h.gesturenav >nul 2>&1
    adb install "%~dp0GestureNav.apk"
)
if %errorlevel% neq 0 (
    echo [错误] 安装失败
    pause
    exit /b 1
)
echo       安装成功

:: Grant overlay permission
echo [3/6] 授予悬浮窗权限...
adb shell appops set com.m3h.gesturenav SYSTEM_ALERT_WINDOW allow
echo       已授权

:: Enable accessibility service
echo [4/6] 启用无障碍服务...
adb shell settings put secure enabled_accessibility_services com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService
adb shell settings put secure accessibility_enabled 1
echo       已启用

:: Hide navigation bar
echo [5/6] 隐藏导航栏...
adb shell settings put global policy_control "immersive.navigation=*"
echo       已隐藏

:: Start app
echo [6/6] 启动手势服务...
adb shell am start -n com.m3h.gesturenav/.MainActivity >nul 2>&1
echo       已启动

echo.
echo ============================================
echo   安装完成！手势导航已启用。
echo.
echo   手势说明:
echo     底部上滑       → 回桌面
echo     底部上滑停顿   → 后台卡片
echo     侧边内滑       → 返回
echo.
echo   重启后如手势失效，手动打开一次 App 即可
echo ============================================
pause
