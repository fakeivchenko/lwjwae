package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.windows.binding.Advapi32;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.Kernel32;
import dev.ivchenko.lwjwae.windows.binding.Ole32;
import dev.ivchenko.lwjwae.windows.binding.Shlwapi;
import dev.ivchenko.lwjwae.windows.binding.User32;
import dev.ivchenko.lwjwae.windows.binding.WebView2;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

class WindowsNativeImageMetadataTest extends NativeImageMetadataContractTest {
  @BeforeEach
  void requireWindows() {
    Assumptions.assumeTrue(PlatformUtil.isWindows(), "Binds Win32 libraries: Windows only");
  }

  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-windows/reachability-metadata.json";
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(
        Kernel32.class,
        User32.class,
        Ole32.class,
        Shlwapi.class,
        Advapi32.class,
        Com.class,
        ComCallback.class,
        WebView2.class,
        WindowsDispatcher.class,
        WindowsWindow.class,
        WindowsApplication.class);
  }
}
