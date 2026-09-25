package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.instance.InstanceLock;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The {@code win32-webview2} backend, on x64, with the WebView2 Evergreen runtime,
   *       which Windows 11 ships and Edge installs on Windows 10.
   *   <li>macOS: The {@code cocoa-wkwebview} backend. The first application also gives the process
   *       the menu bar of a Mac application: the application menu, File, Edit, and Window, which
   *       carry Command-C, V, X, A, Z, and Quit.
   *   <li>Linux, GTK 3: The {@code gtk3-webkit2gtk-4.1} backend, with GTK 3 and WebKitGTK 2.40 or
   *       newer with the 4.1 API. It wins when both Linux backends could run.
   *   <li>Linux, GTK 4: The {@code gtk4-webkitgtk-6.0} backend, with GTK 4 and WebKitGTK 6.0, which
   *       runs its web processes in a bubblewrap sandbox. The sandbox needs unprivileged user
   *       namespaces: Ubuntu 23.10 and newer allow them only through an AppArmor profile for the
   *       executable.
   * </ul>
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
   * Runs an application of one window titled {@code title} that shows {@code target}, a URL or a
   * file of the application such as {@code app/index.html}, and returns when the window closes.
   *
   * <pre>{@code
   * public static void main(String[] args) {
   *   Application.launch("Notes", "app/index.html");
   * }
   * }</pre>
   *
   * @throws BackendNotAvailableException If no backend on the classpath supports this machine.
   */
  static void launch(String title, String target) {
    Application.launch(WindowParameters.of(title, target), _ -> {});
  }

  /** The same as {@link #launch(WindowParameters, Consumer)}, with nothing to set up. */
  static void launch(WindowParameters parameters) {
    Application.launch(parameters, _ -> {});
  }

  /**
   * Runs an application of one window that opens with {@code parameters}, and returns when every
   * window closed. {@code setup} gets the window before it loads its page and shows, to bind what
   * the page calls; {@link Window#application()} reaches the rest.
   *
   * <pre>{@code
   * Application.launch(
   *     WindowParameters.of("Notes", "app/index.html"),
   *     window -> window.bind("save", text -> store.save(text)));
   * }</pre>
   *
   * @throws BackendNotAvailableException If no backend on the classpath supports this machine.
   */
  static void launch(WindowParameters parameters, Consumer<Window> setup) {
    try (Application application = Application.create()) {
      Window window = application.open(parameters.toBuilder().url(null).resource(null).build());
      setup.accept(window);
      if (parameters.resource() != null) {
        window.loadResource(parameters.resource());
      } else if (parameters.url() != null) {
        window.navigate(parameters.url());
      }
      window.show();
      application.run();
    }
  }

  /**
   * Creates the application unless another process of it runs already. That one gets the arguments
   * of this process, its oldest window comes to the front, and its {@link #onSecondInstance}
   * listeners hear of it; this process gets nothing and should end, having opened nothing, not even
   * the toolkit.
   *
   * <pre>{@code
   * public static void main(String[] args) {
   *   ApplicationParameters parameters = ApplicationParameters.builder().name("notes").build();
   *   Optional<Application> created = Application.createSingleInstance(parameters, args);
   *   if (created.isEmpty()) {
   *     return;
   *   }
   *   try (Application application = created.get()) {
   *     application.onSecondInstance(start -> openFiles(start.arguments()));
   *     ...
   *   }
   * }
   * }</pre>
   *
   * <p>The processes find each other by {@link ApplicationParameters#name()} and the user, so two
   * users each run their own.
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
  static Optional<Application> createSingleInstance(
      ApplicationParameters parameters, String... arguments) {
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
      application = Application.create(parameters);
    } catch (RuntimeException | Error e) {
      try (lock) {
        throw e;
      }
    }
    ((AbstractApplication) application).serveInstances(lock);
    return Optional.of(application);
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

  /**
   * The name and version of the engine that draws the pages, such as {@code WebKitGTK 2.46.5}.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: {@code WebView2} and the version of the runtime.
   *   <li>macOS: {@code WKWebView} and the version of WebKit.
   *   <li>Linux, GTK 3: {@code WebKitGTK} and its version.
   *   <li>Linux, GTK 4: {@code WebKitGTK} and its version.
   * </ul>
   */
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

  /**
   * Opens a window that loads {@code target}, a URL or a file of the application such as {@code
   * app/index.html}, and shows it: {@link #open()}, {@link Window#load}, and {@link Window#show()}.
   */
  default Window show(String target) {
    Window window = this.open();
    window.load(target);
    window.show();
    return window;
  }

  /** Opens a window with {@code parameters} and shows it. */
  default Window show(WindowParameters parameters) {
    Window window = this.open(parameters);
    window.show();
    return window;
  }

  /**
   * The screens of the desktop, the primary one first, at the moment of the call: screens come and
   * go, so a program that keeps the list reads it again when it needs it.
   */
  List<Screen> screens();

  /** The primary screen: the one that holds the menu bar or the taskbar. */
  default Screen primaryScreen() {
    List<Screen> screens = this.screens();
    return screens.stream().filter(Screen::primary).findFirst().orElse(screens.getFirst());
  }

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
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: An icon in the notification area. A left click runs {@code onActivate}, or opens
   *       the menu without it; a right click opens the menu. The icon comes back after Explorer
   *       restarts.
   *   <li>macOS: An item in the menu bar, with the image scaled to 18 points. Without {@code
   *       onActivate}, any click opens the menu; with it, a primary click runs it, and a secondary
   *       or Control click opens the menu.
   *   <li>Linux, GTK 3: The first that works: libappindicator, where any click opens the menu and
   *       {@code onActivate} never runs; a StatusNotifierItem that the library serves itself, where
   *       the session has a StatusNotifier host; or {@code GtkStatusIcon} on X11 with a panel that
   *       has a legacy tray. Without any, the call throws.
   *   <li>Linux, GTK 4: A StatusNotifierItem that the library serves itself, so it needs a
   *       StatusNotifier host, which KDE and GNOME with the AppIndicator extension have; without
   *       one, the call throws.
   * </ul>
   *
   * @throws UnsupportedOperationException If this backend has no tray support yet.
   * @throws IllegalStateException If the application is closed.
   */
  Tray tray(TrayIcon icon);

  /**
   * Puts an icon in the system tray: a PNG among the resources of the application, such as {@code
   * app/tray.png}, with {@code menu}.
   */
  default Tray tray(String icon, TrayMenuItem... menu) {
    return this.tray(TrayIcon.builder().icon(icon).menu(menu).build());
  }

  /**
   * Shows a desktop notification and returns the handle that takes it back.
   *
   * <p>The notification belongs to the application. It stays when every window is closed, but it
   * doesn't keep {@link #run()} running, and it goes away with the application, because its buttons
   * and its {@code onActivate} handler go with it.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A toast, filed under an ID made from {@link ApplicationParameters#name()} that
   *       the library registers for the user. A toast that times out moves to Notification Center,
   *       where it can still be clicked, and under Do Not Disturb every toast goes there at once.
   *   <li>macOS: Only for an application packaged as an {@code .app} bundle with an identifier:
   *       under the {@code java} launcher or as a bare executable, the call throws. The first
   *       notification asks the user for permission; after a no, every call throws until the user
   *       allows them in System Settings.
   *   <li>Linux, GTK 3: Through {@code org.freedesktop.Notifications} over D-Bus; without a session
   *       bus or a notification server, the call throws. How much of the image and the buttons
   *       shows is up to the server.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws UnsupportedOperationException If this backend has no notifications yet, or the desktop
   *     has nothing that shows them.
   * @throws IllegalStateException If the application is closed.
   */
  NotificationHandle showNotification(Notification notification);

  /** Shows a notification of {@code title} and {@code body}, and nothing else. */
  default NotificationHandle showNotification(String title, String body) {
    return this.showNotification(Notification.of(title, body));
  }

  /**
   * Listens to the starts of other processes of the application, when it was created by {@link
   * #createSingleInstance}; before the listeners hear of one, the oldest window has come to the
   * front. A listener runs on a virtual thread, and the process that started waits until every
   * listener returned. A start that came before any listener reaches the first one on the thread
   * that registers it.
   */
  EventSubscription onSecondInstance(Consumer<SecondInstanceEvent> listener);

  /**
   * Opens {@code url} where the system opens it: a web page in the default browser, a {@code
   * mailto:} link in the mail client. Returns once the system has taken the URL, not when it shows.
   *
   * <p>Only {@code http}, {@code https}, and {@code mailto} go through: a {@code file:} URL or the
   * scheme of another application would run whatever the system associates with it, which a page
   * must never be able to ask for.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Through {@code ShellExecuteW}.
   *   <li>macOS: Through {@code NSWorkspace}.
   *   <li>Linux, GTK 3: Through {@code g_app_info_launch_default_for_uri}, which goes to the
   *       OpenURI portal inside a sandbox such as Flatpak.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
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
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: On the main thread of a process that hasn't started the application loop, as in
   *       the {@code main} method of a native image, the call runs the loop itself and returns when
   *       the last window closes or on {@link #quit()}. Elsewhere, it waits as described.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: As described.
   * </ul>
   *
   * @throws IllegalStateException If called from the UI thread, where blocking would freeze every
   *     window, except on macOS.
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
