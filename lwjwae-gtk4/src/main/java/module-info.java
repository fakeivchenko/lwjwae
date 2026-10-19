/**
 * The GTK 4 and WebKitGTK 6.0 backend of lwjwae, for Linux. It exports nothing: an application
 * reaches it through {@code dev.ivchenko.lwjwae.Application}, which finds it as a {@code
 * BackendProvider}.
 *
 * <p>Suppressed warnings: {@code module}: the name ends in a digit, as the toolkit that it names
 * does.
 */
@SuppressWarnings("module")
module dev.ivchenko.lwjwae.gtk4 {
  requires transitive dev.ivchenko.lwjwae;
  requires dev.ivchenko.lwjwae.glib;
  requires static lombok;

  provides dev.ivchenko.lwjwae.BackendProvider with
      dev.ivchenko.lwjwae.gtk4.Gtk4BackendProvider;
}
