package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.WebKitGtk;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to WebKitGTK 6.0, the GTK 4 variant: what it has of its own, and {@link #SHARED}, the
 * part that it has in common with WebKitGTK 4.1.
 *
 * <p>The API is that of 4.1 with differences that matter here: a web view is created plain and
 * comes with a user content manager of its own, a script message handler is registered for a script
 * world, a script message arrives as the {@code JSCValue} itself, and the network state, cookies
 * and downloads included, lives in a {@code WebKitNetworkSession} rather than in the web context.
 */
@UtilityClass
public class WebKit {
  /** The library of WebKitGTK that this backend loads. */
  public final String LIBRARY = "libwebkitgtk-6.0.so.4";

  private final SymbolLookup WEBKIT = NativeLibraries.load(LIBRARY, "libwebkitgtk-6.0.so");
  private final SymbolLookup JSC =
      NativeLibraries.load("libjavascriptcoregtk-6.0.so.1", "libjavascriptcoregtk-6.0.so");

  /** What WebKitGTK 6.0 has in common with 4.1, bound from the libraries of this backend. */
  public final WebKitGtk SHARED = new WebKitGtk(WEBKIT, JSC);

  private final MethodHandle WEB_VIEW_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_new", Signatures.POINTER_VOID);
  private final MethodHandle WEB_VIEW_SET_BACKGROUND_COLOR =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_set_background_color", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_USER_CONTENT_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_get_user_content_manager", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_network_session_get_cookie_manager", Signatures.POINTER_POINTER);
  private final MethodHandle NETWORK_SESSION_GET_DEFAULT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_network_session_get_default", Signatures.POINTER_VOID);
  private final MethodHandle REGISTER_SCRIPT_MESSAGE_HANDLER =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_user_content_manager_register_script_message_handler",
          Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_DOWNLOAD_SOURCE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_get_network_session", Signatures.POINTER_POINTER);

  private final AtomicBoolean CONTEXT_RETAINED = new AtomicBoolean();

  /**
   * Takes one permanent reference to the default {@code WebKitWebContext} and to the default {@code
   * WebKitNetworkSession}.
   *
   * <p>WebKit installs an {@code atexit} handler that drops its reference to each of the two
   * singletons. If the last web view was already destroyed by then, the count drops to zero, and
   * WebKit aborts (SIGABRT) while it disposes the session and its website data manager: every JVM
   * that used a web view would end with a core dump. WebKitGTK 6.0 moved the network state out of
   * the context into the session, which is why both need the extra reference that keeps them alive
   * for the lifetime of the process. Call this method on the GTK thread.
   */
  @SneakyThrows
  public void retainDefaultWebContext() {
    if (!CONTEXT_RETAINED.compareAndSet(false, true)) {
      return;
    }

    MemorySegment context = SHARED.defaultWebContext();
    if (!context.equals(MemorySegment.NULL)) {
      Glib.ref(context);
    }
    MemorySegment session = (MemorySegment) NETWORK_SESSION_GET_DEFAULT.invokeExact();
    if (!session.equals(MemorySegment.NULL)) {
      Glib.ref(session);
    }
  }

  /**
   * Calls {@code webkit_web_view_get_user_content_manager}: the holder for the user scripts and the
   * message channels of {@code webView}, which every web view has of its own.
   */
  @SneakyThrows
  public MemorySegment userContentManager(MemorySegment webView) {
    return (MemorySegment) WEB_VIEW_GET_USER_CONTENT_MANAGER.invokeExact(webView);
  }

  /**
   * Opens the {@code name} message channel, so that the page can reach the host through {@code
   * window.webkit.messageHandlers.NAME.postMessage()}.
   *
   * @return True if the channel was opened; false if a handler of that name already exists.
   */
  @SneakyThrows
  public boolean registerScriptMessageHandler(MemorySegment userContentManager, String name) {
    try (Arena arena = Arena.ofConfined()) {
      return (int)
              REGISTER_SCRIPT_MESSAGE_HANDLER.invokeExact(
                  userContentManager, arena.allocateFrom(name), MemorySegment.NULL)
          != 0;
    }
  }

  /**
   * Reads the string that a page posted through a message channel. The signal lends the value, so
   * it must not be unreffed.
   */
  public String scriptMessageText(MemorySegment value) {
    return SHARED.valueText(value);
  }

  /** Calls {@code webkit_web_view_new}. The widget is floating until a window takes it. */
  @SneakyThrows
  public MemorySegment webViewNew() {
    return (MemorySegment) WEB_VIEW_NEW.invokeExact();
  }

  /**
   * Gives the view a transparent background, a {@code GdkRGBA} of four zero floats in GTK 4, so
   * what the page leaves clear shows what is under the view.
   */
  @SneakyThrows
  public void setTransparentBackground(MemorySegment webView) {
    try (Arena arena = Arena.ofConfined()) {
      WEB_VIEW_SET_BACKGROUND_COLOR.invokeExact(webView, arena.allocate(Float.BYTES * 4L));
    }
  }

  /** The {@code WebKitCookieManager} of the default network session, which it owns. */
  @SneakyThrows
  public MemorySegment cookieManager() {
    return (MemorySegment)
        COOKIE_MANAGER.invokeExact((MemorySegment) NETWORK_SESSION_GET_DEFAULT.invokeExact());
  }

  /** The {@code WebKitNetworkSession} of {@code webView}, which emits {@code download-started}. */
  @SneakyThrows
  public MemorySegment downloadSource(MemorySegment webView) {
    return (MemorySegment) WEB_VIEW_GET_DOWNLOAD_SOURCE.invokeExact(webView);
  }

  /**
   * Sets where {@code download} writes, as an absolute path, which WebKitGTK 6.0 takes, and lets it
   * replace a file that is there. Call from {@code decide-destination}.
   */
  public void downloadSetDestination(MemorySegment download, Path destination) {
    SHARED.downloadSetDestination(download, destination.toString());
  }
}
