# M3H 手势导航 — ADB 手动安装教程

不依赖 Magisk，通过 ADB 直接推送到设备。

---

## 前置条件

- 电脑已安装 ADB 工具
- 设备已开启 USB 调试
- USB 数据线连接设备

---

## 一键安装命令

将以下命令**整段复制**到终端执行：

```powershell
# ===== 1. 安装 APK =====
adb install GestureNav.apk

# ===== 2. 授予悬浮窗权限 =====
adb shell appops set com.m3h.gesturenav SYSTEM_ALERT_WINDOW allow

# ===== 3. 启用无障碍服务 =====
adb shell settings put secure enabled_accessibility_services com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService
adb shell settings put secure accessibility_enabled 1

# ===== 4. 隐藏导航栏（全面屏手势模式）=====
adb shell settings put global policy_control "immersive.navigation=*"

# ===== 5. 启动手势服务 =====
adb shell am start -n com.m3h.gesturenav/.MainActivity
```

执行完毕后，底部应该出现手势条，可以开始使用。

---

## 手势说明

| 手势 | 动作 |
|---|---|
| 底部快速上滑 | 回桌面 |
| 底部上滑停顿 | 进入后台卡片 |
| 左/右边缘内滑 | 返回 |

---

## 开机自启

本应用内置开机自启功能（BootReceiver），无需额外设置。

**但需要注意**：部分安卓系统会限制后台应用自启，如果重启后手势不工作，请手动打开一次 App。

---

## 卸载

```powershell
# 卸载应用
adb uninstall com.m3h.gesturenav

# 恢复导航栏（可选）
adb shell settings delete global policy_control
```

---

## 常见问题

### Q: 安装失败 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

签名冲突，先卸载旧版：
```powershell
adb uninstall com.m3h.gesturenav
adb install GestureNav.apk
```

### Q: 手势不工作

检查无障碍服务是否启用：
```powershell
adb shell settings get secure enabled_accessibility_services
```
输出应包含 `com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService`

如不包含，重新执行步骤 3。

### Q: 底部图标点不了

本应用已支持点击穿透，底部区域的单击会传递给桌面图标。如仍有问题，尝试重新安装最新版。

### Q: 重启后手势失效

手动打开一次「M3H 手势导航」App，服务会自动恢复。

---

## 排查命令

```powershell
# 查看服务状态
adb shell dumpsys activity services com.m3h.gesturenav

# 查看无障碍注册
adb shell dumpsys accessibility | findstr "m3h"

# 查看手势日志
adb logcat -d -s GestureOverlay:* GestureEngine:*

# 查看开机日志
adb logcat -d -s GestureBoot:*
```
