package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One desktop application: the process-wide half of the library, and the entry point.
 *
 * <p>An application owns what exists once per process: the UI thread of the toolkit, the codec of
 * the bridge, the development server setting, and the list of open windows. A {@link Window} is one
 * native window with a web view inside; the application opens as many as the program needs, and
 * they share everything above. {@link #run()} blocks the calling thread while any window is open,
 * so the shape of a program is: create the application, open a window, show it, run.
 *
 * <p>The bridge exists at both levels. A binding or a listener on a window belongs to that window
 * alone. A binding on the application reaches every window, the ones already open and the ones
 * opened later, and its handler learns which window called. A listener on the application hears an
 * event from any window, with {@link Event#window()} saying which one. {@link #emit} on the
 * application delivers to every page. A window-level binding wins over an application-level one of
 * the same name in that window.
 *
 * <p>{@link #create} picks the backend for the machine it runs on: it loads every {@link
 * BackendProvider} on the classpath, drops the ones that don't support the machine, and takes the
 * one with the highest priority, or the one named by the {@value #BACKEND_PROPERTY} system property
 * or the {@value #BACKEND_VARIABLE} environment variable. A backend module registers its provider
 * in {@code META-INF/services/dev.ivchenko.lwjwae.BackendProvider}.
 *
 * <p>Every method is safe to call from any thread. Closing the application closes every window.
 */
public interface Application extends AutoCloseable {
  /**
   * The system property that names the backend to use, by {@link BackendProvider#name()}. Wins over
   * the priority of the providers, as long as the named provider supports the machine.
   */
  String BACKEND_PROPERTY = "lwjwae.backend";

  /** The environment variable with the same meaning as {@link #BACKEND_PROPERTY}. */
  String BACKEND_VARIABLE = "LWJWAE_BACKEND";

  /**
   * Creates an application with every default: the first codec on the classpath and no development
   * server, unless the environment says otherwise.
   *
   * @throws BackendNotAvailableException If no backend on the classpath supports this machine.
   */
  static Application create() {
    return Application.create(ApplicationParameters.createDefault());
  }

  /**
   * Creates an application on the backend of this machine. No window exists yet: {@link #open}
   * creates one.
   *
   * @throws BackendNotAvailableException If no backend on the classpath supports this machine.
   */
  static Application create(ApplicationParameters parameters) {
    BackendProvider provider =
        Application.provider()
            .orElseThrow(() -> new BackendNotAvailableException(Application.noBackendMessage()));
    return provider.create(parameters);
  }

  /**
   * The provider that {@link #create} would use: the supported one with the highest priority, or
   * the one that {@link #BACKEND_PROPERTY} or {@link #BACKEND_VARIABLE} names, if it's supported.
   */
  static Optional<BackendProvider> provider() {
    String requested = Application.requestedBackend();
    return Application.providers().stream()
        .filter(BackendProvider::isSupported)
        .filter(provider -> requested == null || provider.name().equals(requested))
        .max(Comparator.comparingInt(BackendProvider::priority));
  }

  /** Every provider on the classpath, supported or not, in service-loader order. */
  static List<BackendProvider> providers() {
    List<BackendProvider> found = new ArrayList<>();
    ServiceLoader.load(BackendProvider.class, BackendProvider.class.getClassLoader())
        .forEach(found::add);
    return List.copyOf(found);
  }

  private static String requestedBackend() {
    String name = System.getProperty(BACKEND_PROPERTY, System.getenv(BACKEND_VARIABLE));
    return name == null || name.isBlank() ? null : name.strip();
  }

  private static String noBackendMessage() {
    List<BackendProvider> providers = Application.providers();
    String requested = Application.requestedBackend();
    if (requested != null) {
      return "Backend '"
          + requested
          + "' (from -D"
          + BACKEND_PROPERTY
          + " / "
          + BACKEND_VARIABLE
          + ") is not on the classpath or does not support this machine. Found: "
          + providers.stream().map(BackendProvider::name).collect(Collectors.joining(", "));
    }
    if (providers.isEmpty()) {
      return "No backend on the classpath. Add one, for example lwjwae-gtk on Linux.";
    }
    String rejected =
        providers.stream()
            .map(provider -> "  " + provider.name() + ": " + provider.unsupportedReason())
            .collect(Collectors.joining(System.lineSeparator()));
    return "No backend supports this machine ("
        + PlatformUtil.osName()
        + ")."
        + System.lineSeparator()
        + rejected;
  }

  /** The parameters that the application was created with, defaults applied. */
  ApplicationParameters parameters();

  /** The name and version of the engine that draws the pages, such as {@code WebKitGTK 2.46.5}. */
  String engine();

  /** Opens a window with every default. The window stays hidden until {@link Window#show()}. */
  Window open();

  /**
   * Opens a window. The window stays hidden until {@link Window#show()}, so a page can load before
   * anything appears on screen. When {@code parameters} carry a URL or a resource, the window
   * navigates there before this method returns.
   *
   * @throws IllegalStateException If the application is closed.
   */
  Window open(WindowParameters parameters);

  /** The windows that are open right now, oldest first. */
  List<Window> windows();

  /** The open window with the given {@link Window#id()}, if there is one. */
  Optional<Window> window(long id);

  /**
   * Exposes a function to every page as {@code window.NAME(payload)}, which returns a promise. The
   * binding reaches every open window and every window opened later.
   *
   * @param name A JavaScript identifier.
   * @param handler Called on a virtual thread with the payload as text. The value that it returns
   *     resolves the promise; an exception rejects it with the message of the root cause.
   * @throws IllegalArgumentException If {@code name} isn't a JavaScript identifier.
   */
  void bind(String name, Function<String, String> handler);

  /**
   * The same as {@link #bind(String, Function)}, for a handler that needs to know which window
   * called.
   */
  void bind(String name, BiFunction<Window, String, String> handler);

  /**
   * Exposes a function to every page that takes and returns objects through the codec. The page
   * passes any value and receives the decoded result.
   *
   * @param name A JavaScript identifier.
   * @param argumentType The type to decode the argument into. {@code Void.class} for a function
   *     without an argument; the handler then receives {@code null}.
   * @param handler Called on a virtual thread. The value that it returns is encoded and resolves
   *     the promise; {@code null} resolves it with {@code null}.
   * @throws IllegalStateException If there is no codec.
   */
  <T, R> void bind(String name, Class<T> argumentType, Function<T, R> handler);

  /**
   * The same as {@link #bind(String, Class, Function)}, for a handler that needs to know which
   * window called.
   */
  <T, R> void bind(String name, Class<T> argumentType, BiFunction<Window, T, R> handler);

  /**
   * Answers the calls that the page makes with {@code lwjwae.call(name, body)}, in every window of
   * the application.
   *
   * <p>Unlike {@link #bind}, a call carries bytes both ways, can be answered as a stream that the
   * page reads while it's produced, and can be abandoned by the page with an {@code AbortSignal}.
   * On the page, the call resolves to a {@code Response}, as {@code fetch} does. See {@link
   * RpcHandler}.
   *
   * @param name Letters, digits, and {@code . _ -}.
   * @throws IllegalArgumentException If {@code name} has any other character.
   */
  void handle(String name, RpcHandler handler);

  /**
   * Delivers an event to every open window and to the listeners of this application. The Java
   * listeners of each window hear it too, with that window as {@link Event#window()}; the listeners
   * of the application hear it once, with no window.
   *
   * @param name The event name.
   * @param payload The payload as text. {@code null} is delivered as an empty string.
   */
  void emit(String name, String payload);

  /**
   * The same as {@link #emit(String, String)}, with the payload encoded by the codec. The pages
   * receive the decoded object.
   *
   * @throws IllegalStateException If there is no codec.
   */
  void emit(String name, Object payload);

  /**
   * Listens to an event from any window, or from {@link #emit} on this application. Listeners run
   * on one virtual thread, in order.
   *
   * @return The subscription, to stop listening.
   */
  EventSubscription listen(String name, Consumer<Event> listener);

  /**
   * The same as {@link #listen(String, Consumer)}, with the payload decoded by the codec. {@code
   * String.class} takes an untyped payload as it is.
   */
  <T> EventSubscription listen(String name, Class<T> type, Consumer<T> listener);

  /** Listens to the next event of the name, then stops. */
  EventSubscription once(String name, Consumer<Event> listener);

  /** The same as {@link #once(String, Consumer)}, with the payload decoded by the codec. */
  <T> EventSubscription once(String name, Class<T> type, Consumer<T> listener);

  /**
   * Puts an icon in the system tray, with the menu and the handlers of {@code icon}, and returns
   * the handle that changes or removes it.
   *
   * <p>A tray icon belongs to the application, not to a window: it stays when every window is
   * closed, and it keeps {@link #run()} running, which is what lets an application live in the tray
   * with its window hidden or gone. It goes away with {@link Tray#close()} or with the application.
   *
   * @throws UnsupportedOperationException If this backend has no tray support yet.
   * @throws IllegalStateException If the application is closed.
   */
  Tray tray(TrayIcon icon);

  /**
   * Shows a desktop notification and returns the handle that takes it back.
   *
   * <p>The notification belongs to the application. It stays when every window is closed, but it
   * doesn't keep {@link #run()} running, and it goes away with the application, because its buttons
   * and its {@code onActivate} handler go with it.
   *
   * @throws UnsupportedOperationException If this backend has no notifications yet, or the desktop
   *     has nothing that shows them.
   * @throws IllegalStateException If the application is closed.
   */
  NotificationHandle showNotification(Notification notification);

  /**
   * Opens {@code url} where the system opens it: a web page in the default browser, a {@code
   * mailto:} link in the mail client. Returns once the system has taken the URL, not when it shows.
   *
   * <p>Only {@code http}, {@code https}, and {@code mailto} go through: a {@code file:} URL or the
   * scheme of another application would run whatever the system associates with it, which a page
   * must never be able to ask for.
   *
   * @throws IllegalArgumentException If {@code url} isn't an absolute URL of one of those schemes.
   * @throws IllegalStateException If the system couldn't open it, or the application is closed.
   */
  void openExternal(String url);

  /**
   * Blocks the calling thread while any window is open or any tray icon is up, or until {@link
   * #quit()}. Returns at once when there is neither. A window opened from another thread, or from a
   * page, in the meantime keeps the application running.
   *
   * @throws IllegalStateException If called from the UI thread, where blocking would freeze every
   *     window. The macOS backend is the exception: there the main thread runs the application loop
   *     itself, and {@code run()} on it returns when the last window closes.
   */
  void run();

  /**
   * Closes every window, every tray icon, every notification, and the application. Every thread
   * blocked in {@link #run()} returns. Nothing can be opened afterwards. Idempotent.
   */
  void quit();

  /** Whether {@link #quit()} or {@link #close()} was called. */
  boolean isClosed();

  /** The same as {@link #quit()}. */
  @Override
  void close();

  /** The codec behind the typed bridge methods, or {@code null} if there is none. */
  default BridgeCodec codec() {
    return this.parameters().codec();
  }
}
