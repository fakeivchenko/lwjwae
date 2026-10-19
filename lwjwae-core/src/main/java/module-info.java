/**
 * The lwjwae API: applications, windows, the bridge between Java and the page, and the services of
 * the desktop.
 *
 * <p>An application reads the exported packages, and a backend extends their base classes. The
 * helpers of native binding, the bridge protocol, and the utilities go only to the backend modules
 * of the library, so that a change to them is no change to the API; the implementation behind the
 * API, in {@code internal}, {@code instance}, and {@code state}, goes to no module at all.
 *
 * <p>On the class path, which is where most applications run, none of this applies, and every
 * public type is visible. On the module path, the backend calls native code, so the application
 * starts with {@code --enable-native-access=dev.ivchenko.lwjwae} and the module of its backend, and
 * a package of its own that holds the files of its pages is open, {@code opens app}, since a
 * resource of a named module is found by name only in an open package.
 *
 * <p>Suppressed warnings: {@code module}: the backends that the internal packages go to are other
 * modules of this build, which the compiler of this one doesn't see.
 */
@SuppressWarnings("module")
module dev.ivchenko.lwjwae {
  requires java.net.http;
  requires static lombok;

  exports dev.ivchenko.lwjwae;
  exports dev.ivchenko.lwjwae.bridge.codec;
  exports dev.ivchenko.lwjwae.clipboard;
  exports dev.ivchenko.lwjwae.cookie;
  exports dev.ivchenko.lwjwae.dialog;
  exports dev.ivchenko.lwjwae.download;
  exports dev.ivchenko.lwjwae.event;
  exports dev.ivchenko.lwjwae.exception;
  exports dev.ivchenko.lwjwae.menu;
  exports dev.ivchenko.lwjwae.notification;
  exports dev.ivchenko.lwjwae.permission;
  exports dev.ivchenko.lwjwae.rpc;
  exports dev.ivchenko.lwjwae.secret;
  exports dev.ivchenko.lwjwae.shortcut;
  exports dev.ivchenko.lwjwae.store;
  exports dev.ivchenko.lwjwae.taskbar;
  exports dev.ivchenko.lwjwae.theme;
  exports dev.ivchenko.lwjwae.tray;
  // The UI thread that a backend gives the base classes of its application and windows.
  exports dev.ivchenko.lwjwae.ui;
  exports dev.ivchenko.lwjwae.update;
  exports dev.ivchenko.lwjwae.bridge to
      dev.ivchenko.lwjwae.glib,
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4,
      dev.ivchenko.lwjwae.macos,
      dev.ivchenko.lwjwae.windows;
  exports dev.ivchenko.lwjwae.foreign to
      dev.ivchenko.lwjwae.glib,
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4,
      dev.ivchenko.lwjwae.macos,
      dev.ivchenko.lwjwae.windows;
  exports dev.ivchenko.lwjwae.util to
      dev.ivchenko.lwjwae.glib,
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4,
      dev.ivchenko.lwjwae.macos,
      dev.ivchenko.lwjwae.windows;

  uses dev.ivchenko.lwjwae.BackendProvider;
  uses dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
}
