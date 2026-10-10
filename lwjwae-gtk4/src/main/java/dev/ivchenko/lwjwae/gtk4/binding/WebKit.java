package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to WebKitGTK 6.0 (the GTK 4 variant) and to the JavaScriptCore value API.
 *
 * <p>The API is that of 4.1 with three differences that matter here: a web view is created plain
 * and comes with a user content manager of its own, a script message handler is registered for a
 * script world, and a script message arrives as the {@code JSCValue} itself.
 */
@UtilityClass
public class WebKit {
  /** The library of WebKitGTK that this backend loads. */
  public final String LIBRARY = "libwebkitgtk-6.0.so.4";

  private final SymbolLookup WEBKIT = NativeLibraries.load(LIBRARY, "libwebkitgtk-6.0.so");
  private final SymbolLookup JSC =
      NativeLibraries.load("libjavascriptcoregtk-6.0.so.1", "libjavascriptcoregtk-6.0.so");

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

  /** {@code WEBKIT_DOWNLOAD_ERROR_CANCELLED_BY_USER}: the code of a download that was canceled. */
  public final int DOWNLOAD_ERROR_CANCELLED_BY_USER = 400;

  private final MethodHandle WEB_VIEW_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_new", Signatures.POINTER_VOID);
  private final MethodHandle WEB_VIEW_SET_BACKGROUND_COLOR =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_set_background_color", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_USER_CONTENT_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_get_user_content_manager", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_EXECUTE_EDITING_COMMAND =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_execute_editing_command", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_LOAD_URI =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_load_uri", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_LOAD_HTML =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_load_html", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_URI =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_uri", Signatures.POINTER_POINTER);
  private final MethodHandle NAVIGATION_ACTION_GET_REQUEST =
      NativeLibraries.downcall(
          WEBKIT, "webkit_navigation_action_get_request", Signatures.POINTER_POINTER);
  private final MethodHandle URI_REQUEST_GET_URI =
      NativeLibraries.downcall(WEBKIT, "webkit_uri_request_get_uri", Signatures.POINTER_POINTER);
  private final MethodHandle WEB_VIEW_GET_SETTINGS =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_settings", Signatures.POINTER_POINTER);
  private final MethodHandle SETTINGS_GET_USER_AGENT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_get_user_agent", Signatures.POINTER_POINTER);
  private final MethodHandle SETTINGS_SET_USER_AGENT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_set_user_agent", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SETTINGS_SET_ENABLE_DEVELOPER_EXTRAS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_set_enable_developer_extras", Signatures.VOID_POINTER_INT);
  private final MethodHandle SETTINGS_GET_ENABLE_DEVELOPER_EXTRAS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_get_enable_developer_extras", Signatures.INT_POINTER);
  private final MethodHandle SETTINGS_SET_ENABLE_MEDIA_STREAM =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_set_enable_media_stream", Signatures.VOID_POINTER_INT);
  private final MethodHandle SETTINGS_SET_ENABLE_MOCK_CAPTURE_DEVICES =
      NativeLibraries.downcall(
          WEBKIT, "webkit_settings_set_enable_mock_capture_devices", Signatures.VOID_POINTER_INT);
  private final MethodHandle USER_MEDIA_REQUEST_GET_TYPE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_media_permission_request_get_type", Signatures.LONG_VOID);
  private final MethodHandle USER_MEDIA_IS_FOR_AUDIO =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_media_permission_is_for_audio_device", Signatures.INT_POINTER);
  private final MethodHandle USER_MEDIA_IS_FOR_VIDEO =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_media_permission_is_for_video_device", Signatures.INT_POINTER);
  private final MethodHandle USER_MEDIA_IS_FOR_DISPLAY =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_media_permission_is_for_display_device", Signatures.INT_POINTER);
  private final MethodHandle PERMISSION_REQUEST_ALLOW =
      NativeLibraries.downcall(WEBKIT, "webkit_permission_request_allow", Signatures.VOID_POINTER);
  private final MethodHandle PERMISSION_REQUEST_DENY =
      NativeLibraries.downcall(WEBKIT, "webkit_permission_request_deny", Signatures.VOID_POINTER);
  private final MethodHandle WEB_VIEW_SET_ZOOM_LEVEL =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_set_zoom_level", Signatures.VOID_POINTER_DOUBLE);
  private final MethodHandle WEB_VIEW_GET_ZOOM_LEVEL =
      NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_zoom_level", Signatures.DOUBLE_POINTER);
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
  private final MethodHandle COOKIE_MANAGER =
      NativeLibraries.downcall(
          WEBKIT, "webkit_network_session_get_cookie_manager", Signatures.POINTER_POINTER);
  private final MethodHandle NETWORK_SESSION_GET_DEFAULT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_network_session_get_default", Signatures.POINTER_VOID);
  private final MethodHandle WEB_CONTEXT_GET_DEFAULT =
      NativeLibraries.downcall(WEBKIT, "webkit_web_context_get_default", Signatures.POINTER_VOID);
  private final MethodHandle REGISTER_SCRIPT_MESSAGE_HANDLER =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_user_content_manager_register_script_message_handler",
          Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle USER_CONTENT_MANAGER_ADD_SCRIPT =
      NativeLibraries.downcall(
          WEBKIT, "webkit_user_content_manager_add_script", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle USER_SCRIPT_NEW =
      NativeLibraries.downcall(WEBKIT, "webkit_user_script_new", Signatures.WEBKIT_USER_SCRIPT_NEW);
  private final MethodHandle USER_SCRIPT_UNREF =
      NativeLibraries.downcall(WEBKIT, "webkit_user_script_unref", Signatures.VOID_POINTER);
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
  private final SymbolLookup SOUP = NativeLibraries.load("libsoup-3.0.so.0", "libsoup-3.0.so");

  // --- RPC: requests with a method and a body, responses with a status, headers, and a stream ---
  private final MethodHandle URI_SCHEME_REQUEST_GET_HTTP_METHOD =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_get_http_method", Signatures.POINTER_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_GET_HTTP_BODY =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_get_http_body", Signatures.POINTER_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_GET_HTTP_HEADERS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_get_http_headers", Signatures.POINTER_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_GET_WEB_VIEW =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_request_get_web_view", Signatures.POINTER_POINTER);
  private final MethodHandle SOUP_MESSAGE_HEADERS_GET_ONE =
      NativeLibraries.downcall(
          SOUP, "soup_message_headers_get_one", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle URI_SCHEME_RESPONSE_NEW =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_response_new", Signatures.POINTER_POINTER_LONG);
  private final MethodHandle URI_SCHEME_RESPONSE_SET_STATUS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_response_set_status", Signatures.VOID_POINTER_INT_POINTER);
  private final MethodHandle URI_SCHEME_RESPONSE_SET_HTTP_HEADERS =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_scheme_response_set_http_headers", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle URI_SCHEME_REQUEST_FINISH_WITH_RESPONSE =
      NativeLibraries.downcall(
          WEBKIT,
          "webkit_uri_scheme_request_finish_with_response",
          Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SOUP_MESSAGE_HEADERS_NEW =
      NativeLibraries.downcall(SOUP, "soup_message_headers_new", Signatures.POINTER_INT);
  private final MethodHandle SOUP_MESSAGE_HEADERS_APPEND =
      NativeLibraries.downcall(
          SOUP, "soup_message_headers_append", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle VALUE_TO_STRING =
      NativeLibraries.downcall(JSC, "jsc_value_to_string", Signatures.POINTER_POINTER);

  private final AtomicBoolean CONTEXT_RETAINED = new AtomicBoolean();
  private final AtomicBoolean SCHEME_REGISTERED = new AtomicBoolean();

  private final MethodHandle WEB_VIEW_GET_DOWNLOAD_SOURCE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_web_view_get_network_session", Signatures.POINTER_POINTER);
  private final MethodHandle DOWNLOAD_GET_WEB_VIEW =
      NativeLibraries.downcall(WEBKIT, "webkit_download_get_web_view", Signatures.POINTER_POINTER);
  private final MethodHandle DOWNLOAD_GET_REQUEST =
      NativeLibraries.downcall(WEBKIT, "webkit_download_get_request", Signatures.POINTER_POINTER);
  private final MethodHandle DOWNLOAD_GET_RESPONSE =
      NativeLibraries.downcall(WEBKIT, "webkit_download_get_response", Signatures.POINTER_POINTER);
  private final MethodHandle URI_RESPONSE_GET_CONTENT_LENGTH =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_response_get_content_length", Signatures.LONG_POINTER);
  private final MethodHandle URI_RESPONSE_GET_MIME_TYPE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_uri_response_get_mime_type", Signatures.POINTER_POINTER);
  private final MethodHandle DOWNLOAD_GET_RECEIVED_DATA_LENGTH =
      NativeLibraries.downcall(
          WEBKIT, "webkit_download_get_received_data_length", Signatures.LONG_POINTER);
  private final MethodHandle DOWNLOAD_SET_DESTINATION =
      NativeLibraries.downcall(
          WEBKIT, "webkit_download_set_destination", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle DOWNLOAD_SET_ALLOW_OVERWRITE =
      NativeLibraries.downcall(
          WEBKIT, "webkit_download_set_allow_overwrite", Signatures.VOID_POINTER_INT);
  private final MethodHandle DOWNLOAD_CANCEL =
      NativeLibraries.downcall(WEBKIT, "webkit_download_cancel", Signatures.VOID_POINTER);

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

    MemorySegment context = (MemorySegment) WEB_CONTEXT_GET_DEFAULT.invokeExact();
    if (!context.equals(MemorySegment.NULL)) {
      Glib.ref(context);
    }
    MemorySegment session = (MemorySegment) NETWORK_SESSION_GET_DEFAULT.invokeExact();
    if (!session.equals(MemorySegment.NULL)) {
      Glib.ref(session);
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

  /**
   * Reads the string that a page posted through a message channel. The signal lends the value, so
   * it must not be unreffed.
   */
  @SneakyThrows
  public String scriptMessageText(MemorySegment value) {
    return Glib.takeString((MemorySegment) VALUE_TO_STRING.invokeExact(value));
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

  /** The URL that a {@code WebKitNavigationAction}, the argument of {@code create}, goes to. */
  @SneakyThrows
  public String navigationActionUri(MemorySegment action) {
    MemorySegment request = (MemorySegment) NAVIGATION_ACTION_GET_REQUEST.invokeExact(action);
    return NativeLibraries.string((MemorySegment) URI_REQUEST_GET_URI.invokeExact(request));
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

  /**
   * Sets {@code enable-media-stream} on the settings of {@code webView}: without it, a page has no
   * {@code navigator.mediaDevices}, and the camera and the microphone never reach the permission
   * request.
   */
  @SneakyThrows
  public void setMediaStreamEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    SETTINGS_SET_ENABLE_MEDIA_STREAM.invokeExact(settings, enabled ? 1 : 0);
  }

  /**
   * Sets {@code enable-mock-capture-devices}: a fake camera and a fake microphone, which let the
   * tests of the library see a permission request on a machine without either.
   */
  @SneakyThrows
  public void setMockCaptureDevicesEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    SETTINGS_SET_ENABLE_MOCK_CAPTURE_DEVICES.invokeExact(settings, enabled ? 1 : 0);
  }

  /** Whether {@code request} is a {@code WebKitUserMediaPermissionRequest}. */
  @SneakyThrows
  public boolean isUserMediaRequest(MemorySegment request) {
    long type = (long) USER_MEDIA_REQUEST_GET_TYPE.invokeExact();
    return Glib.typeCheckInstanceIsA(request, type);
  }

  /** Whether the user media request asks for a microphone. */
  @SneakyThrows
  public boolean isForAudioDevice(MemorySegment request) {
    return (int) USER_MEDIA_IS_FOR_AUDIO.invokeExact(request) != 0;
  }

  /** Whether the user media request asks for a camera. */
  @SneakyThrows
  public boolean isForVideoDevice(MemorySegment request) {
    return (int) USER_MEDIA_IS_FOR_VIDEO.invokeExact(request) != 0;
  }

  /** Whether the user media request asks to capture a screen or a window. */
  @SneakyThrows
  public boolean isForDisplayDevice(MemorySegment request) {
    return (int) USER_MEDIA_IS_FOR_DISPLAY.invokeExact(request) != 0;
  }

  /** Answers a {@code WebKitPermissionRequest} with yes, or with no. */
  @SneakyThrows
  public void answerPermissionRequest(MemorySegment request, boolean granted) {
    if (granted) {
      PERMISSION_REQUEST_ALLOW.invokeExact(request);
    } else {
      PERMISSION_REQUEST_DENY.invokeExact(request);
    }
  }

  /** Calls {@code webkit_web_view_set_zoom_level}: the page at {@code factor}, 1 for 100%. */
  @SneakyThrows
  public void setZoomLevel(MemorySegment webView, double factor) {
    WEB_VIEW_SET_ZOOM_LEVEL.invokeExact(webView, factor);
  }

  /** Calls {@code webkit_web_view_get_zoom_level}. */
  @SneakyThrows
  public double zoomLevel(MemorySegment webView) {
    return (double) WEB_VIEW_GET_ZOOM_LEVEL.invokeExact(webView);
  }

  /** The user agent that {@code webView} sends, the one of WebKit until it's set. */
  @SneakyThrows
  public String userAgent(MemorySegment webView) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    return NativeLibraries.string((MemorySegment) SETTINGS_GET_USER_AGENT.invokeExact(settings));
  }

  /**
   * Runs an editing command on the page, such as {@code Copy}, {@code Paste}, or {@code SelectAll},
   * the names of the {@code WEBKIT_EDITING_COMMAND_*} constants.
   */
  @SneakyThrows
  public void executeEditingCommand(MemorySegment webView, String command) {
    try (Arena arena = Arena.ofConfined()) {
      WEB_VIEW_EXECUTE_EDITING_COMMAND.invokeExact(webView, arena.allocateFrom(command));
    }
  }

  /** Sets the user agent that {@code webView} sends from its next request on. */
  @SneakyThrows
  public void setUserAgent(MemorySegment webView, String userAgent) {
    MemorySegment settings = (MemorySegment) WEB_VIEW_GET_SETTINGS.invokeExact(webView);
    try (Arena arena = Arena.ofConfined()) {
      SETTINGS_SET_USER_AGENT.invokeExact(settings, arena.allocateFrom(userAgent));
    }
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

  /** A header of a custom-scheme request, or {@code null}. */
  @SneakyThrows
  public String uriSchemeRequestHeader(MemorySegment request, String name) {
    MemorySegment headers =
        (MemorySegment) URI_SCHEME_REQUEST_GET_HTTP_HEADERS.invokeExact(request);
    if (headers.equals(MemorySegment.NULL)) {
      return null;
    }
    try (Arena arena = Arena.ofConfined()) {
      return NativeLibraries.string(
          (MemorySegment)
              SOUP_MESSAGE_HEADERS_GET_ONE.invokeExact(headers, arena.allocateFrom(name)));
    }
  }

  /** {@code webkit_uri_scheme_request_get_web_view}: the view whose page made the request. */
  @SneakyThrows
  public MemorySegment uriSchemeRequestWebView(MemorySegment request) {
    return (MemorySegment) URI_SCHEME_REQUEST_GET_WEB_VIEW.invokeExact(request);
  }

  /** {@code webkit_uri_scheme_request_get_http_method}. */
  @SneakyThrows
  public String uriSchemeRequestMethod(MemorySegment request) {
    return NativeLibraries.string(
        (MemorySegment) URI_SCHEME_REQUEST_GET_HTTP_METHOD.invokeExact(request));
  }

  /**
   * {@code webkit_uri_scheme_request_get_http_body}: a {@code GInputStream} that the caller owns,
   * or {@code NULL} for a request without a body.
   */
  @SneakyThrows
  public MemorySegment uriSchemeRequestBody(MemorySegment request) {
    return (MemorySegment) URI_SCHEME_REQUEST_GET_HTTP_BODY.invokeExact(request);
  }

  /**
   * Answers a custom-scheme request with {@code status}, {@code headers}, and a body that WebKit
   * reads from {@code stream} as it arrives, to its end.
   */
  @SneakyThrows
  public void uriSchemeRequestFinishWithStream(
      MemorySegment request, MemorySegment stream, int status, Map<String, String> headers) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment response = (MemorySegment) URI_SCHEME_RESPONSE_NEW.invokeExact(stream, -1L);
      URI_SCHEME_RESPONSE_SET_STATUS.invokeExact(response, status, MemorySegment.NULL);
      MemorySegment soupHeaders = (MemorySegment) SOUP_MESSAGE_HEADERS_NEW.invokeExact(1);
      for (Map.Entry<String, String> header : headers.entrySet()) {
        SOUP_MESSAGE_HEADERS_APPEND.invokeExact(
            soupHeaders,
            arena.allocateFrom(header.getKey()),
            arena.allocateFrom(header.getValue()));
      }
      URI_SCHEME_RESPONSE_SET_HTTP_HEADERS.invokeExact(response, soupHeaders);
      URI_SCHEME_REQUEST_FINISH_WITH_RESPONSE.invokeExact(request, response);
      Glib.unref(response);
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

  /** The web view that started {@code download}, or {@code NULL} for none. */
  @SneakyThrows
  public MemorySegment downloadWebView(MemorySegment download) {
    return (MemorySegment) DOWNLOAD_GET_WEB_VIEW.invokeExact(download);
  }

  /** The URL that {@code download} comes from. */
  @SneakyThrows
  public String downloadUri(MemorySegment download) {
    MemorySegment request = (MemorySegment) DOWNLOAD_GET_REQUEST.invokeExact(download);
    return NativeLibraries.string((MemorySegment) URI_REQUEST_GET_URI.invokeExact(request));
  }

  /** The media type that the server sent for {@code download}, or {@code null} before it did. */
  @SneakyThrows
  public String downloadMimeType(MemorySegment download) {
    MemorySegment response = (MemorySegment) DOWNLOAD_GET_RESPONSE.invokeExact(download);
    if (response.equals(MemorySegment.NULL)) {
      return null;
    }
    return NativeLibraries.string((MemorySegment) URI_RESPONSE_GET_MIME_TYPE.invokeExact(response));
  }

  /** The size of the file of {@code download}, or -1 where the server didn't send it. */
  @SneakyThrows
  public long downloadContentLength(MemorySegment download) {
    MemorySegment response = (MemorySegment) DOWNLOAD_GET_RESPONSE.invokeExact(download);
    if (response.equals(MemorySegment.NULL)) {
      return -1;
    }
    long length = (long) URI_RESPONSE_GET_CONTENT_LENGTH.invokeExact(response);
    return length > 0 ? length : -1;
  }

  /** How much of {@code download} arrived, in bytes. */
  @SneakyThrows
  public long downloadReceivedLength(MemorySegment download) {
    return (long) DOWNLOAD_GET_RECEIVED_DATA_LENGTH.invokeExact(download);
  }

  /**
   * Sets where {@code download} writes, as an absolute path, which WebKitGTK 6.0 takes, and lets it
   * replace a file that is there. Call from {@code decide-destination}.
   */
  @SneakyThrows
  public void downloadSetDestination(MemorySegment download, Path destination) {
    DOWNLOAD_SET_ALLOW_OVERWRITE.invokeExact(download, 1);
    try (Arena arena = Arena.ofConfined()) {
      DOWNLOAD_SET_DESTINATION.invokeExact(download, arena.allocateFrom(destination.toString()));
    }
  }

  /** Cancels {@code download}, which then fails with {@link #DOWNLOAD_ERROR_CANCELLED_BY_USER}. */
  @SneakyThrows
  public void downloadCancel(MemorySegment download) {
    DOWNLOAD_CANCEL.invokeExact(download);
  }
}
