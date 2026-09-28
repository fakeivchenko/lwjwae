package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageButtons;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.MessageLevel;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.util.ResourceUtil;
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

  /**
   * The size of the content area, in the units of the platform.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Pixels as the process sees them: physical ones for a process that declares
   *       itself aware of densities, those of 96 DPI for one that doesn't, whose windows Windows
   *       scales; {@link Screen#scale()} tells which.
   *   <li>macOS: Points, which are two pixels each on a Retina screen.
   *   <li>Linux, GTK 3: Pixels of GTK, which the scale factor of a HiDPI display multiplies.
   *   <li>Linux, GTK 4: The size of the web view, or the default size of a window that was never
   *       shown. Pixels of GTK, as on GTK 3.
   * </ul>
   */
  WindowSize size();

  /**
   * Resizes the content area. The toolkit applies the request asynchronously.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: A size outside the limits of the window is brought within them.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: Only before the window is first shown: after that, its size is the user's,
   *       on X11 as on Wayland, and the call changes nothing.
   * </ul>
   */
  void size(int width, int height);

  /** The same as {@link #size(int, int)}. */
  default void size(WindowSize size) {
    this.size(size.width(), size.height());
  }

  /**
   * The position of the window frame on the screen, from the top left, in the units of the
   * platform.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Pixels.
   *   <li>macOS: Points, from the top left of the primary screen, the one with the menu bar.
   *   <li>Linux, GTK 3: X11: pixels. Wayland: always {@code 0, 0}, since the compositor keeps
   *       placement to itself.
   *   <li>Linux, GTK 4: Always {@code 0, 0}, on X11 as on Wayland: GTK 4 can't tell where a window
   *       is.
   * </ul>
   */
  WindowPosition position();

  /**
   * Moves the window frame.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: Points, from the top left of the primary screen.
   *   <li>Linux, GTK 3: X11: as described. Wayland: does nothing.
   *   <li>Linux, GTK 4: Does nothing, on X11 as on Wayland.
   * </ul>
   */
  void position(int x, int y);

  /** The same as {@link #position(int, int)}. */
  default void position(WindowPosition position) {
    this.position(position.x(), position.y());
  }

  /**
   * The screen that holds most of the window, or the primary one while the platform can't tell.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: As described; on Wayland, the compositor tells which screen the window is
   *       on.
   *   <li>Linux, GTK 4: The primary screen until the window was first shown.
   * </ul>
   */
  Screen screen();

  /**
   * Moves the window to the middle of the screen it's on.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The middle of the work area of its monitor, without the taskbar.
   *   <li>macOS: Where AppKit centers a window: in the middle across, a little above the middle
   *       down.
   *   <li>Linux, GTK 3: X11: the middle of the work area of its monitor. Wayland: a request that
   *       the compositor may ignore.
   *   <li>Linux, GTK 4: Does nothing, on X11 as on Wayland.
   * </ul>
   */
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
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: The limit goes on the web view, since GTK 4 has no minimum size of a
   *       window.
   * </ul>
   */
  void minimumSize(int width, int height);

  /** The same as {@link #minimumSize(int, int)}; {@link WindowSize#NONE} removes the limit. */
  default void minimumSize(WindowSize size) {
    this.minimumSize(size.width(), size.height());
  }

  /**
   * The largest size that the user can resize the content area to, or {@link WindowSize#NONE}.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: Always {@link WindowSize#NONE}: GTK 4 has no such limit.
   * </ul>
   */
  WindowSize maximumSize();

  /**
   * Keeps the user from resizing the content area beyond {@code width} by {@code height}, and
   * shrinks the window if it's larger. Zero in a dimension removes the limit there.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: Does nothing: GTK 4 has no such limit.
   * </ul>
   */
  void maximumSize(int width, int height);

  /** The same as {@link #maximumSize(int, int)}; {@link WindowSize#NONE} removes the limit. */
  default void maximumSize(WindowSize size) {
    this.maximumSize(size.width(), size.height());
  }

  /**
   * Whether the window is minimized: in the taskbar, the Dock, or wherever the desktop keeps it.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: X11: as described. Wayland: always {@code false}, since a client isn't
   *       told.
   *   <li>Linux, GTK 4: X11: as described. Wayland: always {@code false}, since a client isn't
   *       told.
   * </ul>
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
   * Puts the window into full screen, or brings it back. A request that the window manager applies
   * asynchronously.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The window drops its frame and covers its monitor; the frame and the place from
   *       before come back.
   *   <li>macOS: A space of its own, entered and left with an animation. A request made during the
   *       animation waits for its end.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: As described.
   * </ul>
   */
  void fullscreen(boolean fullscreen);

  /**
   * Whether the window stays above other windows.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: As described.
   *   <li>Linux, GTK 3: What was asked for: the window manager may not honor it.
   *   <li>Linux, GTK 4: Always {@code false}: GTK 4 has no way to ask.
   * </ul>
   */
  boolean isAlwaysOnTop();

  /**
   * Keeps the window above other windows, or not.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Granted only to the application in the foreground; {@link
   *       WindowParameters#alwaysOnTop()} works from the background too.
   *   <li>macOS: The floating window level, above the normal windows of every application.
   *   <li>Linux, GTK 3: X11: a hint that the window manager may ignore. Wayland: up to the
   *       compositor, which may ignore it.
   *   <li>Linux, GTK 4: Does nothing: GTK 4 has no way to ask.
   * </ul>
   */
  void alwaysOnTop(boolean alwaysOnTop);

  /** Whether the window has the keyboard focus. */
  boolean isFocused();

  /**
   * Brings the window to the front and gives it the keyboard focus, showing it and restoring it
   * from minimized first.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: From the background too, by joining the input of the window in front for the
   *       moment; Windows may still refuse and flash the taskbar entry.
   *   <li>macOS: Activates the whole application, which comes to the front with the window.
   *   <li>Linux, GTK 3: X11: as described. Wayland: the compositor keeps the focus, and the window
   *       may only ask for attention.
   *   <li>Linux, GTK 4: X11: as described. Wayland: the compositor keeps the focus, and the window
   *       may only ask for attention.
   * </ul>
   */
  void focus();

  /** Whether the developer tools of the engine are reachable from the context menu. */
  boolean isDevToolsEnabled();

  /**
   * Makes the developer tools of the engine reachable from the context menu, or not.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Turns the default context menu of WebView2 on too, with Reload and View source,
   *       since Inspect lives in it.
   *   <li>macOS: Lets the context menu of the page through, with Inspect Element, through {@code
   *       developerExtrasEnabled}, a private key that every embedding application uses.
   *   <li>Linux, GTK 3: Adds Inspect Element to the context menu, which is suppressed otherwise.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   */
  void devToolsEnabled(boolean devToolsEnabled);

  /**
   * The URL of the current document, or {@code null} before the first navigation.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A file of the application is at {@code http://app.localhost/PATH}.
   *   <li>macOS: A file of the application is at {@code app://local/PATH}.
   *   <li>Linux, GTK 3: A file of the application is at {@code app://local/PATH}.
   *   <li>Linux, GTK 4: A file of the application is at {@code app://local/PATH}.
   * </ul>
   */
  String url();

  /** Loads a URL. Returns before the page loads; {@link #onLoad} tells when it did. */
  void navigate(String url);

  /**
   * Replaces the document with the given markup. Either way the page has the bridge.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The document is {@code about:blank}: relative links don't reach the files of the
   *       application.
   *   <li>macOS: The document has the origin of the files of the application, so relative links
   *       resolve to the classpath.
   *   <li>Linux, GTK 3: As on macOS.
   *   <li>Linux, GTK 4: As on macOS.
   * </ul>
   */
  void html(String html);

  /**
   * Loads a file from the classpath, for example {@code app/index.html}. Relative links in the page
   * resolve against it the way they resolve on a web server. In development mode, that is, with
   * {@link ApplicationParameters#devServerUrl()} set, the development server is loaded instead.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Served under {@code http://app.localhost/}, which WebView2 intercepts before the
   *       network and Chromium treats as a secure context.
   *   <li>macOS: Served under the {@code app://local/} scheme.
   *   <li>Linux, GTK 3: Served under the {@code app://local/} scheme.
   *   <li>Linux, GTK 4: Served under the {@code app://local/} scheme.
   * </ul>
   */
  void loadResource(String path);

  /**
   * Loads {@code target}: a URL, such as {@code https://example.com}, with {@link #navigate}, or a
   * file of the application, such as {@code app/index.html}, with {@link #loadResource}.
   */
  default void load(String target) {
    if (ResourceUtil.isUrl(target)) {
      this.navigate(target);
    } else {
      this.loadResource(target);
    }
  }

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
   * without waiting for the user.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The common item dialog, modal to the window; the kinds of file are the filters
   *       of the dialog.
   *   <li>macOS: A sheet of the window. The title shows above the files, since a sheet has no title
   *       bar, and the kinds of file merge into one list of extensions, since a panel has no menu
   *       of them.
   *   <li>Linux, GTK 3: {@code GtkFileChooserNative}: the dialog of the desktop portal inside a
   *       sandbox such as Flatpak, which lets the application see the files that the user picks,
   *       and GTK's own outside one.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return The files or folders that the user picked, or none if they canceled. Canceling the
   *     future closes the dialog.
   */
  CompletableFuture<List<Path>> showOpenDialog(OpenDialogParameters parameters);

  /**
   * Shows the dialog of the platform that saves a file over this window, and returns without
   * waiting for the user. The dialog asks before it picks a file that exists; nothing is written.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The common item dialog, modal to the window.
   *   <li>macOS: A sheet of the window, with the title above the name, as for opening.
   *   <li>Linux, GTK 3: {@code GtkFileChooserNative}, the portal's inside a sandbox, as for
   *       opening.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return The file that the user picked, or empty if they canceled. Canceling the future closes
   *     the dialog.
   */
  CompletableFuture<Optional<Path>> showSaveDialog(SaveDialogParameters parameters);

  /**
   * Shows a message over this window, and returns without waiting for the user.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A message box, modal to the window. The detail follows the message after a blank
   *       line, and without a title the title bar shows the title of the window.
   *   <li>macOS: An alert sheet of the window. The message is in bold and the detail under it; the
   *       title isn't shown, since a sheet has no title bar.
   *   <li>Linux, GTK 3: A {@code GtkMessageDialog}, with the detail as its secondary text and the
   *       buttons in the language of the user, from the translations of GTK.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return {@code true} if the user chose OK or yes, {@code false} for cancel, no, or a closed
   *     dialog. Canceling the future closes the dialog.
   */
  CompletableFuture<Boolean> showMessageDialog(MessageDialogParameters parameters);

  /**
   * Shows {@code message} with an OK button, the way {@code alert} does on a page, and returns
   * without waiting for the user. The future completes when the user closed the message.
   */
  default CompletableFuture<Void> alert(String message) {
    return Window.following(
        this.showMessageDialog(MessageDialogParameters.of(message)), _ -> (Void) null);
  }

  /**
   * Asks {@code message} with OK and Cancel, the way {@code confirm} does on a page, and returns
   * without waiting for the user.
   *
   * @return {@code true} for OK. Canceling the future closes the dialog.
   */
  default CompletableFuture<Boolean> confirm(String message) {
    return this.showMessageDialog(
        MessageDialogParameters.builder()
            .message(message)
            .level(MessageLevel.QUESTION)
            .buttons(MessageButtons.OK_CANCEL)
            .build());
  }

  /**
   * Lets the user pick one file to open, of one of {@code types}, or of any type without them.
   *
   * @return The file, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickFile(FileType... types) {
    return Window.following(
        this.showOpenDialog(OpenDialogParameters.builder().fileTypes(List.of(types)).build()),
        files -> files.stream().findFirst());
  }

  /**
   * Lets the user pick files to open, of {@code types}, or of any type without them.
   *
   * @return The files, or none if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<List<Path>> pickFiles(FileType... types) {
    return this.showOpenDialog(
        OpenDialogParameters.builder().fileTypes(List.of(types)).multiple(true).build());
  }

  /**
   * Lets the user pick a folder.
   *
   * @return The folder, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickFolder() {
    return Window.following(
        this.showOpenDialog(OpenDialogParameters.builder().directories(true).build()),
        folders -> folders.stream().findFirst());
  }

  /**
   * Lets the user pick where to save a file, suggesting {@code fileName}, of one of {@code types}.
   *
   * @return The file, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickSaveFile(String fileName, FileType... types) {
    return this.showSaveDialog(
        SaveDialogParameters.builder().fileName(fileName).fileTypes(List.of(types)).build());
  }

  /**
   * Puts the window on screen, or back on it after {@link #hide()}, and brings it to the front.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: Activates the whole application too, which comes to the front with the window.
   *   <li>Linux, GTK 3: X11: as described. Wayland: the compositor decides whether the window comes
   *       to the front.
   *   <li>Linux, GTK 4: X11: as described. Wayland: the compositor decides whether the window comes
   *       to the front.
   * </ul>
   */
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

  /**
   * {@code dialog} with its answer turned by {@code answer}, where canceling the result cancels
   * {@code dialog} too, which closes it, as canceling {@code dialog} itself does.
   */
  private static <T, R> CompletableFuture<R> following(
      CompletableFuture<T> dialog, Function<T, R> answer) {
    CompletableFuture<R> result = dialog.thenApply(answer);
    result.whenComplete(
        (_, _) -> {
          if (result.isCancelled()) {
            dialog.cancel(true);
          }
        });
    return result;
  }
}
