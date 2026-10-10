# ShadowScreen

Turn any external display (AR glasses, portable monitor, ...) connected to your
Android phone into a **real secondary screen** that can independently host app
tasks — apps launched there stay there, instead of jumping back to the phone.

Works over USB-C DisplayPort Alt Mode displays. No root required (needs
[Shizuku](https://github.com/RikkaApps/Shizuku) running via wireless debugging / root).

## The problem

An ordinary app can create a `VirtualDisplay` and render it to the external
screen via a `Presentation` — but that virtual display lacks the `TRUSTED` flag
(`ADD_TRUSTED_DISPLAY` is a signature-level permission). When an app inside the
secondary screen starts another activity itself (e.g. tapping a video card in
Bilibili), `ActivityTaskSupervisor.isCallerAllowedToLaunchOnDisplay` rejects the
untrusted display and the task silently falls back to the default display —
the "app jumps back to my phone" bug every screen-mirroring app has.

## How it works

```
┌─ Phone ──────────────────────────────────────────┐   ┌─ External display ─┐
│  ShadowScreen (foreground service)               │   │                    │
│                                                  │   │  Presentation      │
│  Presentation + SurfaceView ─── Surface ───────┼──►│  (fullscreen)      │
│         ▲                                        │   │                    │
│  VirtualDisplay (TRUSTED | OWN_DISPLAY_GROUP |  │   └────────────────────┘
│      ALWAYS_UNLOCKED | ...) ── renders to ──────┘                │
│         ▲ created as shell uid via Shizuku                        │
│  IDisplayManager AIDL ◄── ShizukuBinderWrapper ◄── Shizuku server │
└───────────────────────────────────────────────────┘
```

* Shizuku hands us the `display` service binder wrapped so that calls execute
  as **shell (uid 2000)**, which holds `ADD_TRUSTED_DISPLAY` — so the trusted
  flags are accepted.
* No `MediaProjection` is attached (that would force mirror-only mode).
* Hidden APIs (`IDisplayManager`, `IVirtualDisplayCallback`,
  `DisplayManagerGlobal`) are compile-only stubs resolved against the real
  framework at runtime, unblocked by
  [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass).
* The virtual display resolution is fixed to the selected display's **physical
  mode** (`Display.getMode()`); the `SurfaceView` is letterboxed to the same
  aspect ratio so content is never stretched.
* Hotplug: on display removal the service detaches the output surface and keeps
  the virtual display alive. Reconnecting a same-named display reuses its id;
  stopping the service releases it.

The approach is modeled on
[jqssun/android-display-mirror](https://github.com/jqssun/android-display-mirror)
(GPL-3.0) — thanks!

## Requirements

* Android 15 (API 35) or newer (public `VirtualDisplayConfig` API)
* [Shizuku](https://shizuku.rikka.app/) running and this app granted
* An external display connected (USB-C DP Alt Mode, e.g. XREAL One glasses)

## Build

No Gradle — plain command line build:

```bash
# edit SDK paths at the top of build.sh first (JDK 17 + SDK build-tools 36 + android-36 platform)
bash build.sh
adb install -r build/app.apk
```

Third-party jars are vendored in `libs/`:

| Library | License |
|---|---|
| [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) 13.1.5 | MIT |
| [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) 6.1 | LGPL-2.1 |

## Usage

1. Start Shizuku on the phone, grant ShadowScreen access.
2. Open ShadowScreen — it lists every connected presentation-capable display.
3. Tap the display you want. The secondary screen starts and appears on the
   external display.
4. Send apps to the secondary screen with a task-launcher that can target a
   display (e.g. `adb shell am start --display <id>`, scrcpy, or a
   cast/extend companion app).

Status is shown in-app and in the persistent notification, including the
created virtual display id and whether it runs in trusted mode.

## Known limitations

* v0.7 disables virtual-display system decorations to avoid starting the HyperOS
  secondary system launcher/navigation UI. The display remains trusted and hosts
  independent app tasks; send apps with Extend. An empty display is expected
  before an app is launched, and no system navigation bar is provided there.
  On 2026-10-10, the user verified on a Xiaomi 17 Pro running HyperOS 4.0.0.41
  that primary-screen gestures and system icons remained normal after creating
  the secondary display. App activity transitions and hotplug behavior have not
  been revalidated with v0.7.

* Without Shizuku the app falls back to a normal (untrusted) virtual display —
  the jump-back-to-phone bug returns in that mode.
* Virtual display ids drift on every rebuild (expected platform behavior).
* Some external displays negotiate a logical resolution that differs from
  their physical mode (e.g. a portrait letterbox); ShadowScreen always renders
  at the physical mode and letterboxes.

## License

GPL-3.0. See [LICENSE](LICENSE).
