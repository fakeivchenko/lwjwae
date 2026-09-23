package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * Registers the Cocoa and WKWebView backend with {@link dev.ivchenko.lwjwae.Application}.
 *
 * <p>Every Mac has AppKit and WebKit, so the platform check is the whole test. No library can be
 * missing.
 */
public class MacBackendProvider implements BackendProvider {
  @Override
  public String name() {
    return "cocoa-wkwebview";
  }

  @Override
  public boolean isSupported() {
    return PlatformUtil.isMacOs();
  }

  @Override
  public String unsupportedReason() {
    return "runs on macOS, not on " + PlatformUtil.osName();
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new MacApplication(parameters);
  }
}
