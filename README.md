# M3H 手势导航

为 M3H_ultra 安卓随身 WiFi 开发的全屏手势导航应用，替代原生虚拟按键。

## 功能

| 手势 | 动作 |
|---|---|
| 底部快速上滑 | 回桌面 (Home) |
| 底部上滑停顿 | 进入后台卡片 (Recents) |
| 左/右边缘内滑 | 返回 (Back) |

## 特性

- 原生风格白色透明箭头视觉反馈
- 底部区域点击穿透，不影响桌面图标
- 开机无感自启
- 密度自适应，适配不同分辨率设备
- 触发时震动反馈

## 设备信息

| 属性 | 值 |
|---|---|
| 型号 | M3H_ultra |
| 芯片 | MediaTek MT6877V/ZA（天玑 900） |
| 系统 | Android 11（API 30） |
| 屏幕 | 240×320 px，120 dpi |

## 安装方式

### 方式一：ADB 安装（推荐）

1. 手机连接电脑，开启 USB 调试
2. 双击 `一键安装.bat`
3. 等待终端显示「安装完成」
4. 拔线使用

或手动执行：

```bash
adb install GestureNav.apk
adb shell appops set com.m3h.gesturenav SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell settings put global policy_control "immersive.navigation=*"
adb shell am start -n com.m3h.gesturenav/.MainActivity
```

### 方式二：Magisk 模块

1. 将 `M3H-GestureNav-v1.0-Magisk.zip` 推送到设备
2. Magisk App → 模块 → 从本地安装 → 选择 zip
3. 重启设备
4. 打开一次 App 授权悬浮窗权限

## 卸载

```bash
adb uninstall com.m3h.gesturenav
adb shell settings delete global policy_control
```

或双击 `一键卸载.bat`。

## 项目结构

```
GestureNav/
├── AndroidManifest.xml
├── build.bat                         # 构建脚本
├── res/
│   ├── values/strings.xml
│   └── xml/accessibility_service_config.xml
└── src/com/m3h/gesturenav/
    ├── MainActivity.java             # 设置界面
    ├── GestureAccessibilityService.java  # 无障碍服务
    ├── EdgeOverlayService.java       # 手势识别 + 视觉反馈
    ├── GestureEngine.java            # 导航动作执行
    ├── GestureConfig.java            # 手势阈值配置
    ├── BootReceiver.java             # 开机自启
    └── BootTrampolineActivity.java   # 无感启动跳板
```

## 技术说明

- 无障碍服务调用 `performGlobalAction()` 实现 Home/Back/Recents
- 三条独立窄 overlay（底部 + 左 + 右），中间屏幕不遮挡
- 单击穿透：检测触摸距离和时长，短触传递给底层应用
- 视觉反馈：Canvas 绘制跟随手指的白色半透明箭头/横条
- 开机自启：BOOT_COMPLETED → 透明 Trampoline → 前台服务

## 许可

仅供个人学习使用。
