package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to libsecret, the client of the Secret Service of the desktop: GNOME Keyring, KWallet,
 * or KeePassXC.
 *
 * <p>Only the forms of the calls that take the attributes as a {@code GHashTable} are bound,
 * because the others are variadic. The calls are synchronous: libsecret runs a main context of its
 * own while it waits for the service, so they work from any thread, and they block while the
 * service asks the user to unlock the store. Without libsecret on the machine, {@link
 * #isAvailable()} says so and every other call fails.
 */
@UtilityClass
public class Libsecret {
  private final SymbolLookup SECRET =
      NativeLibraries.loadIfPresent("libsecret-1.so.0", "libsecret-1.so");
  private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");

  /** {@code SECRET_SCHEMA_ATTRIBUTE_STRING}. */
  private final int ATTRIBUTE_STRING = 0;

  /**
   * The size of a {@code SecretSchema}: the name, the flags, 32 attributes, and reserved fields.
   */
  private final long SCHEMA_SIZE = 8 + 8 + 32 * 16 + 8 + 7 * 8;

  private final MethodHandle PASSWORD_STOREV_SYNC =
      NativeLibraries.downcallIfPresent(
          SECRET, "secret_password_storev_sync", Signatures.INT_POINTER_X7);
  private final MethodHandle PASSWORD_LOOKUPV_SYNC =
      NativeLibraries.downcallIfPresent(
          SECRET, "secret_password_lookupv_sync", Signatures.POINTER_POINTER_X4);
  private final MethodHandle PASSWORD_CLEARV_SYNC =
      NativeLibraries.downcallIfPresent(
          SECRET, "secret_password_clearv_sync", Signatures.INT_POINTER_X4);
  private final MethodHandle PASSWORD_FREE =
      NativeLibraries.downcallIfPresent(SECRET, "secret_password_free", Signatures.VOID_POINTER);
  private final MethodHandle HASH_TABLE_NEW =
      NativeLibraries.downcall(GLIB, "g_hash_table_new", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle HASH_TABLE_INSERT =
      NativeLibraries.downcall(GLIB, "g_hash_table_insert", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle HASH_TABLE_UNREF =
      NativeLibraries.downcall(GLIB, "g_hash_table_unref", Signatures.VOID_POINTER);
  private final MemorySegment STR_HASH = GLIB.find("g_str_hash").orElseThrow();
  private final MemorySegment STR_EQUAL = GLIB.find("g_str_equal").orElseThrow();

  /**
   * The schema of the secrets of lwjwae: the name of the application and the key, both strings.
   * libsecret keeps a pointer to it, so it lives as long as the process.
   */
  private final MemorySegment SCHEMA = Libsecret.schema();

  /** Whether libsecret is on the machine. */
  public boolean isAvailable() {
    return SECRET != null;
  }

  /**
   * The password of {@code application} under {@code key}, or {@code null} if none.
   *
   * @throws IllegalStateException If the service failed, with its message.
   */
  @SneakyThrows
  public String lookup(String application, String key) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment attributes = Libsecret.attributes(arena, application, key);
      MemorySegment error = Libsecret.error(arena);
      try {
        MemorySegment password =
            (MemorySegment)
                PASSWORD_LOOKUPV_SYNC.invokeExact(SCHEMA, attributes, MemorySegment.NULL, error);
        Libsecret.check(error);
        if (password.equals(MemorySegment.NULL)) {
          return null;
        }
        String text = NativeLibraries.string(password);
        PASSWORD_FREE.invokeExact(password);
        return text;
      } finally {
        HASH_TABLE_UNREF.invokeExact(attributes);
      }
    }
  }

  /**
   * Stores {@code password} of {@code application} under {@code key} in the default collection, in
   * place of the one there, with {@code label} as what a manager of passwords shows.
   *
   * @throws IllegalStateException If the service failed, with its message.
   */
  @SneakyThrows
  public void store(String application, String key, String label, String password) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment attributes = Libsecret.attributes(arena, application, key);
      MemorySegment error = Libsecret.error(arena);
      try {
        int _ =
            (int)
                PASSWORD_STOREV_SYNC.invokeExact(
                    SCHEMA,
                    attributes,
                    MemorySegment.NULL,
                    arena.allocateFrom(label),
                    arena.allocateFrom(password),
                    MemorySegment.NULL,
                    error);
        Libsecret.check(error);
      } finally {
        HASH_TABLE_UNREF.invokeExact(attributes);
      }
    }
  }

  /**
   * Deletes the password of {@code application} under {@code key}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalStateException If the service failed, with its message.
   */
  @SneakyThrows
  public boolean clear(String application, String key) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment attributes = Libsecret.attributes(arena, application, key);
      MemorySegment error = Libsecret.error(arena);
      try {
        int cleared =
            (int) PASSWORD_CLEARV_SYNC.invokeExact(SCHEMA, attributes, MemorySegment.NULL, error);
        Libsecret.check(error);
        return cleared != 0;
      } finally {
        HASH_TABLE_UNREF.invokeExact(attributes);
      }
    }
  }

  /** A {@code GHashTable} of the two attributes, whose strings live in {@code arena}. */
  @SneakyThrows
  private MemorySegment attributes(Arena arena, String application, String key) {
    MemorySegment table = (MemorySegment) HASH_TABLE_NEW.invokeExact(STR_HASH, STR_EQUAL);
    int _ =
        (int)
            HASH_TABLE_INSERT.invokeExact(
                table, arena.allocateFrom("application"), arena.allocateFrom(application));
    int _ =
        (int)
            HASH_TABLE_INSERT.invokeExact(
                table, arena.allocateFrom("key"), arena.allocateFrom(key));
    return table;
  }

  private MemorySegment error(Arena arena) {
    MemorySegment error = arena.allocate(Signatures.C_POINTER);
    error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
    return error;
  }

  private void check(MemorySegment error) {
    String message = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
    if (message != null) {
      throw new IllegalStateException(message);
    }
  }

  /**
   * A {@code SecretSchema} named {@code dev.ivchenko.lwjwae.Secret}: the name at 0, the flags at 8,
   * then {@code SecretSchemaAttribute}s of 16 bytes each, a name and a type, ended by one without a
   * name; the rest stays zero.
   */
  private MemorySegment schema() {
    MemorySegment schema = NativeLibraries.ARENA.allocate(SCHEMA_SIZE, 8);
    schema.fill((byte) 0);
    schema.set(
        Signatures.C_POINTER, 0, NativeLibraries.ARENA.allocateFrom("dev.ivchenko.lwjwae.Secret"));
    schema.set(Signatures.C_POINTER, 16, NativeLibraries.ARENA.allocateFrom("application"));
    schema.set(Signatures.C_INT, 24, ATTRIBUTE_STRING);
    schema.set(Signatures.C_POINTER, 32, NativeLibraries.ARENA.allocateFrom("key"));
    schema.set(Signatures.C_INT, 40, ATTRIBUTE_STRING);
    return schema;
  }
}
