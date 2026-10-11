#!/bin/bash
# P2 app 命令行构建（无 gradle）。依赖: JDK17 + SDK build-tools 36.1.0 + platform android-36
# stubs/ 是隐藏 API 编译桩（compile-only，不打进 dex，运行时由框架提供）
set -e
cd "$(dirname "$0")"

SDK=/c/Users/LiJunlei/AppData/Local/Android/Sdk
BT=$SDK/build-tools/36.1.0
PLATFORM=$SDK/platforms/android-36/android.jar
LIBS="libs/shizuku-api.jar;libs/shizuku-aidl.jar;libs/shizuku-shared.jar;libs/shizuku-provider.jar"

# Keep the build directory itself: Windows readers can hold a directory handle.
mkdir -p build
rm -rf build/* gen classes stub_classes dexout
mkdir -p build gen classes stub_classes dexout

echo "[1/7] aapt2 compile"
"$BT/aapt2" compile --dir res -o build/res.zip

echo "[2/7] aapt2 link"
"$BT/aapt2" link -o build/app.unsigned.apk -I "$PLATFORM" \
    --manifest AndroidManifest.xml -R build/res.zip --java gen \
    --min-sdk-version 30 --target-sdk-version 34 \
    --auto-add-overlay

echo "[3/7] javac stubs (compile-only)"
javac -encoding UTF-8 --release 8 -classpath "$PLATFORM" -d stub_classes \
    stubs/android/hardware/display/*.java \
    stubs/android/hardware/input/*.java \
    stubs/android/view/DisplayInfo.java \
    stubs/android/media/projection/IMediaProjection.java

echo "[4/7] javac app (argfile 规避 MSYS 对 ; 分隔 classpath 的转换)"
CP="$(cygpath -w "$PLATFORM");$(cygpath -w libs/shizuku-api.jar);$(cygpath -w libs/shizuku-aidl.jar);$(cygpath -w libs/shizuku-shared.jar);$(cygpath -w libs/shizuku-provider.jar);$(cygpath -w libs/hiddenapibypass.jar);$(cygpath -w stub_classes)"
printf -- "-classpath\n%s\n" "$CP" > build/javac_opts.txt
javac -encoding UTF-8 --release 8 @build/javac_opts.txt -d classes \
    gen/com/ljl/xrealscreen/R.java src/com/ljl/xrealscreen/*.java

echo "[5/7] d8 (app + Shizuku)"
jar cf build/app-classes.jar -C classes .
"$BT/d8.bat" --release --lib "$PLATFORM" --classpath stub_classes --min-api 30 \
    --output dexout build/app-classes.jar \
    libs/shizuku-api.jar libs/shizuku-aidl.jar libs/shizuku-shared.jar libs/shizuku-provider.jar libs/hiddenapibypass.jar

(cd dexout && jar uf ../build/app.unsigned.apk classes.dex)

echo "[7/7] align + sign"
"$BT/zipalign" -f 4 build/app.unsigned.apk build/app.aligned.apk
KEYSTORE=debug.keystore
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
        -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner.bat" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --key-pass pass:android --out build/app.apk build/app.aligned.apk

echo "DONE: $(pwd)/build/app.apk"
