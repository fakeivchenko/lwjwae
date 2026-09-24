package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
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
import java.util.function.Supplier;

/**
 * The toolkit-independent half of a window: listener bookkeeping, the closed state, and the whole
 * bridge except its transport.
 *
 * <p>A backend subclass owns the native window and the web view. It supplies the window methods of
 * {@link Window}, {@link #injectOnDocumentStart} and {@link #bridgeTransportScript} for the bridge,
 * and calls {@link #installBridge()} once the view exists, {@link #handleBridgeMessage} from the
 * callback that the engine delivers messages on, {@link #serveRpc} for a request to its RPC path,
 * {@link #emitLoad} from the load callbacks, and {@link #markClosed()} when the native window is
 * gone.
 *
 * <p>The bridge has one protocol, RPC. A binding is an RPC handler with a function on the page that
 * calls it; the calls that a page makes through {@code window.lwjwae}, to emit an event, open a
 * window, or close this one, are RPC calls under names that no handler can take, because a handler
 * name can't contain a colon. Java reaches the page through the answer of one call that the page
 * keeps open, see {@link PageEvents}. Two transports carry the calls: the message channel of the
 * engine, cheapest for a small call, see {@link MessageRpcExchange}, and, for {@code lwjwae.call}
 * where the engine can stream it, a request to the resource origin.
 *
 * <p>Handlers run on virtual threads, one per call, so a slow handler never blocks the UI thread or
 * another call. Java event listeners run on one virtual thread per window, in order, so a listener
 * never sees the second event of a name before the first.
 */
public abstract class AbstractWindow implements Window {
  private static final Executor HANDLER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final SecureRandom TOKENS = new SecureRandom();

  private final AbstractApplication application;
  private final long id;
  private final List<Consumer<LoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
  private final Map<String, String> bindingScripts = new ConcurrentHashMap<>();
  private final Map<String, RpcHandler> rpcHandlers = new ConcurrentHashMap<>();
  private final Map<String, MessageRpcExchange> messageCalls = new ConcurrentHashMap<>();
  private final EventListeners listeners = new EventListeners("lwjwae-events");
  private final PageEvents pageEvents = new PageEvents();
  private final String token = newToken();

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
    this.publish(name, (_, payload) -> handler.apply(payload), false);
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
    this.publish(name, typed, true);
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
    this.pageEvents.send(name, payload, typed);
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

  @Override
  public final void handle(String name, RpcHandler handler) {
    RpcNames.check(name);
    this.rpcHandlers.put(name, Objects.requireNonNull(handler, "handler"));
  }

  /**
   * Serves one RPC request of the page of this window. A backend calls this from wherever its
   * engine delivers the request, on any thread.
   *
   * <p>A request from an origin other than the one that serves the resources of the window, or the
   * development server, is refused with {@code 403}: a page that the window navigated to, or a
   * frame that it embeds, can't reach the handlers of the application. A handler of the window wins
   * over one of the application with the same name. The handler runs on a virtual thread of its
   * own.
   */
  protected final void serveRpc(RpcExchange exchange) {
    String origin = exchange.header("Origin");
    // The CORS headers go on every answer, the refusal included: they let the page read the answer,
    // which for a stranger is the 403 below, and the handlers stay out of reach either way.
    Map<String, String> cors = new LinkedHashMap<>();
    if (origin != null) {
      cors.put("Access-Control-Allow-Origin", origin);
      cors.put("Vary", "Origin");
    }
    if ("OPTIONS".equals(exchange.method())) {
      cors.put("Access-Control-Allow-Methods", "POST");
      cors.put("Access-Control-Allow-Headers", "Content-Type");
      cors.put("Access-Control-Max-Age", "600");
      exchange.reply(204, cors, new byte[0]);
      return;
    }
    if (origin != null && !this.isTrustedOrigin(origin)) {
      this.refuse(exchange, 403, cors, "forbidden", "Origin " + origin + " may not call Java");
      return;
    }
    String path = exchange.path();
    String name =
        path.startsWith(RpcExchange.PATH_PREFIX)
            ? URLDecoder.decode(
                path.substring(RpcExchange.PATH_PREFIX.length()), StandardCharsets.UTF_8)
            : "";
    if (!"POST".equals(exchange.method())) {
      this.refuse(exchange, 405, cors, "method", "RPC calls are POST");
      return;
    }
    RpcHandler handler = this.rpcHandler(name);
    if (handler == null) {
      this.refuse(exchange, 404, cors, "not-found", "No handler for " + name);
      return;
    }
    ExchangeRpcCall call = new ExchangeRpcCall(exchange, name, this, cors);
    exchange.onCancel(call::cancel);
    RpcHandler found = handler;
    HANDLER_EXECUTOR.execute(() -> call.run(found));
  }

  /**
   * The page half of the RPC transport: a JavaScript object that the bootstrap reads. {@code base}
   * is where {@code lwjwae.call} sends its requests, {@code null} to send them as messages too, and
   * {@code webview2} tells the bootstrap to take answers from WebView2 events rather than from
   * {@link #postRpcMessage}. The default is {@code fetch} on the resource origin, for the engines
   * that serve a custom scheme with request bodies and streamed responses; an engine without that
   * overrides it.
   */
  protected String rpcTransportScript() {
    return "{base:"
        + ScriptUtil.quote(this.resourceUrl(RpcExchange.PATH_PREFIX.substring(1)))
        + "}";
  }

  /**
   * Hands one RPC message to the page, from any thread. The default evaluates {@code
   * receive(message)} of the bootstrap; an engine that can post a message to the page overrides it.
   *
   * @return What completes once the message is on its way, which is what holds a busy call back.
   */
  protected CompletableFuture<?> postRpcMessage(String message) {
    return this.eval(
        "window." + BridgeProtocol.CHANNEL + ".receive(" + ScriptUtil.quote(message) + ")");
  }

  /**
   * Hands one large part of an RPC answer to the page as bytes, with {@code additionalDataAsJson}
   * that tells the page which call and part it is. The default posts {@code fallback}, the same
   * part as a Base64 message; an engine that can share memory with the page overrides it.
   */
  protected CompletableFuture<?> postRpcBuffer(
      byte[] data, String additionalDataAsJson, Supplier<String> fallback) {
    return this.postRpcMessage(fallback.get());
  }

  /**
   * The handler of {@code name}: the window's, then the application's, then a reserved one of the
   * bridge.
   */
  private RpcHandler rpcHandler(String name) {
    RpcHandler own = this.rpcHandlers.get(name);
    if (own != null) {
      return own;
    }
    RpcHandler shared = this.application.rpcHandler(name);
    if (shared != null) {
      return shared;
    }
    return switch (name) {
      case BridgeProtocol.EVENTS_CALL -> this.pageEvents::serve;
      case BridgeProtocol.EVENT_CALL -> this::emitFromPage;
      case BridgeProtocol.OPEN_CALL -> this::openFromPage;
      case BridgeProtocol.CLOSE_CALL -> _ -> this.close();
      default -> null;
    };
  }

  /** The codec of the application, for {@link dev.ivchenko.lwjwae.rpc.RpcCall#value}. */
  final BridgeCodec rpcCodec() {
    return this.application.requireCodec();
  }

  private boolean isTrustedOrigin(String origin) {
    return this.trustedOrigins().contains(origin);
  }

  /** The origin that serves the resources of the window, and the development server in use. */
  private List<String> trustedOrigins() {
    String resources = originOf(this.resourceUrl(""));
    ApplicationParameters parameters = this.application.parameters();
    return parameters.isDevelopment()
        ? List.of(resources, originOf(parameters.devServerUrl()))
        : List.of(resources);
  }

  private void refuse(
      RpcExchange exchange, int status, Map<String, String> cors, String code, String message) {
    Map<String, String> headers = new LinkedHashMap<>(cors);
    headers.put("Content-Type", "application/json");
    exchange.reply(
        status, headers, ExchangeRpcCall.errorJson(code, message).getBytes(StandardCharsets.UTF_8));
  }

  /** {@code scheme://authority} of {@code url}, the way a browser writes an origin. */
  private static String originOf(String url) {
    URI uri = URI.create(url);
    return uri.getScheme() + "://" + uri.getRawAuthority();
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
        BridgeProtocol.bootstrapScript(
            this.bridgeTransportScript(),
            pageCodec,
            this.rpcTransportScript(),
            this.token,
            this.trustedOrigins()));
  }

  /**
   * Handles one message from the page: an RPC call or its cancellation, see {@link
   * MessageRpcExchange}. A backend calls this from the callback that the engine delivers messages
   * on, on the UI thread. A message without the token of the window is reported and dropped.
   */
  protected final void handleBridgeMessage(String message) {
    String[] fields = message.split(BridgeProtocol.SEPARATOR, 8);
    boolean call = fields[0].equals(MessageRpcExchange.TAG) && fields.length == 8;
    boolean cancel = fields[0].equals(MessageRpcExchange.CANCEL_TAG) && fields.length == 4;
    if (!call && !cancel) {
      ThrowableUtil.report(new IllegalStateException("Malformed bridge message"));
      return;
    }
    if (!fields[1].equals(this.token)) {
      ThrowableUtil.report(new IllegalStateException("Bridge message without the window token"));
      return;
    }
    if (cancel) {
      MessageRpcExchange running =
          this.messageCalls.remove(MessageRpcExchange.key(fields[2], fields[3]));
      if (running != null) {
        running.cancel();
      }
      return;
    }
    String[] callFields = new String[6];
    System.arraycopy(fields, 2, callFields, 0, 6);
    MessageRpcExchange exchange = new MessageRpcExchange(this, callFields);
    this.messageCalls.put(exchange.key(), exchange);
    this.serveRpc(exchange);
  }

  /** Drops a call that answered in full from the calls that the page can still cancel. */
  final void forgetMessageCall(MessageRpcExchange exchange) {
    this.messageCalls.remove(exchange.key(), exchange);
  }

  /** Whether this window has a binding of its own under {@code name}. */
  final boolean hasBinding(String name) {
    return this.bindingScripts.containsKey(name);
  }

  /** {@code window.lwjwae.emit}: the body is {@code typed␟name␟payload}. */
  private void emitFromPage(RpcCall call) {
    Event event = BridgeProtocol.parseEvent(call.text());
    if (event == null) {
      throw RpcException.badRequest("malformed-event", "Malformed event");
    }
    this.listeners.deliver(event.name(), event.payload(), event.typed(), this);
    this.application.deliver(event.name(), event.payload(), event.typed(), this);
  }

  /** {@code window.lwjwae.open}: answers with the ID of the new window. */
  private void openFromPage(RpcCall call) {
    WindowParameters parameters = BridgeProtocol.parseWindowParameters(call.text());
    if (parameters == null) {
      throw RpcException.badRequest("malformed-window", "Malformed window parameters");
    }
    Window opened = this.application.open(parameters);
    opened.show();
    call.reply(Long.toString(opened.id()));
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
    this.pageEvents.close();
    this.messageCalls.values().forEach(MessageRpcExchange::cancel);
    this.messageCalls.clear();
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

  private void publish(String name, BiFunction<Window, String, String> handler, boolean typed) {
    Objects.requireNonNull(name, "name");
    BridgeProtocol.checkIdentifier(name);
    this.rpcHandlers.put(name, AbstractApplication.bindingHandler(handler, typed));

    String script = BridgeProtocol.bindingScript(name, typed);
    // The page looks the handler up by name on every call, so a new handler of the same form takes
    // over without a new script; injecting it again would stack a copy on every document.
    if (script.equals(this.bindingScripts.put(name, script))) {
      return;
    }
    // Once for documents loaded from now on, once for the document already on screen.
    this.injectOnDocumentStart(script);
    this.eval(script);
  }

  private static String newToken() {
    byte[] bytes = new byte[18];
    TOKENS.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
