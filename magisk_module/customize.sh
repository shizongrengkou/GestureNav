#!/system/bin/sh
# M3H Gesture Navigation — Magisk customize script
# Runs during module installation.

ui_print "============================================"
ui_print "  M3H 手势导航 — Gesture Navigation Module  "
ui_print "============================================"
ui_print ""
ui_print "  底部上滑 → 回桌面 (Home)"
ui_print "  底部上滑悬停 → 后台卡片 (Recents)"
ui_print "  侧边内滑 → 返回 (Back)"
ui_print ""
ui_print "  首次安装后请重启设备"
ui_print "  首次使用请先手动打开一次 App 授权悬浮窗权限"
ui_print "============================================"

# Ensure scripts are executable — critical for boot
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/uninstall.sh 0 0 0755
