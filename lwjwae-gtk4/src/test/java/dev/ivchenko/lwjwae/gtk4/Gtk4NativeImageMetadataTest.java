package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.glib.FreedesktopNotifier;
import dev.ivchenko.lwjwae.glib.GlibDispatcher;
import dev.ivchenko.lwjwae.glib.PortalShortcuts;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.WebKitCookies;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.GioMenus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Soup;
import dev.ivchenko.lwjwae.glib.binding.Unix;
import dev.ivchenko.lwjwae.glib.binding.X11;
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
        GioMenus.class,
        GdkPixbuf.class,
        Unix.class,
        GlibDispatcher.class,
        FreedesktopNotifier.class,
        StatusNotifierTray.class,
        X11.class,
        PortalShortcuts.class,
        Soup.class,
        WebKitCookies.class,
        Gtk.class,
        WebKit.class,
        Gtk4Dispatcher.class,
        Gtk4Window.class,
        Gtk4Dialogs.class,
        Gtk4Application.class,
        Gtk4Clipboard.class,
        Gtk4MenuBuilder.class);
  }
}
