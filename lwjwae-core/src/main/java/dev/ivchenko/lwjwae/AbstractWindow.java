package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeMessage;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The toolkit-independent half of a window: listener bookkeeping, the closed state, and the whole
 * bridge except its transport.
 *
 * <p>A backend subclass owns the native window and the web view. It supplies the window methods of
 * {@link Window}, {@link #injectOnDocumentStart} and {@link #bridgeTransportScript} for the bridge,
 * and calls {@link #installBridge()} once the view exists, {@link #handleBridgeMessage} from the
 * callback that the engine delivers messages on, {@link #emitLoad} from the load callbacks, and
 * {@link #markClosed()} when the native window is gone.
 *
 * <p>Bridge handlers run on virtual threads, one per call, so a slow handler never blocks the UI
 * thread or another call. Java event listeners run on one virtual thread per window, in order, so a
 * listener never sees the second event of a name before the first. The reserved calls that a page
 * makes through {@code window.lwjwae}, to emit an event, open a window, or close this one, go
 * through the same path as a binding, under names that no binding can take because a bound name
 * can't contain a colon.
 */
public abstract class AbstractWindow implements Window {
  private static final Executor HANDLER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final AbstractApplication application;
  private final long id;
  private final List<Consumer<LoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
  private final Map<String, Function<String, String>> bindings = new ConcurrentHashMap<>();
  private final EventListeners listeners = new EventListeners("lwjwae-events");

  private volatile boolean closed;
  private volatile CloseAction closeAction = CloseAction.CLOSE;

  /**
   * Records the owner and the ID. The subclass creates the native window afterwards.
   *
   * @param application The application that opens the window.
   * @param id The ID that {@link AbstractApplication#createWindow} was given.
   */
  protected AbstractWindow(AbstractApplication application, long id) {
    this.application = Objects.requireNonNull(application, "application");
    this.id = id;
  }

  /** The UI thread of the toolkit, the one of the application. */
  protected final UiDispatcher dispatcher() {
    return this.application.dispatcher();
  }

  @Override
  public final long id() {
    return this.id;
  }

  @Override
  public final Application application() {
    return this.application;
  }

  @Override
  public final void onLoad(Consumer<LoadEvent> listener) {
    this.loadListeners.add(Objects.requireNonNull(listener, "listener"));
  }

  @Override
  public void loadResource(String path) {
    ApplicationParameters parameters = this.application.parameters();
    if (parameters.isDevelopment()) {
      this.navigate(parameters.devServerUrl());
      return;
    }
    this.navigate(this.resourceUrl(path));
  }

  /**
   * The URL that serves a classpath resource in this engine. The default is the {@code
   * app://local/} scheme of the WebKit backends; an engine without custom schemes overrides it.
   */
  protected String resourceUrl(String path) {
    return ResourceUtil.url(path);
  }

  @Override
  public final void bind(String name, Function<String, String> handler) {
    Objects.requireNonNull(handler, "handler");
    this.publish(name, handler, false);
  }

  @Override
  public final <T, R> void bind(String name, Class<T> argumentType, Function<T, R> handler) {
    Objects.requireNonNull(argumentType, "argumentType");
    Objects.requireNonNull(handler, "handler");
    BiFunction<Window, String, String> typed =
        AbstractApplication.typed(
            this.application.requireCodec(),
            argumentType,
            (_, argument) -> handler.apply(argument));
    this.publish(name, payload -> typed.apply(this, payload), true);
  }

  @Override
  public final void emit(String name, String payload) {
    Objects.requireNonNull(name, "name");
    this.checkOpen();
    String text = payload == null ? "" : payload;
    this.emitLocally(name, text, false);
    this.application.deliver(name, text, false, this);
  }

  @Override
  public final void emit(String name, Object payload) {
    Objects.requireNonNull(name, "name");
    this.checkOpen();
    String text = this.application.requireCodec().encode(payload);
    this.emitLocally(name, text, true);
    this.application.deliver(name, text, true, this);
  }

  /** Delivers to the page and to the listeners of this window, not to the application's. */
  final void emitLocally(String name, String payload, boolean typed) {
    this.eval(BridgeProtocol.emitScript(name, payload, typed));
    this.listeners.deliver(name, payload, typed, this);
  }

  @Override
  public final EventSubscription listen(String name, Consumer<Event> listener) {
    return this.listeners.listen(name, listener);
  }

  @Override
  public final <T> EventSubscription listen(String name, Class<T> type, Consumer<T> listener) {
    return this.listeners.listen(
        name, EventListeners.decoding(this.application.requireCodec(), type, listener));
  }

  @Override
  public final EventSubscription once(String name, Consumer<Event> listener) {
    return this.listeners.once(name, listener);
  }

  @Override
  public final <T> EventSubscription once(String name, Class<T> type, Consumer<T> listener) {
    return this.listeners.once(
        name, EventListeners.decoding(this.application.requireCodec(), type, listener));
  }

  /**
   * Injects the page-side bridge runtime into every document of this window. A backend calls this
   * once its native view exists and before the first page loads.
   */
  protected final void installBridge() {
    BridgeCodec codec = this.application.parameters().codec();
    // Without a codec, the page has no encoder: an untyped call with a non-string payload sends
    // String(payload), and a typed call fails on the Java side before it reaches the page.
    String pageCodec = codec == null ? "null" : codec.pageScript();
    this.injectOnDocumentStart(
        BridgeProtocol.bootstrapScript(this.bridgeTransportScript(), pageCodec));
  }

  /**
   * Handles one message from the page. A backend calls this from the callback that the engine
   * delivers messages on, on the UI thread. The reply, if any, goes back through {@link #eval}.
   */
  protected final void handleBridgeMessage(String message) {
    BridgeMessage parsed = BridgeProtocol.parse(message);
    if (parsed == null) {
      ThrowableUtil.report(new IllegalStateException("Malformed bridge message: " + message));
      return;
    }

    if (parsed.name().equals(BridgeProtocol.EVENT_CALL)) {
      Event event = BridgeProtocol.parseEvent(parsed.payload());
      if (event == null) {
        this.eval(BridgeProtocol.rejectScript(parsed.id(), "Malformed event"));
        return;
      }
      this.listeners.deliver(event.name(), event.payload(), event.typed(), this);
      this.application.deliver(event.name(), event.payload(), event.typed(), this);
      this.eval(BridgeProtocol.resolveScript(parsed.id(), ""));
      return;
    }

    Function<String, String> handler = this.handler(parsed.name());
    if (handler == null) {
      this.eval(BridgeProtocol.rejectScript(parsed.id(), "No handler bound for " + parsed.name()));
      return;
    }
    CompletableFuture.supplyAsync(() -> handler.apply(parsed.payload()), HANDLER_EXECUTOR)
        .whenComplete(
            (result, failure) -> {
              if (this.closed) {
                return;
              }
              this.eval(
                  failure == null
                      ? BridgeProtocol.resolveScript(parsed.id(), result)
                      : BridgeProtocol.rejectScript(parsed.id(), rootMessage(failure)));
            });
  }

  /** Whether this window has a binding of its own under {@code name}. */
  final boolean hasBinding(String name) {
    return this.bindings.containsKey(name);
  }

  /**
   * The handler for a call: this window's binding, then the application's, then a reserved call.
   */
  private Function<String, String> handler(String name) {
    Function<String, String> own = this.bindings.get(name);
    if (own != null) {
      return own;
    }
    BiFunction<Window, String, String> shared = this.application.binding(name);
    if (shared != null) {
      return payload -> shared.apply(this, payload);
    }
    if (name.equals(BridgeProtocol.OPEN_CALL)) {
      return this::openFromPage;
    }
    if (name.equals(BridgeProtocol.CLOSE_CALL)) {
      return this::closeFromPage;
    }
    return null;
  }

  private String closeFromPage(String payload) {
    this.close();
    return "";
  }

  private String openFromPage(String payload) {
    WindowParameters parameters = BridgeProtocol.parseWindowParameters(payload);
    if (parameters == null) {
      throw new IllegalArgumentException("Malformed window parameters");
    }
    Window opened = this.application.open(parameters);
    opened.show();
    return Long.toString(opened.id());
  }

  /**
   * Runs a script in every document of this window before the scripts of the document, from the
   * next document on.
   */
  protected abstract void injectOnDocumentStart(String script);

  /**
   * A JavaScript expression that evaluates to a function of one string and delivers that string to
   * the host, for example {@code (message) => window.webkit.messageHandlers.NAME.postMessage(...)}.
   */
  protected abstract String bridgeTransportScript();

  /** Runs the load listeners. A backend calls this from its load callbacks, on the UI thread. */
  protected final void emitLoad(LoadEvent event) {
    for (Consumer<LoadEvent> listener : this.loadListeners) {
      try {
        listener.accept(event);
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
  }

  /**
   * Records that the native window is gone. A backend calls this from the callback that the toolkit
   * fires on destruction. The application drops the window from its list, and the last window to go
   * releases every thread blocked in {@link Application#run()}.
   */
  protected final void markClosed() {
    this.closed = true;
    this.listeners.shutdown();
    this.application.windowClosed(this);
  }

  /** Fails with {@link IllegalStateException} once the window is closed. */
  protected final void checkOpen() {
    if (this.closed) {
      throw new IllegalStateException("The window is closed");
    }
  }

  @Override
  public final boolean isClosed() {
    return this.closed;
  }

  @Override
  public final CloseAction closeAction() {
    return this.closeAction;
  }

  @Override
  public final void closeAction(CloseAction action) {
    this.closeAction = Objects.requireNonNull(action, "action");
  }

  /**
   * Whether a close that the user asked for, from the title bar or the desktop, should hide the
   * window rather than close it. A backend asks from the handler of that request, on the UI thread,
   * hides the window itself when the answer is {@code true}, and cancels the close. {@link
   * CloseAction#HIDE} counts only while the application has a tray icon, the way back to the
   * window.
   */
  protected final boolean hidesOnCloseRequest() {
    return this.closeAction == CloseAction.HIDE && !this.closed && this.application.hasTrayIcon();
  }

  private void publish(String name, Function<String, String> handler, boolean typed) {
    Objects.requireNonNull(name, "name");
    BridgeProtocol.checkIdentifier(name);
    this.bindings.put(name, handler);

    String script = BridgeProtocol.bindingScript(name, typed);
    // Once for documents loaded from now on, once for the document already on screen.
    this.injectOnDocumentStart(script);
    this.eval(script);
  }

  private static String rootMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
