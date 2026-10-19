/**
 * The GLib, GIO, D-Bus, and WebKitGTK half that the GTK 3 and GTK 4 backends share, which goes to
 * those two modules alone.
 *
 * <p>Suppressed warnings: {@code module}: the backends that the packages go to are other modules of
 * this build, which the compiler of this one doesn't see.
 */
@SuppressWarnings("module")
module dev.ivchenko.lwjwae.glib {
  requires transitive dev.ivchenko.lwjwae;
  requires static lombok;

  exports dev.ivchenko.lwjwae.glib to
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4;
  exports dev.ivchenko.lwjwae.glib.binding to
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4;
  exports dev.ivchenko.lwjwae.glib.util to
      dev.ivchenko.lwjwae.gtk,
      dev.ivchenko.lwjwae.gtk4;
}
