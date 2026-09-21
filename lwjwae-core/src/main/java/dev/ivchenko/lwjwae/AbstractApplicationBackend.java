package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeMessage;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The parts of an {@link ApplicationBackend} that don't depend on a particular toolkit: listener
 * bookkeeping, the closed-window signal, the blocking {@link #run()} loop, and the whole bridge
 * between Java and the page except its transport.
 *
 * <p>A subclass forwards its native calls to {@link #dispatcher()} and provides four things: the
 * two abstract members below, a call to {@link #installBridge()} after the native view exists, and
 * a call to {@link #handleBridgeMessage(String)} from the callback that the engine delivers
 * messages on.
 */
public abstract class AbstractApplicationBackend implements ApplicationBackend {
  /**
   * The executor that runs bound handlers. It uses virtual threads because a handler can block, for
   * example on I/O, on a lock, or on the UI thread itself through {@link #eval}. A pool of platform
   * threads would either starve or grow without bound.
   */
  private static final Executor HANDLER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  private final UiDispatcher dispatcher;
  private final ApplicationParameters parameters;
  private final List<Consumer<LoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
  private final Map<String, Function<String, String>> bindings = new ConcurrentHashMap<>();
  private final CompletableFuture<Void> closeSignal = new CompletableFuture<>();

  private volatile boolean closed;

  /** Binds the backend to the UI thread that its native calls run on and to its parameters. */
  protected AbstractApplicationBackend(UiDispatcher dispatcher, ApplicationParameters parameters) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.parameters = Objects.requireNonNull(parameters, "parameters");
  }

  /** Returns the UI thread that every native call of this backend runs on. */
  protected final UiDispatcher dispatcher() {
    return this.dispatcher;
  }

  /** Returns the parameters that this window was created with. */
  protected final ApplicationParameters parameters() {
    return this.parameters;
  }

  @Override
  public final void onLoad(Consumer<LoadEvent> listener) {
    this.loadListeners.add(Objects.requireNonNull(listener, "listener"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>During development, the bundled files are bypassed entirely and the development server owns
   * the whole page. The bridge is unaffected, because it's injected into every document regardless
   * of its origin.
   */
  @Override
  public void loadResource(String path) {
    if (this.parameters.isDevelopment()) {
      this.navigate(this.parameters.devServerUrl());
      return;
    }
    this.navigate(this.resourceUrl(path));
  }

  /**
   * Returns the URL under which this backend serves the classpath resource {@code path}. WebKit
   * registers a real custom scheme ({@code app://local/...}). An engine without that facility
   * intercepts requests to a reserved host instead, and reports that URL here.
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
    BridgeCodec codec = this.codec();
    this.publish(
        name,
        payload -> {
          T argument = argumentType == Void.class ? null : codec.decode(payload, argumentType);
          return codec.encode(handler.apply(argument));
        },
        true);
  }

  @Override
  public final void emit(String name, String payload) {
    Objects.requireNonNull(name, "name");
    this.eval(BridgeProtocol.emitScript(name, payload == null ? "" : payload, false));
  }

  @Override
  public final void emit(String name, Object payload) {
    Objects.requireNonNull(name, "name");
    this.eval(BridgeProtocol.emitScript(name, this.codec().encode(payload), true));
  }

  /**
   * Installs the page-side bridge runtime. A subclass calls this method after its native view
   * exists and before any page loads.
   */
  protected final void installBridge() {
    BridgeCodec codec = this.parameters.codec();
    // Without a codec, the page has no encoder: an untyped call with a non-string payload sends
    // String(payload), and a typed call fails on the Java side before it reaches the page.
    String pageCodec = codec == null ? "null" : codec.pageScript();
    this.injectOnDocumentStart(
        BridgeProtocol.bootstrapScript(this.bridgeTransportScript(), pageCodec));
  }

  /**
   * Handles one raw message from the page. This method is called on the UI thread. The handler
   * itself runs on another thread, so slow application code can't freeze the window.
   */
  protected final void handleBridgeMessage(String message) {
    BridgeMessage parsed = BridgeProtocol.parse(message);
    if (parsed == null) {
      ThrowableUtil.report(new IllegalStateException("Malformed bridge message: " + message));
      return;
    }

    Function<String, String> handler = this.bindings.get(parsed.name());
    if (handler == null) {
      this.eval(BridgeProtocol.rejectScript(parsed.id(), "No handler bound for " + parsed.name()));
      return;
    }
    CompletableFuture.supplyAsync(() -> handler.apply(parsed.payload()), HANDLER_EXECUTOR)
        .whenComplete(
            (result, failure) -> {
              if (this.isClosed()) {
                return;
              }
              this.eval(
                  failure == null
                      ? BridgeProtocol.resolveScript(parsed.id(), result)
                      : BridgeProtocol.rejectScript(parsed.id(), rootMessage(failure)));
            });
  }

  /**
   * Arranges for {@code script} to run in every document before the scripts of the document run.
   * Every engine offers this feature: {@code webkit_user_content_manager_add_script}, {@code
   * WKUserScript}, and {@code AddScriptToExecuteOnDocumentCreated}.
   */
  protected abstract void injectOnDocumentStart(String script);

  /**
   * Returns a JavaScript expression that evaluates to a function of one string, which delivers the
   * string to this backend. This is the only part of the bridge that differs between engines.
   */
  protected abstract String bridgeTransportScript();

  /**
   * Delivers {@code event} to every listener. This method is called on the UI thread, so a listener
   * that throws is reported instead of propagated. One failing listener must not skip the others or
   * unwind into the toolkit.
   */
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
   * Records that the native window is gone, and releases every thread blocked in {@link #run()}.
   */
  protected final void markClosed() {
    this.closed = true;
    this.closeSignal.complete(null);
  }

  /**
   * Fails fast when the window is gone.
   *
   * @throws IllegalStateException If {@link #close()} ran or the user closed the window.
   */
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
  public void run() {
    if (this.dispatcher.isDispatchThread()) {
      throw new IllegalStateException("run() would block the UI thread");
    }
    this.show();
    this.closeSignal.join();
  }

  private void publish(String name, Function<String, String> handler, boolean typed) {
    Objects.requireNonNull(name, "name");
    if (!IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("Not a JavaScript identifier: " + name);
    }
    this.bindings.put(name, handler);

    String script = BridgeProtocol.bindingScript(name, typed);
    // Once for documents loaded from now on, once for the document already on screen.
    this.injectOnDocumentStart(script);
    this.eval(script);
  }

  private BridgeCodec codec() {
    BridgeCodec codec = this.parameters.codec();
    if (codec == null) {
      throw new IllegalStateException(
          "No bridge codec: add a codec module to the runtime classpath, or set "
              + "ApplicationParameters.codec()");
    }
    return codec;
  }

  /**
   * The message of the innermost cause: the executor wraps handler failures, and the page wants the
   * original.
   */
  private static String rootMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
