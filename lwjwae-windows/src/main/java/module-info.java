/**
 * The Win32 and WebView2 backend of lwjwae, for Windows. It exports nothing: an application reaches
 * it through {@code dev.ivchenko.lwjwae.Application}, which finds it as a {@code BackendProvider}.
 */
module dev.ivchenko.lwjwae.windows {
  requires transitive dev.ivchenko.lwjwae;
  requires static lombok;

  provides dev.ivchenko.lwjwae.BackendProvider with
      dev.ivchenko.lwjwae.windows.WindowsBackendProvider;
}
