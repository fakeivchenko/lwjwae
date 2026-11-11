package dev.ivchenko.lwjwae.module;

/**
 * A part of lwjwae that ships as a module of its own, such as the updater, and joins every
 * application that finds it on the class path or the module path.
 *
 * <p>The core stays small this way: an application that doesn't use a feature doesn't carry it, and
 * a feature can depend on what the core never should, such as an HTTP client. A module registers
 * its provider in {@code META-INF/services/dev.ivchenko.lwjwae.module.ApplicationModule}, or with
 * {@code provides} in its {@code module-info}; {@link dev.ivchenko.lwjwae.Application#create} makes
 * an instance of each for the new application, so the instance may keep the state of that
 * application. The API of the module reaches it through {@link
 * dev.ivchenko.lwjwae.Application#module(Class)}.
 *
 * <p>A module has a public constructor without arguments, as a service does.
 */
public interface ApplicationModule {
  /**
   * Starts the module for the application of {@code context}, once, before the application opens
   * its first window. A page call that the module answers and the part of {@code window.lwjwae}
   * that it adds are registered here.
   */
  void start(ModuleContext context);

  /**
   * Stops the module, once, as the application quits, after its windows closed. The default does
   * nothing.
   */
  default void stop() {}
}
