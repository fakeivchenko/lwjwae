package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to GDBus and to the {@code GVariant} values that its messages carry.
 *
 * <p>Values are built with the typed constructors, one per value, never with {@code g_variant_new}
 * and a format string: that one is variadic, and a variadic downcall would need a descriptor for
 * every combination of arguments. A constructor returns a floating reference, and the container or
 * the call that takes it sinks it, so a value passed on needs no unref.
 *
 * <p>Besides calls and signals, the binding exports objects: {@link #registerObject} puts an
 * interface on a path of a connection, with a vtable of two upcall stubs, one for method calls and
 * one for properties, which is how the tray serves the StatusNotifierItem and dbusmenu interfaces.
 *
 * <p>A {@code GVariantType*} is a pointer to the type string itself, which is what the {@code
 * G_VARIANT_TYPE} macro casts, so a type is passed as a C string.
 */
@UtilityClass
public class Dbus {
  private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");
  private final SymbolLookup GIO = NativeLibraries.load("libgio-2.0.so.0", "libgio-2.0.so");

  private final int BUS_TYPE_SESSION = 2;
  private final int CALL_FLAGS_NONE = 0;
  private final int SIGNAL_FLAGS_NONE = 0;

  /** {@code G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT | ..._MESSAGE_BUS_CONNECTION}. */
  private final int BUS_CLIENT_FLAGS = 1 | 8;

  /** {@code GDBusInterfaceVTable}: three function pointers and eight of padding. */
  private final long VTABLE_SLOTS = 11;

  private final MethodHandle BUS_GET_SYNC =
      NativeLibraries.downcall(GIO, "g_bus_get_sync", Signatures.POINTER_INT_POINTER_POINTER);
  private final MethodHandle CALL_SYNC =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_call_sync", Signatures.G_DBUS_CONNECTION_CALL_SYNC);
  private final MethodHandle SIGNAL_SUBSCRIBE =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_signal_subscribe", Signatures.G_DBUS_CONNECTION_SIGNAL_SUBSCRIBE);
  private final MethodHandle SIGNAL_UNSUBSCRIBE =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_signal_unsubscribe", Signatures.VOID_POINTER_INT);
  private final MethodHandle ADDRESS_GET_FOR_BUS_SYNC =
      NativeLibraries.downcall(
          GIO, "g_dbus_address_get_for_bus_sync", Signatures.POINTER_INT_POINTER_POINTER);
  private final MethodHandle CONNECTION_NEW_FOR_ADDRESS_SYNC =
      NativeLibraries.downcall(
          GIO,
          "g_dbus_connection_new_for_address_sync",
          Signatures.POINTER_POINTER_INT_POINTER_POINTER_POINTER);
  private final MethodHandle CONNECTION_CLOSE_SYNC =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_close_sync", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle CONNECTION_GET_UNIQUE_NAME =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_get_unique_name", Signatures.POINTER_POINTER);
  private final MethodHandle NODE_INFO_NEW_FOR_XML =
      NativeLibraries.downcall(
          GIO, "g_dbus_node_info_new_for_xml", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle NODE_INFO_LOOKUP_INTERFACE =
      NativeLibraries.downcall(
          GIO, "g_dbus_node_info_lookup_interface", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle REGISTER_OBJECT =
      NativeLibraries.downcall(GIO, "g_dbus_connection_register_object", Signatures.INT_POINTER_X7);
  private final MethodHandle UNREGISTER_OBJECT =
      NativeLibraries.downcall(
          GIO, "g_dbus_connection_unregister_object", Signatures.INT_POINTER_INT);
  private final MethodHandle EMIT_SIGNAL =
      NativeLibraries.downcall(GIO, "g_dbus_connection_emit_signal", Signatures.INT_POINTER_X7);
  private final MethodHandle INVOCATION_RETURN_VALUE =
      NativeLibraries.downcall(
          GIO, "g_dbus_method_invocation_return_value", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle VARIANT_NEW_BOOLEAN =
      NativeLibraries.downcall(GLIB, "g_variant_new_boolean", Signatures.POINTER_INT);
  private final MethodHandle VARIANT_NEW_OBJECT_PATH =
      NativeLibraries.downcall(GLIB, "g_variant_new_object_path", Signatures.POINTER_POINTER);
  private final MethodHandle VARIANT_NEW_FIXED_ARRAY =
      NativeLibraries.downcall(
          GLIB, "g_variant_new_fixed_array", Signatures.POINTER_POINTER_POINTER_LONG_LONG);
  private final MethodHandle VARIANT_GET_INT32 =
      NativeLibraries.downcall(GLIB, "g_variant_get_int32", Signatures.INT_POINTER);
  private final MethodHandle VARIANT_N_CHILDREN =
      NativeLibraries.downcall(GLIB, "g_variant_n_children", Signatures.LONG_POINTER);
  private final MethodHandle VARIANT_NEW_STRING =
      NativeLibraries.downcall(GLIB, "g_variant_new_string", Signatures.POINTER_POINTER);
  private final MethodHandle VARIANT_NEW_UINT32 =
      NativeLibraries.downcall(GLIB, "g_variant_new_uint32", Signatures.POINTER_INT);
  private final MethodHandle VARIANT_NEW_INT32 =
      NativeLibraries.downcall(GLIB, "g_variant_new_int32", Signatures.POINTER_INT);
  private final MethodHandle VARIANT_NEW_VARIANT =
      NativeLibraries.downcall(GLIB, "g_variant_new_variant", Signatures.POINTER_POINTER);
  private final MethodHandle VARIANT_NEW_DICT_ENTRY =
      NativeLibraries.downcall(
          GLIB, "g_variant_new_dict_entry", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle VARIANT_NEW_TUPLE =
      NativeLibraries.downcall(GLIB, "g_variant_new_tuple", Signatures.POINTER_POINTER_LONG);
  private final MethodHandle VARIANT_NEW_ARRAY =
      NativeLibraries.downcall(
          GLIB, "g_variant_new_array", Signatures.POINTER_POINTER_POINTER_LONG);
  private final MethodHandle VARIANT_GET_CHILD_VALUE =
      NativeLibraries.downcall(GLIB, "g_variant_get_child_value", Signatures.POINTER_POINTER_LONG);
  private final MethodHandle VARIANT_GET_UINT32 =
      NativeLibraries.downcall(GLIB, "g_variant_get_uint32", Signatures.INT_POINTER);
  private final MethodHandle VARIANT_GET_STRING =
      NativeLibraries.downcall(GLIB, "g_variant_get_string", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle VARIANT_UNREF =
      NativeLibraries.downcall(GLIB, "g_variant_unref", Signatures.VOID_POINTER);

  // --- the bus ---

  /**
   * {@code g_bus_get_sync}: the session bus connection that the process shares, with a reference
   * that the caller gives back with {@link Glib#unref}.
   *
   * @throws IllegalStateException If there is no session bus.
   */
  @SneakyThrows
  public MemorySegment sessionBus() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment connection =
          (MemorySegment) BUS_GET_SYNC.invokeExact(BUS_TYPE_SESSION, MemorySegment.NULL, error);
      if (connection.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(
            "No session bus: " + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      return connection;
    }
  }

  /**
   * {@code g_dbus_connection_call_sync}: calls a method and waits for the reply. Takes the floating
   * {@code parameters}.
   *
   * @param replyType The type string of the reply, such as {@code (u)}, or {@code null} for any.
   * @return The reply, which the caller unrefs.
   * @throws IllegalStateException If the call fails, with the message of the {@code GError}.
   */
  @SneakyThrows
  public MemorySegment call(
      MemorySegment connection,
      String busName,
      String objectPath,
      String interfaceName,
      String method,
      MemorySegment parameters,
      String replyType,
      int timeoutMillis) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment replyTypeString =
          replyType == null ? MemorySegment.NULL : arena.allocateFrom(replyType);
      MemorySegment reply =
          (MemorySegment)
              CALL_SYNC.invokeExact(
                  connection,
                  arena.allocateFrom(busName),
                  arena.allocateFrom(objectPath),
                  arena.allocateFrom(interfaceName),
                  arena.allocateFrom(method),
                  parameters,
                  replyTypeString,
                  CALL_FLAGS_NONE,
                  timeoutMillis,
                  MemorySegment.NULL,
                  error);
      if (reply.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      return reply;
    }
  }

  /**
   * {@code g_dbus_connection_signal_subscribe}: every signal of {@code interfaceName} from {@code
   * sender} on {@code objectPath}. The callback runs in the main context that is the default of the
   * calling thread, so a subscription made on the GTK thread delivers there.
   *
   * @return The subscription ID, for {@link #unsubscribe}.
   */
  @SneakyThrows
  public int subscribe(
      MemorySegment connection,
      String sender,
      String interfaceName,
      String objectPath,
      MemorySegment callback,
      MemorySegment userData) {
    try (Arena arena = Arena.ofConfined()) {
      return (int)
          SIGNAL_SUBSCRIBE.invokeExact(
              connection,
              arena.allocateFrom(sender),
              arena.allocateFrom(interfaceName),
              MemorySegment.NULL,
              arena.allocateFrom(objectPath),
              MemorySegment.NULL,
              SIGNAL_FLAGS_NONE,
              callback,
              userData,
              MemorySegment.NULL);
    }
  }

  /** {@code g_dbus_connection_signal_unsubscribe}. */
  @SneakyThrows
  public void unsubscribe(MemorySegment connection, int subscription) {
    SIGNAL_UNSUBSCRIBE.invokeExact(connection, subscription);
  }

  /**
   * A connection of its own to the session bus, with a unique name of its own, which the caller
   * closes with {@link #close}. Closing it is how every object on it leaves the bus at once, and
   * how a peer that watches the name learns that they are gone.
   *
   * @throws IllegalStateException If there is no session bus.
   */
  @SneakyThrows
  public MemorySegment privateSessionBus() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment address =
          (MemorySegment)
              ADDRESS_GET_FOR_BUS_SYNC.invokeExact(BUS_TYPE_SESSION, MemorySegment.NULL, error);
      if (address.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(
            "No session bus: " + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      try {
        MemorySegment connection =
            (MemorySegment)
                CONNECTION_NEW_FOR_ADDRESS_SYNC.invokeExact(
                    address, BUS_CLIENT_FLAGS, MemorySegment.NULL, MemorySegment.NULL, error);
        if (connection.equals(MemorySegment.NULL)) {
          throw new IllegalStateException(
              "No session bus: " + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
        }
        return connection;
      } finally {
        Glib.free(address);
      }
    }
  }

  /** Closes a connection from {@link #privateSessionBus} and drops the reference to it. */
  @SneakyThrows
  public void close(MemorySegment connection) {
    int _ =
        (int) CONNECTION_CLOSE_SYNC.invokeExact(connection, MemorySegment.NULL, MemorySegment.NULL);
    Glib.unref(connection);
  }

  /** {@code g_dbus_connection_get_unique_name}, such as {@code :1.42}. */
  @SneakyThrows
  public String uniqueName(MemorySegment connection) {
    return NativeLibraries.string(
        (MemorySegment) CONNECTION_GET_UNIQUE_NAME.invokeExact(connection));
  }

  /**
   * Parses introspection XML and returns the {@code GDBusInterfaceInfo} of {@code interfaceName} in
   * it. The parsed node is kept for the life of the process, as the interface info points into it.
   */
  @SneakyThrows
  public MemorySegment interfaceInfo(String xml, String interfaceName) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment node =
          (MemorySegment) NODE_INFO_NEW_FOR_XML.invokeExact(arena.allocateFrom(xml), error);
      if (node.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(
            "Bad introspection XML: " + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      return (MemorySegment)
          NODE_INFO_LOOKUP_INTERFACE.invokeExact(node, arena.allocateFrom(interfaceName));
    }
  }

  /**
   * A {@code GDBusInterfaceVTable} with {@code methodCall} and {@code getProperty}, and no setter,
   * in memory that lives as long as the process.
   */
  public MemorySegment vtable(MemorySegment methodCall, MemorySegment getProperty) {
    MemorySegment vtable = NativeLibraries.ARENA.allocate(Signatures.C_POINTER, VTABLE_SLOTS);
    vtable.setAtIndex(Signatures.C_POINTER, 0, methodCall);
    vtable.setAtIndex(Signatures.C_POINTER, 1, getProperty);
    return vtable;
  }

  /**
   * {@code g_dbus_connection_register_object}: serves {@code interfaceInfo} on {@code objectPath}.
   * The callbacks run in the main context that is the default of the calling thread.
   *
   * @return The registration ID, for {@link #unregisterObject}.
   * @throws IllegalStateException If the path already has that interface.
   */
  @SneakyThrows
  public int registerObject(
      MemorySegment connection,
      String objectPath,
      MemorySegment interfaceInfo,
      MemorySegment vtable,
      MemorySegment userData) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      int id =
          (int)
              REGISTER_OBJECT.invokeExact(
                  connection,
                  arena.allocateFrom(objectPath),
                  interfaceInfo,
                  vtable,
                  userData,
                  MemorySegment.NULL,
                  error);
      if (id == 0) {
        throw new IllegalStateException(
            "Can't export "
                + objectPath
                + ": "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      return id;
    }
  }

  /** {@code g_dbus_connection_unregister_object}. */
  @SneakyThrows
  public void unregisterObject(MemorySegment connection, int registration) {
    int _ = (int) UNREGISTER_OBJECT.invokeExact(connection, registration);
  }

  /** Emits a signal from {@code objectPath} to every listener. Takes the floating parameters. */
  @SneakyThrows
  public void emitSignal(
      MemorySegment connection,
      String objectPath,
      String interfaceName,
      String signal,
      MemorySegment parameters) {
    try (Arena arena = Arena.ofConfined()) {
      int _ =
          (int)
              EMIT_SIGNAL.invokeExact(
                  connection,
                  MemorySegment.NULL,
                  arena.allocateFrom(objectPath),
                  arena.allocateFrom(interfaceName),
                  arena.allocateFrom(signal),
                  parameters,
                  MemorySegment.NULL);
    }
  }

  /** Answers a method call with the floating tuple {@code value}; the invocation is done. */
  @SneakyThrows
  public void returnValue(MemorySegment invocation, MemorySegment value) {
    INVOCATION_RETURN_VALUE.invokeExact(invocation, value);
  }

  // --- building values ---

  /** A floating {@code s}. */
  @SneakyThrows
  public MemorySegment string(String value) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) VARIANT_NEW_STRING.invokeExact(arena.allocateFrom(value));
    }
  }

  /** A floating {@code u}. */
  @SneakyThrows
  public MemorySegment uint32(int value) {
    return (MemorySegment) VARIANT_NEW_UINT32.invokeExact(value);
  }

  /** A floating {@code b}. */
  @SneakyThrows
  public MemorySegment bool(boolean value) {
    return (MemorySegment) VARIANT_NEW_BOOLEAN.invokeExact(value ? 1 : 0);
  }

  /** A floating {@code ay}, with a copy of {@code value}. */
  @SneakyThrows
  public MemorySegment bytes(byte[] value) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_BYTE, value);
      return (MemorySegment)
          VARIANT_NEW_FIXED_ARRAY.invokeExact(
              arena.allocateFrom("y"), data, (long) value.length, 1L);
    }
  }

  /** A floating {@code o}. */
  @SneakyThrows
  public MemorySegment objectPath(String value) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) VARIANT_NEW_OBJECT_PATH.invokeExact(arena.allocateFrom(value));
    }
  }

  /** A floating {@code v} that boxes {@code value}. */
  @SneakyThrows
  public MemorySegment variant(MemorySegment value) {
    return (MemorySegment) VARIANT_NEW_VARIANT.invokeExact(value);
  }

  /** A floating {@code i}. */
  @SneakyThrows
  public MemorySegment int32(int value) {
    return (MemorySegment) VARIANT_NEW_INT32.invokeExact(value);
  }

  /**
   * A floating {@code {sv}} entry of a dictionary: {@code key} and {@code value} boxed in a {@code
   * v}.
   */
  @SneakyThrows
  public MemorySegment dictEntry(String key, MemorySegment value) {
    MemorySegment boxed = (MemorySegment) VARIANT_NEW_VARIANT.invokeExact(value);
    return (MemorySegment) VARIANT_NEW_DICT_ENTRY.invokeExact(Dbus.string(key), boxed);
  }

  /** A floating tuple of {@code children}, in order. */
  @SneakyThrows
  public MemorySegment tuple(List<MemorySegment> children) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment)
          VARIANT_NEW_TUPLE.invokeExact(Dbus.pointers(arena, children), (long) children.size());
    }
  }

  /**
   * A floating array of {@code children}, each of {@code childType}. The type makes an empty array
   * possible.
   */
  @SneakyThrows
  public MemorySegment array(String childType, List<MemorySegment> children) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment)
          VARIANT_NEW_ARRAY.invokeExact(
              arena.allocateFrom(childType),
              Dbus.pointers(arena, children),
              (long) children.size());
    }
  }

  // --- reading values ---

  /** {@code g_variant_get_child_value}: a child with a reference that the caller unrefs. */
  @SneakyThrows
  public MemorySegment child(MemorySegment container, int index) {
    return (MemorySegment) VARIANT_GET_CHILD_VALUE.invokeExact(container, (long) index);
  }

  /** The {@code u} at {@code index} of a tuple. */
  @SneakyThrows
  public int uint32At(MemorySegment tuple, int index) {
    MemorySegment child = Dbus.child(tuple, index);
    try {
      return (int) VARIANT_GET_UINT32.invokeExact(child);
    } finally {
      Dbus.unref(child);
    }
  }

  /** The {@code i} at {@code index} of a tuple or an array. */
  @SneakyThrows
  public int int32At(MemorySegment container, int index) {
    MemorySegment child = Dbus.child(container, index);
    try {
      return (int) VARIANT_GET_INT32.invokeExact(child);
    } finally {
      Dbus.unref(child);
    }
  }

  /** {@code g_variant_n_children}: the size of a tuple, an array, or a dictionary. */
  @SneakyThrows
  public int childCount(MemorySegment container) {
    return (int) (long) VARIANT_N_CHILDREN.invokeExact(container);
  }

  /** The {@code s} at {@code index} of a tuple. */
  @SneakyThrows
  public String stringAt(MemorySegment tuple, int index) {
    MemorySegment child = Dbus.child(tuple, index);
    try {
      return NativeLibraries.string(
          (MemorySegment) VARIANT_GET_STRING.invokeExact(child, MemorySegment.NULL));
    } finally {
      Dbus.unref(child);
    }
  }

  /** {@code g_variant_unref}. */
  @SneakyThrows
  public void unref(MemorySegment value) {
    VARIANT_UNREF.invokeExact(value);
  }

  private MemorySegment pointers(Arena arena, List<MemorySegment> values) {
    if (values.isEmpty()) {
      return MemorySegment.NULL;
    }
    MemorySegment array = arena.allocate(Signatures.C_POINTER, values.size());
    for (int index = 0; index < values.size(); index++) {
      array.setAtIndex(Signatures.C_POINTER, index, values.get(index));
    }
    return array;
  }
}
