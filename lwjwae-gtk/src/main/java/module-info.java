/**
 * The GTK 3 and WebKitGTK 4.1 backend of lwjwae, for Linux. It exports nothing: an application
 * reaches it through {@code dev.ivchenko.lwjwae.Application}, which finds it as a {@code
 * BackendProvider}.
 */
module dev.ivchenko.lwjwae.gtk {
  requires transitive dev.ivchenko.lwjwae;
  requires dev.ivchenko.lwjwae.glib;
  requires static lombok;

  provides dev.ivchenko.lwjwae.BackendProvider with
      dev.ivchenko.lwjwae.gtk.GtkBackendProvider;
}
