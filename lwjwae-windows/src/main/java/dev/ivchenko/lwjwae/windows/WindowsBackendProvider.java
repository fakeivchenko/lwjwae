package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.windows.binding.WebView2Runtime;

/**
 * Registers the WebView2 backend with {@link dev.ivchenko.lwjwae.Application}.
 *
 * <p>The provider is discovered through {@code META-INF/services}. On any other operating system,
 * it steps aside before it loads a Windows library, so the JAR file is harmless on a Linux or macOS
 * classpath.
 */
public class WindowsBackendProvider implements BackendProvider {
  @Override
  public String name() {
    return "win32-webview2";
  }

  @Override
  public boolean isSupported() {
    if (!PlatformUtil.isWindows()) {
      return false;
    }
    return WebView2Runtime.library().isPresent();
  }

  @Override
  public String unsupportedReason() {
    if (!PlatformUtil.isWindows()) {
      return "runs on Windows, not on " + PlatformUtil.osName();
    }
    return "can't find the WebView2 Runtime; it ships with Windows 11 and Edge, and the Evergreen"
        + " installer is at https://developer.microsoft.com/microsoft-edge/webview2/";
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new WindowsApplication(parameters);
  }
}
