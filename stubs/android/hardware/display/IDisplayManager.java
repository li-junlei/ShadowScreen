package android.hardware.display;

import android.media.projection.IMediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;

/** 编译期桩（来自 android-display-mirror hidden-api-stub，compile-only，不打进 dex）。
 *  运行时由 boot classpath 的真实 AIDL 类提供实现。 */
public interface IDisplayManager extends IInterface {
  abstract class Stub extends Binder implements IDisplayManager {
    public static IDisplayManager asInterface(IBinder obj) {
      throw new RuntimeException("Stub!");
    }
  }

  void setUserPreferredDisplayMode(int displayId, Display.Mode mode);

  DisplayInfo getDisplayInfo(int displayId);

  int createVirtualDisplay(
      VirtualDisplayConfig config,
      IVirtualDisplayCallback callback,
      IMediaProjection mediaProjection,
      String packageName);

  int createVirtualDisplay(
      IVirtualDisplayCallback callback,
      IMediaProjection projectionToken,
      String packageName,
      String name,
      int width,
      int height,
      int densityDpi,
      Surface surface,
      int flags,
      String uniqueId);

  boolean requestDisplayPower(int displayId, int state);

  boolean requestDisplayPower(int displayId, boolean state);
}
