package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.instance.internal.InstanceLock;
import dev.ivchenko.lwjwae.instance.internal.InstanceModule;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An application that runs once per user: a second start hands its arguments to the process that
 * runs and ends, having opened nothing.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *   ApplicationParameters parameters = ApplicationParameters.builder().name("notes").build();
 *   Optional<Application> created = SingleInstance.create(parameters, args);
 *   if (created.isEmpty()) {
 *     return;
 *   }
 *   try (Application application = created.get()) {
 *     SingleInstance.of(application).onSecondInstance(start -> openFiles(start.arguments()));
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p>The processes find each other through a socket named after {@link
 * ApplicationParameters#name()} in a directory of the user, so two users each run their own. The
 * running process brings its oldest window to the front for every second start, and hands the links
 * and the files among its arguments to {@link Application#onOpen}.
 */
public final class SingleInstance {
  private final InstanceModule module;

  private SingleInstance(InstanceModule module) {
    this.module = module;
  }

  /**
   * Creates the application unless another process of it runs already. That one gets the arguments
   * of this process, its oldest window comes to the front, and its {@link #onSecondInstance}
   * listeners hear of it; this process gets nothing and should end, having opened nothing, not even
   * the toolkit.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The socket is in the temporary directory of the user, which needs Windows 10 or
   *       later. The window comes forward from the background too.
   *   <li>macOS: The socket is in the temporary directory of the user, or in {@code /tmp} when that
   *       path leaves no room for it.
   *   <li>Linux, GTK 3: The socket is in {@code $XDG_RUNTIME_DIR}. X11: as described. Wayland: the
   *       window comes back but may only ask for attention.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @param parameters The parameters of the application, with a name.
   * @param arguments The arguments of this process, as {@code main} received them.
   * @return The application, or empty when another process of it runs and took over.
   * @throws IllegalArgumentException If {@code parameters} has no name.
   * @throws UncheckedIOException If the processes can't reach each other.
   * @throws BackendNotAvailableException If no backend on the classpath supports this machine.
   */
  public static Optional<Application> create(
      ApplicationParameters parameters, String... arguments) {
    Objects.requireNonNull(parameters, "parameters");
    if (parameters.name() == null) {
      throw new IllegalArgumentException("A single instance needs the name of the application");
    }
    SecondInstanceEvent start =
        new SecondInstanceEvent(List.of(arguments), Path.of("").toAbsolutePath());
    Optional<InstanceLock> claimed = InstanceLock.claim(parameters.name(), start);
    if (claimed.isEmpty()) {
      return Optional.empty();
    }
    InstanceLock lock = claimed.get();
    Application application;
    try {
      application = Application.create(parameters, arguments);
    } catch (RuntimeException | Error e) {
      try (lock) {
        throw e;
      }
    }
    application.module(InstanceModule.class).serve(lock);
    return Optional.of(application);
  }

  /**
   * The single instance of {@code application}.
   *
   * @throws IllegalStateException If the {@code lwjwae-instance} module isn't in the application.
   */
  public static SingleInstance of(Application application) {
    return new SingleInstance(application.module(InstanceModule.class));
  }

  /**
   * Listens to the starts of other processes of the application, when it was created by {@link
   * #create}; before the listeners hear of one, the oldest window has come to the front. A listener
   * runs on a virtual thread, and the process that started waits until every listener returned. A
   * start that came before any listener reaches the first one on the thread that registers it.
   *
   * @return The subscription, to stop listening.
   */
  public EventSubscription onSecondInstance(Consumer<SecondInstanceEvent> listener) {
    return this.module.onSecondInstance(Objects.requireNonNull(listener, "listener"));
  }
}
