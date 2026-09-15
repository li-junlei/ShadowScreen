package android.hardware.display;

import android.view.Display;

/** 编译期桩（compile-only）。运行时真实类提供 createVirtualDisplayWrapper。 */
public class DisplayManagerGlobal {
  public static DisplayManagerGlobal getInstance() {
    throw new RuntimeException("stub!");
  }

  public VirtualDisplay createVirtualDisplayWrapper(
      VirtualDisplayConfig virtualDisplayConfig,
      IVirtualDisplayCallback callbackWrapper,
      int displayId) {
    throw new RuntimeException("stub!");
  }

  public VirtualDisplay createVirtualDisplayWrapper(
      VirtualDisplayConfig virtualDisplayConfig,
      android.content.Context windowContext,
      IVirtualDisplayCallback callbackWrapper,
      int displayId) {
    throw new RuntimeException("stub!");
  }
}
