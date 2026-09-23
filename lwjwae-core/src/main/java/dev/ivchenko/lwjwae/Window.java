package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One native window with a web view inside, opened by an {@link Application}.
 *
 * <p>A window is created hidden, so that a page can load before anything appears on screen; {@link
 * #show()} puts it up. The bridge methods here belong to this window alone: a binding answers this
 * page, a listener hears the events of this page and of this window's own {@link #emit}. The
 * application-level counterparts reach every window.
 *
 * <p>A window that the user closes is closed, unless its {@link #closeAction()} is {@link
 * CloseAction#HIDE}: then it is hidden, stays open, and {@link #show()} brings it back.
 *
 * <p>Every method is safe to call from any thread. The backend forwards the call to the UI thread
 * of its toolkit, and a getter blocks until the UI thread has answered. Once the window is closed,
 * from Java or by the user, every method throws {@link IllegalStateException}, except {@link
 * #isClosed()}, {@link #close()}, and the ones that only register a listener.
 */
public interface Window extends AutoCloseable {
  /** A number that identifies this window within its application, unique for the process. */
  long id();

  /** The application that opened this window. */
  Application application();

  /** The text in the title bar. */
  String title();

  /** Changes the text in the title bar. */
  void title(String title);

  /** The width of the content area, in pixels. */
  int width();

  /** The height of the content area, in pixels. */
  int height();

  /** Resizes the content area. The toolkit applies the request asynchronously. */
  void size(int width, int height);

  /**
   * The position of the window frame on the screen, from the top left, in the units of the
   * platform. On Wayland the compositor keeps placement to itself, and the answer is {@code 0, 0}.
   */
  WindowPosition position();

  /** Moves the window frame. Does nothing on Wayland. */
  void position(int x, int y);

  /** Moves the window to the middle of the screen it's on. A request that Wayland may ignore. */
  void center();

  /** Whether the user can resize the window. */
  boolean isResizable();

  /** Lets the user resize the window, or not. */
  void resizable(boolean resizable);

  /** Whether the developer tools of the engine are reachable from the context menu. */
  boolean isDevToolsEnabled();

  /** Makes the developer tools of the engine reachable from the context menu, or not. */
  void devToolsEnabled(boolean devToolsEnabled);

  /** The URL of the current document, or {@code null} before the first navigation. */
  String url();

  /** Loads a URL. Returns before the page loads; {@link #onLoad} tells when it did. */
  void navigate(String url);

  /** Replaces the document with the given markup. */
  void html(String html);

  /**
   * Loads a file from the classpath, for example {@code app/index.html}. Relative links in the page
   * resolve against it the way they resolve on a web server. In development mode, that is, with
   * {@link ApplicationParameters#devServerUrl()} set, the development server is loaded instead.
   */
  void loadResource(String path);

  /**
   * Evaluates a script in the current document.
   *
   * @return The result, converted to a string the way the engine converts it. The future fails with
   *     {@link dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException} when the script
   *     throws, and with {@link IllegalStateException} when the window is closed.
   */
  CompletableFuture<String> eval(String script);

  /**
   * Exposes a function to the page as {@code window.NAME(payload)}, which returns a promise.
   * Binding after the page loaded works too: the current document gets the function at once.
   *
   * @param name A JavaScript identifier.
   * @param handler Called on a virtual thread with the payload as text. The value that it returns
   *     resolves the promise; an exception rejects it with the message of the root cause.
   * @throws IllegalArgumentException If {@code name} isn't a JavaScript identifier.
   */
  void bind(String name, Function<String, String> handler);

  /**
   * Exposes a function to the page that takes and returns objects through the codec. The page
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
   * Delivers an event to the page, to the listeners of this window, and to the listeners of the
   * application.
   *
   * @param name The event name.
   * @param payload The payload as text. {@code null} is delivered as an empty string.
   */
  void emit(String name, String payload);

  /**
   * The same as {@link #emit(String, String)}, with the payload encoded by the codec. The page
   * receives the decoded object.
   *
   * @throws IllegalStateException If there is no codec.
   */
  void emit(String name, Object payload);

  /**
   * Listens to an event of the page, or of {@link #emit} on this window. Listeners run on one
   * virtual thread per window, in order.
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

  /** Registers a listener for the load lifecycle of every navigation. Runs on the UI thread. */
  void onLoad(Consumer<LoadEvent> listener);

  /** Puts the window on screen, or back on it after {@link #hide()}, and brings it to the front. */
  void show();

  /**
   * Takes the window off the screen without closing it. The window keeps its page, its bindings,
   * and its place in {@link Application#windows()}, and {@link #show()} brings it back.
   */
  void hide();

  /** Whether the window is on screen: shown, and not hidden since. */
  boolean isVisible();

  /**
   * Asks the window to close the way the user does from its title bar: it closes, or hides when its
   * {@link #closeAction()} is {@link CloseAction#HIDE}. Returns before the window has done either
   * on some platforms; {@link #isVisible()} and {@link #isClosed()} tell when it has. Unlike {@link
   * #close()}, which always closes, this is the one to call from a close button of the page.
   */
  void requestClose();

  /** What the window does when the user closes it. */
  CloseAction closeAction();

  /**
   * Changes what the window does when the user closes it, for example to {@link CloseAction#HIDE}
   * while a tray icon can bring it back. Takes effect with the next request.
   */
  void closeAction(CloseAction action);

  /** Whether the native window is gone, closed from Java or by the user. */
  boolean isClosed();

  /**
   * Closes the window. Returns once the native window is gone. Idempotent. The last window to close
   * releases every thread blocked in {@link Application#run()}.
   */
  @Override
  void close();
}
