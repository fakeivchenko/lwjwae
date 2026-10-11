/**
 * The Cocoa and WKWebView backend of lwjwae, for macOS. It exports nothing: an application reaches
 * it through {@code dev.ivchenko.lwjwae.Application}, which finds it as a {@code BackendProvider}.
 */
module dev.ivchenko.lwjwae.macos {
  requires transitive dev.ivchenko.lwjwae;
  requires static lombok;

  provides dev.ivchenko.lwjwae.BackendProvider with
      dev.ivchenko.lwjwae.macos.MacBackendProvider;
}
