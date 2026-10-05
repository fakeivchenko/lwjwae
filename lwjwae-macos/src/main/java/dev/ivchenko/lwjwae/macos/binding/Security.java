package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The keychain, through the {@code SecItem} calls of the Security framework, for generic passwords.
 *
 * <p>The queries are {@code NSDictionary}s, which the framework takes as {@code CFDictionary}s, and
 * their keys are the {@code CFString} constants that the framework exports as variables, read once.
 * The calls are synchronous and work from any thread; each runs in an autorelease pool of its own,
 * since a thread of the caller has none.
 */
@UtilityClass
public class Security {
  private final SymbolLookup SECURITY =
      NativeLibraries.load("/System/Library/Frameworks/Security.framework/Security");

  /** {@code errSecSuccess}. */
  private final int SUCCESS = 0;

  /** {@code errSecItemNotFound}. */
  private final int ITEM_NOT_FOUND = -25300;

  private final MethodHandle ITEM_ADD =
      NativeLibraries.downcall(SECURITY, "SecItemAdd", Signatures.INT_POINTER_POINTER);
  private final MethodHandle ITEM_COPY_MATCHING =
      NativeLibraries.downcall(SECURITY, "SecItemCopyMatching", Signatures.INT_POINTER_POINTER);
  private final MethodHandle ITEM_UPDATE =
      NativeLibraries.downcall(SECURITY, "SecItemUpdate", Signatures.INT_POINTER_POINTER);
  private final MethodHandle ITEM_DELETE =
      NativeLibraries.downcall(SECURITY, "SecItemDelete", Signatures.INT_POINTER);

  // --- keys and values of a query ---
  private final MemorySegment CLASS = Security.constant("kSecClass");
  private final MemorySegment CLASS_GENERIC_PASSWORD =
      Security.constant("kSecClassGenericPassword");
  private final MemorySegment ATTRIBUTE_SERVICE = Security.constant("kSecAttrService");
  private final MemorySegment ATTRIBUTE_ACCOUNT = Security.constant("kSecAttrAccount");
  private final MemorySegment ATTRIBUTE_LABEL = Security.constant("kSecAttrLabel");
  private final MemorySegment VALUE_DATA = Security.constant("kSecValueData");
  private final MemorySegment RETURN_DATA = Security.constant("kSecReturnData");
  private final MemorySegment MATCH_LIMIT = Security.constant("kSecMatchLimit");
  private final MemorySegment MATCH_LIMIT_ONE = Security.constant("kSecMatchLimitOne");

  /**
   * The data of the generic password of {@code service} and {@code account}, or {@code null} if
   * none.
   *
   * @throws IllegalStateException If the keychain answered with another error.
   */
  @SneakyThrows
  public byte[] findPassword(String service, String account) {
    MemorySegment pool = ObjC.autoreleasePoolPush();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment query = Security.query(service, account);
      Security.put(query, RETURN_DATA, ObjC.send(ObjC.cls("NSNumber"), "numberWithBool:", true));
      Security.put(query, MATCH_LIMIT, MATCH_LIMIT_ONE);
      MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
      result.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
      int status = (int) ITEM_COPY_MATCHING.invokeExact(query, result);
      if (status == ITEM_NOT_FOUND) {
        return null;
      }
      Security.check("SecItemCopyMatching", status);
      MemorySegment data = result.get(ValueLayout.ADDRESS, 0);
      byte[] bytes = Foundation.bytes(data);
      // Copy rule: the caller owns the result.
      Foundation.release(data);
      return bytes == null ? new byte[0] : bytes;
    } finally {
      ObjC.autoreleasePoolPop(pool);
    }
  }

  /**
   * Keeps {@code data} as the generic password of {@code service} and {@code account}, in place of
   * the one there, with {@code label} as what Keychain Access shows.
   *
   * @throws IllegalStateException If the keychain refused.
   */
  @SneakyThrows
  public void savePassword(String service, String account, String label, byte[] data) {
    MemorySegment pool = ObjC.autoreleasePoolPush();
    try {
      MemorySegment query = Security.query(service, account);
      MemorySegment value = Foundation.data(data);
      MemorySegment update = ObjC.send(ObjC.cls("NSMutableDictionary"), "dictionary");
      Security.put(update, VALUE_DATA, value);
      int status = (int) ITEM_UPDATE.invokeExact(query, update);
      if (status == ITEM_NOT_FOUND) {
        Security.put(query, VALUE_DATA, value);
        Security.put(query, ATTRIBUTE_LABEL, Foundation.string(label));
        status = (int) ITEM_ADD.invokeExact(query, MemorySegment.NULL);
        Security.check("SecItemAdd", status);
      } else {
        Security.check("SecItemUpdate", status);
      }
    } finally {
      ObjC.autoreleasePoolPop(pool);
    }
  }

  /**
   * Deletes the generic password of {@code service} and {@code account}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalStateException If the keychain answered with another error.
   */
  @SneakyThrows
  public boolean deletePassword(String service, String account) {
    MemorySegment pool = ObjC.autoreleasePoolPush();
    try {
      int status = (int) ITEM_DELETE.invokeExact(Security.query(service, account));
      if (status == ITEM_NOT_FOUND) {
        return false;
      }
      Security.check("SecItemDelete", status);
      return true;
    } finally {
      ObjC.autoreleasePoolPop(pool);
    }
  }

  /** An autoreleased query for the generic password of {@code service} and {@code account}. */
  private MemorySegment query(String service, String account) {
    MemorySegment query = ObjC.send(ObjC.cls("NSMutableDictionary"), "dictionary");
    Security.put(query, CLASS, CLASS_GENERIC_PASSWORD);
    Security.put(query, ATTRIBUTE_SERVICE, Foundation.string(service));
    Security.put(query, ATTRIBUTE_ACCOUNT, Foundation.string(account));
    return query;
  }

  private void put(MemorySegment dictionary, MemorySegment key, MemorySegment value) {
    ObjC.sendVoid(dictionary, "setObject:forKey:", value, key);
  }

  private void check(String call, int status) {
    if (status != SUCCESS) {
      throw new IllegalStateException(call + " failed, OSStatus " + status);
    }
  }

  /** The {@code CFStringRef} in the exported variable {@code name}. */
  private MemorySegment constant(String name) {
    return SECURITY
        .find(name)
        .orElseThrow(() -> new IllegalStateException("No " + name + " in the Security framework"))
        .reinterpret(ValueLayout.ADDRESS.byteSize())
        .get(ValueLayout.ADDRESS, 0);
  }
}
