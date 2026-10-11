package com.ljl.xrealscreen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Primary-display controller; the secondary app retains its own task. */
public class GamepadActivity extends Activity {
    private GamepadClient client;
    private TextView status;
    private int targetId;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!SubScreenService.sInputReady || targetId != SubScreenService.sVirtualDisplayId) {
                status.setText("副屏已断开或停止；重连后请重新打开手柄");
                stopInput();
            }
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        targetId = getIntent().getIntExtra(SubScreenService.EXTRA_DISPLAY_ID, -1);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 21, 28));
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(16, 0, 16, 0);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.addView(status, new LinearLayout.LayoutParams(0,
                (int)(52 * getResources().getDisplayMetrics().density), 1));
        Button exit = new Button(this);
        exit.setText("退出转发");
        exit.setOnClickListener(v -> finish());
        bar.addView(exit);
        root.addView(bar);
        TextView help = new TextView(this);
        help.setText("GKME 转发模式\n\n保持此页在主屏前台，在它上方显示 GKME 悬浮手柄。\n"
                + "将游戏通过 Extend 投送到副屏，再操作手柄。\n\n"
                + "如果按键没有到达此页，GKME 的当前输入方式无法由此模式转发。");
        help.setTextColor(Color.LTGRAY);
        help.setTextSize(20);
        help.setGravity(android.view.Gravity.CENTER);
        root.addView(help, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        registerReceiver(receiver, new IntentFilter(SubScreenService.ACTION_STATUS_CHANGED),
                Context.RECEIVER_NOT_EXPORTED);
        client = new GamepadClient(this, targetId, (message, ready) -> {
            status.setText(message);
        });
        client.start();
    }

    private void stopInput() {
        if (client != null) client.close();
    }

    @Override protected void onStop() {
        stopInput();
        finish();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopInput();
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        boolean gamepad = (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean dpad = (event.getSource() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
        if (client != null && (gamepad || dpad) && client.forward(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (client != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                && client.forward(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

}
