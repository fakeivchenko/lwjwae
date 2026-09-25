package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
 * CloseAction#HIDE} and the application has a tray icon up: then it is hidden, stays open, and
 * {@link #show()} brings it back.
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

  /** The size of the content area, in the units of the platform: pixels, or points on macOS. */
  WindowSize size();

  /**
   * Resizes the content area. The toolkit applies the request asynchronously. GTK 4 applies it only
   * to a window that hasn't been on screen yet: once it has, its size is the user's.
   */
  void size(int width, int height);

  /** The same as {@link #size(int, int)}. */
  default void size(WindowSize size) {
    this.size(size.width(), size.height());
  }

  /**
   * The position of the window frame on the screen, from the top left, in the units of the
   * platform. On Wayland the compositor keeps placement to itself, and the answer is {@code 0, 0}.
   */
  WindowPosition position();

  /** Moves the window frame. Does nothing on Wayland. */
  void position(int x, int y);

  /** The same as {@link #position(int, int)}. */
  default void position(WindowPosition position) {
    this.position(position.x(), position.y());
  }

  /** Moves the window to the middle of the screen it's on. A request that Wayland may ignore. */
  void center();

  /** Whether the user can resize the window. */
  boolean isResizable();

  /** Lets the user resize the window, or not. */
  void resizable(boolean resizable);

  /** The smallest size that the user can resize the content area to, or {@link WindowSize#NONE}. */
  WindowSize minimumSize();

  /**
   * Keeps the user from resizing the content area below {@code width} by {@code height}, and grows
   * the window if it's smaller. Zero in a dimension removes the limit there.
   */
  void minimumSize(int width, int height);

  /** The same as {@link #minimumSize(int, int)}; {@link WindowSize#NONE} removes the limit. */
  default void minimumSize(WindowSize size) {
    this.minimumSize(size.width(), size.height());
  }

  /**
   * The largest size that the user can resize the content area to, or {@link WindowSize#NONE}. GTK
   * 4 has no such limit, and the answer there is always {@code NONE}.
   */
  WindowSize maximumSize();

  /**
   * Keeps the user from resizing the content area beyond {@code width} by {@code height}, and
   * shrinks the window if it's larger. Zero in a dimension removes the limit there. Does nothing on
   * GTK 4.
   */
  void maximumSize(int width, int height);

  /** The same as {@link #maximumSize(int, int)}; {@link WindowSize#NONE} removes the limit. */
  default void maximumSize(WindowSize size) {
    this.maximumSize(size.width(), size.height());
  }

  /**
   * Whether the window is minimized: in the taskbar, the Dock, or wherever the desktop keeps it.
   * Wayland doesn't tell a client, and the answer there is {@code false}.
   */
  boolean isMinimized();

  /** Minimizes the window. A request that the window manager applies asynchronously. */
  void minimize();

  /** Whether the window fills the work area of its screen, as the maximize button does. */
  boolean isMaximized();

  /** Maximizes the window. A request that the window manager applies asynchronously. */
  void maximize();

  /**
   * Brings a minimized or maximized window back to its normal size and place. Full screen is left
   * alone; {@link #fullscreen(boolean)} ends it. A request that the window manager applies
   * asynchronously.
   */
  void restore();

  /** Whether the window covers its whole screen, without a frame. */
  boolean isFullscreen();

  /**
   * Puts the window into full screen, or brings it back. On macOS, full screen is a space of its
   * own, entered with an animation. A request that the window manager applies asynchronously.
   */
  void fullscreen(boolean fullscreen);

  /**
   * Whether the window stays above other windows. On Linux, this is what was asked for: the window
   * manager may not honor it, and GTK 4 has no way to ask, so the answer there is {@code false}.
   */
  boolean isAlwaysOnTop();

  /**
   * Keeps the window above other windows, or not. Does nothing on GTK 4, and a Wayland compositor
   * may ignore it. Windows grants it only to the application in the foreground; {@link
   * WindowParameters#alwaysOnTop()} works from the background too.
   */
  void alwaysOnTop(boolean alwaysOnTop);

  /** Whether the window has the keyboard focus. */
  boolean isFocused();

  /**
   * Brings the window to the front and gives it the keyboard focus, showing it and restoring it
   * from minimized first. The system may refuse to take the focus from another application, and
   * draw attention to the window instead.
   */
  void focus();

  /** Whether the developer tools of the engine are reachable from the context menu. */
  boolean isDevToolsEnabled();

  /** Makes the developer tools of the engine reachable from the context menu, or not. */
  void devToolsEnabled(boolean devToolsEnabled);

  /** The URL of the current document, or {@code null} before the first navigation. */
  String url();

  /** Loads a URL. Returns before the page loads; {@link #onLoad} tells when it did. */
  void navigate(String url);

  /**
   * Replaces the document with the given markup. Where the engine lets the markup have an origin,
   * it gets the one of the resources, so relative links resolve to the classpath; WebView2 shows it
   * as {@code about:blank}. Either way the page has the bridge.
   */
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
   * Answers the calls that the page makes with {@code lwjwae.call(name, body)}, in this window.
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

  /**
   * Registers a listener for every change of the window: its size, its place, minimized, maximized,
   * full screen, and focus. It runs on a thread of the window, one event after the other. The page
   * hears the same events through {@code window.lwjwae.window.listen}.
   */
  EventSubscription onWindowEvent(Consumer<WindowEvent> listener);

  /**
   * Decides where a link that leaves the application goes: a click in a page of the application on
   * a link to another origin, a {@code mailto:} link, {@code window.open} of such a URL, a link
   * with {@code target="_blank"} or another request for a new window, and {@code
   * window.lwjwae.openExternal(url)}. By default, {@link Application#openExternal} opens it in the
   * browser or the mail client of the system, and the window stays where it is.
   *
   * <p>A new window of the application's own origin opens in this window instead, since a web view
   * has no tabs; a link inside the application, or a navigation of a page from elsewhere, stays in
   * the window, as does a navigation from Java.
   *
   * @param handler Receives the absolute URL on a virtual thread, and may open it anywhere, {@link
   *     #navigate} to it, or drop it; {@code null} brings the default back.
   */
  void externalLinkHandler(Consumer<String> handler);

  /**
   * Shows the dialog of the platform that opens files, or folders, over this window, and returns
   * without waiting for the user. On Linux, inside a sandbox, the dialog comes from the portal of
   * the desktop.
   *
   * @return The files or folders that the user picked, or none if they canceled. Canceling the
   *     future closes the dialog.
   */
  CompletableFuture<List<Path>> showOpenDialog(OpenDialogParameters parameters);

  /**
   * Shows the dialog of the platform that saves a file over this window, and returns without
   * waiting for the user. The dialog asks before it picks a file that exists; nothing is written.
   *
   * @return The file that the user picked, or empty if they canceled. Canceling the future closes
   *     the dialog.
   */
  CompletableFuture<Optional<Path>> showSaveDialog(SaveDialogParameters parameters);

  /**
   * Shows a message over this window, and returns without waiting for the user.
   *
   * @return {@code true} if the user chose OK or yes, {@code false} for cancel, no, or a closed
   *     dialog. Canceling the future closes the dialog.
   */
  CompletableFuture<Boolean> showMessageDialog(MessageDialogParameters parameters);

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
   * {@link #closeAction()} is {@link CloseAction#HIDE} and a tray icon is up. Returns before the
   * window has done either on some platforms; {@link #isVisible()} and {@link #isClosed()} tell
   * when it has. Unlike {@link #close()}, which always closes, this is the one to call from a close
   * button of the page.
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
