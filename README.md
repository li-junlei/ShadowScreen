# ShadowScreen

[English](README.en.md)

将通过 USB-C DisplayPort Alt Mode 连接的 AR 眼镜、便携显示器等设备，变成可独立运行 Android 应用的副屏。通过 Shizuku 创建受信任的虚拟屏，解决副屏应用打开后续页面时跳回手机主屏的问题。

当前版本：**[v0.16](https://github.com/li-junlei/ShadowScreen/releases/tag/v0.16)（仅保留 GKME / 外接手柄转发）**。无需 root；需要运行并授权 [Shizuku](https://shizuku.rikka.app/)。配合 [Extend](https://github.com/jqssun/android-display-extend) 等应用投送内容。更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## 安装与使用

1. 从 [Releases](https://github.com/li-junlei/ShadowScreen/releases) 下载 APK 并安装。
2. 启动 Shizuku，并向 ShadowScreen 授权。
3. 连接外接显示器，打开 ShadowScreen，选择目标显示器并启动副屏。
4. 副屏显示灰黑色底色及提示：“副屏已就绪，请通过 Extend 等应用向此屏幕投送内容”。
5. 在 Extend 等应用中选择 ShadowScreen 创建的**虚拟屏编号**，投送应用；投送内容会覆盖提示页。
6. 停止副屏服务可释放虚拟屏。

应用内状态卡及常驻通知显示虚拟屏编号、分辨率和是否处于 Shizuku trusted 模式。副屏不提供系统导航栏。

## GKME / 外接手柄转发

先启动 Shizuku trusted 副屏，通过 Extend 将游戏投送到虚拟屏编号，再打开“GKME / 外接手柄转发”。保持转发页在主屏前台，在其上方显示 GKME 本机模式的悬浮手柄。

转发页收到的 Android 手柄按键、十字键和摇杆事件，经 Shizuku 指定目标虚拟屏发送。停止副屏、Shizuku 断开或退出页面时停止转发并尝试释放输入。重连后重新打开转发页。

v0.16 已删除内置虚拟手柄、原版控件移植、uinput 后端及相关依赖，使用独立安装的 GKME。外部转发曾由用户确认可用；新版删减后尚未在手机复测。GKME 输入必须到达主屏转发页，才能转送副屏。

## 功能与原理

- **受信任的独立副屏**：通过 Shizuku，以 shell（uid 2000）身份调用 `IDisplayManager` 创建带 `TRUSTED` 的虚拟屏，使普通应用可以在副屏继续启动 Activity。
- **物理分辨率输出**：按目标显示器 `Display.getMode()` 的物理尺寸创建虚拟屏，`SurfaceView` 等比居中显示，避免逻辑分辨率与物理分辨率不一致导致拉伸。
- **插拔复用**：断开时挂起输出 Surface，保留虚拟屏；同名显示器重连后复用同一编号。服务停止或进程结束后，重新创建的编号可能变化，无法指定固定编号。
- **空闲提示**：v0.8 在虚拟屏创建时启动独立的底层提示任务；投送应用覆盖它，重连时不重新启动提示任务，避免覆盖现有内容。
- **主屏兼容性**：不启用副屏系统装饰，避免 HyperOS 的副屏桌面、导航界面干扰主屏手势和系统图标。
- **实时状态**：服务广播、显示器监听和 Shizuku 监听驱动状态刷新。

内容经虚拟屏 → Surface → 外接屏上的 `Presentation` 输出。不绑定 `MediaProjection`，避免被强制为镜像模式。隐藏 API 通过 [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) 解限；`stubs/` 仅用于编译，不打包进 APK。

实现参考 [jqssun/android-display-mirror](https://github.com/jqssun/android-display-mirror)（GPL-3.0）。

## 环境要求与验证状态

- Android 15（API 35）及以上，建议使用 Shizuku trusted 模式。
- 支持 USB-C DisplayPort Alt Mode 的手机与外接显示器。
- 已测试设备：Xiaomi 17 Pro、XREAL One、HyperOS 4.0.0.41、Shizuku+ 13.6.0。
- 2026-10-10：用户确认 v0.7 创建副屏后，主屏手势与系统图标正常；确认 v0.8 空闲提示功能验证成功。v0.8 编译及 APK 签名验证通过。
- 2026-10-12：v0.16 构建、APK v3 签名、安装包内容检查及 MuMu 主页面入口检查通过；无内置手柄、Kotlin 控件或原生 uinput 库。未操作手机，新版转发效果仍需实机复测。
- 尚待单独完成最终验收：B 站打开视频等后续 Activity 留在副屏、物理分辨率显示效果，以及多次拔插后的编号与状态同步。空闲提示验证成功不代表这些项目全部验收完成。

## 已知限制

- Shizuku 不可用时回退到普通、不受信任的虚拟屏，应用后续页面仍可能跳回主屏。
- 不提供一体化应用启动器，需配合 Extend 等应用。
- 不同设备的后台 Activity 启动策略可能影响初始提示页，需要实机验证。
- 包名仍为 `com.ljl.xrealscreen`；服务日志标签仍为 `XREALSubScreen`。

## 构建

不使用 Gradle。需要 JDK 17、Android SDK build-tools 36.1.0 和 android-36 平台，在 Git Bash 中运行；先按本机环境修改 `build.sh` 的 SDK 路径。

```bash
bash build.sh
# 仅在需要安装到设备时执行：
adb install -r build/app.apk
```

产物为 `build/app.apk`。构建流程：aapt2 → javac → d8 → zipalign → apksigner。`debug.keystore` 不纳入 Git；发布 APK 使用当前本地密钥签名，后续覆盖更新需保留同一密钥。

随项目提供的第三方依赖：

| 依赖 | 许可证 |
|---|---|
| [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 13.1.5 | MIT |
| [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) 6.1 | LGPL-2.1 |

## 许可证

GPL-3.0，详见 [LICENSE](LICENSE)。
