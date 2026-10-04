package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.ExchangeRpcCall;
import dev.ivchenko.lwjwae.bridge.MessageRpcCalls;
import dev.ivchenko.lwjwae.bridge.MessageRpcExchange;
import dev.ivchenko.lwjwae.bridge.PageEvents;
import dev.ivchenko.lwjwae.bridge.PageMenu;
import dev.ivchenko.lwjwae.bridge.RpcMessageChannel;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.FileDropEvent;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.event.WindowEvents;
import dev.ivchenko.lwjwae.exception.ConstraintViolatedException;
import dev.ivchenko.lwjwae.exception.StoreFailedException;
import dev.ivchenko.lwjwae.exception.UpdateDownloadFailedException;
import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import dev.ivchenko.lwjwae.menu.ActionMenuItem;
import dev.ivchenko.lwjwae.menu.CheckMenuItem;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.menu.RoleMenuItem;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.permission.PermissionDecision;
import dev.ivchenko.lwjwae.permission.PermissionKind;
import dev.ivchenko.lwjwae.permission.PermissionRequest;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.store.StorePageCommands;
import dev.ivchenko.lwjwae.taskbar.ProgressState;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.update.DownloadedUpdate;
import dev.ivchenko.lwjwae.update.Update;
import dev.ivchenko.lwjwae.update.Updater;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.JsonUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.util.UserAgentUtil;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  /** How long a read of the clipboard for a page may take before the call fails. */
  private static final long CLIPBOARD_TIMEOUT_SECONDS = 30;

  private final AbstractApplication application;
  private final long id;
  private final List<Consumer<LoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
  private final Map<String, String> bindingScripts = new ConcurrentHashMap<>();
  private final Map<String, RpcHandler> rpcHandlers = new ConcurrentHashMap<>();
  private final EventListeners listeners = new EventListeners("lwjwae-events");
  private final PageEvents pageEvents = new PageEvents();
  private final WindowEvents windowEvents = new WindowEvents(this, this::sendToPage);
  private final String token = AbstractWindow.newToken();
  private final Set<DialogCompletion<?>> dialogs = ConcurrentHashMap.newKeySet();
  private final boolean closable;
  private final boolean maximizable;

  private volatile MessageRpcCalls messageCalls;

  /** The menu bar of this window alone, or {@code null} for the one of the application. */
  private volatile List<MenuItem> ownMenu;

  private volatile MenuCommands menuBar = MenuCommands.empty();
  private volatile MenuCommands contextMenu;

  private volatile boolean closed;
  private volatile CloseAction closeAction = CloseAction.CLOSE;
  private volatile Consumer<String> externalLinkHandler;
  private volatile Function<PermissionRequest, PermissionDecision> permissionHandler;
  private volatile EventSubscription themeSubscription;
  private final List<Consumer<FileDropEvent>> fileDropListeners = new CopyOnWriteArrayList<>();

  /**
   * Records the owner and the ID. The subclass creates the native window afterwards.
   *
   * @param application The application that opens the window.
   * @param id The ID that {@link AbstractApplication#createWindow} was given.
   * @param parameters What the window starts with; the core keeps the parts of the frame that it
   *     acts on itself.
   */
  protected AbstractWindow(AbstractApplication application, long id, WindowParameters parameters) {
    this.application = Objects.requireNonNull(application, "application");
    this.id = id;
    this.closable = parameters.closable();
    this.maximizable = parameters.maximizable();
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
  public final EventSubscription onWindowEvent(Consumer<WindowEvent> listener) {
    return this.windowEvents.listen(Objects.requireNonNull(listener, "listener"));
  }

  /**
   * Reports that the window may have changed: its size, its place, its state, or its focus. A
   * backend calls this from every toolkit callback that may mean one of these, on any thread; the
   * core reads the window and works out the events, see {@link WindowEvents}.
   */
  protected final void windowChanged() {
    this.windowEvents.changed(this.dispatcher()::post);
  }

  /** Hands a window event to the page, as {@link BridgeProtocol#WINDOW_EVENT} with JSON. */
  private void sendToPage(WindowEvent event) {
    String json =
        "{\"type\":\"%s\",\"width\":%d,\"height\":%d,\"x\":%d,\"y\":%d}"
            .formatted(
                event.type().pageName(),
                event.size().width(),
                event.size().height(),
                event.position().x(),
                event.position().y());
    this.pageEvents.send(BridgeProtocol.WINDOW_EVENT, json, false);
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
   * The user agent that the web view of this window sends: {@code engine}, the one of the engine,
   * followed by the name of the application and lwjwae with its version, see {@link UserAgentUtil}.
   * A backend sets it before the first page loads.
   */
  protected final String userAgent(String engine) {
    return UserAgentUtil.of(engine, this.application.parameters().name());
  }

  /**
   * What {@link #userAgent} adds to the user agent of the engine, for an engine that appends it
   * itself.
   */
  protected final String userAgentSuffix() {
    return UserAgentUtil.suffix(this.application.parameters().name());
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
    BridgeProtocol.checkRpcName(name);
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
    ExchangeRpcCall call =
        new ExchangeRpcCall(exchange, name, this, this.application::requireCodec, cors);
    exchange.onCancel(call::cancel);
    RpcHandler found = handler;
    HANDLER_EXECUTOR.execute(() -> call.run(found));
  }

  /**
   * The page half of the RPC transport: a JavaScript object that the bootstrap reads. {@code base}
   * is where {@code lwjwae.call} sends its requests, {@code null} to send them as messages too, and
   * {@code webview2} tells the bootstrap to take answers from WebView2 events rather than from
   * {@link #rpcMessageChannel()}. The default is {@code fetch} on the resource origin, for the
   * engines that serve a custom scheme with request bodies and streamed responses; an engine
   * without that overrides it.
   */
  protected String rpcTransportScript() {
    return "{base:"
        + ScriptUtil.quote(this.resourceUrl(RpcExchange.PATH_PREFIX.substring(1)))
        + "}";
  }

  /**
   * The way back of the message channel, see {@link RpcMessageChannel}. The default evaluates
   * {@code receive(message)} of the bootstrap; an engine that can post to the page overrides it.
   * Called once, from {@link #installBridge()}.
   */
  protected RpcMessageChannel rpcMessageChannel() {
    return message ->
        this.eval(
            "window." + BridgeProtocol.CHANNEL + ".receive(" + ScriptUtil.quote(message) + ")");
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
      case BridgeProtocol.CONTROL_CALL -> this::controlFromPage;
      case BridgeProtocol.CLIPBOARD_CALL -> this::clipboardFromPage;
      case BridgeProtocol.DIALOG_CALL -> this::dialogFromPage;
      case BridgeProtocol.MENU_CALL -> this::menuFromPage;
      case BridgeProtocol.STORE_CALL -> this::storeFromPage;
      case BridgeProtocol.UPDATE_CALL -> this::updateFromPage;
      default -> null;
    };
  }

  private boolean isTrustedOrigin(String origin) {
    return this.trustedOrigins().contains(origin);
  }

  /** The origin that serves the resources of the window, and the development server in use. */
  private List<String> trustedOrigins() {
    String resources = AbstractWindow.originOf(this.resourceUrl(""));
    ApplicationParameters parameters = this.application.parameters();
    return parameters.isDevelopment()
        ? List.of(resources, AbstractWindow.originOf(parameters.devServerUrl()))
        : List.of(resources);
  }

  private void refuse(
      RpcExchange exchange, int status, Map<String, String> cors, String code, String message) {
    Map<String, String> headers = new LinkedHashMap<>(cors);
    headers.put("Content-Type", "application/json");
    exchange.reply(
        status, headers, ExchangeRpcCall.errorJson(code, message).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The origin of {@code url}, or an empty string for {@code null} and for a URL without one. The
   * engines tell the page by its URL, or by an origin with a trailing slash.
   */
  private static String originOrEmpty(String url) {
    if (url == null) {
      return "";
    }
    try {
      URI uri = URI.create(url);
      return uri.getScheme() == null || uri.getRawAuthority() == null
          ? ""
          : uri.getScheme() + "://" + uri.getRawAuthority();
    } catch (IllegalArgumentException _) {
      return "";
    }
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
    this.messageCalls = new MessageRpcCalls(this, this.rpcMessageChannel(), this.token);
    this.themeSubscription =
        this.application.onThemeChange(
            theme -> this.pageEvents.send(BridgeProtocol.THEME_EVENT, theme.pageName(), false));
    // Once the window is complete: what the first window event compares with.
    this.dispatcher().post(this.windowEvents::start);
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
            this.trustedOrigins(),
            this.pageResizeEdges()));
  }

  /**
   * Handles one message from the page: an RPC call or its cancellation, see {@link
   * MessageRpcCalls}. A backend calls this from the callback that the engine delivers messages on,
   * on the UI thread. A message without the token of the window is reported and dropped.
   */
  protected final void handleBridgeMessage(String message) {
    MessageRpcExchange exchange = this.messageCalls.receive(message);
    if (exchange != null) {
      this.serveRpc(exchange);
    }
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
   * {@code window.lwjwae.window}: the body is an action and, for some, an argument after {@link
   * BridgeProtocol#SEPARATOR}. {@code state} answers with JSON; the others answer with nothing once
   * the window has taken the request.
   */
  private void controlFromPage(RpcCall call) {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 2);
    String argument = parts.length > 1 ? parts[1] : "";
    switch (parts[0]) {
      case "minimize" -> this.minimize();
      case "maximize" -> this.maximize();
      case "restore" -> this.restore();
      case "toggle-maximize" -> this.toggleMaximize();
      case "fullscreen" -> this.fullscreen("1".equals(argument));
      case "close" -> this.requestClose();
      case "move" -> this.beginMove();
      case "resize" -> {
        WindowEdge edge = WindowEdge.ofPageName(argument);
        if (edge == null) {
          throw RpcException.badRequest("malformed-edge", "No such edge: " + argument);
        }
        this.beginResize(edge);
      }
      case "title-bar-double-click" -> this.titleBarDoubleClicked();
      case "open-external" -> this.leave(argument);
      case "state" -> call.reply(this.dispatcher().call(this::stateJson));
      case "theme" -> call.reply(this.application.theme().pageName());
      case "progress" -> this.application.progress(AbstractWindow.parseProgress(argument));
      case "badge" -> this.application.badgeCount(AbstractWindow.parseCount(argument));
      default -> throw RpcException.badRequest("malformed-control", "No such action: " + parts[0]);
    }
  }

  /** The progress of {@code lwjwae.taskbar.progress}: the name of the state and the value. */
  private static TaskbarProgress parseProgress(String argument) {
    String[] fields = argument.split(BridgeProtocol.SEPARATOR, -1);
    ProgressState state = ProgressState.ofPageName(fields[0]);
    try {
      if (state == null || fields.length != 2) {
        throw new NumberFormatException(argument);
      }
      return new TaskbarProgress(state, Double.parseDouble(fields[1]));
    } catch (NumberFormatException _) {
      throw RpcException.badRequest("malformed-progress", "Malformed progress: " + argument);
    }
  }

  /** The count of {@code lwjwae.taskbar.badge}: a whole number from 0. */
  private static int parseCount(String argument) {
    try {
      int count = Integer.parseInt(argument);
      if (count >= 0) {
        return count;
      }
    } catch (NumberFormatException _) {
      // Answered below, as a negative count is.
    }
    throw RpcException.badRequest("malformed-badge", "Malformed count: " + argument);
  }

  /**
   * {@code window.lwjwae.store}: the body is a command of {@link StorePageCommands} as JSON, and
   * the answer its result as JSON. A malformed command or SQL that SQLite refuses answers {@code
   * 400}, a row that a constraint refuses {@code 409}.
   */
  private void storeFromPage(RpcCall call) {
    try {
      call.reply(StorePageCommands.run(this.application.store(), call.text()));
    } catch (ConstraintViolatedException e) {
      throw RpcException.conflict("constraint", e.getMessage());
    } catch (StoreFailedException e) {
      throw RpcException.badRequest("sql-error", e.getMessage());
    } catch (IllegalArgumentException e) {
      throw RpcException.badRequest("malformed-command", e.getMessage());
    }
  }

  /**
   * {@code window.lwjwae.updates}: {@code check} answers with the update as JSON, or nothing for
   * none; {@code install} checks again, downloads the update with its progress as events to this
   * window, and installs it, which quits the application. No update answers {@code 404}, one that
   * this process can't install {@code 409}, and a manifest or a file that fails its checks or its
   * download {@code 502}.
   */
  private void updateFromPage(RpcCall call) throws Exception {
    Updater updater = this.application.updater();
    try {
      Optional<Update> found = updater.check().get();
      switch (call.text()) {
        case "check" -> call.reply(found.map(AbstractWindow::updateJson).orElse(""));
        case "install" -> {
          Update update =
              found.orElseThrow(() -> RpcException.notFound("no-update", "No newer version"));
          if (!update.installable()) {
            throw RpcException.conflict(
                "not-installable", "A package manager updates this application");
          }
          DownloadedUpdate downloaded =
              updater
                  .download(
                      update,
                      fraction ->
                          this.emit(
                              BridgeProtocol.UPDATE_PROGRESS_EVENT, Double.toString(fraction)))
                  .get();
          updater.installAndRestart(downloaded);
        }
        default ->
            throw RpcException.badRequest("malformed-command", "Not a command: " + call.text());
      }
    } catch (ExecutionException e) {
      throw switch (e.getCause()) {
        case UpdateRejectedException rejected ->
            RpcException.badGateway("update-rejected", rejected.getMessage());
        case UpdateDownloadFailedException failed ->
            RpcException.badGateway("download-failed", failed.getMessage());
        default -> e;
      };
    }
  }

  private static String updateJson(Update update) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("version", update.version());
    fields.put("notes", update.notes());
    fields.put("mandatory", update.mandatory());
    fields.put("installable", update.installable());
    fields.put("size", update.artifact().size());
    return JsonUtil.write(fields);
  }

  /**
   * {@code window.lwjwae.clipboard}: {@code read-text} answers {@code 1} and the text, or {@code
   * 0}; {@code write-text} puts the text after the separator on the clipboard.
   */
  private void clipboardFromPage(RpcCall call) throws Exception {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 2);
    Clipboard clipboard = this.application.clipboard();
    switch (parts[0]) {
      case "read-text" ->
          call.reply(
              clipboard
                  .readText()
                  .get(CLIPBOARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                  .map(text -> "1" + text)
                  .orElse("0"));
      case "write-text" -> clipboard.writeText(parts.length > 1 ? parts[1] : "");
      default ->
          throw RpcException.badRequest("malformed-clipboard", "No such action: " + parts[0]);
    }
  }

  @Override
  public final EventSubscription onFileDrop(Consumer<FileDropEvent> listener) {
    Objects.requireNonNull(listener, "listener");
    this.fileDropListeners.add(listener);
    return () -> this.fileDropListeners.remove(listener);
  }

  /**
   * The user dropped files on the page. A backend calls this from the callback that hands it their
   * paths, on any thread: Java listeners and the page both hear of it. A drop with no file in it is
   * ignored.
   *
   * @param paths The files and folders, as the file manager gave them.
   * @param x Where the pointer was, from the left edge of the page.
   * @param y The same from the top edge of the page.
   */
  protected final void filesDropped(List<Path> paths, int x, int y) {
    if (paths.isEmpty() || this.closed) {
      return;
    }
    FileDropEvent event = new FileDropEvent(this, paths, x, y);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paths", event.paths().stream().map(Path::toString).toList());
    payload.put("x", x);
    payload.put("y", y);
    this.pageEvents.send(BridgeProtocol.FILES_EVENT, JsonUtil.write(payload), false);
    for (Consumer<FileDropEvent> listener : this.fileDropListeners) {
      HANDLER_EXECUTOR.execute(
          () -> {
            try {
              listener.accept(event);
            } catch (Throwable t) {
              ThrowableUtil.report(t);
            }
          });
    }
  }

  @Override
  public final double zoom() {
    this.checkOpen();
    return this.dispatcher().call(this::currentZoom);
  }

  @Override
  public final void zoom(double factor) {
    this.checkOpen();
    if (!(factor >= MINIMUM_ZOOM && factor <= MAXIMUM_ZOOM)) {
      throw new IllegalArgumentException(
          "The zoom is from " + MINIMUM_ZOOM + " to " + MAXIMUM_ZOOM + ": " + factor);
    }
    this.dispatcher().run(() -> this.presentZoom(factor));
  }

  /** Zooms the page of the web view to {@code factor}, already checked. Called on the UI thread. */
  protected abstract void presentZoom(double factor);

  /** The zoom that the web view shows now. Called on the UI thread. */
  protected abstract double currentZoom();

  @Override
  public final void icon(byte[] png) {
    this.checkOpen();
    if (png != null && !AbstractWindow.isPng(png)) {
      throw new IllegalArgumentException("The icon of a window is a PNG image");
    }
    this.dispatcher().run(() -> this.presentIcon(png));
  }

  /** Whether {@code bytes} start with the signature of a PNG file. */
  private static boolean isPng(byte[] bytes) {
    byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    return bytes.length > signature.length
        && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
  }

  /**
   * Shows {@code png}, already checked, as the icon of the window, or the icon of the platform for
   * {@code null}. Called on the UI thread.
   */
  protected abstract void presentIcon(byte[] png);

  @Override
  public final void externalLinkHandler(Consumer<String> handler) {
    this.externalLinkHandler = handler;
  }

  /**
   * The page asked for a new window, with {@code target="_blank"} or {@code window.open}, and the
   * engine left the decision to the host. A backend calls this from the callback of that request,
   * on the UI thread, and opens no window itself: a URL of the application's own origin opens in
   * this window, since a web view has no tabs, one of the web or {@code mailto:} goes to {@link
   * #externalLinkHandler}, and anything else, {@code about:blank} of an empty {@code window.open}
   * included, is dropped.
   */
  protected final void newWindowRequested(String url) {
    if (url == null || url.isEmpty()) {
      return;
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException _) {
      return;
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (uri.getRawAuthority() != null && this.isTrustedOrigin(AbstractWindow.originOf(url))) {
      this.dispatcher().post(() -> this.navigate(url));
    } else if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto")) {
      HANDLER_EXECUTOR.execute(() -> this.leaveReporting(url));
    }
  }

  @Override
  public final void permissionHandler(Function<PermissionRequest, PermissionDecision> handler) {
    this.permissionHandler = handler;
  }

  /**
   * The page asked for permissions, and the engine waits for the answer. A backend calls this from
   * the callback of that request, on the UI thread, and answers the engine with the result.
   *
   * @param origin The URL or the origin of the page, or {@code null} where the engine doesn't tell;
   *     the handler gets the origin of it, {@code scheme://authority}.
   * @param kinds What the page asks for, all of it at once.
   * @return {@code true} only if the handler granted every kind; with no handler, or one that
   *     throws, {@code false}.
   */
  protected final boolean permissionRequested(String origin, PermissionKind... kinds) {
    Function<PermissionRequest, PermissionDecision> handler = this.permissionHandler;
    if (handler == null || kinds.length == 0) {
      return false;
    }
    try {
      for (PermissionKind kind : kinds) {
        PermissionRequest request =
            new PermissionRequest(kind, AbstractWindow.originOrEmpty(origin));
        if (handler.apply(request) != PermissionDecision.GRANT) {
          return false;
        }
      }
      return true;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return false;
    }
  }

  /** Hands a link that leaves the application to the handler of the window, or to the system. */
  private void leave(String url) {
    Consumer<String> handler = this.externalLinkHandler;
    if (handler != null) {
      handler.accept(url);
    } else {
      this.application.openExternal(url);
    }
  }

  private void leaveReporting(String url) {
    try {
      this.leave(url);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  @Override
  public final CompletableFuture<List<Path>> showOpenDialog(OpenDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.showDialog(completion -> this.presentOpenDialog(parameters, completion));
  }

  @Override
  public final CompletableFuture<Optional<Path>> showSaveDialog(SaveDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.showDialog(completion -> this.presentSaveDialog(parameters, completion));
  }

  @Override
  public final CompletableFuture<Boolean> showMessageDialog(MessageDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.showDialog(completion -> this.presentMessageDialog(parameters, completion));
  }

  /**
   * Shows a dialog on the UI thread. The window keeps the dialogs that are up, and cancels them
   * when it closes, which closes them: a dialog must not outlive the window that it belongs to.
   */
  private <T> CompletableFuture<T> showDialog(Consumer<DialogCompletion<T>> present) {
    this.checkOpen();
    DialogCompletion<T> completion = new DialogCompletion<>(this.dispatcher());
    this.dialogs.add(completion);
    completion.future().whenComplete((_, _) -> this.dialogs.remove(completion));
    this.dispatcher()
        .post(
            () -> {
              try {
                this.checkOpen();
                present.accept(completion);
              } catch (Throwable t) {
                completion.fail(t);
              }
            });
    return completion.future();
  }

  /**
   * Shows the dialog of the platform that opens files or folders, parented to this window, on the
   * UI thread. The backend registers how to close it with {@link DialogCompletion#onCancel} before
   * it shows it, and completes {@code completion} with the picked paths, none for a cancel.
   */
  protected abstract void presentOpenDialog(
      OpenDialogParameters parameters, DialogCompletion<List<Path>> completion);

  /** The same as {@link #presentOpenDialog} for the dialog that saves a file. */
  protected abstract void presentSaveDialog(
      SaveDialogParameters parameters, DialogCompletion<Optional<Path>> completion);

  /**
   * The same as {@link #presentOpenDialog} for a message: {@code true} for OK or yes, {@code false}
   * for anything else.
   */
  protected abstract void presentMessageDialog(
      MessageDialogParameters parameters, DialogCompletion<Boolean> completion);

  /**
   * {@code window.lwjwae.dialog}: the body is {@code open}, {@code save}, or {@code message}, and
   * the fields of the dialog, see {@link BridgeProtocol#parseOpenDialog} and its siblings. The
   * answer is the picked paths separated by {@link BridgeProtocol#SEPARATOR}, empty for none, or
   * {@code 1} and {@code 0} for a message. A page that abandons the call closes the dialog.
   */
  private void dialogFromPage(RpcCall call) throws Exception {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 2);
    String fields = parts.length > 1 ? parts[1] : "";
    // The future of the dialog itself, which a cancellation must reach to close it, and the answer.
    CompletableFuture<?> dialog;
    CompletableFuture<String> answer;
    switch (parts[0]) {
      case "open" -> {
        OpenDialogParameters parameters = BridgeProtocol.parseOpenDialog(fields);
        CompletableFuture<List<Path>> open =
            parameters == null ? null : this.showOpenDialog(parameters);
        dialog = open;
        answer = open == null ? null : open.thenApply(AbstractWindow::joinPaths);
      }
      case "save" -> {
        SaveDialogParameters parameters = BridgeProtocol.parseSaveDialog(fields);
        CompletableFuture<Optional<Path>> save =
            parameters == null ? null : this.showSaveDialog(parameters);
        dialog = save;
        answer = save == null ? null : save.thenApply(path -> path.map(Path::toString).orElse(""));
      }
      case "message" -> {
        MessageDialogParameters parameters = BridgeProtocol.parseMessageDialog(fields);
        CompletableFuture<Boolean> message =
            parameters == null ? null : this.showMessageDialog(parameters);
        dialog = message;
        answer = message == null ? null : message.thenApply(yes -> yes ? "1" : "0");
      }
      default -> {
        dialog = null;
        answer = null;
      }
    }
    if (answer == null) {
      throw RpcException.badRequest("malformed-dialog", "Malformed dialog: " + parts[0]);
    }
    // A page that gives the call up interrupts this thread.
    try {
      if (call.isCancelled()) {
        throw new InterruptedException();
      }
      call.reply(answer.get());
    } catch (InterruptedException e) {
      dialog.cancel(false);
      throw e;
    }
  }

  @Override
  public final List<MenuItem> menu() {
    List<MenuItem> own = this.ownMenu;
    return own != null ? own : this.application.menu();
  }

  @Override
  public final void menu(List<MenuItem> items) {
    List<MenuItem> bar = AbstractWindow.requireMenuBar(items);
    this.checkOpen();
    this.ownMenu = bar;
    this.refreshMenuBar();
  }

  @Override
  public final void useApplicationMenu() {
    this.checkOpen();
    this.ownMenu = null;
    this.refreshMenuBar();
  }

  /**
   * Starts with the menu bar of {@code parameters}, or the one of the application. The application
   * calls this once, as it opens the window.
   */
  final void initializeMenu(List<MenuItem> items) {
    this.ownMenu = items == null ? null : AbstractWindow.requireMenuBar(items);
    if (!this.menu().isEmpty()) {
      this.refreshMenuBar();
    }
  }

  /** The menu of the application changed: this window shows it, unless it has one of its own. */
  final void applicationMenuChanged() {
    if (this.ownMenu == null) {
      this.refreshMenuBar();
    }
  }

  /**
   * The menu bar that the window shows. A backend asks, on the UI thread, when it needs the bar
   * again, as macOS does when the window comes to the front.
   */
  protected final MenuCommands menuBar() {
    return this.menuBar;
  }

  /** Builds the bar from the menu of this moment, on the UI thread, so the last change wins. */
  private void refreshMenuBar() {
    this.dispatcher()
        .run(
            () -> {
              if (this.closed) {
                return;
              }
              MenuCommands commands = new MenuCommands(this.menu());
              this.menuBar = commands;
              this.presentMenuBar(commands);
            });
  }

  /**
   * A copy of {@code items} as a menu bar.
   *
   * @throws IllegalArgumentException If an entry isn't a submenu.
   */
  static List<MenuItem> requireMenuBar(List<MenuItem> items) {
    Objects.requireNonNull(items, "items");
    for (MenuItem item : items) {
      if (!(item instanceof SubmenuItem)) {
        throw new IllegalArgumentException("A menu bar holds submenus only, not " + item);
      }
    }
    return List.copyOf(items);
  }

  @Override
  public final void contextMenu(List<MenuItem> items) {
    Objects.requireNonNull(items, "items");
    this.checkOpen();
    this.contextMenu = items.isEmpty() ? null : new MenuCommands(items);
    // Once for documents loaded from now on, once for the document already on screen.
    String flag =
        "if (window.%s) window.%s.contextMenu = %b;"
            .formatted(BridgeProtocol.CHANNEL, BridgeProtocol.CHANNEL, !items.isEmpty());
    this.injectOnDocumentStart(flag);
    this.eval(flag);
  }

  @Override
  public final CompletableFuture<Void> showContextMenu(List<MenuItem> items) {
    return AbstractWindow.following(this.popUp(new MenuCommands(items), null), _ -> null);
  }

  /**
   * Opens {@code commands} at {@code place} in the page, or at the pointer for {@code null}, and
   * completes with the number of the entry picked, 0 for none, once that entry has started.
   */
  private CompletableFuture<Integer> popUp(MenuCommands commands, WindowPosition place) {
    CompletableFuture<Integer> shown =
        this.showDialog(completion -> this.presentContextMenu(commands, place, completion));
    return AbstractWindow.following(
        shown,
        id -> {
          this.menuItemPicked(commands, id);
          return id;
        });
  }

  /**
   * {@code source} with its result turned by {@code then}, where canceling the result cancels
   * {@code source} too, which closes the menu, as canceling {@code source} itself does.
   */
  private static <T, R> CompletableFuture<R> following(
      CompletableFuture<T> source, Function<T, R> then) {
    CompletableFuture<R> result = source.thenApply(then);
    result.whenComplete(
        (_, _) -> {
          if (result.isCancelled()) {
            source.cancel(false);
          }
        });
    return result;
  }

  /**
   * Does what the entry {@code id} of {@code commands} does: runs its action off the UI thread,
   * flips its check mark, or plays its role. A backend calls this for a pick of the menu bar and
   * for its accelerators; 0 and a number of no entry do nothing.
   */
  protected final void menuItemPicked(MenuCommands commands, int id) {
    switch (commands.item(id)) {
      case ActionMenuItem action -> HandlerUtil.runOffTheUiThread(action.action());
      case CheckMenuItem check -> {
        boolean state = commands.toggle(id);
        if (check.onToggle() != null) {
          HandlerUtil.runOffTheUiThread(() -> check.onToggle().accept(state));
        }
      }
      case RoleMenuItem role -> this.play(role.role());
      case null, default -> {}
    }
  }

  /**
   * Plays {@code role} on this window. An editing role waits for the menu to close, which gives the
   * keyboard back to the page, on the UI thread; the rest run off it, like an action.
   */
  private void play(MenuRole role) {
    if (role.isEditing()) {
      this.dispatcher()
          .post(
              () -> {
                if (!this.closed) {
                  this.performEditing(role);
                }
              });
      return;
    }
    HandlerUtil.runOffTheUiThread(
        switch (role) {
          case CLOSE_WINDOW -> this::requestClose;
          case MINIMIZE -> this::minimize;
          case FULLSCREEN -> () -> this.fullscreen(!this.isFullscreen());
          case QUIT -> this.application::quit;
          case UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL ->
              throw new IllegalStateException("An editing role: " + role);
        });
  }

  /**
   * Shows {@code commands} as the menu bar of the window, on the UI thread, in place of the one
   * before; an empty menu takes the bar away. The backend reports a pick and an accelerator to
   * {@link #menuItemPicked}.
   */
  protected abstract void presentMenuBar(MenuCommands commands);

  /**
   * Opens {@code commands} as a menu over the window, on the UI thread: at {@code place}, in the
   * pixels of the page from its top left corner, or at the pointer for {@code null}. The backend
   * registers how to close it with {@link DialogCompletion#onCancel} and completes {@code picked}
   * with the number of the entry picked, 0 for none, once the menu is closed; the core does what
   * the entry does.
   */
  protected abstract void presentContextMenu(
      MenuCommands commands, WindowPosition place, DialogCompletion<Integer> picked);

  /** Runs the editing command of {@code role} on the page, on the UI thread. */
  protected abstract void performEditing(MenuRole role);

  /**
   * {@code window.lwjwae.menu} and the context menu of the page: the body is {@code context} or
   * {@code popup}, the place, and for a popup the entries, see {@link BridgeProtocol#parseMenu}. A
   * popup answers with the ID that the page gave the entry picked, empty for none. A page that
   * abandons the call closes the menu.
   */
  private void menuFromPage(RpcCall call) throws Exception {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 4);
    if (parts.length < 3) {
      throw RpcException.badRequest("malformed-menu", "Malformed menu");
    }
    WindowPosition place;
    try {
      place =
          parts[1].isEmpty() || parts[2].isEmpty()
              ? null
              : new WindowPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    } catch (NumberFormatException _) {
      throw RpcException.badRequest(
          "malformed-menu", "Malformed place: " + parts[1] + ", " + parts[2]);
    }
    MenuCommands commands;
    Map<MenuItem, String> pageIds;
    switch (parts[0]) {
      case "context" -> {
        commands = this.contextMenu;
        pageIds = Map.of();
      }
      case "popup" -> {
        PageMenu menu = parts.length < 4 ? null : BridgeProtocol.parseMenu(parts[3]);
        if (menu == null) {
          throw RpcException.badRequest("malformed-menu", "Malformed entries");
        }
        commands = new MenuCommands(menu.items());
        pageIds = menu.pageIds();
      }
      default -> throw RpcException.badRequest("malformed-menu", "Malformed menu: " + parts[0]);
    }
    if (commands == null || commands.isEmpty()) {
      call.reply("");
      return;
    }
    CompletableFuture<Integer> picked = this.popUp(commands, place);
    // A page that gives the call up interrupts this thread.
    try {
      if (call.isCancelled()) {
        throw new InterruptedException();
      }
      MenuItem item = commands.item(picked.get());
      call.reply(item == null ? "" : pageIds.getOrDefault(item, ""));
    } catch (InterruptedException e) {
      picked.cancel(false);
      throw e;
    }
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream().map(Path::toString).collect(Collectors.joining(BridgeProtocol.SEPARATOR));
  }

  private void toggleMaximize() {
    if (this.isMaximized()) {
      this.restore();
    } else {
      this.maximize();
    }
  }

  /** What {@code window.lwjwae.window.state()} resolves to. Call on the UI thread. */
  private String stateJson() {
    WindowSize size = this.size();
    WindowPosition position = this.position();
    return ("{\"width\":%d,\"height\":%d,\"x\":%d,\"y\":%d,\"minimized\":%b,\"maximized\":%b,"
            + "\"fullscreen\":%b,\"focused\":%b,\"resizable\":%b}")
        .formatted(
            size.width(),
            size.height(),
            position.x(),
            position.y(),
            this.isMinimized(),
            this.isMaximized(),
            this.isFullscreen(),
            this.isFocused(),
            this.isResizable());
  }

  /**
   * A double click on a drag region of the page, which stands in for the title bar: toggles
   * maximized, the way a double click on a title bar does, unless the window can't be maximized or
   * resized. A backend whose desktop lets the user choose another action overrides it.
   */
  protected void titleBarDoubleClicked() {
    if (this.maximizable && this.isResizable()) {
      this.toggleMaximize();
    }
  }

  /**
   * Hands the pointer to the window manager, which moves the window until the user lets go of the
   * button, the way a drag on the title bar does. The page calls this while the button is down,
   * from a drag region; on any thread.
   */
  protected abstract void beginMove();

  /**
   * Hands the pointer to the window manager, which resizes the window from {@code edge} until the
   * user lets go of the button. The page calls this while the button is down; on any thread. Does
   * nothing when the window isn't resizable.
   */
  protected abstract void beginResize(WindowEdge edge);

  /**
   * The edges at which the page offers to resize the window, because the window has no native
   * resize edges there: a strip along each edge takes the pointer and calls {@link #beginResize}.
   * The default is none; a backend whose windows without a title bar lose some of their edges lists
   * those.
   */
  protected List<WindowEdge> pageResizeEdges() {
    return List.of();
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
    // Straight after the flag: a thread that sees the window closed must not find it in the list.
    this.application.windowClosed(this);
    EventSubscription themeSubscription = this.themeSubscription;
    if (themeSubscription != null) {
      themeSubscription.unlisten();
    }
    this.pageEvents.close();
    this.windowEvents.shutdown();
    this.dialogs.forEach(dialog -> dialog.future().cancel(false));
    MessageRpcCalls calls = this.messageCalls;
    if (calls != null) {
      calls.cancelAll();
    }
    this.listeners.shutdown();
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

  /**
   * Whether a close that the user asked for should be refused, because the window isn't {@link
   * WindowParameters#closable()}. A backend asks before {@link #hidesOnCloseRequest()}, and cancels
   * the close when the answer is {@code true}, whatever the desktop let through: a shortcut, a menu
   * of the taskbar, or a close button that the platform shows anyway.
   */
  protected final boolean refusesCloseRequest() {
    return !this.closable;
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
