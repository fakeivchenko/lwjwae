package dev.ivchenko.lwjwae.secret.internal;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.module.ApplicationModule;
import dev.ivchenko.lwjwae.module.ModuleContext;
import dev.ivchenko.lwjwae.secret.Secrets;
import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * The secrets of one application, in the store of credentials of the platform that it runs on, made
 * on their first use.
 *
 * <p>The store is the one of the system, not of a toolkit, so the module binds it itself, whichever
 * backend draws the windows: the Credential Manager on Windows, the keychain on macOS, and the
 * Secret Service on Linux. Nothing is opened before the first use, since most runs of most
 * applications never read a secret.
 */
public final class SecretsModule implements ApplicationModule {
  private Application application;
  private Secrets secrets;

  @Override
  public void start(ModuleContext context) {
    this.application = context.application();
  }

  /**
   * The secrets of the application, under its name.
   *
   * @throws IllegalStateException If the application has no name, or is closed.
   * @throws UnsupportedOperationException If this platform has no store that the module binds.
   */
  public synchronized Secrets secrets() {
    if (this.application.isClosed()) {
      throw new IllegalStateException("The application is closed");
    }
    String name = this.application.parameters().name();
    if (name == null) {
      throw new IllegalStateException("Secrets need the name of the application");
    }
    if (this.secrets == null) {
      this.secrets = SecretsModule.forThisPlatform(name);
    }
    return this.secrets;
  }

  /**
   * The secrets of the application named {@code service} in the store of this platform.
   *
   * @throws UnsupportedOperationException If this platform has no store that the module binds.
   */
  static Secrets forThisPlatform(String service) {
    if (PlatformUtil.isWindows()) {
      return new CredentialManagerSecrets(service);
    }
    if (PlatformUtil.isMacOs()) {
      return new KeychainSecrets(service);
    }
    if (PlatformUtil.isUnixDesktop()) {
      return new SecretServiceSecrets(service);
    }
    throw new UnsupportedOperationException("No store of credentials on " + PlatformUtil.osName());
  }
}
