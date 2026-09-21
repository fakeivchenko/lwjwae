package dev.ivchenko.lwjwae.windows.binding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Finds the installed WebView2 Evergreen runtime.
 *
 * <p>The official entry point is {@code WebView2Loader.dll} from the SDK, a native artifact that
 * this library doesn't ship. The loader itself is thin: it reads the install location of the
 * runtime from the registry and loads {@code EmbeddedBrowserWebView.dll} from there. That's all
 * that this class replicates, and the same lookup answers the cheap question of whether WebView2 is
 * installed, for backend selection.
 */
@UtilityClass
public class WebView2Runtime {
  /**
   * The Edge Update client ID of the runtime. Its {@code ClientState} key records the install
   * directory.
   */
  private final String CLIENT_STATE =
      "\\Microsoft\\EdgeUpdate\\ClientState\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}";

  private final String INSTALL_DIRECTORY_VALUE = "EBWebView";
  private final String LIBRARY = "EBWebView\\x64\\EmbeddedBrowserWebView.dll";

  /** The core library of the runtime, or empty when WebView2 isn't installed. */
  public Optional<Path> library() {
    return Advapi32.readString(
            Advapi32.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\WOW6432Node" + CLIENT_STATE,
            INSTALL_DIRECTORY_VALUE)
        .or(
            () ->
                Advapi32.readString(
                    Advapi32.HKEY_CURRENT_USER, "SOFTWARE" + CLIENT_STATE, INSTALL_DIRECTORY_VALUE))
        .map(directory -> Path.of(directory, LIBRARY))
        .filter(Files::isRegularFile);
  }
}
