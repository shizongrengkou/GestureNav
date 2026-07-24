# M3H 手势导航

为 M3H_ultra 安卓随身 WiFi 开发的全屏手势导航应用，替代原生虚拟按键。

> **⚠️ 免责声明**
>
> 本项目为个人开发的手势导航工具，**未经充分的兼容性和安全性测试**。Magisk 模块方案涉及系统级修改，可能存在未知风险（如开机异常、权限问题等），**刷入前请务必备份数据**。
>
> **推荐使用 ADB 直接安装 APK 的方式**，操作简单、风险可控、卸载方便。Magisk 模块仅作为进阶选项提供，不保证在所有设备和系统版本上正常工作。

## 下载

| 文件 | 说明 |
|---|---|
| [GestureNav.apk](./GestureNav.apk) | 预构建 APK，直接 `adb install` 即可 |
| [M3H-GestureNav-v1.0-Magisk.zip](./M3H-GestureNav-v1.0-Magisk.zip) | Magisk 模块包（进阶，未充分测试） |

> 若点击 APK 链接后看到乱码或直接打开了二进制内容，请右键「链接另存为」下载，或在上方 `Raw` 按钮上右键保存。GitHub 不会对仓库内的大文件做 CDN 加速，首次下载可能略慢。

## 功能

| 手势 | 动作 |
|---|---|
| 底部快速上滑 | 回桌面 (Home) |
| 底部上滑停顿 | 进入后台卡片 (Recents) |
| 左/右边缘内滑 | 返回 (Back) |

## 特性

- 原生风格白色透明箭头视觉反馈
- **底部条仅占 14dp**，不遮挡第三方 App 的底部导航栏与图标（桌面与第三方应用内均可正常点击底部元素）
- 手势方向预判：只有手指明确朝目标方向移动时才接管触摸，向下按压 / 横向拖动 / 静止点按均透传给底层 App
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

> 🆕 **新手用户请看**：👉 **[小白安装教程（图文版）](./小白安装教程.md)** 👈
>
> 面向完全没用过命令行的用户，从下载、装 ADB、开 USB 调试到一键安装，全程图文手把手，约 10 分钟搞定。

### 方式一：ADB 一键安装（推荐）

**最简单的流程：**

1. 下载 [`GestureNav.apk`](./GestureNav.apk) 和 [`一键安装.bat`](./一键安装.bat)，放在**同一个文件夹**
2. 电脑装好 ADB（[官方下载](https://developer.android.com/tools/releases/platform-tools)），随身WiFi 开启 **USB 调试**
3. 数据线连上电脑，手机上点「允许 USB 调试」
4. 双击 `一键安装.bat`，脚本自动完成安装 + 授权 + 启动

> 完整图文步骤见 **[小白安装教程](./小白安装教程.md)**。

或手动执行：

```bash
adb install GestureNav.apk
adb shell appops set com.m3h.gesturenav SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell settings put global policy_control "immersive.navigation=*"
adb shell am start -n com.m3h.gesturenav/.MainActivity
```

### 方式二：Magisk 模块（⚠️ 未充分测试，谨慎使用）

> **注意**：Magisk 模块方案未经充分验证，刷入前请备份数据。如遇问题可通过 Magisk 安全模式卸载模块。

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
├── build.bat                         # 构建脚本（CMD 版本）
├── build.ps1                         # 构建脚本（PowerShell 版本，推荐）
├── GestureNav.apk                    # 预构建 APK，可直接安装
├── 一键安装.bat                       # 小白一键安装（Windows 双击运行）
├── 一键卸载.bat                       # 一键卸载
├── 小白安装教程.md                     # 🆕 图文版新手教程（推荐先看）
├── 使用教程-ADB安装.md                 # ADB 命令行安装教程
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
- 三条独立窄 overlay（底部 14dp + 左 8dp + 右 8dp），中间屏幕不遮挡
- 方向预判接管：`ACTION_DOWN` 时静默观察，仅当手指明显朝目标方向（底部向上 / 侧边向内）移动才接管触摸并显示反馈；其余方向透传，避免误触和吞掉 App 的触摸事件
- 视觉反馈：Canvas 绘制跟随手指的白色半透明箭头/横条
- 开机自启：BOOT_COMPLETED → 透明 Trampoline → 前台服务

## 许可

仅供个人学习使用。
