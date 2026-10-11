package com.ljl.xrealscreen;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/** Forwards Android controller events received by the primary-screen page. */
final class GamepadClient implements AutoCloseable {
    interface Listener { void status(String message, boolean ready); }
    private final Context context;
    private final int displayId;
    private final Listener listener;
    private final Handler main = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Integer, KeyEvent> pressed = new HashMap<>();
    private final Map<Integer, MotionEvent> motions = new HashMap<>();
    private IInputManager input;
    private Method setDisplay, getDisplay;
    private volatile boolean closed, ready;
    private final Shizuku.OnBinderDeadListener binderDead = () -> fail("Shizuku 已断开，请重新打开转发页");

    GamepadClient(Context context, int displayId, Listener listener) {
        this.context = context; this.displayId = displayId; this.listener = listener;
    }
    void start() {
        try {
            if (!targetAvailable() || !TrustedDisplayClient.isReady())
                throw new IllegalStateException("请先启动 Shizuku trusted 副屏");
            setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
            getDisplay = InputEvent.class.getMethod("getDisplayId");
            input = IInputManager.Stub.asInterface(new ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)));
            Shizuku.addBinderDeadListener(binderDead);
            ready = true;
            listener.status("转发到 Display " + displayId + " · 在主屏显示 GKME 悬浮手柄", true);
        } catch (Exception e) { fail(e.getMessage()); }
    }
    private boolean targetAvailable() {
        return displayId > 0 && displayId == SubScreenService.sVirtualDisplayId
                && SubScreenService.sInputReady
                && context.getSystemService(DisplayManager.class).getDisplay(displayId) != null;
    }
    boolean isReady() { return ready && !closed; }
    boolean forward(InputEvent original) {
        if (!isReady()) return false;
        try { if ((int) getDisplay.invoke(original) == displayId) return true; }
        catch (Exception e) { fail("无法读取输入目标屏幕：" + e.getMessage()); return true; }
        if (original instanceof KeyEvent) send(new KeyEvent((KeyEvent) original));
        else send(MotionEvent.obtain((MotionEvent) original));
        return true;
    }

    private void send(InputEvent event) {
        if (!isReady()) { recycle(event); return; }
        worker.execute(() -> {
            try {
                if (!targetAvailable()) { fail("副屏已断开，输入已停止"); return; }
                if (event instanceof KeyEvent) {
                    KeyEvent key = (KeyEvent) event;
                    if (key.getAction() == KeyEvent.ACTION_DOWN) {
                        KeyEvent previous = pressed.get(key.getKeyCode());
                        if (previous != null) {
                            key = new KeyEvent(previous.getDownTime(), key.getEventTime(), key.getAction(),
                                    key.getKeyCode(), key.getRepeatCount(), key.getMetaState(), key.getDeviceId(),
                                    key.getScanCode(), key.getFlags(), key.getSource());
                        }
                        pressed.put(key.getKeyCode(), key);
                    } else {
                        KeyEvent previous = pressed.remove(key.getKeyCode());
                        if (previous == null) return;
                        key = new KeyEvent(previous.getDownTime(), key.getEventTime(), KeyEvent.ACTION_UP,
                                key.getKeyCode(), 0, key.getMetaState(), key.getDeviceId(), key.getScanCode(),
                                key.getFlags(), key.getSource());
                    }
                    inject(key);
                } else {
                    MotionEvent motion = (MotionEvent) event;
                    MotionEvent old = motions.put(event.getDeviceId(), MotionEvent.obtain(motion));
                    if (old != null) old.recycle();
                    inject(motion);
                }
            } catch (Exception e) { fail("副屏输入失败：" + e.getMessage()); }
            finally { recycle(event); }
        });
    }

    private void inject(InputEvent event) throws Exception {
        setDisplay.invoke(event, displayId);
        if (!input.injectInputEvent(event, 0)) throw new IllegalStateException("系统拒绝输入");
    }

    private void fail(String message) {
        ready = false;
        main.post(() -> {
            if (!closed) {
                listener.status(message == null ? "手柄不可用" : message, false);
                close();
            }
        });
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ready = false;
        Shizuku.removeBinderDeadListener(binderDead);
        worker.execute(() -> {
            try {
                // Release even after display removal; never fall back to Display 0.
                for (KeyEvent down : pressed.values()) {
                    try { inject(new KeyEvent(down.getDownTime(), SystemClock.uptimeMillis(),
                            KeyEvent.ACTION_UP, down.getKeyCode(), 0, 0, down.getDeviceId(),
                            down.getScanCode(), 0, down.getSource())); } catch (Exception ignored) { }
                }
                for (MotionEvent motion : motions.values()) {
                    MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties();
                    motion.getPointerProperties(0, prop);
                    MotionEvent neutral = MotionEvent.obtain(0, SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_MOVE, 1, new MotionEvent.PointerProperties[]{prop},
                            new MotionEvent.PointerCoords[]{new MotionEvent.PointerCoords()},
                            0, 0, 1, 1, motion.getDeviceId(), 0, motion.getSource(), 0);
                    try { inject(neutral); } catch (Exception ignored) { }
                    neutral.recycle();
                    motion.recycle();
                }
                pressed.clear();
                motions.clear();
            } catch (Exception ignored) { }
        });
        worker.shutdown();
    }

    private static void recycle(InputEvent event) {
        if (event instanceof MotionEvent) ((MotionEvent) event).recycle();
    }
}
