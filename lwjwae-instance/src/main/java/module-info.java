/**
 * The single instance of an lwjwae application: {@code SingleInstance.create} starts the
 * application unless another process of it runs, and hands the arguments of a second start to the
 * one that runs.
 *
 * <p>It joins every application that finds it as a module of the application, see {@link
 * dev.ivchenko.lwjwae.module.ApplicationModule}.
 */
module dev.ivchenko.lwjwae.instance {
  requires transitive dev.ivchenko.lwjwae;
  requires static lombok;

  exports dev.ivchenko.lwjwae.instance;

  provides dev.ivchenko.lwjwae.module.ApplicationModule with
      dev.ivchenko.lwjwae.instance.internal.InstanceModule;
}
