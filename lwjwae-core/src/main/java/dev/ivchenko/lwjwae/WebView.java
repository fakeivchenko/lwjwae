package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.FileDropEvent;
import dev.ivchenko.lwjwae.permission.PermissionDecision;
import dev.ivchenko.lwjwae.permission.PermissionRequest;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The web view of one window, as a browser engine rather than as a page: its zoom, its developer
 * tools, and what it does with links, permissions, and files that leave or enter the page. {@link
 * Window#webView()} hands it out.
 *
 * <p>What the page shows, and the bridge to it, stay on {@link Window}: this is the part of the
 * engine that the page can't reach, which only Java decides.
 *
 * <p>Every method is safe to call from any thread. Once the window is closed, {@link #zoom()},
 * {@link #zoom(double)}, {@link #isDevToolsEnabled()}, and {@link #devToolsEnabled(boolean)} throw
 * {@link IllegalStateException}; the others only register a handler or a listener.
 */
public interface WebView {
  /** The smallest zoom of a page, 25%. */
  double MINIMUM_ZOOM = 0.25;

  /** The largest zoom of a page, 500%. */
  double MAXIMUM_ZOOM = 5.0;

  /**
   * The zoom of the page: 1 is 100%, 1.5 is 150%, and 0.5 is 50%. A page starts at 1, and the
   * answer is what the engine shows now, which is what {@link #zoom(double)} set last.
   *
   * @throws IllegalStateException If the window is closed.
   */
  double zoom();

  /**
   * Zooms the page, the whole of its content, as the zoom of a browser does: text, images, and
   * layout grow together, and the page sees a narrower viewport in CSS pixels. The user can't zoom
   * the page with the keyboard or the wheel; only Java does. The zoom belongs to the window and
   * stays through navigations.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The zoom factor of the web view.
   *   <li>macOS: The page zoom of the web view, which needs macOS 11 or later.
   *   <li>Linux, GTK 3: The zoom level of the web view.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @param factor The zoom, from {@link #MINIMUM_ZOOM} to {@link #MAXIMUM_ZOOM}.
   * @throws IllegalArgumentException If {@code factor} is outside that range, or isn't a number.
   * @throws IllegalStateException If the window is closed.
   */
  void zoom(double factor);

  /**
   * Whether the developer tools of the engine are reachable from the context menu.
   *
   * @throws IllegalStateException If the window is closed.
   */
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
   *
   * @throws IllegalStateException If the window is closed.
   */
  void devToolsEnabled(boolean devToolsEnabled);

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
   *     Window#navigate} to it, or drop it; {@code null} brings the default back.
   */
  void externalLinkHandler(Consumer<String> handler);

  /**
   * Decides what a page may use that needs the permission of the user: the camera, the microphone,
   * the position, and notifications. By default, every request is denied, on every platform, so
   * that the page fails the same way everywhere and no engine shows a prompt of its own.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Every kind. The prompt of WebView2 never shows.
   *   <li>macOS: The camera and the microphone. After the handler grants one, the system asks the
   *       user for its own permission of the application, which needs the usage description of the
   *       camera or the microphone in {@code Info.plist}. WKWebView has no hook for the position
   *       and notifications, so the handler doesn't hear of them there.
   *   <li>Linux, GTK 3 and GTK 4: The camera and the microphone. The position and notifications are
   *       always denied, and the handler doesn't hear of them.
   * </ul>
   *
   * @param handler Receives each request on the UI thread, so it must answer without waiting. A
   *     handler that throws denies the request. {@code null} brings the default back.
   */
  void permissionHandler(Function<PermissionRequest, PermissionDecision> handler);

  /**
   * Calls {@code listener} when the user drops files from the file manager of the desktop onto the
   * page, with their paths, which a page can't read from the files that the browser gives it. A
   * page hears the same through {@code lwjwae.files.listen}.
   *
   * <p>A file dropped on a page doesn't open in the window: the library stops the engine from
   * navigating to it, unless the page handles {@code drop} itself and stops it first. The {@code
   * dragenter}, {@code dragover}, and {@code drop} events still reach the page, for the highlight
   * of a drop zone.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Through the files that a {@code drop} event of the page hands to the host, which
   *       needs the WebView2 runtime 1.0.1264 or later. The window must have the focus of the page
   *       script, as for any drop.
   *   <li>macOS: The file URLs on the pasteboard of the drag, which the web view reads as it takes
   *       the drop, at the place of the {@code drop} event of the page.
   *   <li>Linux, GTK 3: The URI list of the drop, so a file that lives only in a remote location
   *       that the file manager mounted is left out.
   *   <li>Linux, GTK 4: The files that the drag offers, read as it comes over the page, at the
   *       place of the {@code drop} event of the page; a file that isn't local is left out.
   * </ul>
   *
   * @param listener Receives the events on a thread of the window, one drop after the other.
   * @return The subscription, to stop listening.
   */
  EventSubscription onFileDrop(Consumer<FileDropEvent> listener);
}
