package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of Glib, GObject, and GIO that the GTK backends need.
 *
 * <p>The handles are {@code static final} on purpose. {@code invokeExact} only compiles to a direct
 * call when the JIT compiler can treat the handle as a constant, and these functions are on the hot
 * path of every window operation.
 */
@UtilityClass
public class Glib {
  private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");
  private final SymbolLookup GOBJECT =
      NativeLibraries.load("libgobject-2.0.so.0", "libgobject-2.0.so");
  private final SymbolLookup GIO = NativeLibraries.load("libgio-2.0.so.0", "libgio-2.0.so");

  /** The return value of a {@code GSourceFunc} that must not be invoked again. */
  public final int SOURCE_REMOVE = 0;

  /** {@code struct _GError { GQuark domain; gint code; gchar *message; }}. */
  private final MemoryLayout ERROR_LAYOUT =
      MemoryLayout.structLayout(
          Signatures.C_INT.withName("domain"),
          Signatures.C_INT.withName("code"),
          Signatures.C_POINTER.withName("message"));

  private final VarHandle ERROR_MESSAGE =
      ERROR_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("message"));

  private final MethodHandle MAIN_LOOP_NEW =
      NativeLibraries.downcall(GLIB, "g_main_loop_new", Signatures.POINTER_POINTER_INT);
  private final MethodHandle MAIN_LOOP_RUN =
      NativeLibraries.downcall(GLIB, "g_main_loop_run", Signatures.VOID_POINTER);
  private final MethodHandle IDLE_ADD =
      NativeLibraries.downcall(GLIB, "g_idle_add", Signatures.INT_POINTER_POINTER);
  private final MethodHandle FREE =
      NativeLibraries.downcall(GLIB, "g_free", Signatures.VOID_POINTER);
  private final MethodHandle ERROR_FREE =
      NativeLibraries.downcall(GLIB, "g_error_free", Signatures.VOID_POINTER);
  private final MethodHandle SIGNAL_CONNECT_DATA =
      NativeLibraries.downcall(GOBJECT, "g_signal_connect_data", Signatures.G_SIGNAL_CONNECT_DATA);
  private final MethodHandle OBJECT_REF =
      NativeLibraries.downcall(GOBJECT, "g_object_ref", Signatures.POINTER_POINTER);
  private final MethodHandle TYPE_CHECK_INSTANCE_IS_A =
      NativeLibraries.downcall(GOBJECT, "g_type_check_instance_is_a", Signatures.INT_POINTER_LONG);
  private final MethodHandle OBJECT_UNREF =
      NativeLibraries.downcall(GOBJECT, "g_object_unref", Signatures.VOID_POINTER);
  private final MethodHandle VALUE_INIT =
      NativeLibraries.downcall(GOBJECT, "g_value_init", Signatures.POINTER_POINTER_LONG);
  private final MethodHandle VALUE_GET_STRING =
      NativeLibraries.downcall(GOBJECT, "g_value_get_string", Signatures.POINTER_POINTER);
  private final MethodHandle VALUE_UNSET =
      NativeLibraries.downcall(GOBJECT, "g_value_unset", Signatures.VOID_POINTER);
  private final MethodHandle VALUE_SET_STRING =
      NativeLibraries.downcall(GOBJECT, "g_value_set_string", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle VALUE_SET_ENUM =
      NativeLibraries.downcall(GOBJECT, "g_value_set_enum", Signatures.VOID_POINTER_INT);
  private final MethodHandle OBJECT_SET_PROPERTY =
      NativeLibraries.downcall(
          GOBJECT, "g_object_set_property", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle OBJECT_NEW_WITH_PROPERTIES =
      NativeLibraries.downcall(
          GOBJECT, "g_object_new_with_properties", Signatures.POINTER_LONG_INT_POINTER_POINTER);
  private final MethodHandle DGETTEXT =
      NativeLibraries.downcall(GLIB, "g_dgettext", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle SLIST_FREE =
      NativeLibraries.downcall(GLIB, "g_slist_free", Signatures.VOID_POINTER);
  private final MethodHandle FILE_NEW_FOR_PATH =
      NativeLibraries.downcall(GIO, "g_file_new_for_path", Signatures.POINTER_POINTER);
  private final MethodHandle FILE_GET_PATH =
      NativeLibraries.downcall(GIO, "g_file_get_path", Signatures.POINTER_POINTER);
  private final MethodHandle LIST_MODEL_GET_N_ITEMS =
      NativeLibraries.downcall(GIO, "g_list_model_get_n_items", Signatures.INT_POINTER);
  private final MethodHandle LIST_MODEL_GET_ITEM =
      NativeLibraries.downcall(GIO, "g_list_model_get_item", Signatures.POINTER_POINTER_INT);
  private final MethodHandle OBJECT_GET_PROPERTY =
      NativeLibraries.downcall(
          GOBJECT, "g_object_get_property", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle MALLOC =
      NativeLibraries.downcall(GLIB, "g_malloc", Signatures.POINTER_LONG);
  private final MethodHandle QUARK_FROM_STRING =
      NativeLibraries.downcall(GLIB, "g_quark_from_string", Signatures.INT_POINTER);
  private final MethodHandle ERROR_NEW_LITERAL =
      NativeLibraries.downcall(GLIB, "g_error_new_literal", Signatures.POINTER_INT_INT_POINTER);
  private final MethodHandle APP_INFO_LAUNCH_DEFAULT_FOR_URI =
      NativeLibraries.downcall(
          GIO, "g_app_info_launch_default_for_uri", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle MEMORY_INPUT_STREAM_NEW_FROM_DATA =
      NativeLibraries.downcall(
          GIO, "g_memory_input_stream_new_from_data", Signatures.POINTER_POINTER_LONG_POINTER);

  /**
   * The address of {@code g_free} itself, for APIs that take a {@code GDestroyNotify}. Native code
   * frees the buffer after it's done with it.
   */
  public final MemorySegment FREE_FUNCTION =
      GLIB.find("g_free").orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: g_free"));

  /**
   * Runs the default main context on the calling thread and doesn't return: what {@code gtk_main()}
   * did in GTK 3, which GTK 4 no longer has.
   */
  @SneakyThrows
  public void runMainLoop() {
    MemorySegment loop = (MemorySegment) MAIN_LOOP_NEW.invokeExact(MemorySegment.NULL, 0);
    MAIN_LOOP_RUN.invokeExact(loop);
  }

  /**
   * Schedules {@code callback} on the default main context. This method is safe to call from any
   * thread.
   */
  @SneakyThrows
  public void idleAdd(MemorySegment callback, MemorySegment userData) {
    int _ = (int) IDLE_ADD.invokeExact(callback, userData);
  }

  /** Calls {@code g_signal_connect_data} without a destroy notify and with the default flags. */
  @SneakyThrows
  public void signalConnect(
      MemorySegment instance, String signal, MemorySegment callback, MemorySegment userData) {
    try (Arena arena = Arena.ofConfined()) {
      long _ =
          (long)
              SIGNAL_CONNECT_DATA.invokeExact(
                  instance, arena.allocateFrom(signal), callback, userData, MemorySegment.NULL, 0);
    }
  }

  /** Calls {@code g_free}. */
  @SneakyThrows
  public void free(MemorySegment pointer) {
    FREE.invokeExact(pointer);
  }

  /** Calls {@code g_object_ref}. */
  @SneakyThrows
  public void ref(MemorySegment object) {
    MemorySegment _ = (MemorySegment) OBJECT_REF.invokeExact(object);
  }

  /** Calls {@code g_type_check_instance_is_a}: whether {@code instance} is of {@code type}. */
  @SneakyThrows
  public boolean typeCheckInstanceIsA(MemorySegment instance, long type) {
    return (int) TYPE_CHECK_INSTANCE_IS_A.invokeExact(instance, type) != 0;
  }

  /** {@code G_TYPE_STRING}: fundamental type 16, shifted as GObject stores fundamentals. */
  private final long TYPE_STRING = 16L << 2;

  /** {@code sizeof(GValue)}: the type, and two 8-byte words of data. */
  private final long VALUE_SIZE = 24;

  /**
   * Reads a string property of a GObject through {@code g_object_get_property}, which takes a
   * {@code GValue} where {@code g_object_get} would need a variadic call.
   *
   * @return The value, or {@code null} when the property is unset.
   */
  @SneakyThrows
  public String stringProperty(MemorySegment object, String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(VALUE_SIZE, 8);
      MemorySegment _ = (MemorySegment) VALUE_INIT.invokeExact(value, TYPE_STRING);
      try {
        OBJECT_GET_PROPERTY.invokeExact(object, arena.allocateFrom(name), value);
        return NativeLibraries.string((MemorySegment) VALUE_GET_STRING.invokeExact(value));
      } finally {
        VALUE_UNSET.invokeExact(value);
      }
    }
  }

  /**
   * Calls {@code g_object_new_with_properties} with no properties: a new object of {@code type}
   * with its defaults, what {@code g_object_new(type, NULL)} makes without a variadic call.
   */
  @SneakyThrows
  public MemorySegment objectNew(long type) {
    return (MemorySegment)
        OBJECT_NEW_WITH_PROPERTIES.invokeExact(type, 0, MemorySegment.NULL, MemorySegment.NULL);
  }

  /** Sets a string property through {@code g_object_set_property}. */
  @SneakyThrows
  public void setStringProperty(MemorySegment object, String name, String text) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(VALUE_SIZE, 8);
      MemorySegment _ = (MemorySegment) VALUE_INIT.invokeExact(value, TYPE_STRING);
      try {
        MemorySegment string = text == null ? MemorySegment.NULL : arena.allocateFrom(text);
        VALUE_SET_STRING.invokeExact(value, string);
        OBJECT_SET_PROPERTY.invokeExact(object, arena.allocateFrom(name), value);
      } finally {
        VALUE_UNSET.invokeExact(value);
      }
    }
  }

  /**
   * Sets an enum property of the enum type {@code enumType} through {@code g_object_set_property}.
   */
  @SneakyThrows
  public void setEnumProperty(MemorySegment object, String name, long enumType, int constant) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(VALUE_SIZE, 8);
      MemorySegment _ = (MemorySegment) VALUE_INIT.invokeExact(value, enumType);
      try {
        VALUE_SET_ENUM.invokeExact(value, constant);
        OBJECT_SET_PROPERTY.invokeExact(object, arena.allocateFrom(name), value);
      } finally {
        VALUE_UNSET.invokeExact(value);
      }
    }
  }

  /**
   * Calls {@code g_dgettext}: {@code text} in the language of the user, from the translations of
   * {@code domain}, such as the labels of the buttons of GTK from {@code gtk30}.
   */
  @SneakyThrows
  public String translate(String domain, String text) {
    try (Arena arena = Arena.ofConfined()) {
      return NativeLibraries.string(
          (MemorySegment)
              DGETTEXT.invokeExact(arena.allocateFrom(domain), arena.allocateFrom(text)));
    }
  }

  /**
   * The strings of a {@code GSList} of {@code gchar*} that the caller owns, as {@code
   * gtk_file_chooser_get_filenames} returns: each string and the list are freed.
   */
  @SneakyThrows
  public List<String> takeStringList(MemorySegment list) {
    List<String> strings = new ArrayList<>();
    MemorySegment node = list;
    while (!node.equals(MemorySegment.NULL)) {
      MemorySegment cell = node.reinterpret(2 * Signatures.C_POINTER.byteSize());
      strings.add(Glib.takeString(cell.get(Signatures.C_POINTER, 0)));
      node = cell.get(Signatures.C_POINTER, Signatures.C_POINTER.byteSize());
    }
    SLIST_FREE.invokeExact(list);
    return strings;
  }

  /** Calls {@code g_file_new_for_path}. The caller owns the {@code GFile}. */
  @SneakyThrows
  public MemorySegment fileForPath(String path) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) FILE_NEW_FOR_PATH.invokeExact(arena.allocateFrom(path));
    }
  }

  /**
   * The items of a {@code GListModel} that the caller doesn't own, each with a reference that the
   * caller gives back with {@link #unref}.
   */
  @SneakyThrows
  public List<MemorySegment> listItems(MemorySegment model) {
    int count = (int) LIST_MODEL_GET_N_ITEMS.invokeExact(model);
    List<MemorySegment> items = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      items.add((MemorySegment) LIST_MODEL_GET_ITEM.invokeExact(model, index));
    }
    return items;
  }

  /**
   * The local paths of the {@code GFile} items of a {@code GListModel}, as {@code
   * gtk_file_chooser_get_files} of GTK 4 returns, which the caller owns: the model is released.
   * Items without a local path are left out.
   */
  @SneakyThrows
  public List<String> takeFilePaths(MemorySegment model) {
    List<String> paths = new ArrayList<>();
    int count = (int) LIST_MODEL_GET_N_ITEMS.invokeExact(model);
    for (int index = 0; index < count; index++) {
      MemorySegment file = (MemorySegment) LIST_MODEL_GET_ITEM.invokeExact(model, index);
      String path = Glib.takeString((MemorySegment) FILE_GET_PATH.invokeExact(file));
      if (path != null) {
        paths.add(path);
      }
      Glib.unref(file);
    }
    Glib.unref(model);
    return paths;
  }

  /**
   * Opens {@code uri} in the application that the desktop chose for its scheme, through {@code
   * g_app_info_launch_default_for_uri}, which goes through the OpenURI portal inside a sandbox.
   *
   * @throws IllegalStateException If nothing opens it.
   */
  @SneakyThrows
  public void launchDefaultForUri(String uri) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      int launched =
          (int)
              APP_INFO_LAUNCH_DEFAULT_FOR_URI.invokeExact(
                  arena.allocateFrom(uri), MemorySegment.NULL, error);
      if (launched == 0) {
        throw new IllegalStateException(
            "Could not open "
                + uri
                + ": "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
    }
  }

  /** Calls {@code g_object_unref}. */
  @SneakyThrows
  public void unref(MemorySegment object) {
    OBJECT_UNREF.invokeExact(object);
  }

  /**
   * Copies {@code data} into a buffer that Glib owns, to pass to native code that frees it later.
   * The JVM heap isn't an option here: a moving garbage collector can relocate the array, and the
   * stream outlives the call that creates it.
   */
  @SneakyThrows
  public MemorySegment copyToNative(byte[] data) {
    MemorySegment buffer =
        ((MemorySegment) MALLOC.invokeExact((long) data.length)).reinterpret(data.length);
    MemorySegment.copy(MemorySegment.ofArray(data), 0L, buffer, 0L, data.length);
    return buffer;
  }

  /**
   * Wraps {@code data} in a {@code GInputStream}. The stream takes ownership and frees the buffer
   * when it's disposed.
   */
  @SneakyThrows
  public MemorySegment memoryInputStream(MemorySegment data, long length) {
    return (MemorySegment)
        MEMORY_INPUT_STREAM_NEW_FROM_DATA.invokeExact(data, length, FREE_FUNCTION);
  }

  /** Creates a {@code GError} that the caller owns. */
  @SneakyThrows
  public MemorySegment error(String domain, int code, String message) {
    try (Arena arena = Arena.ofConfined()) {
      int quark = (int) QUARK_FROM_STRING.invokeExact(arena.allocateFrom(domain));
      return (MemorySegment)
          ERROR_NEW_LITERAL.invokeExact(quark, code, arena.allocateFrom(message));
    }
  }

  /** Reads a {@code gchar*} that the caller owns, then frees it. */
  public String takeString(MemorySegment pointer) {
    if (pointer.equals(MemorySegment.NULL)) {
      return null;
    }

    String value = NativeLibraries.string(pointer);
    Glib.free(pointer);
    return value;
  }

  /** Reads {@code error->message} from a borrowed {@code GError}. */
  public String errorMessage(MemorySegment error) {
    if (error.equals(MemorySegment.NULL)) {
      return null;
    }

    MemorySegment struct = error.reinterpret(ERROR_LAYOUT.byteSize());
    return NativeLibraries.string((MemorySegment) ERROR_MESSAGE.get(struct, 0L));
  }

  /** Reads {@code error->message} and frees a {@code GError} that the caller owns. */
  @SneakyThrows
  public String takeErrorMessage(MemorySegment error) {
    if (error.equals(MemorySegment.NULL)) {
      return null;
    }

    String message = Glib.errorMessage(error);
    ERROR_FREE.invokeExact(error);
    return message;
  }
}
