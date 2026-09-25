package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import lombok.experimental.UtilityClass;

/**
 * Every native signature that the GTK 4 backend binds, in one place.
 *
 * <p>Descriptors are named after their shape instead of after one function, because the same shape
 * serves many calls and the JIT compiler only needs the shape. Where a signature is so long that a
 * shape name would be unreadable, the name of the function is used instead.
 *
 * <p>Loading this class has no side effects. It opens no library and creates no {@link
 * java.lang.foreign.Linker}, so a build-time tool such as {@code native-image} can inspect the
 * descriptors without using the toolkit.
 */
@UtilityClass
public class Signatures {
  /** {@code gint}, {@code gboolean}, {@code guint}, {@code GQuark}. */
  public final ValueLayout.OfInt C_INT = Layouts.C_INT;

  /** {@code glong}, {@code gulong}, {@code gssize}, {@code gint64}: GTK targets are LP64. */
  public final ValueLayout.OfLong C_LONG = Layouts.C_LONG_LONG;

  /** Any {@code T*}. */
  public final AddressLayout C_POINTER = Layouts.C_POINTER;

  /** {@code double}. */
  public final ValueLayout.OfDouble C_DOUBLE = Layouts.C_DOUBLE;

  // --- shapes ---

  /**
   * {@code void f(GdkToplevel*, GdkDevice*, gint button, double x, double y, guint32 timestamp)}:
   * {@code gdk_toplevel_begin_move}.
   */
  public final FunctionDescriptor GDK_TOPLEVEL_BEGIN_MOVE =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT, C_DOUBLE, C_DOUBLE, C_INT);

  /**
   * {@code void f(GdkToplevel*, GdkSurfaceEdge, GdkDevice*, gint button, double x, double y,
   * guint32 timestamp)}: {@code gdk_toplevel_begin_resize}.
   */
  public final FunctionDescriptor GDK_TOPLEVEL_BEGIN_RESIZE =
      FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_POINTER, C_INT, C_DOUBLE, C_DOUBLE, C_INT);

  /**
   * {@code gboolean f(GdkSurface*, GdkDevice*, double* x, double* y, GdkModifierType* mask)}:
   * {@code gdk_surface_get_device_position}.
   */
  public final FunctionDescriptor GDK_SURFACE_GET_DEVICE_POSITION =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code double f(T*)}: {@code gdk_monitor_get_scale}. */
  public final FunctionDescriptor DOUBLE_POINTER = FunctionDescriptor.of(C_DOUBLE, C_POINTER);

  /** {@code T* f(void)}. */
  public final FunctionDescriptor POINTER_VOID = FunctionDescriptor.of(C_POINTER);

  /** {@code gint f(void)}. */
  public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

  /** {@code void f(T*)}. */
  public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

  /** {@code T* f(U*)}. */
  public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

  /** {@code T* f(gsize)}. */
  public final FunctionDescriptor POINTER_LONG = FunctionDescriptor.of(C_POINTER, C_LONG);

  /** {@code gint f(T*)}. */
  public final FunctionDescriptor INT_POINTER = FunctionDescriptor.of(C_INT, C_POINTER);

  /** {@code gboolean f(T*, U*)}: {@code g_idle_add}. */
  public final FunctionDescriptor INT_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER);

  /** {@code gboolean f(T*, U*, V*)}: {@code webkit_user_content_manager_register_...}. */
  public final FunctionDescriptor INT_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void f(T*, U*)}. */
  public final FunctionDescriptor VOID_POINTER_POINTER =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

  /** {@code void f(T*, U*, V*)}. */
  public final FunctionDescriptor VOID_POINTER_POINTER_POINTER =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

  /** {@code void f(T*, gint)}. */
  public final FunctionDescriptor VOID_POINTER_INT = FunctionDescriptor.ofVoid(C_POINTER, C_INT);

  /** {@code void f(T*, gint, gint)}. */
  public final FunctionDescriptor VOID_POINTER_INT_INT =
      FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT);

  /** {@code void f(T*, guint, const gchar*)}: {@code webkit_uri_scheme_response_set_status}. */
  public final FunctionDescriptor VOID_POINTER_INT_POINTER =
      FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_POINTER);

  /** {@code GType f(void)}: the {@code _get_type} functions. */
  public final FunctionDescriptor LONG_VOID = FunctionDescriptor.of(C_LONG);

  /** {@code T* f(U*, const gchar*, gint)}: {@code gtk_dialog_add_button}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_INT =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_INT);

  /** {@code T* f(const gchar*, U*, gint, const gchar*, const gchar*)}: the native file chooser. */
  public final FunctionDescriptor GTK_FILE_CHOOSER_NATIVE_NEW =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER);

  /** {@code T* f(gint, gint)}: {@code gtk_box_new}. */
  public final FunctionDescriptor POINTER_INT_INT = FunctionDescriptor.of(C_POINTER, C_INT, C_INT);

  /** {@code T* f(U*, gboolean)}: {@code g_main_loop_new}. */
  public final FunctionDescriptor POINTER_POINTER_INT =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT);

  /** {@code void f(T*, U*, gint64, V*)}: {@code webkit_uri_scheme_request_finish}. */
  public final FunctionDescriptor VOID_POINTER_POINTER_LONG_POINTER =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /** {@code T* f(U*, gssize, V*)}: {@code g_memory_input_stream_new_from_data}. */
  public final FunctionDescriptor POINTER_POINTER_LONG_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /** {@code T* f(GQuark, gint, const gchar*)}: {@code g_error_new_literal}. */
  public final FunctionDescriptor POINTER_INT_INT_POINTER =
      FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_POINTER);

  /** {@code T* f(U*, V*, W*)}: {@code webkit_web_view_evaluate_javascript_finish}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void f(T*, U*, V*, W*)}: {@code gdk_clipboard_read_text_async}. */
  public final FunctionDescriptor VOID_POINTER_X4 =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void f(T*, U*, V*, W*, X*)}: {@code webkit_web_context_register_uri_scheme}. */
  public final FunctionDescriptor VOID_POINTER_X5 =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code T* f(gint)}: {@code g_variant_new_uint32}. */
  public final FunctionDescriptor POINTER_INT = FunctionDescriptor.of(C_POINTER, C_INT);

  /** {@code T* f(U*, V*)}: {@code g_dbus_node_info_new_for_xml}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER);

  /** {@code T* f(U*, gsize)}: {@code g_variant_new_tuple}, {@code g_variant_get_child_value}. */
  public final FunctionDescriptor POINTER_POINTER_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_LONG);

  /** {@code T* f(U*, V*, gsize)}: {@code g_variant_new_array}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code T* f(gint, U*, V*)}: {@code g_bus_get_sync}. */
  public final FunctionDescriptor POINTER_INT_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_INT, C_POINTER, C_POINTER);

  /** {@code gsize f(T*)}: {@code g_variant_n_children}. */
  public final FunctionDescriptor LONG_POINTER = FunctionDescriptor.of(C_LONG, C_POINTER);

  /** {@code gboolean f(T*, guint)}: {@code g_dbus_connection_unregister_object}. */
  public final FunctionDescriptor INT_POINTER_INT = FunctionDescriptor.of(C_INT, C_POINTER, C_INT);

  /**
   * {@code T* f(const gchar*, GDBusConnectionFlags, U*, V*, W*)}: {@code
   * g_dbus_connection_new_for_address_sync}.
   */
  public final FunctionDescriptor POINTER_POINTER_INT_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code gint f(T*, U*, V*, W*, X*, Y*, Z*)}: {@code g_dbus_connection_register_object} and
   * {@code g_dbus_connection_emit_signal}.
   */
  public final FunctionDescriptor INT_POINTER_X7 =
      FunctionDescriptor.of(
          C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void f(T*, U*, gsize)}: {@code gdk_texture_download}. */
  public final FunctionDescriptor VOID_POINTER_POINTER_LONG =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG);

  /** {@code T* f(U*, V*, gsize, gsize)}: {@code g_variant_new_fixed_array}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_LONG_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_LONG, C_LONG);

  // --- named signatures, too long to describe by shape ---

  /**
   * {@code gulong g_signal_connect_data(gpointer, const gchar*, GCallback, gpointer,
   * GClosureNotify, GConnectFlags)}.
   */
  public final FunctionDescriptor G_SIGNAL_CONNECT_DATA =
      FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT);

  /**
   * {@code WebKitUserScript* webkit_user_script_new(const gchar*, WebKitUserContentInjectedFrames,
   * WebKitUserScriptInjectionTime, const gchar* const*, const gchar* const*)}.
   */
  public final FunctionDescriptor WEBKIT_USER_SCRIPT_NEW =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_INT, C_POINTER, C_POINTER);

  /**
   * {@code void webkit_web_view_evaluate_javascript(WebKitWebView*, const char*, gssize, const
   * char*, const char*, GCancellable*, GAsyncReadyCallback, gpointer)}.
   */
  public final FunctionDescriptor WEBKIT_EVALUATE_JAVASCRIPT =
      FunctionDescriptor.ofVoid(
          C_POINTER, C_POINTER, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code GVariant* g_dbus_connection_call_sync(GDBusConnection*, const gchar* bus_name, const
   * gchar* object_path, const gchar* interface_name, const gchar* method_name, GVariant*
   * parameters, const GVariantType* reply_type, GDBusCallFlags, gint timeout_msec, GCancellable*,
   * GError**)}.
   */
  public final FunctionDescriptor G_DBUS_CONNECTION_CALL_SYNC =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER,
          C_INT, C_INT, C_POINTER, C_POINTER);

  /**
   * {@code guint g_dbus_connection_signal_subscribe(GDBusConnection*, const gchar* sender, const
   * gchar* interface_name, const gchar* member, const gchar* object_path, const gchar* arg0,
   * GDBusSignalFlags, GDBusSignalCallback, gpointer, GDestroyNotify)}.
   */
  public final FunctionDescriptor G_DBUS_CONNECTION_SIGNAL_SUBSCRIBE =
      FunctionDescriptor.of(
          C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER,
          C_POINTER, C_POINTER);

  // --- callbacks (upcalls) ---

  /** {@code gboolean (*GSourceFunc)(gpointer)}. */
  public final FunctionDescriptor G_SOURCE_FUNC = INT_POINTER;

  /** {@code void (*GAsyncReadyCallback)(GObject*, GAsyncResult*, gpointer)}. */
  public final FunctionDescriptor G_ASYNC_READY_CALLBACK = VOID_POINTER_POINTER_POINTER;

  /** {@code void (*)(GtkWidget*, gpointer)}: the {@code destroy} signal of a window. */
  public final FunctionDescriptor WIDGET_CALLBACK = VOID_POINTER_POINTER;

  /**
   * {@code gboolean (*)(GtkWindow*, gpointer)}: the {@code close-request} signal of a window, whose
   * {@code TRUE} cancels the close.
   */
  public final FunctionDescriptor CLOSE_REQUEST_CALLBACK = INT_POINTER_POINTER;

  /** {@code void (*)(GObject*, GParamSpec*, gpointer)}: a {@code notify::} handler. */
  public final FunctionDescriptor NOTIFY_CALLBACK = VOID_POINTER_POINTER_POINTER;

  /** {@code void (*)(GdkSurface*, gint, gint, gpointer)}: {@code layout} of a surface. */
  public final FunctionDescriptor LAYOUT_CALLBACK =
      FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_POINTER);

  /**
   * {@code void (*)(WebKitUserContentManager*, JSCValue*, gpointer)}: bridge messages. WebKitGTK
   * 6.0 passes the value itself, where 4.1 wrapped it in a {@code WebKitJavascriptResult}.
   */
  public final FunctionDescriptor SCRIPT_MESSAGE_CALLBACK = VOID_POINTER_POINTER_POINTER;

  /** {@code void (*WebKitURISchemeRequestCallback)(WebKitURISchemeRequest*, gpointer)}. */
  public final FunctionDescriptor URI_SCHEME_REQUEST_CALLBACK = VOID_POINTER_POINTER;

  /**
   * {@code WebKitWebView* (*)(WebKitWebView*, WebKitNavigationAction*, gpointer)}: {@code create},
   * the request for a new window, which a {@code NULL} answer declines.
   */
  public final FunctionDescriptor CREATE_CALLBACK = POINTER_POINTER_POINTER_POINTER;

  /**
   * {@code void (*)(T*, gint response_id, gpointer)}: the {@code response} signal of a dialog and
   * of a native dialog.
   */
  public final FunctionDescriptor RESPONSE_CALLBACK = VOID_POINTER_INT_POINTER;

  /** {@code void (*)(WebKitWebView*, WebKitLoadEvent, gpointer)}: {@code load-changed}. */
  public final FunctionDescriptor LOAD_CHANGED_CALLBACK =
      FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_POINTER);

  /**
   * {@code gboolean (*)(WebKitWebView*, WebKitContextMenu*, WebKitHitTestResult*, gpointer)}:
   * {@code context-menu}. WebKitGTK 6.0 dropped the {@code GdkEvent*} that 4.1 passed third.
   */
  public final FunctionDescriptor CONTEXT_MENU_CALLBACK =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code gboolean (*)(WebKitWebView*, WebKitLoadEvent, gchar*, GError*, gpointer)}: {@code
   * load-failed}.
   */
  public final FunctionDescriptor LOAD_FAILED_CALLBACK =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code void GDBusSignalCallback(GDBusConnection*, const gchar* sender_name, const gchar*
   * object_path, const gchar* interface_name, const gchar* signal_name, GVariant* parameters,
   * gpointer user_data)}.
   */
  public final FunctionDescriptor G_DBUS_SIGNAL_CALLBACK =
      FunctionDescriptor.ofVoid(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code void GDBusInterfaceMethodCallFunc(GDBusConnection*, const gchar* sender, const gchar*
   * object_path, const gchar* interface_name, const gchar* method_name, GVariant* parameters,
   * GDBusMethodInvocation*, gpointer user_data)}.
   */
  public final FunctionDescriptor G_DBUS_METHOD_CALL =
      FunctionDescriptor.ofVoid(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code GVariant* GDBusInterfaceGetPropertyFunc(GDBusConnection*, const gchar* sender, const
   * gchar* object_path, const gchar* interface_name, const gchar* property_name, GError**, gpointer
   * user_data)}.
   */
  public final FunctionDescriptor G_DBUS_GET_PROPERTY =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);
}
