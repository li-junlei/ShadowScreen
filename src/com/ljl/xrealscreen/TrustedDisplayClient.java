package com.ljl.xrealscreen;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * 经 Shizuku 以 shell(uid 2000) 身份创建 trusted 虚拟屏（参照 android-display-mirror）。
 *
 * 机制：Shizuku 服务端替我们取 display 服务的 binder，ShizukuBinderWrapper 让 transact
 * 以 shell uid 执行——shell 持有 ADD_TRUSTED_DISPLAY 签名权限，因此 TRUSTED/
 * OWN_DISPLAY_GROUP 等 flag 可用，副屏内 app 二次启动 Activity 不会被
 * isCallerAllowedToLaunchOnDisplay 拒绝回主屏。
 *
 * 不绑定 MediaProjection（projection=null）：绑了会 shouldOnlyMirror=true 无法承载 task。
 */
public final class TrustedDisplayClient {

    private static final String TAG = "XREALSubScreen";

    /** Shizuku 服务端是否已启动（binder 已收到） */
    public static boolean isShizukuStarted() {
        try {
            Shizuku.checkSelfPermission();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shizuku 已启动且本 app 已被授权 */
    public static boolean isReady() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 创建 trusted 虚拟屏；调用前提 isReady() && SDK_INT >= 35（VirtualDisplayConfig 公开 API） */
    public static VirtualDisplay create(int width, int height, int dpi, int flags, Surface surface) {
        IBinder raw = SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE);
        IDisplayManager dm = IDisplayManager.Stub.asInterface(new ShizukuBinderWrapper(raw));

        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "ShadowScreen", width, height, dpi)
                .setSurface(surface)
                .setFlags(flags)
                .build();

        IVirtualDisplayCallback callback = new VirtualDisplayCallback();
        int displayId = dm.createVirtualDisplay(config, callback, null, "com.android.shell");
        Log.i(TAG, "trusted virtual display created: " + displayId);

        // 本进程 DisplayManagerGlobal 直接管理生命周期（token 鉴权，不依赖 uid）
        return DisplayManagerGlobal.getInstance()
                .createVirtualDisplayWrapper(config, callback, displayId);
    }

    private static final class VirtualDisplayCallback extends IVirtualDisplayCallback.Stub {
        @Override
        public void onPaused() { }

        @Override
        public void onResumed() { }

        @Override
        public void onStopped() {
            Log.i(TAG, "virtual display stopped by service");
        }
    }

    private TrustedDisplayClient() { }
}
