package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import lombok.experimental.UtilityClass;

/**
 * Every native signature that the GLib bindings bind, in one place.
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

  // --- shapes ---

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

  /** {@code gboolean f(T*, U*, V*)}: {@code g_dbus_connection_close_sync}. */
  public final FunctionDescriptor INT_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER);

  /** {@code gboolean f(T*, GType)}: {@code g_type_check_instance_is_a}. */
  public final FunctionDescriptor INT_POINTER_LONG =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG);

  /** {@code gint f(gint)}: {@code close}. */
  public final FunctionDescriptor INT_INT = FunctionDescriptor.of(C_INT, C_INT);

  /** {@code gssize f(gint, T*, gsize)}: {@code write}. */
  public final FunctionDescriptor LONG_INT_POINTER_LONG =
      FunctionDescriptor.of(C_LONG, C_INT, C_POINTER, C_LONG);

  /** {@code T* f(gint, gboolean)}: {@code g_unix_input_stream_new}. */
  public final FunctionDescriptor POINTER_INT_INT = FunctionDescriptor.of(C_POINTER, C_INT, C_INT);

  /**
   * {@code gboolean g_input_stream_read_all(GInputStream*, void*, gsize, gsize*, GCancellable*,
   * GError**)}.
   */
  public final FunctionDescriptor G_INPUT_STREAM_READ_ALL =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG, C_POINTER, C_POINTER, C_POINTER);

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

  /** {@code T* f(U*, gboolean)}: {@code g_main_loop_new}. */
  public final FunctionDescriptor POINTER_POINTER_INT =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT);

  /** {@code T* f(U*, gssize, V*)}: {@code g_memory_input_stream_new_from_data}. */
  public final FunctionDescriptor POINTER_POINTER_LONG_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /** {@code T* f(GQuark, gint, const gchar*)}: {@code g_error_new_literal}. */
  public final FunctionDescriptor POINTER_INT_INT_POINTER =
      FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_POINTER);

  /** {@code T* f(U*, V*, W*)}: {@code gdk_pixbuf_new_from_stream}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

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
