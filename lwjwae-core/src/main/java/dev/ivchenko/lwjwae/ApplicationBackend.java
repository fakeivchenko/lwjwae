package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A single native application window with a web view inside.
 *
 * <p>Implementations own a native toolkit that is almost always single-threaded. You can call the
 * methods declared here from any thread: the backend forwards them to its UI thread. Getters block
 * until the UI thread has answered.
 */
public interface ApplicationBackend extends AutoCloseable, Runnable {
  /** Returns the window title. */
  String title();

  /** Sets the window title. */
  void title(String title);

  /**
   * Returns the web engine that draws the page, with its version, for example {@code "WebKitGTK
   * 2.48.3"} or {@code "WebView2 138.0.3351.65"}. This value is for diagnostics, not a contract:
   * the exact wording belongs to the backend.
   */
  String engine();

  /** Returns the current window width, in pixels. */
  int width();

  /** Returns the current window height, in pixels. */
  int height();

  /**
   * Resizes the window. Before {@link #show()}, this method sets the size that the window opens
   * with.
   */
  void size(int width, int height);

  /** Checks whether the user can resize the window. */
  boolean isResizable();

  /** Allows or forbids resizing by the user. */
  void resizable(boolean resizable);

  /** Checks whether the page can open the developer tools of the engine. */
  boolean isDevToolsEnabled();

  /**
   * Allows or forbids the developer tools of the engine, for example the WebKit inspector or the
   * Chrome DevTools.
   *
   * @param devToolsEnabled If true, the user can open the developer tools from the context menu or
   *     the usual keyboard shortcut. If false, the tools are unavailable, which is the state a
   *     shipped application wants.
   */
  void devToolsEnabled(boolean devToolsEnabled);

  /** Returns the URL currently displayed, or {@code null} before the first navigation. */
  String url();

  /** Starts loading {@code url}. Progress arrives through {@link #onLoad(Consumer)}. */
  void navigate(String url);

  /** Replaces the page with {@code html}, as if loaded from an unnamed origin. */
  void html(String html);

  /**
   * Loads a page that ships inside the application, addressed by its classpath path, for example
   * {@code loadResource("app/index.html")}.
   *
   * <p>The page is served under a custom scheme, so links, stylesheets, scripts, and {@code fetch}
   * calls inside it resolve relative to that path, the same way they resolve on a web server.
   */
  void loadResource(String path);

  /**
   * Evaluates {@code script} in the current page and completes with its result rendered as a
   * string. The future fails with {@link
   * dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException} if the script throws.
   *
   * <p>The result must be a value that the engine can return: a string, a number, or a boolean. An
   * object that the engine can't serialize, above all a {@code Promise}, fails the future instead
   * of being awaited. To run something asynchronous, let the script store its outcome ({@code
   * someAsyncCall().then(v => window.result = v); undefined;}) and read the outcome afterwards, or
   * drive the call from the page through {@link #bind(String, Function)}.
   */
  CompletableFuture<String> eval(String script);

  /**
   * Exposes {@code handler} to the page as {@code window.NAME(payload)}, which returns a promise
   * that resolves to the return value of the handler.
   *
   * <p>This is the direction from the page to Java. {@link #emit(String, String)} is the other
   * direction. Handlers run off the UI thread, so blocking work inside a handler doesn't freeze the
   * window. A handler that throws rejects the page-side promise instead of being ignored.
   *
   * @param name A JavaScript identifier to publish on {@code window}.
   * @param handler The function that receives the payload as text and returns the answer as text.
   * @throws IllegalArgumentException If {@code name} isn't a JavaScript identifier.
   */
  void bind(String name, Function<String, String> handler);

  /**
   * The typed form of {@link #bind(String, Function)}. The page passes any value, the handler
   * receives it as {@code argumentType} decoded from JSON, and the return value of the handler goes
   * back as JSON, so the page-side promise resolves to an object. {@code Void.class} declares a
   * handler that takes no argument.
   *
   * <p>This method needs a {@link BridgeCodec}: a codec module on the classpath, or one given in
   * {@link ApplicationParameters#codec()}.
   *
   * @throws IllegalStateException If no codec is available.
   */
  <T, R> void bind(String name, Class<T> argumentType, Function<T, R> handler);

  /**
   * Emits an event to every listener of {@code name}: the ones on the page, registered with {@code
   * window.lwjwae.listen(name, handler)} or {@code once}, and the ones in Java, registered with
   * {@link #listen}. A page listener receives {@code { event, id, payload }}; a Java one an {@link
   * Event}. This is the direction from Java to the page, without writing a script.
   */
  void emit(String name, String payload);

  /**
   * The same as {@link #emit(String, String)}, with {@code payload} encoded by the codec, so page
   * listeners receive the value and typed Java listeners decode it.
   *
   * @throws IllegalStateException If no {@link BridgeCodec} is available.
   */
  void emit(String name, Object payload);

  /**
   * Registers a listener for every event named {@code name}, whether the page emits it with {@code
   * window.lwjwae.emit(name, payload)} or Java does with {@link #emit}. The listener runs off the
   * UI thread; a listener that throws is reported, not propagated.
   *
   * @return The handle that removes the listener.
   */
  EventSubscription listen(String name, Consumer<Event> listener);

  /**
   * The same as {@link #listen(String, Consumer)}, with the payload decoded as {@code type} before
   * the listener sees it. {@code String.class} takes the text as it is when the event was emitted
   * untyped.
   *
   * @throws IllegalStateException If no {@link BridgeCodec} is available.
   */
  <T> EventSubscription listen(String name, Class<T> type, Consumer<T> listener);

  /** The same as {@link #listen(String, Consumer)}, for the next event only. */
  EventSubscription once(String name, Consumer<Event> listener);

  /** The same as {@link #listen(String, Class, Consumer)}, for the next event only. */
  <T> EventSubscription once(String name, Class<T> type, Consumer<T> listener);

  /** Registers a listener that's notified on every page load transition. */
  void onLoad(Consumer<LoadEvent> listener);

  /** Makes the window visible. */
  void show();

  /**
   * Blocks the calling thread until the window is closed, either by the user or by {@link
   * #close()}. This method shows the window first. Don't call it from the UI thread of the backend.
   */
  @Override
  void run();

  /**
   * Checks whether the window was destroyed, by the user or by {@link #close()}. Every other call
   * then fails.
   */
  boolean isClosed();

  /** Destroys the window and releases the native resources. This method is idempotent. */
  @Override
  void close();
}
