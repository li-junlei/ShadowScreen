package com.ljl.xrealscreen;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 全屏 Presentation：SurfaceView 的 surface 即虚拟屏渲染目标（零中间层直通）。
 * 只负责 surface 生命周期上抛，虚拟屏的创建/参数由 Service 决定
 * （Shizuku trusted 通路 或 DisplayManager 普通通路）。
 *
 * SurfaceView 按目标虚拟屏宽高比居中布局：外接屏的逻辑分辨率可能与物理 mode
 * 宽高比不一致（如 XREAL 逻辑区 1220x2656 竖条 vs 物理 mode 1920x1080 横），
 * 全屏铺满会拉伸变形，等比缩放只留黑边。
 */
public class SubScreenPresentation extends Presentation {

    interface Listener {
        /** surface 就绪或尺寸变化（surfaceCreated 后必有一次 changed） */
        void onSurfaceReady(Surface surface, int width, int height);

        void onSurfaceDestroyed();
    }

    private final Listener mListener;
    /** 目标虚拟屏宽高比（宽/高），<=0 表示未知、退回全屏铺满 */
    private final float mTargetAspect;
    private int mLastWidth;
    private int mLastHeight;

    public SubScreenPresentation(Context outerContext, Display display, Listener listener,
            int targetWidth, int targetHeight) {
        super(outerContext, display, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        mListener = listener;
        mTargetAspect = targetWidth > 0 && targetHeight > 0
                ? (float) targetWidth / targetHeight : 0f;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.black);

        SurfaceView surfaceView = new SurfaceView(getContext()) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                if (mTargetAspect <= 0f) {
                    super.onMeasure(widthSpec, heightSpec);
                    return;
                }
                int maxW = MeasureSpec.getSize(widthSpec);
                int maxH = MeasureSpec.getSize(heightSpec);
                int w = maxW;
                int h = Math.round(w / mTargetAspect);
                if (h > maxH) {
                    h = maxH;
                    w = Math.round(h * mTargetAspect);
                }
                setMeasuredDimension(w, h);
            }
        };
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) { /* 尺寸在 changed 中确定 */ }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (width == mLastWidth && height == mLastHeight && mLastWidth != 0) {
                    return; // 尺寸未变，surface 对象可能更新但内容通路一致
                }
                mLastWidth = width;
                mLastHeight = height;
                mListener.onSurfaceReady(holder.getSurface(), width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mLastWidth = mLastHeight = 0;
                mListener.onSurfaceDestroyed();
            }
        });
        FrameLayout root = new FrameLayout(getContext());
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        setContentView(root);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // surfaceDestroyed 通常先于此触发；此为兜底
        mListener.onSurfaceDestroyed();
    }
}
