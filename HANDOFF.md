# M3H 手势导航 - 项目交接文档

## 一、项目概述

为 M3H_ultra 安卓随身 WiFi 开发的全屏手势导航应用，替代原生虚拟按键。

### 目标设备信息

| 属性 | 值 |
|---|---|
| 型号 | M3H_ultra |
| 芯片 | MediaTek MT6877V/ZA（天玑 900） |
| 系统 | Android 11（API 30），userdebug/test-keys |
| 屏幕 | 240×320 px，density=0.75（120 dpi） |
| 内存 | ~7.5 GB |
| 存储 | 225 GB（可用 208 GB） |
| 原有虚拟按键 | tw.com.daxia.virtualsoftkeys（已禁用） |
| Magisk | 已安装 |

---

## 二、项目结构

所有源码、资源和构建产物均在：C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav\

`
GestureNav/
├── AndroidManifest.xml
├── build.bat                    # 完整构建脚本（参考用，当前 buildtmp 临时构建）
├── buildtmp/                    # 临时构建目录（每次构建时创建/清空）
│   ├── obj/                     # aapt2 compile 输出 (.flat) + javac 输出 (.class)
│   ├── gen/                     # aapt2 link 生成的 R.java
│   ├── dex/                     # d8 输出的 classes.dex
│   ├── base.apk                 # 资源链接后的原始 APK
│   ├── merged.apk               # 注入 dex 后的 APK
│   ├── aligned.apk              # zipalign 后的 APK
│   └── debug.keystore           # 自动生成的调试签名密钥
├── res/
│   ├── values/
│   │   ├── strings.xml          # app_name = "M3H 手势导航"
│   │   └── colors.xml
│   └── xml/
│       └── accessibility_service_config.xml
└── src/com/m3h/gesturenav/
    ├── MainActivity.java              # 中文设置界面
    ├── GestureAccessibilityService.java  # 无障碍服务（核心权限载体）
    ├── EdgeOverlayService.java        # 前台服务 + 三条边缘触摸条
    ├── GestureEngine.java             # 手势动作执行器（Home/Back/Recents + 震动）
    ├── GestureConfig.java             # 密度自适应手势阈值
    └── BootReceiver.java              # 开机自启
`

- 包名：com.m3h.gesturenav

---

## 三、架构原理

### 3.1 为什么需要 AccessibilityService

Android 不允许普通应用调用全局导航操作（Home/Back/Recents）。GestureAccessibilityService 继承 AccessibilityService，是唯一能调用 performGlobalAction() 的方式。它本身不处理触摸事件，只作为底层能力提供者。

### 3.2 为什么用三条窄条而非全屏 overlay

最初版本用一个全屏透明 View 覆盖整个屏幕，在 onTouchEvent 中根据触摸坐标判断是否在边缘区域。**问题**：Android WindowManager overlay 即使返回 alse，不同 ROM 对触摸穿透的行为不一致——在 M3H 上中间区域的点击和滑动全被拦截。

**当前方案**（EdgeOverlayService.java 已写好但未部署）：

- **底部条**：全宽 × 21px 高度，贴在屏幕底部（Gravity.BOTTOM）
- **左侧条**：6px 宽 × 全高，贴在屏幕左边（Gravity.LEFT）
- **右侧条**：6px 宽 × 全高，贴在屏幕右边（Gravity.RIGHT）

每条都是独立 WindowManager 窗口，只覆盖自己的边缘区域，中间屏幕完全不挡。

### 3.3 手势识别逻辑

所有手势识别在 EdgeOverlayService.EdgeStripView.onTouchEvent() 中完成：

- **底部上滑 → Home**：dy < 0 且距离 > 32dp，或速度 > 0.6dp/ms
- **底部上滑并停留 → Recents**：上滑超过 48dp 且停留 > 280ms
- **左右边缘内滑 → Back**：向屏幕内侧滑动 > 18dp

### 3.4 GestureConfig 阈值计算

`java
density = 从 DisplayMetrics 实时读取（M3H 上为 0.75）
dp(value) = value * density  // 物理像素转换
`

所有阈值都以 dp 定义，确保不同设备自动适配。

---

## 四、构建流程（纯命令行，无 Gradle）

### 工具链位置

| 工具 | 路径 |
|---|---|
| JDK 17 | C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot |
| Android SDK | C:\Users\Administrator\AppData\Local\Android\Sdk |
| Build-tools | %SDK%\build-tools\34.0.0 |
| Platform | %SDK%\platforms\android-30 |
| ADB | C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_*\platform-tools\adb.exe |

### 构建步骤（必须按顺序）

`
[1] aapt2 compile  →  res/ → obj/*.flat
[2] aapt2 link     →  obj/*.flat + AndroidManifest.xml → base.apk + gen/R.java
[3] javac          →  src/*.java + gen/R.java → obj/*.class
[4] d8             →  obj/*.class → dex/classes.dex
[5] ZipFile merge  →  base.apk + classes.dex → merged.apk
[6] zipalign       →  merged.apk → aligned.apk
[7] apksigner      →  aligned.apk → GestureNav.apk
`

### 关键注意事项

1. **中文路径问题**：m3hu折腾项目 对 aapt2 不可见。必须 cd 到项目根目录，使用相对路径 es 而非绝对路径
2. **通配符问题**：aapt2 link 在 Windows 上不接受 *.flat，必须用 or %%f 逐个展开成空格分隔的绝对路径列表
3. **aapt2 add 已移除**：build-tools 34 没有 apt2 add 子命令，dex 注入改用 PowerShell 的 System.IO.Compression.ZipFile
4. **BOM 问题**：PowerShell Out-File 默认加 UTF-8 BOM，javac 不认。用 [System.IO.File]::WriteAllBytes 去掉前 3 字节
5. **jar 打包陷阱**：jar cf 会试图把当前目录的 hsperfdata_*（JVM perf 文件）打包进去导致失败，用 ZipFile API 避免
6. **foregroundServiceType**：API 30 不支持 specialUse，改用 dataSync

---

## 五、当前状态与待办

### 已完成
- [x] 工具链安装（JDK17 + SDK + build-tools + platform-tools）
- [x] 全部源码编写（6 个 Java 文件 + 资源文件）
- [x] 一次成功构建和部署（旧版全屏 overlay）
- [x] 权限配置（悬浮窗、无障碍、导航栏隐藏）
- [x] 禁用原有虚拟按键 (	w.com.daxia.virtualsoftkeys)
- [x] 底部上滑回到桌面（验证通过）
- [x] BOM 修复（本回合刚完成，所有 Java 文件 BOM 已清除）

### 本轮进行中
- [x] EdgeOverlayService.java 改写为三窄条方案（已完成写入）
- [ ] **构建并部署新版 APK**（构建到第 3 步 javac 时因 BOM 中断，BOM 已修复，下一步继续 javac）

### 待验证
- [ ] 中间屏幕触摸是否恢复正常（三窄条方案的核心目的）
- [ ] 左右边缘返回手势是否正常
- [ ] 底部上滑停留→最近任务是否正常
- [ ] 触摸反馈体验是否需要调整（震动强度、阈值大小）

---

## 六、继续工作的步骤

### 6.1 从当前中断点继续构建

BOM 已清除，直接从 javac 继续：

`powershell
C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav = "C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav"
 = "C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav\buildtmp"
 = "C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\34.0.0"
 = "C:\Users\Administrator\AppData\Local\Android\Sdk\platforms\android-30"
 = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
 = 
C:\Users\Administrator\.codex\tmp\arg0\codex-arg0FaIkBP;C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\override;C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin;C:\Windows\system32;C:\Windows;C:\Windows\System32\Wbem;C:\Windows\System32\WindowsPowerShell\v1.0\;C:\Windows\System32\OpenSSH\;C:\Program Files\nodejs\;C:\Program Files\Git\cmd;C:\Program Files\dotnet\;C:\Program Files\Bandizip\;C:\Program Files\Go\bin;C:\Users\Administrator\AppData\Local\AMD\AI_Bundle\VSCode\bin;C:\Users\Administrator\.local\bin;C:\Users\Administrator\AppData\Local\Programs\Python\Python312\Scripts\;C:\Users\Administrator\AppData\Local\Programs\Python\Python312\;C:\Users\Administrator\AppData\Local\Programs\Python\Launcher\;c:\Users\Administrator\AppData\Local\Programs\Trae CN\bin;C:\Users\Administrator\AppData\Local\Microsoft\WindowsApps;C:\Users\Administrator\AppData\Roaming\npm;C:\Users\Administrator\go\bin;C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Links;C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools;C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback;C:\Users\Administrator\AppData\Local\OpenAI\Codex\bin\ada252862d154cdd;C:\Program Files\WindowsApps\OpenAI.Codex_26.715.9868.0_x64__2p2nqsd0c76g0\app\resources = "\bin;C:\Users\Administrator\.codex\tmp\arg0\codex-arg0FaIkBP;C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\override;C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin;C:\Windows\system32;C:\Windows;C:\Windows\System32\Wbem;C:\Windows\System32\WindowsPowerShell\v1.0\;C:\Windows\System32\OpenSSH\;C:\Program Files\nodejs\;C:\Program Files\Git\cmd;C:\Program Files\dotnet\;C:\Program Files\Bandizip\;C:\Program Files\Go\bin;C:\Users\Administrator\AppData\Local\AMD\AI_Bundle\VSCode\bin;C:\Users\Administrator\.local\bin;C:\Users\Administrator\AppData\Local\Programs\Python\Python312\Scripts\;C:\Users\Administrator\AppData\Local\Programs\Python\Python312\;C:\Users\Administrator\AppData\Local\Programs\Python\Launcher\;c:\Users\Administrator\AppData\Local\Programs\Trae CN\bin;C:\Users\Administrator\AppData\Local\Microsoft\WindowsApps;C:\Users\Administrator\AppData\Roaming\npm;C:\Users\Administrator\go\bin;C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Links;C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools;C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback;C:\Users\Administrator\AppData\Local\OpenAI\Codex\bin\ada252862d154cdd;C:\Program Files\WindowsApps\OpenAI.Codex_26.715.9868.0_x64__2p2nqsd0c76g0\app\resources"

# Step 3: javac (aapt2 compile + link 已完成，/obj/*.flat 和 /gen/R.java 已有)
 = (Get-ChildItem "C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav\src\com\m3h\gesturenav\*.java" | % { """""" }) -join " "
 = (Get-ChildItem "\gen\com\m3h\gesturenav\*.java" | % { """""" }) -join " "
cmd /c ""\bin\javac.exe" -encoding UTF-8 -d "\obj" -cp "\android.jar" --release 11  "

# Step 4: d8
 = (Get-ChildItem "\obj\com\m3h\gesturenav\*.class" | % { """""" }) -join " "
cmd /c "mkdir "\dex" 2>nul & call "\d8.bat" --lib "\android.jar" --output "\dex" "

# Step 5: ZipFile merge
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item "\base.apk" "\merged.apk" -Force
 = [System.IO.Compression.ZipFile]::Open("\merged.apk", 2)
 = .CreateEntry("classes.dex")
 = .Open()
 = [System.IO.File]::ReadAllBytes("\dex\classes.dex")
.Write(, 0, .Length)
.Dispose(); .Dispose()

# Step 6: zipalign
& "\zipalign.exe" -p 4 "\merged.apk" "\aligned.apk"

# Step 7: sign
cmd /c "call "\apksigner.bat" sign --ks "\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav\GestureNav.apk" "\aligned.apk""

# Step 8: install
adb install -r "C:\Users\Administrator\Documents\m3hu折腾项目\GestureNav\GestureNav.apk"
`

### 6.2 部署后配置

`powershell
# 确保权限
adb shell appops set com.m3h.gesturenav SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services com.m3h.gesturenav/com.m3h.gesturenav.GestureAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 隐藏导航栏
adb shell settings put global policy_control "immersive.navigation=*"

# 启动前台服务
adb shell am start-foreground-service com.m3h.gesturenav/.EdgeOverlayService
`

### 6.3 排查命令

`powershell
# 查看服务状态
adb shell dumpsys activity services com.m3h.gesturenav

# 查看无障碍注册
adb shell dumpsys accessibility | Select-String "com.m3h.gesturenav"

# 查看日志
adb logcat -d -s GestureOverlay:* GestureEngine:*
`

### 6.4 如需调整手势灵敏度

修改 GestureConfig.java 中的常量（均在 dp 单位下），重新构建：
- BOTTOM_EDGE_HEIGHT_DP = 28 — 底部触发区高度
- LEFT_EDGE_WIDTH_DP = 8 / RIGHT_EDGE_WIDTH_DP = 8 — 侧边触发区宽度
- SWIPE_UP_THRESHOLD_DP = 32 — 上滑最小距离
- HOLD_TIME_MS = 280 — 停留触发 Recents 的时间
- SIDE_SWIPE_THRESHOLD_DP = 18 — 侧滑最小距离

---

## 七、已知问题与解决记录

| # | 问题 | 原因 | 解决 |
|---|---|---|---|
| 1 | aapt2 找不到 res 目录 | 中文路径 m3hu折腾项目 不识别 | cd 到项目根用相对路径 |
| 2 | aapt2 link 不认 *.flat | Windows 通配符兼容性 | for 循环拼空格分隔列表 |
| 3 | apt2 add 不存在 | build-tools 34 移除了该子命令 | PowerShell ZipFile API 注入 dex |
| 4 | javac 报 BOM 非法字符 | Out-File 写入 UTF-8 BOM | 用 WriteAllBytes 去掉前 3 字节 |
| 5 | jar cf 失败 | JVM perfdata 文件被打包 | 改用 ZipFile API |
| 6 | foregroundServiceType 不兼容 | API30 无 specialUse | 改为 dataSync |
| 7 | **中间屏幕触摸失效** | 全屏 overlay 拦截所有触摸 | 改为三条独立窄条（已编码未部署） |

