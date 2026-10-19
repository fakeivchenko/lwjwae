package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;

/**
 * Bindings to the part of WebKitGTK that version 4.1, the one of GTK 3, and version 6.0, the one of
 * GTK 4, share under the same names, and to the JavaScriptCore value API.
 *
 * <p>The two libraries export these symbols alike, but a process loads only the one of its toolkit,
 * and every symbol must come from that one. So this binding is an object that a backend makes once,
 * from the libraries that it loaded, rather than a class of static methods, as {@link
 * dev.ivchenko.lwjwae.glib.WebKitCookies} is. What differs between the versions, such as how a web
 * view comes to be or what emits the start of a download, stays in the binding of the backend.
 */
public final class WebKitGtk {
  /** {@code WEBKIT_LOAD_STARTED}. */
  public static final int LOAD_STARTED = 0;

  /** {@code WEBKIT_LOAD_REDIRECTED}. */
  public static final int LOAD_REDIRECTED = 1;

  /** {@code WEBKIT_LOAD_COMMITTED}. */
  public static final int LOAD_COMMITTED = 2;

  /** {@code WEBKIT_LOAD_FINISHED}. */
  public static final int LOAD_FINISHED = 3;

  /** {@code WEBKIT_DOWNLOAD_ERROR_CANCELLED_BY_USER}: the code of a download that was canceled. */
  public static final int DOWNLOAD_ERROR_CANCELLED_BY_USER = 400;

  /** {@code WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES}. */
  private static final int INJECT_ALL_FRAMES = 0;

  /** {@code WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START}. */
  private static final int INJECT_AT_DOCUMENT_START = 0;

  private final AtomicBoolean schemeRegistered = new AtomicBoolean();

  // --- web view ---
  private final MethodHandle webViewExecuteEditingCommand;
  private final MethodHandle webViewLoadUri;
  private final MethodHandle webViewLoadHtml;
  private final MethodHandle webViewGetUri;
  private final MethodHandle webViewGetSettings;
  private final MethodHandle webViewSetZoomLevel;
  private final MethodHandle webViewGetZoomLevel;
  private final MethodHandle evaluateJavascript;
  private final MethodHandle evaluateJavascriptFinish;
  private final MethodHandle navigationActionGetRequest;
  private final MethodHandle uriRequestGetUri;

  // --- settings ---
  private final MethodHandle settingsGetUserAgent;
  private final MethodHandle settingsSetUserAgent;
  private final MethodHandle settingsSetEnableDeveloperExtras;
  private final MethodHandle settingsGetEnableDeveloperExtras;
  private final MethodHandle settingsSetEnableMediaStream;
  private final MethodHandle settingsSetEnableMockCaptureDevices;

  // --- permissions ---
  private final MethodHandle userMediaRequestGetType;
  private final MethodHandle userMediaIsForAudio;
  private final MethodHandle userMediaIsForVideo;
  private final MethodHandle userMediaIsForDisplay;
  private final MethodHandle permissionRequestAllow;
  private final MethodHandle permissionRequestDeny;

  // --- library ---
  private final MethodHandle getMajorVersion;
  private final MethodHandle getMinorVersion;
  private final MethodHandle getMicroVersion;
  private final MethodHandle webContextGetDefault;

  // --- user scripts ---
  private final MethodHandle userContentManagerAddScript;
  private final MethodHandle userScriptNew;
  private final MethodHandle userScriptUnref;
  private final MethodHandle valueToString;

  // --- the scheme of the application, and RPC requests on it ---
  private final MethodHandle registerUriScheme;
  private final MethodHandle uriSchemeRequestGetPath;
  private final MethodHandle uriSchemeRequestFinish;
  private final MethodHandle uriSchemeRequestFinishError;
  private final MethodHandle uriSchemeRequestGetHttpMethod;
  private final MethodHandle uriSchemeRequestGetHttpBody;
  private final MethodHandle uriSchemeRequestGetHttpHeaders;
  private final MethodHandle uriSchemeRequestGetWebView;
  private final MethodHandle uriSchemeResponseNew;
  private final MethodHandle uriSchemeResponseSetStatus;
  private final MethodHandle uriSchemeResponseSetHttpHeaders;
  private final MethodHandle uriSchemeRequestFinishWithResponse;

  // --- downloads ---
  private final MethodHandle downloadGetWebView;
  private final MethodHandle downloadGetRequest;
  private final MethodHandle downloadGetResponse;
  private final MethodHandle uriResponseGetContentLength;
  private final MethodHandle uriResponseGetMimeType;
  private final MethodHandle downloadGetReceivedDataLength;
  private final MethodHandle downloadSetDestination;
  private final MethodHandle downloadSetAllowOverwrite;
  private final MethodHandle downloadCancel;

  /**
   * Binds the shared functions of the libraries that the backend loaded.
   *
   * @param webKit The library of WebKitGTK, such as {@code libwebkit2gtk-4.1.so.0}.
   * @param javaScriptCore The library of JavaScriptCore that goes with it.
   */
  public WebKitGtk(SymbolLookup webKit, SymbolLookup javaScriptCore) {
    this.webViewExecuteEditingCommand =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_execute_editing_command", Signatures.VOID_POINTER_POINTER);
    this.webViewLoadUri =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_load_uri", Signatures.VOID_POINTER_POINTER);
    this.webViewLoadHtml =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_load_html", Signatures.VOID_POINTER_POINTER_POINTER);
    this.webViewGetUri =
        NativeLibraries.downcall(webKit, "webkit_web_view_get_uri", Signatures.POINTER_POINTER);
    this.webViewGetSettings =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_get_settings", Signatures.POINTER_POINTER);
    this.webViewSetZoomLevel =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_set_zoom_level", Signatures.VOID_POINTER_DOUBLE);
    this.webViewGetZoomLevel =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_get_zoom_level", Signatures.DOUBLE_POINTER);
    this.evaluateJavascript =
        NativeLibraries.downcall(
            webKit, "webkit_web_view_evaluate_javascript", Signatures.WEBKIT_EVALUATE_JAVASCRIPT);
    this.evaluateJavascriptFinish =
        NativeLibraries.downcall(
            webKit,
            "webkit_web_view_evaluate_javascript_finish",
            Signatures.POINTER_POINTER_POINTER_POINTER);
    this.navigationActionGetRequest =
        NativeLibraries.downcall(
            webKit, "webkit_navigation_action_get_request", Signatures.POINTER_POINTER);
    this.uriRequestGetUri =
        NativeLibraries.downcall(webKit, "webkit_uri_request_get_uri", Signatures.POINTER_POINTER);

    this.settingsGetUserAgent =
        NativeLibraries.downcall(
            webKit, "webkit_settings_get_user_agent", Signatures.POINTER_POINTER);
    this.settingsSetUserAgent =
        NativeLibraries.downcall(
            webKit, "webkit_settings_set_user_agent", Signatures.VOID_POINTER_POINTER);
    this.settingsSetEnableDeveloperExtras =
        NativeLibraries.downcall(
            webKit, "webkit_settings_set_enable_developer_extras", Signatures.VOID_POINTER_INT);
    this.settingsGetEnableDeveloperExtras =
        NativeLibraries.downcall(
            webKit, "webkit_settings_get_enable_developer_extras", Signatures.INT_POINTER);
    this.settingsSetEnableMediaStream =
        NativeLibraries.downcall(
            webKit, "webkit_settings_set_enable_media_stream", Signatures.VOID_POINTER_INT);
    this.settingsSetEnableMockCaptureDevices =
        NativeLibraries.downcall(
            webKit, "webkit_settings_set_enable_mock_capture_devices", Signatures.VOID_POINTER_INT);

    this.userMediaRequestGetType =
        NativeLibraries.downcall(
            webKit, "webkit_user_media_permission_request_get_type", Signatures.LONG_VOID);
    this.userMediaIsForAudio =
        NativeLibraries.downcall(
            webKit, "webkit_user_media_permission_is_for_audio_device", Signatures.INT_POINTER);
    this.userMediaIsForVideo =
        NativeLibraries.downcall(
            webKit, "webkit_user_media_permission_is_for_video_device", Signatures.INT_POINTER);
    this.userMediaIsForDisplay =
        NativeLibraries.downcall(
            webKit, "webkit_user_media_permission_is_for_display_device", Signatures.INT_POINTER);
    this.permissionRequestAllow =
        NativeLibraries.downcall(
            webKit, "webkit_permission_request_allow", Signatures.VOID_POINTER);
    this.permissionRequestDeny =
        NativeLibraries.downcall(webKit, "webkit_permission_request_deny", Signatures.VOID_POINTER);

    this.getMajorVersion =
        NativeLibraries.downcall(webKit, "webkit_get_major_version", Signatures.INT_VOID);
    this.getMinorVersion =
        NativeLibraries.downcall(webKit, "webkit_get_minor_version", Signatures.INT_VOID);
    this.getMicroVersion =
        NativeLibraries.downcall(webKit, "webkit_get_micro_version", Signatures.INT_VOID);
    this.webContextGetDefault =
        NativeLibraries.downcall(webKit, "webkit_web_context_get_default", Signatures.POINTER_VOID);

    this.userContentManagerAddScript =
        NativeLibraries.downcall(
            webKit, "webkit_user_content_manager_add_script", Signatures.VOID_POINTER_POINTER);
    this.userScriptNew =
        NativeLibraries.downcall(
            webKit, "webkit_user_script_new", Signatures.WEBKIT_USER_SCRIPT_NEW);
    this.userScriptUnref =
        NativeLibraries.downcall(webKit, "webkit_user_script_unref", Signatures.VOID_POINTER);
    this.valueToString =
        NativeLibraries.downcall(javaScriptCore, "jsc_value_to_string", Signatures.POINTER_POINTER);

    this.registerUriScheme =
        NativeLibraries.downcall(
            webKit, "webkit_web_context_register_uri_scheme", Signatures.VOID_POINTER_X5);
    this.uriSchemeRequestGetPath =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_get_path", Signatures.POINTER_POINTER);
    this.uriSchemeRequestFinish =
        NativeLibraries.downcall(
            webKit,
            "webkit_uri_scheme_request_finish",
            Signatures.VOID_POINTER_POINTER_LONG_POINTER);
    this.uriSchemeRequestFinishError =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_finish_error", Signatures.VOID_POINTER_POINTER);
    this.uriSchemeRequestGetHttpMethod =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_get_http_method", Signatures.POINTER_POINTER);
    this.uriSchemeRequestGetHttpBody =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_get_http_body", Signatures.POINTER_POINTER);
    this.uriSchemeRequestGetHttpHeaders =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_get_http_headers", Signatures.POINTER_POINTER);
    this.uriSchemeRequestGetWebView =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_request_get_web_view", Signatures.POINTER_POINTER);
    this.uriSchemeResponseNew =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_response_new", Signatures.POINTER_POINTER_LONG);
    this.uriSchemeResponseSetStatus =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_response_set_status", Signatures.VOID_POINTER_INT_POINTER);
    this.uriSchemeResponseSetHttpHeaders =
        NativeLibraries.downcall(
            webKit, "webkit_uri_scheme_response_set_http_headers", Signatures.VOID_POINTER_POINTER);
    this.uriSchemeRequestFinishWithResponse =
        NativeLibraries.downcall(
            webKit,
            "webkit_uri_scheme_request_finish_with_response",
            Signatures.VOID_POINTER_POINTER);

    this.downloadGetWebView =
        NativeLibraries.downcall(
            webKit, "webkit_download_get_web_view", Signatures.POINTER_POINTER);
    this.downloadGetRequest =
        NativeLibraries.downcall(webKit, "webkit_download_get_request", Signatures.POINTER_POINTER);
    this.downloadGetResponse =
        NativeLibraries.downcall(
            webKit, "webkit_download_get_response", Signatures.POINTER_POINTER);
    this.uriResponseGetContentLength =
        NativeLibraries.downcall(
            webKit, "webkit_uri_response_get_content_length", Signatures.LONG_POINTER);
    this.uriResponseGetMimeType =
        NativeLibraries.downcall(
            webKit, "webkit_uri_response_get_mime_type", Signatures.POINTER_POINTER);
    this.downloadGetReceivedDataLength =
        NativeLibraries.downcall(
            webKit, "webkit_download_get_received_data_length", Signatures.LONG_POINTER);
    this.downloadSetDestination =
        NativeLibraries.downcall(
            webKit, "webkit_download_set_destination", Signatures.VOID_POINTER_POINTER);
    this.downloadSetAllowOverwrite =
        NativeLibraries.downcall(
            webKit, "webkit_download_set_allow_overwrite", Signatures.VOID_POINTER_INT);
    this.downloadCancel =
        NativeLibraries.downcall(webKit, "webkit_download_cancel", Signatures.VOID_POINTER);
  }

  /** The default {@code WebKitWebContext}, which the library owns. */
  @SneakyThrows
  public MemorySegment defaultWebContext() {
    return (MemorySegment) this.webContextGetDefault.invokeExact();
  }

  /**
   * Configures the default web context to serve {@code scheme://}, and dispatches every request to
   * {@code callback}. A scheme can only be registered once per context, which is why there's a
   * latch.
   */
  @SneakyThrows
  public void registerUriScheme(String scheme, MemorySegment callback) {
    if (!this.schemeRegistered.compareAndSet(false, true)) {
      return;
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment context = this.defaultWebContext();
      this.registerUriScheme.invokeExact(
          context, arena.allocateFrom(scheme), callback, MemorySegment.NULL, MemorySegment.NULL);
    }
  }

  /** Arranges for {@code source} to run in every frame, before the scripts of the document. */
  @SneakyThrows
  public void addUserScript(MemorySegment userContentManager, String source) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment script =
          (MemorySegment)
              this.userScriptNew.invokeExact(
                  arena.allocateFrom(source),
                  INJECT_ALL_FRAMES,
                  INJECT_AT_DOCUMENT_START,
                  MemorySegment.NULL, // allow_list: every URL
                  MemorySegment.NULL); // block_list
      this.userContentManagerAddScript.invokeExact(userContentManager, script);
      this.userScriptUnref.invokeExact(script);
    }
  }

  /** The text of a {@code JSCValue}, which {@code jsc_value_to_string} renders. */
  @SneakyThrows
  public String valueText(MemorySegment value) {
    return Glib.takeString((MemorySegment) this.valueToString.invokeExact(value));
  }

  /** Calls {@code webkit_web_view_load_uri}. */
  @SneakyThrows
  public void loadUri(MemorySegment webView, String uri) {
    try (Arena arena = Arena.ofConfined()) {
      this.webViewLoadUri.invokeExact(webView, arena.allocateFrom(uri));
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
      this.webViewLoadHtml.invokeExact(webView, arena.allocateFrom(html), base);
    }
  }

  /**
   * Calls {@code webkit_web_view_get_uri}: the URI that's shown, or {@code null} before any load.
   */
  @SneakyThrows
  public String uri(MemorySegment webView) {
    return NativeLibraries.string((MemorySegment) this.webViewGetUri.invokeExact(webView));
  }

  /** The URL that a {@code WebKitNavigationAction}, the argument of {@code create}, goes to. */
  @SneakyThrows
  public String navigationActionUri(MemorySegment action) {
    MemorySegment request = (MemorySegment) this.navigationActionGetRequest.invokeExact(action);
    return NativeLibraries.string((MemorySegment) this.uriRequestGetUri.invokeExact(request));
  }

  /**
   * Sets {@code enable-developer-extras} on the settings of {@code webView}: the Web Inspector, and
   * the "Inspect Element" entry of the context menu.
   */
  @SneakyThrows
  public void setDeveloperExtrasEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    this.settingsSetEnableDeveloperExtras.invokeExact(settings, enabled ? 1 : 0);
  }

  /**
   * Sets {@code enable-media-stream} on the settings of {@code webView}: without it, a page has no
   * {@code navigator.mediaDevices}, and the camera and the microphone never reach the permission
   * request.
   */
  @SneakyThrows
  public void setMediaStreamEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    this.settingsSetEnableMediaStream.invokeExact(settings, enabled ? 1 : 0);
  }

  /**
   * Sets {@code enable-mock-capture-devices}: a fake camera and a fake microphone, which let the
   * tests of the library see a permission request on a machine without either.
   */
  @SneakyThrows
  public void setMockCaptureDevicesEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    this.settingsSetEnableMockCaptureDevices.invokeExact(settings, enabled ? 1 : 0);
  }

  /** Whether {@code request} is a {@code WebKitUserMediaPermissionRequest}. */
  @SneakyThrows
  public boolean isUserMediaRequest(MemorySegment request) {
    long type = (long) this.userMediaRequestGetType.invokeExact();
    return Glib.typeCheckInstanceIsA(request, type);
  }

  /** Whether the user media request asks for a microphone. */
  @SneakyThrows
  public boolean isForAudioDevice(MemorySegment request) {
    return (int) this.userMediaIsForAudio.invokeExact(request) != 0;
  }

  /** Whether the user media request asks for a camera. */
  @SneakyThrows
  public boolean isForVideoDevice(MemorySegment request) {
    return (int) this.userMediaIsForVideo.invokeExact(request) != 0;
  }

  /** Whether the user media request asks to capture a screen or a window. */
  @SneakyThrows
  public boolean isForDisplayDevice(MemorySegment request) {
    return (int) this.userMediaIsForDisplay.invokeExact(request) != 0;
  }

  /** Answers a {@code WebKitPermissionRequest} with yes, or with no. */
  @SneakyThrows
  public void answerPermissionRequest(MemorySegment request, boolean granted) {
    if (granted) {
      this.permissionRequestAllow.invokeExact(request);
    } else {
      this.permissionRequestDeny.invokeExact(request);
    }
  }

  /** Calls {@code webkit_web_view_set_zoom_level}: the page at {@code factor}, 1 for 100%. */
  @SneakyThrows
  public void setZoomLevel(MemorySegment webView, double factor) {
    this.webViewSetZoomLevel.invokeExact(webView, factor);
  }

  /** Calls {@code webkit_web_view_get_zoom_level}. */
  @SneakyThrows
  public double zoomLevel(MemorySegment webView) {
    return (double) this.webViewGetZoomLevel.invokeExact(webView);
  }

  /** The user agent that {@code webView} sends, the one of WebKit until it's set. */
  @SneakyThrows
  public String userAgent(MemorySegment webView) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    return NativeLibraries.string((MemorySegment) this.settingsGetUserAgent.invokeExact(settings));
  }

  /**
   * Runs an editing command on the page, such as {@code Copy}, {@code Paste}, or {@code SelectAll},
   * the names of the {@code WEBKIT_EDITING_COMMAND_*} constants.
   */
  @SneakyThrows
  public void executeEditingCommand(MemorySegment webView, String command) {
    try (Arena arena = Arena.ofConfined()) {
      this.webViewExecuteEditingCommand.invokeExact(webView, arena.allocateFrom(command));
    }
  }

  /** Sets the user agent that {@code webView} sends from its next request on. */
  @SneakyThrows
  public void setUserAgent(MemorySegment webView, String userAgent) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    try (Arena arena = Arena.ofConfined()) {
      this.settingsSetUserAgent.invokeExact(settings, arena.allocateFrom(userAgent));
    }
  }

  /** Reads {@code enable-developer-extras} from the settings of {@code webView}. */
  @SneakyThrows
  public boolean isDeveloperExtrasEnabled(MemorySegment webView) {
    MemorySegment settings = (MemorySegment) this.webViewGetSettings.invokeExact(webView);
    return (int) this.settingsGetEnableDeveloperExtras.invokeExact(settings) != 0;
  }

  /** Returns the version of the loaded WebKitGTK library, as {@code major.minor.micro}. */
  @SneakyThrows
  public String version() {
    return "%d.%d.%d"
        .formatted(
            (int) this.getMajorVersion.invokeExact(),
            (int) this.getMinorVersion.invokeExact(),
            (int) this.getMicroVersion.invokeExact());
  }

  /** Returns the path part of a custom-scheme request, for example {@code /app/index.html}. */
  @SneakyThrows
  public String uriSchemeRequestPath(MemorySegment request) {
    return NativeLibraries.string(
        (MemorySegment) this.uriSchemeRequestGetPath.invokeExact(request));
  }

  /** Answers a custom-scheme request. WebKit consumes the stream. */
  @SneakyThrows
  public void uriSchemeRequestFinish(
      MemorySegment request, MemorySegment stream, long length, String contentType) {
    try (Arena arena = Arena.ofConfined()) {
      this.uriSchemeRequestFinish.invokeExact(
          request, stream, length, arena.allocateFrom(contentType));
    }
  }

  /** Fails a custom-scheme request. WebKit takes ownership of {@code error}. */
  @SneakyThrows
  public void uriSchemeRequestFinishError(MemorySegment request, MemorySegment error) {
    this.uriSchemeRequestFinishError.invokeExact(request, error);
  }

  /**
   * Starts an asynchronous evaluation. {@code callback} is invoked on the GTK thread and must
   * complete the evaluation with {@link #evaluateJavascriptFinish}.
   */
  @SneakyThrows
  public void evaluateJavascript(
      MemorySegment webView, String script, MemorySegment callback, MemorySegment userData) {
    try (Arena arena = Arena.ofConfined()) {
      this.evaluateJavascript.invokeExact(
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
          (MemorySegment) this.evaluateJavascriptFinish.invokeExact(webView, result, error);
      if (value.equals(MemorySegment.NULL)) {
        String message = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
        throw new ScriptEvaluationFailedException(
            message == null ? "Script evaluation failed" : message);
      }
      try {
        return this.valueText(value);
      } finally {
        Glib.unref(value);
      }
    }
  }

  /** A header of a custom-scheme request, or {@code null}. */
  @SneakyThrows
  public String uriSchemeRequestHeader(MemorySegment request, String name) {
    MemorySegment headers =
        (MemorySegment) this.uriSchemeRequestGetHttpHeaders.invokeExact(request);
    if (headers.equals(MemorySegment.NULL)) {
      return null;
    }
    return Soup.messageHeader(headers, name);
  }

  /** {@code webkit_uri_scheme_request_get_web_view}: the view whose page made the request. */
  @SneakyThrows
  public MemorySegment uriSchemeRequestWebView(MemorySegment request) {
    return (MemorySegment) this.uriSchemeRequestGetWebView.invokeExact(request);
  }

  /** {@code webkit_uri_scheme_request_get_http_method}. */
  @SneakyThrows
  public String uriSchemeRequestMethod(MemorySegment request) {
    return NativeLibraries.string(
        (MemorySegment) this.uriSchemeRequestGetHttpMethod.invokeExact(request));
  }

  /**
   * {@code webkit_uri_scheme_request_get_http_body}: a {@code GInputStream} that the caller owns,
   * or {@code NULL} for a request without a body.
   */
  @SneakyThrows
  public MemorySegment uriSchemeRequestBody(MemorySegment request) {
    return (MemorySegment) this.uriSchemeRequestGetHttpBody.invokeExact(request);
  }

  /**
   * Answers a custom-scheme request with {@code status}, {@code headers}, and a body that WebKit
   * reads from {@code stream} as it arrives, to its end.
   */
  @SneakyThrows
  public void uriSchemeRequestFinishWithStream(
      MemorySegment request, MemorySegment stream, int status, Map<String, String> headers) {
    MemorySegment response = (MemorySegment) this.uriSchemeResponseNew.invokeExact(stream, -1L);
    this.uriSchemeResponseSetStatus.invokeExact(response, status, MemorySegment.NULL);
    this.uriSchemeResponseSetHttpHeaders.invokeExact(response, Soup.messageHeaders(headers));
    this.uriSchemeRequestFinishWithResponse.invokeExact(request, response);
    Glib.unref(response);
  }

  /** The web view that started {@code download}, or {@code NULL} for none. */
  @SneakyThrows
  public MemorySegment downloadWebView(MemorySegment download) {
    return (MemorySegment) this.downloadGetWebView.invokeExact(download);
  }

  /** The URL that {@code download} comes from. */
  @SneakyThrows
  public String downloadUri(MemorySegment download) {
    MemorySegment request = (MemorySegment) this.downloadGetRequest.invokeExact(download);
    return NativeLibraries.string((MemorySegment) this.uriRequestGetUri.invokeExact(request));
  }

  /** The media type that the server sent for {@code download}, or {@code null} before it did. */
  @SneakyThrows
  public String downloadMimeType(MemorySegment download) {
    MemorySegment response = (MemorySegment) this.downloadGetResponse.invokeExact(download);
    if (response.equals(MemorySegment.NULL)) {
      return null;
    }
    return NativeLibraries.string(
        (MemorySegment) this.uriResponseGetMimeType.invokeExact(response));
  }

  /** The size of the file of {@code download}, or -1 where the server didn't send it. */
  @SneakyThrows
  public long downloadContentLength(MemorySegment download) {
    MemorySegment response = (MemorySegment) this.downloadGetResponse.invokeExact(download);
    if (response.equals(MemorySegment.NULL)) {
      return -1;
    }
    long length = (long) this.uriResponseGetContentLength.invokeExact(response);
    return length > 0 ? length : -1;
  }

  /** How much of {@code download} arrived, in bytes. */
  @SneakyThrows
  public long downloadReceivedLength(MemorySegment download) {
    return (long) this.downloadGetReceivedDataLength.invokeExact(download);
  }

  /**
   * Sets where {@code download} writes, as the library of the backend takes it, and lets it replace
   * a file that is there. Call from {@code decide-destination}.
   *
   * @param destination A {@code file://} URI for WebKitGTK 4.1, a path for 6.0.
   */
  @SneakyThrows
  public void downloadSetDestination(MemorySegment download, String destination) {
    this.downloadSetAllowOverwrite.invokeExact(download, 1);
    try (Arena arena = Arena.ofConfined()) {
      this.downloadSetDestination.invokeExact(download, arena.allocateFrom(destination));
    }
  }

  /** Cancels {@code download}, which then fails with {@link #DOWNLOAD_ERROR_CANCELLED_BY_USER}. */
  @SneakyThrows
  public void downloadCancel(MemorySegment download) {
    this.downloadCancel.invokeExact(download);
  }
}
