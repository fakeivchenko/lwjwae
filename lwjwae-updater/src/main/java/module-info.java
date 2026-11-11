/**
 * The updater of lwjwae: finds, downloads, and installs the new versions of an application from a
 * signed manifest, for Java through {@code Updater.of(application)} and for its pages through
 * {@code window.lwjwae.updates}.
 *
 * <p>It joins every application that finds it as a module of the application, see {@link
 * dev.ivchenko.lwjwae.module.ApplicationModule}, and needs the HTTP client of the JDK, which the
 * core does without.
 */
module dev.ivchenko.lwjwae.updater {
  requires transitive dev.ivchenko.lwjwae;
  requires java.net.http;
  requires static lombok;

  exports dev.ivchenko.lwjwae.update;

  provides dev.ivchenko.lwjwae.module.ApplicationModule with
      dev.ivchenko.lwjwae.update.internal.UpdaterModule;
}
