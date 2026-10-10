package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.permission.PermissionKind;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to WebView2: environment creation through the export of the runtime itself, and the
 * {@code ICoreWebView2*} methods that the backend uses, addressed by vtable slot.
 *
 * <p>The slot numbers and IIDs are those of {@code WebView2.h} in SDK 1.0.4191.47. The interfaces
 * used are the original, unversioned ones, which every runtime implements. Nothing here needs a
 * {@code _2} or {@code _3} extension.
 */
@UtilityClass
public class WebView2 {
  public final MemorySegment IID_ENVIRONMENT_COMPLETED =
      Com.guid("4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d");
  public final MemorySegment IID_CONTROLLER_COMPLETED =
      Com.guid("6c4819f3-c9b7-4260-8127-c9f5bde7f68c");
  public final MemorySegment IID_WEB_MESSAGE_RECEIVED =
      Com.guid("57213f19-00e6-49fa-8e07-898ea01ecbd2");
  public final MemorySegment IID_NAVIGATION_STARTING =
      Com.guid("9adbe429-f36d-432b-9ddc-f8881fbd76e3");
  public final MemorySegment IID_NAVIGATION_COMPLETED =
      Com.guid("d33a35bf-1c49-4f98-93ab-006e0533fe1c");
  public final MemorySegment IID_CONTENT_LOADING = Com.guid("364471e7-f2be-4910-bdba-d72077d51c4b");
  public final MemorySegment IID_WEB_RESOURCE_REQUESTED =
      Com.guid("ab00b74c-15f1-4646-80e8-e76341d25d71");
  public final MemorySegment IID_NEW_WINDOW_REQUESTED =
      Com.guid("d4c185fe-c81c-4989-97af-2d3fa7ab5651");
  public final MemorySegment IID_PERMISSION_REQUESTED =
      Com.guid("15e1c6a3-c72a-4df3-91d7-d097fbec6bfd");
  public final MemorySegment IID_EXECUTE_SCRIPT_COMPLETED =
      Com.guid("49511172-cc67-4bca-9923-137112f4c4cc");
  public final MemorySegment IID_ACCELERATOR_KEY_PRESSED =
      Com.guid("b29c7e28-fa79-41a8-8e44-65811c76dcb2");
  public final MemorySegment IID_ADD_SCRIPT_COMPLETED =
      Com.guid("b99369f3-9b11-47b5-bc6f-8e7895fcea17");

  /** {@code COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL}. */
  public final int RESOURCE_CONTEXT_ALL = 0;

  /** {@code COREWEBVIEW2_WEB_RESOURCE_CONTEXT_DOCUMENT}. */
  public final int RESOURCE_CONTEXT_DOCUMENT = 1;

  /** {@code COREWEBVIEW2_WEB_ERROR_STATUS}, by ordinal. */
  public final List<String> WEB_ERROR_STATUS =
      List.of(
          "UNKNOWN",
          "CERTIFICATE_COMMON_NAME_IS_INCORRECT",
          "CERTIFICATE_EXPIRED",
          "CLIENT_CERTIFICATE_CONTAINS_ERRORS",
          "CERTIFICATE_REVOKED",
          "CERTIFICATE_IS_INVALID",
          "SERVER_UNREACHABLE",
          "TIMEOUT",
          "ERROR_HTTP_INVALID_SERVER_RESPONSE",
          "CONNECTION_ABORTED",
          "CONNECTION_RESET",
          "DISCONNECTED",
          "CANNOT_CONNECT",
          "HOST_NAME_NOT_RESOLVED",
          "OPERATION_CANCELED",
          "REDIRECT_FAILED",
          "UNEXPECTED_ERROR",
          "VALID_AUTHENTICATION_CREDENTIALS_REQUIRED",
          "VALID_PROXY_AUTHENTICATION_REQUIRED");

  // ICoreWebView2AcceleratorKeyPressedEventArgs
  private final int ACCELERATOR_KEY_GET_KEY_EVENT_KIND = 3;
  private final int ACCELERATOR_KEY_GET_VIRTUAL_KEY = 4;
  private final int ACCELERATOR_KEY_PUT_HANDLED = 8;

  /** {@code COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN} and {@code _SYSTEM_KEY_DOWN}, with Alt. */
  private final int KEY_EVENT_KIND_KEY_DOWN = 0;

  private final int KEY_EVENT_KIND_SYSTEM_KEY_DOWN = 2;

  // ICoreWebView2Environment
  private final int ENVIRONMENT_CREATE_CONTROLLER = 3;
  private final int ENVIRONMENT_CREATE_RESPONSE = 4;
  private final int ENVIRONMENT_GET_BROWSER_VERSION_STRING = 5;
  // ICoreWebView2Controller
  private final int CONTROLLER_PUT_IS_VISIBLE = 4;
  private final int CONTROLLER_PUT_BOUNDS = 6;
  private final int CONTROLLER_GET_ZOOM_FACTOR = 7;
  private final int CONTROLLER_PUT_ZOOM_FACTOR = 8;
  private final int CONTROLLER_MOVE_FOCUS = 12;
  private final int CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED = 19;
  private final int CONTROLLER_CLOSE = 24;
  private final int CONTROLLER_GET_CORE_WEBVIEW2 = 25;
  // ICoreWebView2Controller2
  private final MemorySegment IID_CONTROLLER_2 = Com.guid("c979903e-d4ca-4228-92eb-47ee3fa96eab");
  private final int CONTROLLER_2_PUT_DEFAULT_BACKGROUND_COLOR = 27;
  // ICoreWebView2
  private final int WEBVIEW_GET_SETTINGS = 3;
  private final int WEBVIEW_GET_SOURCE = 4;
  private final int WEBVIEW_NAVIGATE = 5;
  private final int WEBVIEW_NAVIGATE_TO_STRING = 6;
  private final int WEBVIEW_ADD_NAVIGATION_STARTING = 7;
  private final int WEBVIEW_ADD_CONTENT_LOADING = 9;
  private final int WEBVIEW_ADD_NAVIGATION_COMPLETED = 15;
  private final int WEBVIEW_ADD_PERMISSION_REQUESTED = 23;
  private final int WEBVIEW_ADD_SCRIPT_ON_DOCUMENT_CREATED = 27;
  private final int WEBVIEW_EXECUTE_SCRIPT = 29;
  private final int WEBVIEW_POST_WEB_MESSAGE_AS_STRING = 33;
  private final int WEBVIEW_ADD_WEB_MESSAGE_RECEIVED = 34;
  private final int WEBVIEW_ADD_NEW_WINDOW_REQUESTED = 44;
  private final int WEBVIEW_ADD_WEB_RESOURCE_REQUESTED = 55;
  private final int WEBVIEW_ADD_WEB_RESOURCE_REQUESTED_FILTER = 57;
  // ICoreWebView2Settings
  private final int SETTINGS_GET_DEV_TOOLS_ENABLED = 11;
  private final int SETTINGS_PUT_DEV_TOOLS_ENABLED = 12;
  private final int SETTINGS_PUT_DEFAULT_CONTEXT_MENUS_ENABLED = 14;
  private final int SETTINGS_PUT_IS_ZOOM_CONTROL_ENABLED = 18;
  // ICoreWebView2Settings2
  private final MemorySegment IID_SETTINGS_2 = Com.guid("ee9a0f68-f46c-4e32-ac23-ef8cac224d2a");
  private final int SETTINGS_2_GET_USER_AGENT = 21;
  private final int SETTINGS_2_PUT_USER_AGENT = 22;
  // event args
  private final int NAVIGATION_STARTING_GET_URI = 3;
  private final int NAVIGATION_COMPLETED_GET_IS_SUCCESS = 3;
  private final int NAVIGATION_COMPLETED_GET_ERROR_STATUS = 4;
  private final int WEB_MESSAGE_TRY_GET_AS_STRING = 5;
  private final int NEW_WINDOW_GET_URI = 3;
  private final int NEW_WINDOW_PUT_HANDLED = 6;
  // ICoreWebView2WebMessageReceivedEventArgs2, ICoreWebView2ObjectCollectionView, ICoreWebView2File
  private final MemorySegment IID_WEB_MESSAGE_ARGS_2 =
      Com.guid("06fc7ab7-c90c-4297-9389-33ca01cf6d5e");
  private final int WEB_MESSAGE_2_GET_ADDITIONAL_OBJECTS = 6;
  private final int OBJECT_COLLECTION_GET_COUNT = 3;
  private final int OBJECT_COLLECTION_GET_VALUE_AT_INDEX = 4;
  private final MemorySegment IID_FILE = Com.guid("f2c19559-6bc1-4583-a757-90021be9afec");
  private final int FILE_GET_PATH = 3;
  private final int PERMISSION_GET_URI = 3;
  private final int PERMISSION_GET_KIND = 4;
  private final int PERMISSION_PUT_STATE = 7;
  // ICoreWebView2PermissionRequestedEventArgs3
  private final MemorySegment IID_PERMISSION_ARGS_3 =
      Com.guid("e61670bc-3dce-4177-86d2-c629ae3cb6ac");
  private final int PERMISSION_3_PUT_SAVES_IN_PROFILE = 12;

  // ICoreWebView2_4, the downloads, which came with runtime 1.0.902
  public final MemorySegment IID_WEBVIEW_4 = Com.guid("20d02d59-6df2-42dc-bd06-f98a694b1302");
  public final MemorySegment IID_DOWNLOAD_STARTING =
      Com.guid("efedc989-c396-41ca-83f7-07f845a55724");
  public final MemorySegment IID_BYTES_RECEIVED_CHANGED =
      Com.guid("828e8ab6-d94c-4264-9cef-5217170d6251");
  public final MemorySegment IID_DOWNLOAD_STATE_CHANGED =
      Com.guid("81336594-7ede-4ba9-bf71-acf0a95b58dd");
  private final int WEBVIEW_4_ADD_DOWNLOAD_STARTING = 75;
  private final int DOWNLOAD_STARTING_GET_OPERATION = 3;
  private final int DOWNLOAD_STARTING_PUT_CANCEL = 5;
  private final int DOWNLOAD_STARTING_GET_RESULT_FILE_PATH = 6;
  private final int DOWNLOAD_STARTING_PUT_RESULT_FILE_PATH = 7;
  private final int DOWNLOAD_STARTING_PUT_HANDLED = 9;
  private final int DOWNLOAD_STARTING_GET_DEFERRAL = 10;
  private final int DOWNLOAD_ADD_BYTES_RECEIVED_CHANGED = 3;
  private final int DOWNLOAD_ADD_STATE_CHANGED = 7;
  private final int DOWNLOAD_GET_URI = 9;
  private final int DOWNLOAD_GET_MIME_TYPE = 11;
  private final int DOWNLOAD_GET_TOTAL_BYTES_TO_RECEIVE = 12;
  private final int DOWNLOAD_GET_BYTES_RECEIVED = 13;
  private final int DOWNLOAD_GET_STATE = 16;
  private final int DOWNLOAD_GET_INTERRUPT_REASON = 17;
  private final int DOWNLOAD_CANCEL = 18;

  /** {@code COREWEBVIEW2_DOWNLOAD_STATE_INTERRUPTED}. */
  public final int DOWNLOAD_STATE_INTERRUPTED = 1;

  /** {@code COREWEBVIEW2_DOWNLOAD_STATE_COMPLETED}. */
  public final int DOWNLOAD_STATE_COMPLETED = 2;

  /** {@code COREWEBVIEW2_DOWNLOAD_INTERRUPT_REASON_USER_CANCELED}. */
  public final int DOWNLOAD_INTERRUPT_REASON_USER_CANCELED = 26;

  /** {@code COREWEBVIEW2_DOWNLOAD_INTERRUPT_REASON}, by ordinal. */
  public final List<String> DOWNLOAD_INTERRUPT_REASON =
      List.of(
          "NONE",
          "FILE_FAILED",
          "FILE_ACCESS_DENIED",
          "FILE_NO_SPACE",
          "FILE_NAME_TOO_LONG",
          "FILE_TOO_LARGE",
          "FILE_MALICIOUS",
          "FILE_TRANSIENT_ERROR",
          "FILE_BLOCKED_BY_POLICY",
          "FILE_SECURITY_CHECK_FAILED",
          "FILE_TOO_SHORT",
          "FILE_HASH_MISMATCH",
          "NETWORK_FAILED",
          "NETWORK_TIMEOUT",
          "NETWORK_DISCONNECTED",
          "NETWORK_SERVER_DOWN",
          "NETWORK_INVALID_REQUEST",
          "SERVER_FAILED",
          "SERVER_NO_RANGE",
          "SERVER_BAD_CONTENT",
          "SERVER_UNAUTHORIZED",
          "SERVER_CERTIFICATE_PROBLEM",
          "SERVER_FORBIDDEN",
          "SERVER_UNEXPECTED_RESPONSE",
          "SERVER_CONTENT_LENGTH_MISMATCH",
          "SERVER_CROSS_ORIGIN_REDIRECT",
          "USER_CANCELED",
          "USER_SHUTDOWN",
          "USER_PAUSED",
          "DOWNLOAD_PROCESS_CRASHED");

  /** {@code COREWEBVIEW2_PERMISSION_STATE_ALLOW} and {@code _DENY}. */
  private final int PERMISSION_STATE_ALLOW = 1;

  private final int PERMISSION_STATE_DENY = 2;

  /** {@code COREWEBVIEW2_PERMISSION_KIND_MICROPHONE}, {@code _CAMERA}, ... */
  private final int PERMISSION_KIND_MICROPHONE = 1;

  private final int PERMISSION_KIND_CAMERA = 2;
  private final int PERMISSION_KIND_GEOLOCATION = 3;
  private final int PERMISSION_KIND_NOTIFICATIONS = 4;
  private final int RESOURCE_REQUESTED_GET_REQUEST = 3;
  private final int RESOURCE_REQUESTED_PUT_RESPONSE = 5;
  private final int RESOURCE_REQUESTED_GET_DEFERRAL = 6;
  private final int DEFERRAL_COMPLETE = 3;
  private final int RESOURCE_REQUESTED_GET_CONTEXT = 7;
  private final int REQUEST_GET_URI = 3;
  private final MemorySegment IID_WEBVIEW_17 = Com.guid("702e75d4-fd44-434d-9d70-1a68a6b1192a");
  private final MemorySegment IID_ENVIRONMENT_12 = Com.guid("f503db9b-739f-48dd-b151-fdfcf253f54e");
  private final int WEBVIEW_17_POST_SHARED_BUFFER_TO_SCRIPT = 116;
  private final int ENVIRONMENT_12_CREATE_SHARED_BUFFER = 24;
  private final int SHARED_BUFFER_GET_BUFFER = 4;
  private final int SHARED_BUFFER_ACCESS_READ_ONLY = 0;

  /**
   * The one export used from {@code EmbeddedBrowserWebView.dll}. Loading this class loads the
   * runtime. {@link WebView2Runtime#library()} answers whether the runtime is installed without
   * loading it.
   */
  private final MethodHandle CREATE_ENVIRONMENT_INTERNAL =
      NativeLibraries.downcall(
          Kernel32.symbols(
              Kernel32.loadLibrary(
                  WebView2Runtime.library()
                      .orElseThrow(
                          () -> new UnsatisfiedLinkError("WebView2 Runtime is not installed"))
                      .toString())),
          "CreateWebViewEnvironmentWithOptionsInternal",
          Signatures.INT_BOOL_INT_POINTER_X3);

  private final VarHandle RECT_RIGHT =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("right"));
  private final VarHandle RECT_BOTTOM =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("bottom"));

  /**
   * {@code CreateWebViewEnvironmentWithOptionsInternal(true, installed, userDataFolder, NULL,
   * handler)}: what the public function of {@code WebView2Loader.dll} does after it locates the
   * runtime. Call this method on an STA thread. The handler fires on that thread.
   */
  @SneakyThrows
  public void createEnvironment(String userDataFolder, MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "CreateWebViewEnvironmentWithOptionsInternal",
          (int)
              CREATE_ENVIRONMENT_INTERNAL.invokeExact(
                  true, 0, Wide.allocate(arena, userDataFolder), MemorySegment.NULL, handler));
    }
  }

  /**
   * Calls {@code ICoreWebView2Environment::CreateCoreWebView2Controller}. The handler fires on the
   * UI thread.
   */
  public void createController(
      MemorySegment environment, MemorySegment hwnd, MemorySegment handler) {
    Com.check(
        "CreateCoreWebView2Controller",
        Com.call(environment, ENVIRONMENT_CREATE_CONTROLLER, hwnd, handler));
  }

  /**
   * A response object that the caller owns. {@code stream} can be {@code NULL} for an empty body.
   */
  public MemorySegment createResponse(
      MemorySegment environment, MemorySegment stream, int status, String reason, String headers) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "CreateWebResourceResponse",
          Com.call(
              environment,
              ENVIRONMENT_CREATE_RESPONSE,
              stream,
              status,
              Wide.allocate(arena, reason),
              Wide.allocate(arena, headers),
              out));
      return Com.pointerAt(out);
    }
  }

  /** The {@code ICoreWebView2} behind a controller. The caller owns the returned reference. */
  public MemorySegment coreWebView2(MemorySegment controller) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_CoreWebView2", Com.call(controller, CONTROLLER_GET_CORE_WEBVIEW2, out));
      return Com.pointerAt(out);
    }
  }

  /** Calls {@code ICoreWebView2Controller::put_IsVisible}. */
  public void setVisible(MemorySegment controller, boolean visible) {
    Com.check("put_IsVisible", Com.call(controller, CONTROLLER_PUT_IS_VISIBLE, visible ? 1 : 0));
  }

  /**
   * Calls {@code ICoreWebView2Controller2::put_DefaultBackgroundColor} with a transparent color, so
   * what the page leaves clear shows what is under the view. The {@code COREWEBVIEW2_COLOR} of four
   * bytes goes by value, in a register, which is where an {@code int} of zero goes.
   */
  public void setTransparentBackground(MemorySegment controller) {
    MemorySegment controller2 = WinRt.query(controller, IID_CONTROLLER_2);
    try {
      Com.check(
          "put_DefaultBackgroundColor",
          Com.call(controller2, CONTROLLER_2_PUT_DEFAULT_BACKGROUND_COLOR, 0));
    } finally {
      Com.release(controller2);
    }
  }

  /** Sizes the view to {@code width} by {@code height} at the origin of the parent. */
  public void setBounds(MemorySegment controller, int width, int height) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.RECT);
      RECT_RIGHT.set(rect, 0L, width);
      RECT_BOTTOM.set(rect, 0L, height);
      Com.check("put_Bounds", Com.callWithRect(controller, CONTROLLER_PUT_BOUNDS, rect));
    }
  }

  /**
   * Calls {@code ICoreWebView2Controller::Close}: releases the browser process resources of the
   * view.
   */
  public void close(MemorySegment controller) {
    Com.check("Close", Com.call(controller, CONTROLLER_CLOSE));
  }

  /**
   * Switches the developer tools and, with them, the right-click menu of the engine. A shipped
   * application wants neither: the menu offers "Reload", "View source", and "Inspect". With the
   * tools on, the menu stays, because "Inspect" lives there. The {@code contextmenu} handlers of
   * the page are unaffected either way.
   */
  public void setDevToolsEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = WebView2.settings(webView);
    try {
      Com.check(
          "put_AreDevToolsEnabled",
          Com.call(settings, SETTINGS_PUT_DEV_TOOLS_ENABLED, enabled ? 1 : 0));
      Com.check(
          "put_AreDefaultContextMenusEnabled",
          Com.call(settings, SETTINGS_PUT_DEFAULT_CONTEXT_MENUS_ENABLED, enabled ? 1 : 0));
    } finally {
      Com.release(settings);
    }
  }

  /**
   * Sets {@code IsZoomControlEnabled}: whether the user zooms the page with Ctrl and the wheel or
   * with Ctrl and the plus and minus keys. Off, only {@link #setZoomFactor} zooms.
   */
  public void setZoomControlEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment settings = WebView2.settings(webView);
    try {
      Com.check(
          "put_IsZoomControlEnabled",
          Com.call(settings, SETTINGS_PUT_IS_ZOOM_CONTROL_ENABLED, enabled ? 1 : 0));
    } finally {
      Com.release(settings);
    }
  }

  /** Calls {@code ICoreWebView2Controller::put_ZoomFactor}. */
  public void setZoomFactor(MemorySegment controller, double factor) {
    Com.check("put_ZoomFactor", Com.call(controller, CONTROLLER_PUT_ZOOM_FACTOR, factor));
  }

  /** Calls {@code ICoreWebView2Controller::get_ZoomFactor}. */
  public double zoomFactor(MemorySegment controller) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_DOUBLE);
      Com.check("get_ZoomFactor", Com.call(controller, CONTROLLER_GET_ZOOM_FACTOR, out));
      return out.get(Signatures.C_DOUBLE, 0);
    }
  }

  /** Reads {@code AreDevToolsEnabled} from the settings of {@code webView}. */
  public boolean isDevToolsEnabled(MemorySegment webView) {
    MemorySegment settings = WebView2.settings(webView);
    try {
      return WebView2.integer(settings, SETTINGS_GET_DEV_TOOLS_ENABLED, "get_AreDevToolsEnabled")
          != 0;
    } finally {
      Com.release(settings);
    }
  }

  /**
   * Sets the user agent of {@code webView} to what {@code change} makes of the current one, through
   * {@code ICoreWebView2Settings2}, which every runtime since 86 has.
   */
  public void changeUserAgent(MemorySegment webView, UnaryOperator<String> change) {
    MemorySegment settings = WebView2.settings(webView);
    MemorySegment settings2 = null;
    try (Arena arena = Arena.ofConfined()) {
      settings2 = WinRt.query(settings, IID_SETTINGS_2);
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_UserAgent", Com.call(settings2, SETTINGS_2_GET_USER_AGENT, out));
      String userAgent = change.apply(Wide.take(Com.pointerAt(out)));
      Com.check(
          "put_UserAgent",
          Com.call(settings2, SETTINGS_2_PUT_USER_AGENT, Wide.allocate(arena, userAgent)));
    } finally {
      Com.release(settings2);
      Com.release(settings);
    }
  }

  /**
   * The {@code ICoreWebView2Settings} of {@code webView}. The caller owns the returned reference.
   */
  private MemorySegment settings(MemorySegment webView) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Settings", Com.call(webView, WEBVIEW_GET_SETTINGS, out));
      return Com.pointerAt(out);
    }
  }

  /**
   * {@code ICoreWebView2Environment::get_BrowserVersionString}: the runtime version, for example
   * {@code 138.0.3351.65}.
   */
  public String browserVersion(MemorySegment environment) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "get_BrowserVersionString",
          Com.call(environment, ENVIRONMENT_GET_BROWSER_VERSION_STRING, out));
      return Wide.take(Com.pointerAt(out));
    }
  }

  /** Calls {@code ICoreWebView2::get_Source}: the URI of the current document. */
  public String source(MemorySegment webView) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Source", Com.call(webView, WEBVIEW_GET_SOURCE, out));
      return Wide.take(Com.pointerAt(out));
    }
  }

  /** Calls {@code ICoreWebView2::Navigate}. */
  public void navigate(MemorySegment webView, String uri) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check("Navigate", Com.call(webView, WEBVIEW_NAVIGATE, Wide.allocate(arena, uri)));
    }
  }

  /**
   * Calls {@code ICoreWebView2::NavigateToString}: shows {@code html} under {@code about:blank}.
   */
  public void navigateToString(MemorySegment webView, String html) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "NavigateToString",
          Com.call(webView, WEBVIEW_NAVIGATE_TO_STRING, Wide.allocate(arena, html)));
    }
  }

  /** Calls {@code ICoreWebView2::add_NavigationStarting}. */
  public void onNavigationStarting(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(webView, WEBVIEW_ADD_NAVIGATION_STARTING, handler, "add_NavigationStarting");
  }

  /**
   * Calls {@code ICoreWebView2Controller::add_AcceleratorKeyPressed}: the keys that the page gets
   * with a modifier, and the function keys, before the page does.
   */
  public void onAcceleratorKeyPressed(MemorySegment controller, MemorySegment handler) {
    WebView2.addEvent(
        controller, CONTROLLER_ADD_ACCELERATOR_KEY_PRESSED, handler, "add_AcceleratorKeyPressed");
  }

  /**
   * The virtual key of a key pressed down, from the arguments of {@code AcceleratorKeyPressed}, or
   * 0 for a key let go.
   */
  public int pressedKey(MemorySegment arguments) {
    int kind = WebView2.integer(arguments, ACCELERATOR_KEY_GET_KEY_EVENT_KIND, "get_KeyEventKind");
    if (kind != KEY_EVENT_KIND_KEY_DOWN && kind != KEY_EVENT_KIND_SYSTEM_KEY_DOWN) {
      return 0;
    }
    return WebView2.integer(arguments, ACCELERATOR_KEY_GET_VIRTUAL_KEY, "get_VirtualKey");
  }

  /**
   * Calls {@code ICoreWebView2Controller::MoveFocus} for {@code PROGRAMMATIC}: the page gets the
   * keyboard, where it had it last.
   */
  public void moveFocus(MemorySegment controller) {
    Com.check("MoveFocus", Com.call(controller, CONTROLLER_MOVE_FOCUS, 0));
  }

  /** Keeps a key of {@code AcceleratorKeyPressed} away from the page. */
  public void markHandled(MemorySegment arguments) {
    Com.check("put_Handled", Com.call(arguments, ACCELERATOR_KEY_PUT_HANDLED, 1));
  }

  /** Calls {@code ICoreWebView2::add_ContentLoading}. */
  public void onContentLoading(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(webView, WEBVIEW_ADD_CONTENT_LOADING, handler, "add_ContentLoading");
  }

  /** Calls {@code ICoreWebView2::add_NavigationCompleted}. */
  public void onNavigationCompleted(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(
        webView, WEBVIEW_ADD_NAVIGATION_COMPLETED, handler, "add_NavigationCompleted");
  }

  /** Calls {@code ICoreWebView2::add_WebMessageReceived}. */
  public void onWebMessageReceived(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(webView, WEBVIEW_ADD_WEB_MESSAGE_RECEIVED, handler, "add_WebMessageReceived");
  }

  /** Calls {@code ICoreWebView2::add_NewWindowRequested}. */
  public void onNewWindowRequested(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(webView, WEBVIEW_ADD_NEW_WINDOW_REQUESTED, handler, "add_NewWindowRequested");
  }

  /** Calls {@code ICoreWebView2::add_PermissionRequested}. */
  public void onPermissionRequested(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(
        webView, WEBVIEW_ADD_PERMISSION_REQUESTED, handler, "add_PermissionRequested");
  }

  /** The origin that a {@code PermissionRequested} event comes from. */
  public String permissionUri(MemorySegment arguments) {
    return WebView2.uri(arguments, PERMISSION_GET_URI);
  }

  /**
   * The kind that a {@code PermissionRequested} event asks for, or {@code null} for a kind that the
   * library doesn't pass on, such as the clipboard or a sensor.
   */
  public PermissionKind permissionKind(MemorySegment arguments) {
    return switch (WebView2.integer(arguments, PERMISSION_GET_KIND, "get_PermissionKind")) {
      case PERMISSION_KIND_MICROPHONE -> PermissionKind.MICROPHONE;
      case PERMISSION_KIND_CAMERA -> PermissionKind.CAMERA;
      case PERMISSION_KIND_GEOLOCATION -> PermissionKind.GEOLOCATION;
      case PERMISSION_KIND_NOTIFICATIONS -> PermissionKind.NOTIFICATIONS;
      default -> null;
    };
  }

  /**
   * Answers a {@code PermissionRequested} event with {@code put_State}. Either answer keeps
   * WebView2 from asking the user. The answer is not saved in the profile, or WebView2 would repeat
   * the first one of an origin without asking the handler again.
   */
  public void answerPermission(MemorySegment arguments, boolean granted) {
    MemorySegment arguments3 = null;
    try {
      arguments3 = WinRt.query(arguments, IID_PERMISSION_ARGS_3);
      Com.check("put_SavesInProfile", Com.call(arguments3, PERMISSION_3_PUT_SAVES_IN_PROFILE, 0));
    } catch (RuntimeException _) {
      // A runtime without the setting keeps the answer for the session: a loss of the handler's
      // second look, not of the answer.
    } finally {
      Com.release(arguments3);
    }
    Com.check(
        "put_State",
        Com.call(
            arguments,
            PERMISSION_PUT_STATE,
            granted ? PERMISSION_STATE_ALLOW : PERMISSION_STATE_DENY));
  }

  /** Calls {@code ICoreWebView2::add_WebResourceRequested}. */
  public void onWebResourceRequested(MemorySegment webView, MemorySegment handler) {
    WebView2.addEvent(
        webView, WEBVIEW_ADD_WEB_RESOURCE_REQUESTED, handler, "add_WebResourceRequested");
  }

  /**
   * Routes requests that match {@code uriPattern} (wildcards allowed) through the requested event.
   */
  public void addWebResourceRequestedFilter(MemorySegment webView, String uriPattern) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "AddWebResourceRequestedFilter",
          Com.call(
              webView,
              WEBVIEW_ADD_WEB_RESOURCE_REQUESTED_FILTER,
              Wide.allocate(arena, uriPattern),
              RESOURCE_CONTEXT_ALL));
    }
  }

  /**
   * Calls {@code ICoreWebView2::AddScriptToExecuteOnDocumentCreated}: runs {@code script} in every
   * later document before its own scripts.
   */
  public void addScriptToExecuteOnDocumentCreated(
      MemorySegment webView, String script, MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "AddScriptToExecuteOnDocumentCreated",
          Com.call(
              webView,
              WEBVIEW_ADD_SCRIPT_ON_DOCUMENT_CREATED,
              Wide.allocate(arena, script),
              handler));
    }
  }

  /** Calls {@code ICoreWebView2::ExecuteScript}. The handler receives the result as JSON text. */
  public void executeScript(MemorySegment webView, String script, MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "ExecuteScript",
          Com.call(webView, WEBVIEW_EXECUTE_SCRIPT, Wide.allocate(arena, script), handler));
    }
  }

  /** Returns the URI of a {@code NavigationStarting} event. */
  public String navigationStartingUri(MemorySegment arguments) {
    return WebView2.uri(arguments, NAVIGATION_STARTING_GET_URI);
  }

  /**
   * Takes over a {@code NewWindowRequested} event: {@code put_Handled(TRUE)} keeps WebView2 from
   * opening a window of its own, and the URL it was for is returned.
   */
  public String takeNewWindowRequest(MemorySegment arguments) {
    Com.check("put_Handled", Com.call(arguments, NEW_WINDOW_PUT_HANDLED, 1));
    return WebView2.uri(arguments, NEW_WINDOW_GET_URI);
  }

  /** Reads {@code IsSuccess} from a {@code NavigationCompleted} event. */
  public boolean isNavigationSuccessful(MemorySegment arguments) {
    return WebView2.integer(arguments, NAVIGATION_COMPLETED_GET_IS_SUCCESS, "get_IsSuccess") != 0;
  }

  /** The {@code COREWEBVIEW2_WEB_ERROR_STATUS} of the failed navigation, as its enum name. */
  public String navigationErrorStatus(MemorySegment arguments) {
    int status =
        WebView2.integer(arguments, NAVIGATION_COMPLETED_GET_ERROR_STATUS, "get_WebErrorStatus");
    return status >= 0 && status < WEB_ERROR_STATUS.size()
        ? WEB_ERROR_STATUS.get(status)
        : "STATUS_" + status;
  }

  /**
   * The message that a page posted, if it posted a string, or {@code null} for any other JSON
   * value.
   */
  public String webMessageAsString(MemorySegment arguments) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      int hresult = Com.call(arguments, WEB_MESSAGE_TRY_GET_AS_STRING, out);
      return hresult < 0 ? null : Wide.take(Com.pointerAt(out));
    }
  }

  /**
   * The paths of the files that a page posted with {@code postMessageWithAdditionalObjects}: the
   * {@code File} objects of a {@code drop} event, which the host sees as {@code ICoreWebView2File}.
   * Empty for a message without any, and for a runtime that is older than the call.
   */
  public List<String> additionalFilePaths(MemorySegment arguments) {
    MemorySegment arguments2 = null;
    MemorySegment collection = null;
    try (Arena arena = Arena.ofConfined()) {
      arguments2 = WinRt.query(arguments, IID_WEB_MESSAGE_ARGS_2);
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "get_AdditionalObjects", Com.call(arguments2, WEB_MESSAGE_2_GET_ADDITIONAL_OBJECTS, out));
      collection = Com.pointerAt(out);
      if (collection.equals(MemorySegment.NULL)) {
        return List.of();
      }
      int count = WebView2.integer(collection, OBJECT_COLLECTION_GET_COUNT, "get_Count");
      List<String> paths = new ArrayList<>();
      for (int index = 0; index < count; index++) {
        MemorySegment item = arena.allocate(Signatures.C_POINTER);
        Com.check(
            "GetValueAtIndex",
            Com.call(collection, OBJECT_COLLECTION_GET_VALUE_AT_INDEX, index, item));
        MemorySegment object = Com.pointerAt(item);
        MemorySegment file = null;
        try {
          file = WinRt.query(object, IID_FILE);
          MemorySegment path = arena.allocate(Signatures.C_POINTER);
          Com.check("get_Path", Com.call(file, FILE_GET_PATH, path));
          paths.add(Wide.take(Com.pointerAt(path)));
        } finally {
          Com.release(file);
          Com.release(object);
        }
      }
      return paths;
    } catch (RuntimeException e) {
      // No such interface in an older runtime: the page gets its files, and Java gets none.
      return List.of();
    } finally {
      Com.release(collection);
      Com.release(arguments2);
    }
  }

  /** The URI of the intercepted request. */
  public String requestedUri(MemorySegment arguments) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Request", Com.call(arguments, RESOURCE_REQUESTED_GET_REQUEST, out));
      MemorySegment request = Com.pointerAt(out);
      try {
        return WebView2.uri(request, REQUEST_GET_URI);
      } finally {
        Com.release(request);
      }
    }
  }

  /** Checks whether an intercepted request is for the main document, not a subresource. */
  public boolean isDocumentRequest(MemorySegment arguments) {
    return WebView2.integer(arguments, RESOURCE_REQUESTED_GET_CONTEXT, "get_ResourceContext")
        == RESOURCE_CONTEXT_DOCUMENT;
  }

  /** Calls {@code ICoreWebView2WebResourceRequestedEventArgs::put_Response}. */
  public void respond(MemorySegment arguments, MemorySegment response) {
    Com.check("put_Response", Com.call(arguments, RESOURCE_REQUESTED_PUT_RESPONSE, response));
  }

  /**
   * Calls {@code ICoreWebView2WebResourceRequestedEventArgs::GetDeferral}: the request waits for
   * {@link #completeDeferral} instead of for the return of the handler. The caller releases it.
   */
  public MemorySegment deferral(MemorySegment arguments) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("GetDeferral", Com.call(arguments, RESOURCE_REQUESTED_GET_DEFERRAL, out));
      return Com.pointerAt(out);
    }
  }

  /** Calls {@code ICoreWebView2Deferral::Complete}: the response set by now goes to the page. */
  public void completeDeferral(MemorySegment deferral) {
    Com.check("Complete", Com.call(deferral, DEFERRAL_COMPLETE));
  }

  private void addEvent(MemorySegment webView, int slot, MemorySegment handler, String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment token = arena.allocate(Signatures.C_LONG_PTR);
      Com.check(name, Com.call(webView, slot, handler, token));
    }
  }

  private String uri(MemorySegment object, int slot) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Uri", Com.call(object, slot, out));
      return Wide.take(Com.pointerAt(out));
    }
  }

  private int integer(MemorySegment object, int slot, String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_INT);
      Com.check(name, Com.call(object, slot, out));
      return out.get(Signatures.C_INT, 0);
    }
  }

  // --- downloads ---

  /** Calls {@code ICoreWebView2_4::add_DownloadStarting} on the {@code ICoreWebView2_4}. */
  public void onDownloadStarting(MemorySegment webView4, MemorySegment handler) {
    WebView2.addEvent(webView4, WEBVIEW_4_ADD_DOWNLOAD_STARTING, handler, "add_DownloadStarting");
  }

  /**
   * The {@code ICoreWebView2DownloadOperation} of a {@code DownloadStarting} event, which the
   * caller releases.
   */
  public MemorySegment downloadOperation(MemorySegment arguments) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_DownloadOperation", Com.call(arguments, DOWNLOAD_STARTING_GET_OPERATION, out));
      return Com.pointerAt(out);
    }
  }

  /** The file that WebView2 would write a download of a {@code DownloadStarting} event to. */
  public String downloadDefaultPath(MemorySegment arguments) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "get_ResultFilePath", Com.call(arguments, DOWNLOAD_STARTING_GET_RESULT_FILE_PATH, out));
      return Wide.take(Com.pointerAt(out));
    }
  }

  /**
   * Marks a {@code DownloadStarting} event as handled, which keeps the download bubble of Edge
   * away, and takes its deferral, which the caller completes with {@link #completeDeferral} and
   * releases: the download waits until then.
   */
  public MemorySegment deferDownload(MemorySegment arguments) {
    Com.check("put_Handled", Com.call(arguments, DOWNLOAD_STARTING_PUT_HANDLED, 1));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("GetDeferral", Com.call(arguments, DOWNLOAD_STARTING_GET_DEFERRAL, out));
      return Com.pointerAt(out);
    }
  }

  /** Answers a {@code DownloadStarting} event with the file to write. */
  public void setDownloadPath(MemorySegment arguments, String path) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "put_ResultFilePath",
          Com.call(arguments, DOWNLOAD_STARTING_PUT_RESULT_FILE_PATH, Wide.allocate(arena, path)));
    }
  }

  /** Answers a {@code DownloadStarting} event with no download at all. */
  public void denyDownload(MemorySegment arguments) {
    Com.check("put_Cancel", Com.call(arguments, DOWNLOAD_STARTING_PUT_CANCEL, 1));
  }

  /** Calls {@code add_BytesReceivedChanged} of a download. */
  public void onDownloadBytesReceived(MemorySegment operation, MemorySegment handler) {
    WebView2.addEvent(
        operation, DOWNLOAD_ADD_BYTES_RECEIVED_CHANGED, handler, "add_BytesReceivedChanged");
  }

  /** Calls {@code add_StateChanged} of a download. */
  public void onDownloadStateChanged(MemorySegment operation, MemorySegment handler) {
    WebView2.addEvent(operation, DOWNLOAD_ADD_STATE_CHANGED, handler, "add_StateChanged");
  }

  /** The URL that a download comes from. */
  public String downloadUri(MemorySegment operation) {
    return WebView2.uri(operation, DOWNLOAD_GET_URI);
  }

  /** The media type that the server sent for a download. */
  public String downloadMimeType(MemorySegment operation) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_MimeType", Com.call(operation, DOWNLOAD_GET_MIME_TYPE, out));
      return Wide.take(Com.pointerAt(out));
    }
  }

  /** The size of the file of a download, or -1 where the server didn't send it. */
  public long downloadTotalBytes(MemorySegment operation) {
    long total =
        WebView2.int64(operation, DOWNLOAD_GET_TOTAL_BYTES_TO_RECEIVE, "get_TotalBytesToReceive");
    return total > 0 ? total : -1;
  }

  /** How much of a download arrived, in bytes. */
  public long downloadBytesReceived(MemorySegment operation) {
    return WebView2.int64(operation, DOWNLOAD_GET_BYTES_RECEIVED, "get_BytesReceived");
  }

  /** {@code COREWEBVIEW2_DOWNLOAD_STATE} of a download. */
  public int downloadState(MemorySegment operation) {
    return WebView2.integer(operation, DOWNLOAD_GET_STATE, "get_State");
  }

  /** Why a download stopped, as a name of {@link #DOWNLOAD_INTERRUPT_REASON}. */
  public String downloadInterruptReason(MemorySegment operation) {
    int reason = WebView2.integer(operation, DOWNLOAD_GET_INTERRUPT_REASON, "get_InterruptReason");
    return reason >= 0 && reason < DOWNLOAD_INTERRUPT_REASON.size()
        ? DOWNLOAD_INTERRUPT_REASON.get(reason)
        : "UNKNOWN_" + reason;
  }

  /** Whether a download stopped because it was canceled. */
  public boolean isDownloadCanceled(MemorySegment operation) {
    return WebView2.integer(operation, DOWNLOAD_GET_INTERRUPT_REASON, "get_InterruptReason")
        == DOWNLOAD_INTERRUPT_REASON_USER_CANCELED;
  }

  /** Cancels a download, which then changes its state to interrupted. */
  public void cancelDownload(MemorySegment operation) {
    Com.check("Cancel", Com.call(operation, DOWNLOAD_CANCEL));
  }

  private long int64(MemorySegment object, int slot, String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_LONG_PTR);
      Com.check(name, Com.call(object, slot, out));
      return out.get(Signatures.C_LONG_PTR, 0);
    }
  }

  // --- RPC: web messages and shared buffers ---

  /** {@code PostWebMessageAsString}: {@code event.data} of a {@code message} event on the page. */
  public void postWebMessageAsString(MemorySegment webView, String message) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "PostWebMessageAsString",
          Com.call(webView, WEBVIEW_POST_WEB_MESSAGE_AS_STRING, Wide.allocate(arena, message)));
    }
  }

  /**
   * Copies {@code data} into a new shared buffer and posts it to the page, which receives a {@code
   * sharedbufferreceived} event with the buffer as an {@code ArrayBuffer}, read-only, and {@code
   * additionalDataAsJson} as {@code additionalData}. The page releases the buffer with {@code
   * chrome.webview.releaseBuffer}; Java releases its reference here, and the memory goes when both
   * have. {@code ICoreWebView2_17} and {@code ICoreWebView2Environment12}, WebView2 runtime 114 and
   * later.
   */
  public void postSharedBuffer(
      MemorySegment environment, MemorySegment webView, byte[] data, String additionalDataAsJson) {
    MemorySegment environment12 = WinRt.query(environment, IID_ENVIRONMENT_12);
    MemorySegment webView17 = null;
    MemorySegment buffer = null;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "CreateSharedBuffer",
          Com.call(environment12, ENVIRONMENT_12_CREATE_SHARED_BUFFER, (long) data.length, out));
      buffer = Com.pointerAt(out);
      Com.check("get_Buffer", Com.call(buffer, SHARED_BUFFER_GET_BUFFER, out));
      MemorySegment.copy(
          MemorySegment.ofArray(data),
          0,
          Com.pointerAt(out).reinterpret(data.length),
          0,
          data.length);
      webView17 = WinRt.query(webView, IID_WEBVIEW_17);
      Com.check(
          "PostSharedBufferToScript",
          Com.call(
              webView17,
              WEBVIEW_17_POST_SHARED_BUFFER_TO_SCRIPT,
              buffer,
              SHARED_BUFFER_ACCESS_READ_ONLY,
              Wide.allocate(arena, additionalDataAsJson)));
    } finally {
      Com.release(buffer);
      Com.release(webView17);
      Com.release(environment12);
    }
  }
}
