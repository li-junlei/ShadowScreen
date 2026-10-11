# ShadowScreen

[简体中文](README.md)

Turn a USB-C DisplayPort Alt Mode display, such as AR glasses or a portable monitor, into an independent Android secondary screen. ShadowScreen creates a trusted virtual display through Shizuku so apps can open subsequent activities on that display.

Current version: **[v0.16](https://github.com/li-junlei/ShadowScreen/releases/tag/v0.16) (GKME / external controller forwarding only)**. No root required. Run and authorize [Shizuku](https://shizuku.rikka.app/), then use [Extend](https://github.com/jqssun/android-display-extend) or a similar app to launch content on the secondary screen. See [CHANGELOG.md](CHANGELOG.md) for release notes.

## Installation and usage

1. Download and install the APK from [Releases](https://github.com/li-junlei/ShadowScreen/releases).
2. Start Shizuku and grant ShadowScreen access.
3. Connect an external display, open ShadowScreen, select the target display, and start the secondary screen.
4. A dark gray idle screen shows “副屏已就绪，请通过 Extend 等应用向此屏幕投送内容” (Secondary screen ready. Use Extend or a similar app to send content to this screen).
5. In Extend, select the **virtual display id** created by ShadowScreen and launch an app. The app covers the idle task.
6. Stop the service to release the virtual display.

The app and persistent notification show the virtual display id, resolution, and trusted-mode status. No system navigation bar is provided on the secondary screen.

## GKME / external controller forwarding

Start the trusted secondary display and launch the game there through Extend.
Open the forwarding page on the primary screen and show GKME's local-mode
floating controller above it. Received Android gamepad/D-pad/joystick events
are forwarded through Shizuku to the virtual display ID. Exit/disconnect stops
forwarding and attempts to release inputs. Reopen the page after reconnecting.

v0.16 removes the built-in controller, vendored frontend, uinput backend and
their dependencies. Use GKME as a separate app. Forwarding was previously
confirmed by the user; this removal build has not been retested on the phone.
Input must reach the forwarding page to be forwarded.

## Features and implementation

- **Trusted display:** Shizuku wraps the display service binder so calls execute as shell (uid 2000). The service creates a virtual display with `TRUSTED`, allowing ordinary apps to launch subsequent activities there.
- **Physical resolution:** The virtual display uses `Display.getMode()` physical dimensions. Its output `SurfaceView` is centered and letterboxed to avoid stretching when logical and physical dimensions differ.
- **Hotplug reuse:** Disconnecting detaches the output Surface while keeping the virtual display alive. Reconnecting a same-named display reuses its id. Stopping the service or ending its process requires a new display; Android does not allow a fixed id to be assigned.
- **Idle screen:** v0.8 launches a separate underlying idle task when the virtual display is first created. Cast apps cover it. Reconnection does not relaunch it over existing content.
- **Primary-screen compatibility:** System decorations are disabled to avoid HyperOS secondary launcher/navigation UI interfering with primary-screen gestures and icons.
- **Live status:** Service broadcasts, display listeners, and Shizuku listeners drive status updates.

Content flows from the virtual display through a Surface into a `Presentation` on the external display. No `MediaProjection` is attached, avoiding mirror-only behavior. Hidden APIs are enabled through [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass); `stubs/` is compile-only and is not packaged in the APK.

The approach is modeled on [jqssun/android-display-mirror](https://github.com/jqssun/android-display-mirror) (GPL-3.0).

## Requirements and validation

2026-10-12: v0.16 build, APK v3 signature, package exclusion checks and the MuMu
launcher check passed. No embedded widgets, Kotlin controller code or native
uinput libraries are packaged. Phone forwarding has not been retested.

- Android 15 (API 35) or newer; Shizuku trusted mode is recommended.
- A phone and external display supporting USB-C DisplayPort Alt Mode.
- Tested setup: Xiaomi 17 Pro, XREAL One, HyperOS 4.0.0.41, Shizuku+ 13.6.0.
- On 2026-10-10, the user confirmed normal primary-screen gestures and icons with v0.7 and successful idle-screen validation with v0.8. The v0.8 build and APK signature verification passed.
- Final acceptance is still pending for subsequent activities (such as opening a Bilibili video), physical-resolution output, and repeated hotplug id/status stability. Successful idle-screen validation does not establish acceptance of those separate checks.

## Known limitations

- Without Shizuku, the app falls back to an untrusted virtual display; subsequent activities may return to the phone screen.
- No integrated app launcher is provided. Use Extend or a similar app.
- Device-specific background activity launch policies may affect the initial idle screen and require hardware validation.
- The package remains `com.ljl.xrealscreen`; the service log tag remains `XREALSubScreen`.

## Build

No Gradle. Use JDK 17, Android SDK build-tools 36.1.0, and the android-36 platform. Run in Git Bash after updating the SDK path in `build.sh` for your machine.

```bash
bash build.sh
# Run only when installing to a device:
adb install -r build/app.apk
```

Output: `build/app.apk`. Build pipeline: aapt2 → javac → d8 → zipalign → apksigner. `debug.keystore` is excluded from Git. Release APKs use the current local signing key; preserve that key for compatible updates.

Vendored dependencies:

| Library | License |
|---|---|
| [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 13.1.5 | MIT |
| [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) 6.1 | LGPL-2.1 |

## License

GPL-3.0. See [LICENSE](LICENSE).
