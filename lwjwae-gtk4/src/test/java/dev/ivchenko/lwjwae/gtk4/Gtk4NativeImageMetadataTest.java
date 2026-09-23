package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.gtk4.binding.Dbus;
import dev.ivchenko.lwjwae.gtk4.binding.Gdk;
import dev.ivchenko.lwjwae.gtk4.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.WebKit;
import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

class Gtk4NativeImageMetadataTest extends NativeImageMetadataContractTest {
  @BeforeEach
  void requireLinux() {
    Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Binds GTK libraries: Linux only");
  }

  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-gtk4/reachability-metadata.json";
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(
        Glib.class,
        Gtk.class,
        WebKit.class,
        Gtk4Dispatcher.class,
        Gtk4Window.class,
        Gtk4Application.class,
        Dbus.class,
        Gdk.class,
        Gtk4Notifier.class,
        Gtk4Tray.class);
  }
}
