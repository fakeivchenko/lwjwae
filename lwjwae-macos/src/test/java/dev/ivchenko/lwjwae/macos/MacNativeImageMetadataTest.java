package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.WebKit;
import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

class MacNativeImageMetadataTest extends NativeImageMetadataContractTest {
  @BeforeEach
  void requireMacOs() {
    Assumptions.assumeTrue(PlatformUtil.isMacOs(), "Binds AppKit and WebKit: macOS only");
  }

  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-macos/reachability-metadata.json";
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(
        ObjC.class,
        Foundation.class,
        AppKit.class,
        WebKit.class,
        MacDispatcher.class,
        MacWindow.class,
        MacApplication.class,
        MacTray.class);
  }
}
