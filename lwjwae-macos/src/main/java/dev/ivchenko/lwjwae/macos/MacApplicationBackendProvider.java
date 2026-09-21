package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * Registers the Cocoa and WKWebView backend with {@link dev.ivchenko.lwjwae.Application}.
 *
 * <p>Every Mac has AppKit and WebKit, so the platform check is the whole test. No library can be
 * missing.
 */
public class MacApplicationBackendProvider implements ApplicationBackendProvider {
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
  public ApplicationBackend create(ApplicationParameters parameters) {
    return new MacApplicationBackend(parameters);
  }
}
