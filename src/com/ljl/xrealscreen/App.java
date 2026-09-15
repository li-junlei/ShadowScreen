package com.ljl.xrealscreen;

import android.app.Application;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * 进程入口：豁免 hidden API 限制。
 *
 * IVirtualDisplayCallback$Stub / IDisplayManager / DisplayManagerGlobal 均为
 * hidden API（MAX-TARGET-O 级），targetSdk>26 直接链接会 NoSuchMethodError。
 * 豁免后 TrustedDisplayClient 的 compile-only 桩链接才能落到框架真实类。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HiddenApiBypass.setHiddenApiExemptions("L");
    }
}
