package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The toolkit-independent half of an application: the window list, the run loop, and the bridge at
 * the application level.
 *
 * <p>A backend supplies the {@link UiDispatcher}, {@link #createWindow}, and, if it has a tray,
 * {@link #createTray}. Everything else lives here so that the three backends agree on what "the
 * application" means: a window that closes, from Java or by the user, leaves the list through
 * {@link AbstractWindow#markClosed()}, a tray icon leaves its set when it closes, and the last of
 * either to leave releases {@link #run()}. Nothing here touches the toolkit, because the toolkit
 * keeps running after the application is closed: the dispatcher is a process-wide singleton, and
 * another application can be created on it.
 *
 * <p>Application-level bindings are stored as scripts as well as handlers, because a window opened
 * later needs the binding injected into its documents, and only the window knows how. A window that
 * receives a call it has no handler for asks {@link #binding} before it rejects the call.
 */
public abstract class AbstractApplication implements Application {
  private final UiDispatcher dispatcher;
  private final ApplicationParameters parameters;
  private final Map<Long, AbstractWindow> windows = new ConcurrentHashMap<>();
  private final Set<Tray> trays = ConcurrentHashMap.newKeySet();
  private final AtomicLong windowIds = new AtomicLong();
  private final Map<String, BiFunction<Window, String, String>> bindings =
      new ConcurrentHashMap<>();
  private final Map<String, String> bindingScripts = new ConcurrentHashMap<>();
  private final EventListeners listeners = new EventListeners("lwjwae-application-events");

  private final ReentrantLock lifecycle = new ReentrantLock();
  private final Condition idle = this.lifecycle.newCondition();

  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates an application on the UI thread of a toolkit. Opens no window.
   *
   * @param dispatcher The UI thread of the toolkit, started.
   * @param parameters The parameters of the application, defaults applied.
   */
  protected AbstractApplication(UiDispatcher dispatcher, ApplicationParameters parameters) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.parameters = Objects.requireNonNull(parameters, "parameters");
  }

  /** The UI thread of the toolkit, shared by every window. */
  protected final UiDispatcher dispatcher() {
    return this.dispatcher;
  }

  @Override
  public final ApplicationParameters parameters() {
    return this.parameters;
  }

  /**
   * Creates the native window. Called by {@link #open} with the ID that the window reports from
   * {@link Window#id()}. The window must exist, hidden, when this method returns.
   */
  protected abstract AbstractWindow createWindow(long id, WindowParameters parameters);

  @Override
  public final Window open() {
    return this.open(WindowParameters.createDefault());
  }

  @Override
  public final Window open(WindowParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    this.checkOpen();
    long id = this.windowIds.incrementAndGet();
    AbstractWindow window = this.createWindow(id, parameters);
    window.closeAction(parameters.closeAction());
    this.windows.put(id, window);
    // Closed while it was being created: leave the list the way markClosed would have.
    if (this.closed.get()) {
      window.close();
      this.windows.remove(id, window);
      throw new IllegalStateException("The application is closed");
    }
    this.bindingScripts.values().forEach(window::injectOnDocumentStart);
    if (parameters.resource() != null) {
      window.loadResource(parameters.resource());
    } else if (parameters.url() != null) {
      window.navigate(parameters.url());
    }
    return window;
  }

  @Override
  public final List<Window> windows() {
    return this.windows.values().stream()
        .sorted(Comparator.comparingLong(AbstractWindow::id))
        .map(Window.class::cast)
        .toList();
  }

  @Override
  public final Optional<Window> window(long id) {
    return Optional.ofNullable(this.windows.get(id));
  }

  @Override
  public final void bind(String name, Function<String, String> handler) {
    Objects.requireNonNull(handler, "handler");
    this.bind(name, (_, payload) -> handler.apply(payload));
  }

  @Override
  public final void bind(String name, BiFunction<Window, String, String> handler) {
    Objects.requireNonNull(handler, "handler");
    this.publish(name, handler, false);
  }

  @Override
  public final <T, R> void bind(String name, Class<T> argumentType, Function<T, R> handler) {
    Objects.requireNonNull(handler, "handler");
    this.bind(name, argumentType, (_, argument) -> handler.apply(argument));
  }

  @Override
  public final <T, R> void bind(
      String name, Class<T> argumentType, BiFunction<Window, T, R> handler) {
    Objects.requireNonNull(argumentType, "argumentType");
    Objects.requireNonNull(handler, "handler");
    this.publish(name, typed(this.requireCodec(), argumentType, handler), true);
  }

  /**
   * Adapts a typed handler to the text that the bridge carries. Package-private so that a window
   * binds with the same rule.
   */
  static <T, R> BiFunction<Window, String, String> typed(
      BridgeCodec codec, Class<T> argumentType, BiFunction<Window, T, R> handler) {
    return (window, payload) -> {
      T argument = argumentType == Void.class ? null : codec.decode(payload, argumentType);
      return codec.encode(handler.apply(window, argument));
    };
  }

  /** The application-level handler bound under {@code name}, or {@code null}. */
  final BiFunction<Window, String, String> binding(String name) {
    return this.bindings.get(name);
  }

  @Override
  public final void emit(String name, String payload) {
    Objects.requireNonNull(name, "name");
    this.broadcast(name, payload == null ? "" : payload, false);
  }

  @Override
  public final void emit(String name, Object payload) {
    Objects.requireNonNull(name, "name");
    this.broadcast(name, this.requireCodec().encode(payload), true);
  }

  private void broadcast(String name, String payload, boolean typed) {
    this.forEachWindow(window -> window.emitLocally(name, payload, typed));
    this.deliver(name, payload, typed, null);
  }

  /**
   * Runs {@code action} on every open window. A window that closes while the loop runs rejects the
   * call, and a backend can fail for its own reasons; either way the remaining windows still get
   * their turn, because one window closing is no reason for the others to miss an event or a
   * binding.
   */
  private void forEachWindow(Consumer<AbstractWindow> action) {
    for (AbstractWindow window : this.windows.values()) {
      if (window.isClosed()) {
        continue;
      }
      try {
        action.accept(window);
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
  }

  @Override
  public final EventSubscription listen(String name, Consumer<Event> listener) {
    return this.listeners.listen(name, listener);
  }

  @Override
  public final <T> EventSubscription listen(String name, Class<T> type, Consumer<T> listener) {
    return this.listeners.listen(
        name, EventListeners.decoding(this.requireCodec(), type, listener));
  }

  @Override
  public final EventSubscription once(String name, Consumer<Event> listener) {
    return this.listeners.once(name, listener);
  }

  @Override
  public final <T> EventSubscription once(String name, Class<T> type, Consumer<T> listener) {
    return this.listeners.once(name, EventListeners.decoding(this.requireCodec(), type, listener));
  }

  /** Runs the application-level listeners of {@code name}. A window calls this for its events. */
  final void deliver(String name, String payload, boolean typed, Window window) {
    this.listeners.deliver(name, payload, typed, window);
  }

  @Override
  public final Tray tray(TrayIcon icon) {
    Objects.requireNonNull(icon, "icon");
    this.checkOpen();
    Tray tray = this.createTray(icon, this::trayClosed);
    this.trays.add(tray);
    // Closed while it was being created: the tray missed the close of quit(), so close it here.
    if (this.closed.get()) {
      tray.close();
      throw new IllegalStateException("The application is closed");
    }
    return tray;
  }

  /**
   * Puts up the native tray icon. The default throws, for a backend without tray support.
   *
   * @param icon What the icon shows and does.
   * @param closed To run once when the icon goes away, however it goes: the application stops
   *     counting it for {@link #run()} then.
   * @throws UnsupportedOperationException If the backend has no tray.
   */
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    throw new UnsupportedOperationException("The " + this.engine() + " backend has no tray yet");
  }

  /** Called by a window once its native window is gone. The last one wakes {@link #run()}. */
  final void windowClosed(AbstractWindow window) {
    this.windows.remove(window.id(), window);
    if (!this.isRunnable()) {
      this.signalIdle();
    }
  }

  /** Called by a tray icon once it is gone. The last thing to go wakes {@link #run()}. */
  private void trayClosed(Tray tray) {
    this.trays.remove(tray);
    if (!this.isRunnable()) {
      this.signalIdle();
    }
  }

  @Override
  public void run() {
    if (this.dispatcher.isDispatchThread()) {
      throw new IllegalStateException("run() would block the UI thread");
    }
    this.lifecycle.lock();
    try {
      while (this.isRunnable()) {
        this.idle.awaitUninterruptibly();
      }
    } finally {
      this.lifecycle.unlock();
    }
  }

  /** Whether {@link #run()} has something to wait for: an open window or a tray icon. */
  protected final boolean isRunnable() {
    return !this.closed.get() && (!this.windows.isEmpty() || !this.trays.isEmpty());
  }

  @Override
  public final void quit() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    this.forEachWindow(AbstractWindow::close);
    for (Tray tray : List.copyOf(this.trays)) {
      try {
        tray.close();
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
    this.listeners.shutdown();
    this.signalIdle();
    this.onClose();
  }

  @Override
  public final boolean isClosed() {
    return this.closed.get();
  }

  @Override
  public final void close() {
    this.quit();
  }

  /**
   * Called once every window is closed, or once {@link #quit()} was called, after the threads
   * blocked in {@link #run()} were woken. A backend whose {@code run()} drives the loop of the
   * toolkit itself stops it here. Runs on the thread that closed the last window, which is the UI
   * thread when the user closed it. May run more than once; resources that belong to the
   * application are released in {@link #onClose()} instead.
   */
  protected void onIdle() {}

  /**
   * Called exactly once, by the first {@link #quit()}, after every window was asked to close and
   * after {@link #onIdle()}. A backend releases what it holds for the whole application here. Runs
   * on the thread that called {@code quit()}.
   */
  protected void onClose() {}

  private void signalIdle() {
    this.lifecycle.lock();
    try {
      this.idle.signalAll();
    } finally {
      this.lifecycle.unlock();
    }
    this.onIdle();
  }

  /** The codec, or an exception that says how to get one. */
  final BridgeCodec requireCodec() {
    BridgeCodec codec = this.parameters.codec();
    if (codec == null) {
      throw new IllegalStateException(
          "No bridge codec: add a codec module to the runtime classpath, or set "
              + "ApplicationParameters.codec()");
    }
    return codec;
  }

  private void checkOpen() {
    if (this.closed.get()) {
      throw new IllegalStateException("The application is closed");
    }
  }

  private void publish(String name, BiFunction<Window, String, String> handler, boolean typed) {
    Objects.requireNonNull(name, "name");
    BridgeProtocol.checkIdentifier(name);
    String script = BridgeProtocol.bindingScript(name, typed);
    this.bindings.put(name, handler);
    this.bindingScripts.put(name, script);
    // Once for documents loaded from now on, once for the document already on screen.
    this.forEachWindow(
        window -> {
          // The window's own binding answers the call there, so the page has to speak its form:
          // injected later, this script would switch the page to the other encoding.
          if (window.hasBinding(name)) {
            return;
          }
          window.injectOnDocumentStart(script);
          window.eval(script);
        });
  }
}
