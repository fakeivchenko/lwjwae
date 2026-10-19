/**
 * The secrets of an lwjwae application, such as tokens and passwords, in the store of credentials
 * of the system, through {@code Secrets.of(application)}.
 *
 * <p>It joins every application that finds it as a module of the application, see {@link
 * dev.ivchenko.lwjwae.module.ApplicationModule}, and binds the store of each platform itself: the
 * Credential Manager, the keychain, and the Secret Service, none of which needs a toolkit.
 */
module dev.ivchenko.lwjwae.secrets {
  requires transitive dev.ivchenko.lwjwae;
  requires static lombok;

  exports dev.ivchenko.lwjwae.secret;

  provides dev.ivchenko.lwjwae.module.ApplicationModule with
      dev.ivchenko.lwjwae.secret.internal.SecretsModule;
}
