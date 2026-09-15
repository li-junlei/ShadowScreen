package com.ljl.xrealscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Presentation;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

/**
 * 前台服务：持有 Presentation + 虚拟屏生命周期。
 *
 * 虚拟屏创建双通路：
 * - Shizuku 通路（TrustedDisplayClient）：shell 身份创建 TRUSTED|OWN_DISPLAY_GROUP 虚拟屏，
 *   副屏内 app 自行再启动 Activity 不会回跳主屏（修复 B站/小红书跳回）。
 * - DisplayManager 普通通路（无 Shizuku 兜底）：仅 PUBLIC|PRESENTATION|OWN_CONTENT_ONLY，
 *   存在跳回主屏的已知限制。
 */
public class SubScreenService extends Service
        implements DisplayManager.DisplayListener, SubScreenPresentation.Listener {

    static final String ACTION_START = "com.ljl.xrealscreen.START";
    static final String ACTION_STOP = "com.ljl.xrealscreen.STOP";
    /** 状态变化广播（app 内）：MainActivity 收到即刷新状态卡与列表 */
    static final String ACTION_STATUS_CHANGED = "com.ljl.xrealscreen.STATUS_CHANGED";
    static final String EXTRA_DISPLAY_ID = "display_id";

    private static final String CHANNEL = "subscreen";
    private static final String TAG = "XREALSubScreen";
    /** @FlaggedApi 常量不在公开 android.jar，按 AOSP 取值（见 android-display-mirror DisplayFlags） */
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    /**
     * Shizuku 通路 flag：android-display-mirror 的 auto() 组合（该设备上经用户验证无跳回）
     * + PRESENTATION（满足 launchAllowedByDisplayPolicy 对 VIRTUAL 类型屏的要求）。
     * 注意不带 OWN_FOCUS（mirror 实测组合不含它也可承载 task）。
     */
    private static final int FLAGS_TRUSTED = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | FLAG_TRUSTED | FLAG_OWN_DISPLAY_GROUP | FLAG_ALWAYS_UNLOCKED
            | FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
            | FLAG_TOUCH_FEEDBACK_DISABLED | FLAG_SUPPORTS_TOUCH;
    /** 普通通路 flag 上限（TRUSTED/OWN_GROUP 需 ADD_TRUSTED_DISPLAY） */
    private static final int FLAGS_NORMAL = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

    /** 供 MainActivity 刷新显示的粗状态 */
    static volatile String sStatus = "未运行";
    static volatile int sTargetDisplayId = -1;

    private DisplayManager mDm;
    private Presentation mPresentation;
    private String mTargetName; // 插拔重连的匹配键
    /** 所选显示器物理 mode（VD 固定按此创建，不跟随 surface 的逻辑尺寸） */
    private int mTargetWidth;
    private int mTargetHeight;

    private VirtualDisplay mVd;
    private boolean mTrusted;  // mVd 是否 Shizuku trusted 通路创建
    /** mVd 当前分辨率（复用挂起 VD 时判断是否需要 resize） */
    private int mVdWidth;
    private int mVdHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        mDm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        mDm.registerDisplayListener(this, null);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "副屏服务", NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            teardown();
            stopSelf();
            return START_NOT_STICKY;
        }
        // startForegroundService 语义：必须无条件先进入前台
        startAsForeground("正在启动副屏…");
        int displayId = intent != null
                ? intent.getIntExtra(EXTRA_DISPLAY_ID, -1) : -1;
        if (displayId < 0 || !showPresentation(displayId)) {
            sStatus = "目标显示器不可用";
            updateNotification(sStatus);
        }
        return START_STICKY;
    }

    private void startAsForeground(String text) {
        startForeground(1, buildNotification(text));
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(1, buildNotification(text));
        postStatusChanged();
    }

    /** 通知 UI 状态已变化（无人接收时无害） */
    private void postStatusChanged() {
        sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(getPackageName()));
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_shadow)
                .setContentTitle("ShadowScreen")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    /** 在目标外接屏上显示 Presentation；已存在则复用（修 displayId 漂移） */
    private synchronized boolean showPresentation(int displayId) {
        Display display = mDm.getDisplay(displayId);
        if (display == null || !display.isValid()) {
            Log.w(TAG, "display " + displayId + " not available");
            return false;
        }
        if (mPresentation != null && mPresentation.isShowing()) {
            Log.i(TAG, "presentation already showing, reuse");
            return true;
        }
        mTargetName = display.getName();
        sTargetDisplayId = displayId;
        Display.Mode mode = display.getMode();
        mTargetWidth = mode.getPhysicalWidth();
        mTargetHeight = mode.getPhysicalHeight();
        try {
            mPresentation = new SubScreenPresentation(this, display, this,
                    mTargetWidth, mTargetHeight);
            mPresentation.show();
            return true;
        } catch (RuntimeException e) {
            // display 失效等场景 show() 抛运行时异常（InvalidDisplayException 已从 SDK 移除）
            Log.e(TAG, "presentation show failed", e);
            mPresentation = null;
            return false;
        }
    }

    private synchronized void teardown() {
        if (mPresentation != null) {
            try {
                mPresentation.dismiss(); // 触发 onSurfaceDestroyed → releaseVd
            } catch (Exception e) {
                Log.w(TAG, "dismiss: " + e);
            }
            mPresentation = null;
        }
        releaseVd();
        sTargetDisplayId = -1;
        sStatus = "未运行";
        postStatusChanged();
    }

    // ---- 虚拟屏创建/管理（SubScreenPresentation.Listener） ----

    @Override
    public synchronized void onSurfaceReady(Surface surface, int width, int height) {
        // VD 分辨率固定取所选显示器物理 mode；surface 只是输出目标（SF 等比缩放）
        int vdWidth = mTargetWidth > 0 ? mTargetWidth : width;
        int vdHeight = mTargetHeight > 0 ? mTargetHeight : height;
        int dpi = dpiFor(vdWidth);
        if (mVd == null) {
            if (Build.VERSION.SDK_INT >= 35 && TrustedDisplayClient.isReady()) {
                try {
                    mVd = TrustedDisplayClient.create(vdWidth, vdHeight, dpi, FLAGS_TRUSTED, surface);
                    mTrusted = true;
                    sStatus = "虚拟屏 Display " + mVd.getDisplay().getDisplayId()
                            + "（" + vdWidth + "x" + vdHeight + "，Shizuku trusted 模式）";
                    Log.i(TAG, sStatus);
                } catch (Throwable e) {
                    Log.w(TAG, "shizuku create failed, fallback to normal", e);
                    mVd = null;
                }
            }
            if (mVd == null) {
                mVd = mDm.createVirtualDisplay(
                        "ShadowScreen", vdWidth, vdHeight, dpi, surface, FLAGS_NORMAL);
                mTrusted = false;
                sStatus = mVd != null
                        ? "虚拟屏（普通模式，Shizuku 不可用）" : "虚拟屏创建失败";
                Log.i(TAG, "normal mode: " + sStatus);
            }
            updateNotification(sStatus);
            mVdWidth = vdWidth;
            mVdHeight = vdHeight;
        } else {
            // surface 重建/插拔重连：换输出目标复用同一 VD（displayId 不变），
            // 仅当接的是不同显示器（物理 mode 变了）才 resize
            mVd.setSurface(surface);
            if (vdWidth != mVdWidth || vdHeight != mVdHeight) {
                mVd.resize(vdWidth, vdHeight, dpi);
                mVdWidth = vdWidth;
                mVdHeight = vdHeight;
            }
            sStatus = "虚拟屏 Display " + mVd.getDisplay().getDisplayId()
                    + "（" + vdWidth + "x" + vdHeight
                    + (mTrusted ? "，Shizuku trusted 模式）" : "）");
            updateNotification(sStatus);
        }
    }

    @Override
    public synchronized void onSurfaceDestroyed() {
        // 挂起而非销毁：保留 VD 让 displayId 不随插拔递增，重连后在
        // onSurfaceReady 的复用分支接回。真正销毁只在 teardown（用户停止）。
        if (mVd != null) {
            try {
                mVd.setSurface(null);
            } catch (Exception e) {
                Log.w(TAG, "detach surface: " + e);
            }
        }
    }

    private void releaseVd() {
        if (mVd != null) {
            try {
                mVd.release();
            } catch (Exception e) {
                Log.w(TAG, "release: " + e);
            }
            mVd = null;
        }
        mTrusted = false;
    }

    /** 外接屏 EDID 密度不可靠，按像素宽分档取舒适 DPI */
    private static int dpiFor(int width) {
        if (width >= 2560) return 320;
        if (width >= 1920) return 240;
        return 160;
    }

    // ---- DisplayListener：插拔自动重连 ----

    @Override
    public void onDisplayAdded(int displayId) {
        if (mTargetName == null) return;
        Display d = mDm.getDisplay(displayId);
        if (d != null && mTargetName.equals(d.getName())) {
            Log.i(TAG, "target display re-added as " + displayId + ", rebuilding");
            showPresentation(displayId);
        }
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        if (displayId == sTargetDisplayId) {
            // DP 链路抖动会产生 removed→added 快速成对事件（displayId 可能变化）：
            // 只释放资源、保持服务存活监听 onDisplayAdded 里的同名重建
            Log.i(TAG, "target display removed, releasing and waiting for reconnect");
            if (mPresentation != null) {
                try {
                    mPresentation.dismiss();
                } catch (Exception e) {
                    Log.w(TAG, "dismiss: " + e);
                }
                mPresentation = null;
            }
            // 不 release VD：保持挂起等 onDisplayAdded 同名重连后复用（id 不变）
            sTargetDisplayId = -1;
            sStatus = "显示器断开，等待重连…";
            updateNotification(sStatus);
        }
    }

    @Override
    public void onDisplayChanged(int displayId) { /* Presentation 自适应 */ }

    @Override
    public void onDestroy() {
        teardown();
        mDm.unregisterDisplayListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
