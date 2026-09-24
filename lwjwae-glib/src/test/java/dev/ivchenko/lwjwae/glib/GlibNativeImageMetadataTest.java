package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Unix;
import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

class GlibNativeImageMetadataTest extends NativeImageMetadataContractTest {
  @BeforeEach
  void requireLinux() {
    Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Binds GLib libraries: Linux only");
  }

  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-glib/reachability-metadata.json";
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(
        Glib.class,
        Dbus.class,
        GdkPixbuf.class,
        Unix.class,
        GlibDispatcher.class,
        FreedesktopNotifier.class,
        StatusNotifierTray.class);
  }
}
