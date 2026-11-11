package dev.ivchenko.lwjwae.secret.internal;

import dev.ivchenko.lwjwae.foreign.Layouts;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the keychain services of the Security framework, for generic passwords, through the C
 * API of Core Foundation.
 *
 * <p>The queries are {@code CFDictionary}s that this class makes and releases itself, with the
 * callbacks of Core Foundation for their keys and values, so no Objective-C runtime and no
 * autorelease pool is involved, and a call works from any thread. Every object that a {@code
 * Create} or {@code Copy} call returns is released before the call returns.
 */
@UtilityClass
class Keychain {
  private final SymbolLookup SECURITY =
      NativeLibraries.load("/System/Library/Frameworks/Security.framework/Security");
  private final SymbolLookup CORE_FOUNDATION =
      NativeLibraries.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation");

  /** {@code errSecSuccess}. */
  private final int SUCCESS = 0;

  /** {@code errSecItemNotFound}. */
  private final int ITEM_NOT_FOUND = -25300;

  /** {@code kCFStringEncodingUTF8}. */
  private final int UTF8 = 0x08000100;

  // --- the forms of the calls ---
  private final FunctionDescriptor INT_POINTER_POINTER =
      FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER, Layouts.C_POINTER);
  private final FunctionDescriptor INT_POINTER =
      FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER);
  private final FunctionDescriptor DICTIONARY_CREATE_MUTABLE =
      FunctionDescriptor.of(
          Layouts.C_POINTER,
          Layouts.C_POINTER,
          ValueLayout.JAVA_LONG,
          Layouts.C_POINTER,
          Layouts.C_POINTER);
  private final FunctionDescriptor VOID_POINTER_POINTER_POINTER =
      FunctionDescriptor.ofVoid(Layouts.C_POINTER, Layouts.C_POINTER, Layouts.C_POINTER);
  private final FunctionDescriptor STRING_CREATE_WITH_BYTES =
      FunctionDescriptor.of(
          Layouts.C_POINTER,
          Layouts.C_POINTER,
          Layouts.C_POINTER,
          ValueLayout.JAVA_LONG,
          Layouts.C_INT,
          ValueLayout.JAVA_BOOLEAN);
  private final FunctionDescriptor DATA_CREATE =
      FunctionDescriptor.of(
          Layouts.C_POINTER, Layouts.C_POINTER, Layouts.C_POINTER, ValueLayout.JAVA_LONG);
  private final FunctionDescriptor LONG_POINTER =
      FunctionDescriptor.of(ValueLayout.JAVA_LONG, Layouts.C_POINTER);
  private final FunctionDescriptor POINTER_POINTER =
      FunctionDescriptor.of(Layouts.C_POINTER, Layouts.C_POINTER);
  private final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(Layouts.C_POINTER);

  private final MethodHandle ITEM_ADD =
      NativeLibraries.downcall(SECURITY, "SecItemAdd", INT_POINTER_POINTER);
  private final MethodHandle ITEM_COPY_MATCHING =
      NativeLibraries.downcall(SECURITY, "SecItemCopyMatching", INT_POINTER_POINTER);
  private final MethodHandle ITEM_UPDATE =
      NativeLibraries.downcall(SECURITY, "SecItemUpdate", INT_POINTER_POINTER);
  private final MethodHandle ITEM_DELETE =
      NativeLibraries.downcall(SECURITY, "SecItemDelete", INT_POINTER);
  private final MethodHandle DICTIONARY_CREATE =
      NativeLibraries.downcall(
          CORE_FOUNDATION, "CFDictionaryCreateMutable", DICTIONARY_CREATE_MUTABLE);
  private final MethodHandle DICTIONARY_SET_VALUE =
      NativeLibraries.downcall(
          CORE_FOUNDATION, "CFDictionarySetValue", VOID_POINTER_POINTER_POINTER);
  private final MethodHandle STRING_CREATE =
      NativeLibraries.downcall(
          CORE_FOUNDATION, "CFStringCreateWithBytes", STRING_CREATE_WITH_BYTES);
  private final MethodHandle DATA_CREATE_HANDLE =
      NativeLibraries.downcall(CORE_FOUNDATION, "CFDataCreate", DATA_CREATE);
  private final MethodHandle DATA_GET_LENGTH =
      NativeLibraries.downcall(CORE_FOUNDATION, "CFDataGetLength", LONG_POINTER);
  private final MethodHandle DATA_GET_BYTE_PTR =
      NativeLibraries.downcall(CORE_FOUNDATION, "CFDataGetBytePtr", POINTER_POINTER);
  private final MethodHandle RELEASE =
      NativeLibraries.downcall(CORE_FOUNDATION, "CFRelease", VOID_POINTER);

  // --- the callbacks of the dictionaries, and the keys and the values of a query ---
  private final MemorySegment KEY_CALLBACKS =
      CORE_FOUNDATION.find("kCFTypeDictionaryKeyCallBacks").orElseThrow();
  private final MemorySegment VALUE_CALLBACKS =
      CORE_FOUNDATION.find("kCFTypeDictionaryValueCallBacks").orElseThrow();
  private final MemorySegment TRUE = Keychain.constant(CORE_FOUNDATION, "kCFBooleanTrue");
  private final MemorySegment CLASS = Keychain.constant(SECURITY, "kSecClass");
  private final MemorySegment CLASS_GENERIC_PASSWORD =
      Keychain.constant(SECURITY, "kSecClassGenericPassword");
  private final MemorySegment ATTRIBUTE_SERVICE = Keychain.constant(SECURITY, "kSecAttrService");
  private final MemorySegment ATTRIBUTE_ACCOUNT = Keychain.constant(SECURITY, "kSecAttrAccount");
  private final MemorySegment ATTRIBUTE_LABEL = Keychain.constant(SECURITY, "kSecAttrLabel");
  private final MemorySegment VALUE_DATA = Keychain.constant(SECURITY, "kSecValueData");
  private final MemorySegment RETURN_DATA = Keychain.constant(SECURITY, "kSecReturnData");
  private final MemorySegment MATCH_LIMIT = Keychain.constant(SECURITY, "kSecMatchLimit");
  private final MemorySegment MATCH_LIMIT_ONE = Keychain.constant(SECURITY, "kSecMatchLimitOne");

  /**
   * The data of the generic password of {@code service} and {@code account}, or {@code null} if
   * none.
   *
   * @throws IllegalStateException If the keychain answered with another error.
   */
  @SneakyThrows
  byte[] findPassword(String service, String account) {
    MemorySegment query = Keychain.query(service, account);
    try (Arena arena = Arena.ofConfined()) {
      Keychain.put(query, RETURN_DATA, TRUE);
      Keychain.put(query, MATCH_LIMIT, MATCH_LIMIT_ONE);
      MemorySegment result = arena.allocate(Layouts.C_POINTER);
      result.set(Layouts.C_POINTER, 0, MemorySegment.NULL);
      int status = (int) ITEM_COPY_MATCHING.invokeExact(query, result);
      if (status == ITEM_NOT_FOUND) {
        return null;
      }
      Keychain.check("SecItemCopyMatching", status);
      MemorySegment data = result.get(Layouts.C_POINTER, 0);
      try {
        long length = (long) DATA_GET_LENGTH.invokeExact(data);
        if (length == 0) {
          return new byte[0];
        }
        MemorySegment bytes = (MemorySegment) DATA_GET_BYTE_PTR.invokeExact(data);
        return bytes.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
      } finally {
        Keychain.release(data);
      }
    } finally {
      Keychain.release(query);
    }
  }

  /**
   * Keeps {@code data} as the generic password of {@code service} and {@code account}, in place of
   * the one there, with {@code label} as what Keychain Access shows.
   *
   * @throws IllegalStateException If the keychain refused.
   */
  @SneakyThrows
  void savePassword(String service, String account, String label, byte[] data) {
    MemorySegment query = Keychain.query(service, account);
    MemorySegment value = Keychain.data(data);
    MemorySegment update = Keychain.dictionary();
    try {
      Keychain.put(update, VALUE_DATA, value);
      // SecItemUpdate keeps the data as it was for an empty one: the item goes, and comes back.
      int status =
          data.length == 0
              ? Keychain.deleted((int) ITEM_DELETE.invokeExact(query))
              : (int) ITEM_UPDATE.invokeExact(query, update);
      if (status == ITEM_NOT_FOUND) {
        MemorySegment text = Keychain.string(label);
        try {
          Keychain.put(query, VALUE_DATA, value);
          Keychain.put(query, ATTRIBUTE_LABEL, text);
          Keychain.check("SecItemAdd", (int) ITEM_ADD.invokeExact(query, MemorySegment.NULL));
        } finally {
          Keychain.release(text);
        }
      } else {
        Keychain.check("SecItemUpdate", status);
      }
    } finally {
      Keychain.release(update);
      Keychain.release(value);
      Keychain.release(query);
    }
  }

  /**
   * Deletes the generic password of {@code service} and {@code account}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalStateException If the keychain answered with another error.
   */
  @SneakyThrows
  boolean deletePassword(String service, String account) {
    MemorySegment query = Keychain.query(service, account);
    try {
      int status = (int) ITEM_DELETE.invokeExact(query);
      if (status == ITEM_NOT_FOUND) {
        return false;
      }
      Keychain.check("SecItemDelete", status);
      return true;
    } finally {
      Keychain.release(query);
    }
  }

  /**
   * {@code errSecItemNotFound} after a delete that removed the item or found none, which is what
   * makes the caller add it.
   *
   * @throws IllegalStateException If the delete failed for another reason.
   */
  private int deleted(int status) {
    if (status != ITEM_NOT_FOUND) {
      Keychain.check("SecItemDelete", status);
    }
    return ITEM_NOT_FOUND;
  }

  /**
   * A query for the generic password of {@code service} and {@code account}, which the caller
   * releases. The dictionary keeps its own references to the strings, so they go at once.
   */
  private MemorySegment query(String service, String account) {
    MemorySegment query = Keychain.dictionary();
    MemorySegment serviceText = Keychain.string(service);
    MemorySegment accountText = Keychain.string(account);
    try {
      Keychain.put(query, CLASS, CLASS_GENERIC_PASSWORD);
      Keychain.put(query, ATTRIBUTE_SERVICE, serviceText);
      Keychain.put(query, ATTRIBUTE_ACCOUNT, accountText);
    } finally {
      Keychain.release(accountText);
      Keychain.release(serviceText);
    }
    return query;
  }

  /** A new mutable {@code CFDictionary} that retains its keys and values. */
  @SneakyThrows
  private MemorySegment dictionary() {
    return (MemorySegment)
        DICTIONARY_CREATE.invokeExact(MemorySegment.NULL, 0L, KEY_CALLBACKS, VALUE_CALLBACKS);
  }

  @SneakyThrows
  private void put(MemorySegment dictionary, MemorySegment key, MemorySegment value) {
    DICTIONARY_SET_VALUE.invokeExact(dictionary, key, value);
  }

  /** A new {@code CFString} of {@code text}. */
  @SneakyThrows
  private MemorySegment string(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment)
          STRING_CREATE.invokeExact(
              MemorySegment.NULL,
              arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes),
              (long) bytes.length,
              UTF8,
              false);
    }
  }

  /** A new {@code CFData} with a copy of {@code bytes}. */
  @SneakyThrows
  private MemorySegment data(byte[] bytes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment copy =
          bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
      return (MemorySegment)
          DATA_CREATE_HANDLE.invokeExact(MemorySegment.NULL, copy, (long) bytes.length);
    }
  }

  @SneakyThrows
  private void release(MemorySegment object) {
    if (!object.equals(MemorySegment.NULL)) {
      RELEASE.invokeExact(object);
    }
  }

  private void check(String call, int status) {
    if (status != SUCCESS) {
      throw new IllegalStateException(call + " failed, OSStatus " + status);
    }
  }

  /** The object in the exported variable {@code name} of {@code library}. */
  private MemorySegment constant(SymbolLookup library, String name) {
    return library
        .find(name)
        .orElseThrow(() -> new IllegalStateException("No " + name + " in its framework"))
        .reinterpret(Layouts.C_POINTER.byteSize())
        .get(Layouts.C_POINTER, 0);
  }
}
