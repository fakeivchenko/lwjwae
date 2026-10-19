package dev.ivchenko.lwjwae.gtk.binding;

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
 * Bindings to WebKitGTK 4.1, the GTK 3 variant: what it has of its own, and {@link #SHARED}, the
 * part that it has in common with WebKitGTK 6.0.
 */
@UtilityClass
public class WebKit {
  /** The library of WebKitGTK that this backend loads. */
  public final String LIBRARY = "libwebkit2gtk-4.1.so.0";

  private final SymbolLookup WEBKIT = NativeLibraries.load(LIBRARY, "libwebkit2gtk-4.1.so");
  private final SymbolLookup JSC =
      NativeLibraries.load("libjavascriptcoregtk-4.1.so.0", "libjavascriptcoregtk-4.1.so");

  /** What WebKitGTK 4.1 has in common with 6.0, bound from the libraries of this backend. */
  public final WebKitGtk SHARED = new WebKitGtk(WEBKIT, JSC);

  private final MethodHandle WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_new_with_user_content_manager", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_SET_BACKGROUND_COLOR =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_set_background_color", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle COOKIE_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_context_get_cookie_manager", Signatures.POINTER_POINTER);
  private final MethodHandle USER_CONTENT_MANAGER_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_user_content_manager_new", Signatures.POINTER_VOID);
  private final MethodHandle REGISTER_SCRIPT_MESSAGE_HANDLER =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_user_content_manager_register_script_message_handler",
          Signatures.INT_POINTER_POINTER);
  private final MethodHandle JAVASCRIPT_RESULT_GET_JS_VALUE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_javascript_result_get_js_value", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_DOWNLOAD_SOURCE =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_context", Signatures.POINTER_POINTER);

  private final AtomicBoolean CONTEXT_RETAINED = new AtomicBoolean();

  /**
   * Takes one permanent reference to the default {@code WebKitWebContext}.
   *
   * <p>WebKit installs an {@code atexit} handler that decrements the reference count of the default
   * context. If the last web view was already destroyed by then, the count drops to zero and WebKit
   * aborts (SIGABRT) while it disposes the website data manager. The extra reference keeps the
   * singleton alive for the lifetime of the process. Call this method on the GTK thread.
   */
  public void retainDefaultWebContext() {
    if (!CONTEXT_RETAINED.compareAndSet(false, true)) {
      return;
    }

    MemorySegment context = SHARED.defaultWebContext();
    if (!context.equals(MemorySegment.NULL)) {
      Glib.ref(context);
    }
  }

  /**
   * Calls {@code webkit_user_content_manager_new}: the holder for user scripts and message
   * channels.
   */
  @SneakyThrows
  public MemorySegment userContentManagerNew() {
    return (MemorySegment) USER_CONTENT_MANAGER_NEW.invokeExact();
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
                  userContentManager, arena.allocateFrom(name))
          != 0;
    }
  }

  /** Reads the string that a page posted through a message channel. */
  @SneakyThrows
  public String scriptMessageText(MemorySegment javascriptResult) {
    // get_js_value is transfer-none: the result owns the value, so it must not be unreffed.
    MemorySegment value =
        (MemorySegment) JAVASCRIPT_RESULT_GET_JS_VALUE.invokeExact(javascriptResult);
    return SHARED.valueText(value);
  }

  /** Calls {@code webkit_web_view_new_with_user_content_manager}. The widget is floating. */
  @SneakyThrows
  public MemorySegment webViewNew(MemorySegment userContentManager) {
    return (MemorySegment) WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER.invokeExact(userContentManager);
  }

  /**
   * Gives the view a transparent background, a {@code GdkRGBA} of four zero doubles, so what the
   * page leaves clear shows what is under the view.
   */
  @SneakyThrows
  public void setTransparentBackground(MemorySegment webView) {
    try (Arena arena = Arena.ofConfined()) {
      WEB_VIEW_SET_BACKGROUND_COLOR.invokeExact(webView, arena.allocate(Double.BYTES * 4L));
    }
  }

  /** The {@code WebKitCookieManager} of the default web context, which it owns. */
  @SneakyThrows
  public MemorySegment cookieManager() {
    return (MemorySegment) COOKIE_MANAGER.invokeExact(SHARED.defaultWebContext());
  }

  /** The {@code WebKitWebContext} of {@code webView}, which emits {@code download-started}. */
  @SneakyThrows
  public MemorySegment downloadSource(MemorySegment webView) {
    return (MemorySegment) WEB_VIEW_GET_DOWNLOAD_SOURCE.invokeExact(webView);
  }

  /**
   * Sets where {@code download} writes, as a {@code file://} URI, which WebKitGTK 4.1 takes, and
   * lets it replace a file that is there. Call from {@code decide-destination}.
   */
  public void downloadSetDestination(MemorySegment download, Path destination) {
    SHARED.downloadSetDestination(download, destination.toUri().toString());
  }
}
