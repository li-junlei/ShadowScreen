package com.ljl.xrealscreen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** 虚拟屏底层任务：投送的应用覆盖它，退出应用后恢复提示。 */
public class IdleScreenActivity extends Activity {
    static final String ACTION_CLOSE = "com.ljl.xrealscreen.CLOSE_IDLE_SCREEN";
    private final BroadcastReceiver mCloseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finishAndRemoveTask();
        }
    };
    private boolean mRegistered;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 系统若把任务回退到主屏，立即退出，避免遮挡手机上的应用。
        if (getDisplay() == null || getDisplay().getDisplayId() !=
                getIntent().getIntExtra(SubScreenService.EXTRA_DISPLAY_ID, -1)) {
            finishAndRemoveTask();
            return;
        }
        registerReceiver(mCloseReceiver, new IntentFilter(ACTION_CLOSE),
                Context.RECEIVER_NOT_EXPORTED);
        mRegistered = true;
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        TextView message = new TextView(this);
        message.setBackgroundColor(Color.rgb(28, 30, 34));
        message.setTextColor(Color.rgb(190, 194, 202));
        message.setTextSize(24);
        message.setGravity(Gravity.CENTER);
        int padding = Math.round(32 * getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding, padding, padding);
        message.setText("副屏已就绪\n请通过 Extend 等应用向此屏幕投送内容");
        setContentView(message);
    }

    @Override
    public void onBackPressed() {
        // 空闲提示保留在任务底层。
    }

    @Override
    protected void onDestroy() {
        if (mRegistered) unregisterReceiver(mCloseReceiver);
        super.onDestroy();
    }
}
