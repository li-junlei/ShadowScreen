package com.ljl.xrealscreen;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import rikka.shizuku.Shizuku;

/**
 * 主界面：深色卡片风格（参照 android-display-mirror / extend 的观感）。
 *
 * 所有状态事件驱动刷新，无需切屏重进：
 * - 副屏服务状态：SubScreenService 每次 updateNotification 时发 ACTION_STATUS_CHANGED
 * - 显示器插拔：本页自有 DisplayListener
 * - Shizuku：授权结果 + binder 收到/断开 三个监听
 */
public class MainActivity extends Activity {

    private static final int REQ_SHIZUKU = 1001;

    private DisplayManager mDm;
    private LinearLayout mList;
    private TextView mServiceStatus;
    private TextView mShizukuStatus;
    private TextView mEmpty;
    private Button mShizukuButton;
    private View mDotService;
    private View mDotShizuku;

    // ---- 事件源（回调均在主线程），统一走 refreshAll() ----

    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAll();
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) { refreshAll(); }

                @Override
                public void onDisplayRemoved(int displayId) { refreshAll(); }

                @Override
                public void onDisplayChanged(int displayId) { /* 仅属性变化，不重建卡片 */ }
            };

    private final Shizuku.OnRequestPermissionResultListener mPermListener =
            (requestCode, result) -> refreshAll();
    private final Shizuku.OnBinderReceivedListener mBinderListener = () -> refreshAll();
    private final Shizuku.OnBinderDeadListener mBinderDeadListener = () -> refreshAll();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDm = (DisplayManager) getSystemService(DISPLAY_SERVICE);

        mDotService = findViewById(R.id.dot_service);
        mDotShizuku = findViewById(R.id.dot_shizuku);
        mServiceStatus = findViewById(R.id.tv_service_status);
        mShizukuStatus = findViewById(R.id.tv_shizuku_status);
        mShizukuButton = findViewById(R.id.btn_shizuku);
        mEmpty = findViewById(R.id.tv_empty);
        mList = findViewById(R.id.list_displays);

        mShizukuButton.setOnClickListener(v -> {
            try {
                Shizuku.requestPermission(REQ_SHIZUKU);
            } catch (Throwable t) {
                Toast.makeText(this, "Shizuku 未响应：" + t, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.btn_stop).setOnClickListener(v ->
                startService(new Intent(this, SubScreenService.class)
                        .setAction(SubScreenService.ACTION_STOP)));
        findViewById(R.id.btn_gamepad_forward).setOnClickListener(v -> openGamepad());

        IntentFilter f = new IntentFilter(SubScreenService.ACTION_STATUS_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mStatusReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mStatusReceiver, f);
        }
        mDm.registerDisplayListener(mDisplayListener, null);
        Shizuku.addRequestPermissionResultListener(mPermListener);
        Shizuku.addBinderReceivedListenerSticky(mBinderListener);
        Shizuku.addBinderDeadListener(mBinderDeadListener);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mStatusReceiver);
        mDm.unregisterDisplayListener(mDisplayListener);
        Shizuku.removeRequestPermissionResultListener(mPermListener);
        Shizuku.removeBinderReceivedListener(mBinderListener);
        Shizuku.removeBinderDeadListener(mBinderDeadListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    /** 统一刷新：状态卡 + Shizuku 区 + 显示器列表 */
    private void refreshAll() {
        String s = SubScreenService.sStatus;
        mServiceStatus.setText(s);
        setDot(mDotService, s.contains("trusted") ? R.color.ok
                : "未运行".equals(s) ? R.color.text_secondary : R.color.warn);

        refreshShizuku();
        boolean canControl = SubScreenService.sInputReady && TrustedDisplayClient.isReady();
        findViewById(R.id.btn_gamepad_forward).setEnabled(canControl);
        rebuildList();
    }

    private void refreshShizuku() {
        boolean started = TrustedDisplayClient.isShizukuStarted();
        boolean granted = started && TrustedDisplayClient.isReady();
        if (granted) {
            mShizukuStatus.setText("已授权 · trusted 副屏可用");
            setDot(mDotShizuku, R.color.ok);
            mShizukuButton.setVisibility(View.GONE);
        } else if (started) {
            mShizukuStatus.setText("已启动，未授权本应用");
            setDot(mDotShizuku, R.color.warn);
            mShizukuButton.setVisibility(View.VISIBLE);
        } else {
            mShizukuStatus.setText("未启动");
            setDot(mDotShizuku, R.color.error);
            mShizukuButton.setVisibility(View.GONE);
        }
    }

    private void setDot(View dot, int colorRes) {
        dot.setBackgroundTintList(getColorStateList(colorRes));
    }

    private void rebuildList() {
        mList.removeAllViews();
        Display[] displays = mDm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        mEmpty.setVisibility(displays.length == 0 ? View.VISIBLE : View.GONE);
        for (Display d : displays) {
            mList.addView(makeCard(d, d.getDisplayId() == SubScreenService.sTargetDisplayId));
        }
    }

    /** 单个显示器卡片：名称 + Display id/分辨率 + 运行中标记 */
    private View makeCard(Display d, boolean running) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(running
                ? R.drawable.bg_card_running : R.drawable.bg_card_ripple);
        int pad = dp(18);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> startSubScreen(d.getDisplayId()));

        TextView name = new TextView(this);
        name.setText(d.getName());
        name.setTextColor(getColor(R.color.text_primary));
        name.setTextSize(17);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);

        TextView meta = new TextView(this);
        meta.setText(String.format(Locale.CHINA, "Display %d · %dx%d%s",
                d.getDisplayId(), d.getMode().getPhysicalWidth(),
                d.getMode().getPhysicalHeight(), running ? " · 运行中" : ""));
        meta.setTextColor(getColor(running ? R.color.accent : R.color.text_secondary));
        meta.setTextSize(13);

        card.addView(name);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(4);
        card.addView(meta, metaLp);
        return card;
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void openGamepad() {
        if (!SubScreenService.sInputReady || !TrustedDisplayClient.isReady()) {
            Toast.makeText(this, "请先启动 Shizuku trusted 副屏", Toast.LENGTH_LONG).show();
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
        startActivity(new Intent(this, GamepadActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SubScreenService.EXTRA_DISPLAY_ID, SubScreenService.sVirtualDisplayId), options.toBundle());
    }

    private void startSubScreen(int displayId) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予『显示在其他应用上层』权限", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!TrustedDisplayClient.isReady()) {
            Toast.makeText(this, "Shizuku 未授权，将使用普通模式（副屏内切窗口会跳回主屏）",
                    Toast.LENGTH_LONG).show();
        }
        startForegroundService(new Intent(this, SubScreenService.class)
                .setAction(SubScreenService.ACTION_START)
                .putExtra(SubScreenService.EXTRA_DISPLAY_ID, displayId));
    }
}
