package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.glib.FreedesktopNotifier;
import dev.ivchenko.lwjwae.glib.GlibDispatcher;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Unix;
import dev.ivchenko.lwjwae.gtk.binding.AppIndicator;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

class GtkNativeImageMetadataTest extends NativeImageMetadataContractTest {
  @BeforeEach
  void requireLinux() {
    Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Binds GTK libraries: Linux only");
  }

  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-gtk/reachability-metadata.json";
  }

  @Override
  protected List<String> inheritedMetadataPaths() {
    return List.of(
        "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-glib/reachability-metadata.json");
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(
        // The GLib module, whose metadata this one inherits.
        Glib.class,
        Dbus.class,
        GdkPixbuf.class,
        Unix.class,
        GlibDispatcher.class,
        FreedesktopNotifier.class,
        StatusNotifierTray.class,
        Gdk.class,
        Gtk.class,
        WebKit.class,
        AppIndicator.class,
        GtkDispatcher.class,
        GtkWindow.class,
        GtkDialogs.class,
        GtkApplication.class,
        GtkTray.class);
  }
}
