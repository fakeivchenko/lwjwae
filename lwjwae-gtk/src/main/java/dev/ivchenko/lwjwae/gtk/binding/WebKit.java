package dev.ivchenko.lwjwae.gtk.binding;

import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Bindings to WebKitGTK 4.1 (the GTK 3 variant) and to the JavaScriptCore value API. */
@UtilityClass
public class WebKit {
  private final SymbolLookup WEBKIT =
      NativeLibraries.load("libwebkit2gtk-4.1.so.0", "libwebkit2gtk-4.1.so");
  private final SymbolLookup JSC =
      NativeLibraries.load("libjavascriptcoregtk-4.1.so.0", "libjavascriptcoregtk-4.1.so");

  /** {@code WEBKIT_LOAD_STARTED}. */
  public final int LOAD_STARTED = 0;

  /** {@code WEBKIT_LOAD_REDIRECTED}. */
  public final int LOAD_REDIRECTED = 1;

  /** {@code WEBKIT_LOAD_COMMITTED}. */
  public final int LOAD_COMMITTED = 2;

  /** {@code WEBKIT_LOAD_FINISHED}. */
  public final int LOAD_FINISHED = 3;

  /** {@code WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES}. */
  private final int INJECT_ALL_FRAMES = 0;

  /** {@code WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START}. */
  private final int INJECT_AT_DOCUMENT_START = 0;

  private final MethodHandle WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_new_with_user_content_manager", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_LOAD_URI =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_load_uri", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_LOAD_HTML =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_load_html", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_URI =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_uri", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_SETTINGS =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_settings", Signatures.POINTER_POINTER);
  private final MethodHandle SETTINGS_SET_ENABLE_DEVELOPER_EXTRAS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_set_enable_developer_extras", Signatures.VOID_POINTER_INT);
  private final MethodHandle SETTINGS_GET_ENABLE_DEVELOPER_EXTRAS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_get_enable_developer_extras", Signatures.INT_POINTER);
  private final MethodHandle GET_MAJOR_VERSION =
      NativeLibraries.downcall(WEBKIT, "webkit_get_major_version", Signatures.INT_VOID);
  private final MethodHandle GET_MINOR_VERSION =
      NativeLibraries.downcall(WEBKIT, "webkit_get_minor_version", Signatures.INT_VOID);
  private final MethodHandle GET_MICRO_VERSION =
      NativeLibraries.downcall(WEBKIT, "webkit_get_micro_version", Signatures.INT_VOID);
  private final MethodHandle EVALUATE_JAVASCRIPT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_evaluate_javascript", Signatures.WEBKIT_EVALUATE_JAVASCRIPT);
  private final MethodHandle EVALUATE_JAVASCRIPT_FINISH =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_web_view_evaluate_javascript_finish",
          Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle WEB_CONTEXT_GET_DEFAULT =
      NativeLibraries.downcall(WEBKIT, "webkit_web_context_get_default", Signatures.POINTER_VOID);
  private final MethodHandle USER_CONTENT_MANAGER_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_user_content_manager_new", Signatures.POINTER_VOID);
  private final MethodHandle REGISTER_SCRIPT_MESSAGE_HANDLER =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_user_content_manager_register_script_message_handler",
          Signatures.INT_POINTER_POINTER);
  private final MethodHandle USER_CONTENT_MANAGER_ADD_SCRIPT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_content_manager_add_script", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle USER_SCRIPT_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_user_script_new", Signatures.WEBKIT_USER_SCRIPT_NEW);
  private final MethodHandle USER_SCRIPT_UNREF =
      NativeLibraries.downcall(WEBKIT, "webkit_user_script_unref", Signatures.VOID_POINTER);
  private final MethodHandle JAVASCRIPT_RESULT_GET_JS_VALUE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_javascript_result_get_js_value", Signatures.POINTER_POINTER);
  private final MethodHandle REGISTER_URI_SCHEME =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_context_register_uri_scheme", Signatures.VOID_POINTER_X5);
  private final MethodHandle URI_SCHEME_REQUEST_GET_PATH =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_get_path", Signatures.POINTER_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_FINISH =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_finish", Signatures.VOID_POINTER_POINTER_LONG_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_FINISH_ERROR =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_finish_error", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle VALUE_TO_STRING =
      NativeLibraries.downcall(JSC, "jsc_value_to_string", Signatures.POINTER_POINTER);

  private final AtomicBoolean CONTEXT_RETAINED = new AtomicBoolean();
  private final AtomicBoolean SCHEME_REGISTERED = new AtomicBoolean();

  /**
   * Takes one permanent reference to the default {@code WebKitWebContext}.
   *
   * <p>WebKit installs an {@code atexit} handler that decrements the reference count of the default
   * context. If the last web view was already destroyed by then, the count drops to zero and WebKit
   * aborts (SIGABRT) while it disposes the website data manager. The extra reference keeps the
   * singleton alive for the lifetime of the process. Call this method on the GTK thread.
   */
  @SneakyThrows
  public void retainDefaultWebContext() {
    if (!CONTEXT_RETAINED.compareAndSet(false, true)) {
      return;
    }

    MemorySegment context = (MemorySegment) WEB_CONTEXT_GET_DEFAULT.invokeExact();
    if (!context.equals(MemorySegment.NULL)) {
      Glib.ref(context);
    }
  }

  /**
   * Configures the default web context to serve {@code scheme://}, and dispatches every request to
   * {@code callback}. A scheme can only be registered once per context, which is why there's a
   * latch.
   */
  @SneakyThrows
  public void registerUriScheme(String scheme, MemorySegment callback) {
    if (!SCHEME_REGISTERED.compareAndSet(false, true)) {
      return;
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment context = (MemorySegment) WEB_CONTEXT_GET_DEFAULT.invokeExact();
      REGISTER_URI_SCHEME.invokeExact(
          context, arena.allocateFrom(scheme), callback, MemorySegment.NULL, MemorySegment.NULL);
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

  /** Arranges for {@code source} to run in every frame, before the scripts of the document. */
  @SneakyThrows
  public void addUserScript(MemorySegment userContentManager, String source) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment script =
          (MemorySegment)
              USER_SCRIPT_NEW.invokeExact(
                  arena.allocateFrom(source),
                  INJECT_ALL_FRAMES,
                  INJECT_AT_DOCUMENT_START,
                  MemorySegment.NULL, // allow_list: every URL
                  MemorySegment.NULL); // block_list
      USER_CONTENT_MANAGER_ADD_SCRIPT.invokeExact(userContentManager, script);
      USER_SCRIPT_UNREF.invokeExact(script);
    }
  }

  /** Reads the string that a page posted through a message channel. */
  @SneakyThrows
  public String scriptMessageText(MemorySegment javascriptResult) {
    // get_js_value is transfer-none: the result owns the value, so it must not be unreffed.
    MemorySegment value =
        (MemorySegment) JAVASCRIPT_RESULT_GET_JS_VALUE.invokeExact(javascriptResult);
    return Glib.takeString((MemorySegment) VALUE_TO_STRING.invokeExact(value));
  }

  /** Calls {@code webkit_web_view_new_with_user_content_manager}. The widget is floating. */
  @SneakyThrows
  public MemorySegment webViewNew(MemorySegment userContentManager) {
    return (MemorySegment) WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER.invokeExact(userContentManager);
  }

  /** Calls {@code webkit_web_view_load_uri}. */
  @SneakyThrows
  public void loadUri(MemorySegment webView, String uri) {
    try (Arena arena = Arena.ofConfined()) {
      WEB_VIEW_LOAD_URI.invokeExact(webView, arena.allocateFrom(uri));
    }
  }

  /**
   * Calls {@code webkit_web_view_load_html}. With a {@code null} base URI, relative links can't be
   * resolved.
   */
  @SneakyThrows
  public void loadHtml(MemorySegment webView, String html, String baseUri) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment base = baseUri == null ? MemorySegment.NULL : arena.allocateFrom(baseUri);
      WEB_VIEW_LOAD_HTML.invokeExact(webView, arena.allocateFrom(html), base);
    }
  }

  /**
   * Calls {@code webkit_web_view_get_uri}: the URI that's shown, or {@code null} before any load.
   */
  @SneakyThrows
  public String uri(MemorySegment webView) {
    return NativeLibraries.string((MemorySegment) WEB_VIEW_GET_URI.invokeExact(webView));
  }

  /**
   * Sets {@code enable-developer-extras} on the settings of {@code webView}: the Web Inspector, and
   * the "Inspect Element" entry of the context menu.
   */
  @SneakyThrows
  public void setDeveloperExtrasEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    SETTINGS_SET_ENABLE_DEVELOPER_EXTRAS.invokeExact(settings, enabled ? 1 : 0);
  }

  /** Reads {@code enable-developer-extras} from the settings of {@code webView}. */
  @SneakyThrows
  public boolean isDeveloperExtrasEnabled(MemorySegment webView) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    return (int) SETTINGS_GET_ENABLE_DEVELOPER_EXTRAS.invokeExact(settings) != 0;
  }

  /** Returns the version of the loaded WebKitGTK library, as {@code major.minor.micro}. */
  @SneakyThrows
  public String version() {
    return "%d.%d.%d"
        .formatted(
            (int) GET_MAJOR_VERSION.invokeExact(),
            (int) GET_MINOR_VERSION.invokeExact(),
            (int) GET_MICRO_VERSION.invokeExact());
  }

  /** Returns the path part of a custom-scheme request, for example {@code /app/index.html}. */
  @SneakyThrows
  public String uriSchemeRequestPath(MemorySegment request) {
    return NativeLibraries.string((MemorySegment) URI_SCHEME_REQUEST_GET_PATH.invokeExact(request));
  }

  /** Answers a custom-scheme request. WebKit consumes the stream. */
  @SneakyThrows
  public void uriSchemeRequestFinish(
      MemorySegment request, MemorySegment stream, long length, String contentType) {
    try (Arena arena = Arena.ofConfined()) {
      URI_SCHEME_REQUEST_FINISH.invokeExact(
          request, stream, length, arena.allocateFrom(contentType));
    }
  }

  /** Fails a custom-scheme request. WebKit takes ownership of {@code error}. */
  @SneakyThrows
  public void uriSchemeRequestFinishError(MemorySegment request, MemorySegment error) {
    URI_SCHEME_REQUEST_FINISH_ERROR.invokeExact(request, error);
  }

  /**
   * Starts an asynchronous evaluation. {@code callback} is invoked on the GTK thread and must
   * complete the evaluation with {@link #evaluateJavascriptFinish}.
   */
  @SneakyThrows
  public void evaluateJavascript(
      MemorySegment webView, String script, MemorySegment callback, MemorySegment userData) {
    try (Arena arena = Arena.ofConfined()) {
      EVALUATE_JAVASCRIPT.invokeExact(
          webView,
          arena.allocateFrom(script),
          -1L, // length: NUL terminated
          MemorySegment.NULL, // world_name: default world
          MemorySegment.NULL, // source_uri
          MemorySegment.NULL, // cancellable
          callback,
          userData);
    }
  }

  /**
   * Completes an evaluation that {@link #evaluateJavascript} started, and returns the result
   * rendered as a string.
   *
   * @throws ScriptEvaluationFailedException If the script threw or the evaluation failed.
   */
  @SneakyThrows
  public String evaluateJavascriptFinish(MemorySegment webView, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);

      MemorySegment value =
          (MemorySegment) EVALUATE_JAVASCRIPT_FINISH.invokeExact(webView, result, error);
      if (value.equals(MemorySegment.NULL)) {
        String message = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
        throw new ScriptEvaluationFailedException(
            message == null ? "Script evaluation failed" : message);
      }
      try {
        return Glib.takeString((MemorySegment) VALUE_TO_STRING.invokeExact(value));
      } finally {
        Glib.unref(value);
      }
    }
  }
}
