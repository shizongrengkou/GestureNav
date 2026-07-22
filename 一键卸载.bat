@echo off
chcp 65001 >nul
echo ============================================
echo   M3H 手势导航 — 一键卸载
echo ============================================
echo.

echo [1/3] 卸载应用...
adb uninstall com.m3h.gesturenav
echo.

echo [2/3] 恢复导航栏...
adb shell settings delete global policy_control
echo.

echo [3/3] 清理无障碍设置...
adb shell settings put secure enabled_accessibility_services ""
echo.

echo ============================================
echo   卸载完成！导航栏已恢复。
echo ============================================
pause
