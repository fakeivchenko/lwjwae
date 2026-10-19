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
 * Bindings to the generic credentials of the Credential Manager of Windows, in {@code
 * advapi32.dll}. The calls are synchronous and work from any thread.
 */
@UtilityClass
class Credentials {
  private final SymbolLookup ADVAPI32 = NativeLibraries.load("advapi32.dll");
  private final SymbolLookup KERNEL32 = NativeLibraries.load("kernel32.dll");

  /** {@code CRED_TYPE_GENERIC}: a credential that only the application that wrote it reads. */
  private final int CRED_TYPE_GENERIC = 1;

  /** {@code CRED_PERSIST_LOCAL_MACHINE}: kept across logons, for this user on this machine. */
  private final int CRED_PERSIST_LOCAL_MACHINE = 2;

  /** {@code ERROR_NOT_FOUND}: no credential of that name. */
  private final int ERROR_NOT_FOUND = 1168;

  // --- CREDENTIALW on 64-bit Windows: its size and the offsets of the fields that it sets ---
  private final long CREDENTIAL_SIZE = 80;
  private final long CREDENTIAL_TYPE = 4;
  private final long CREDENTIAL_TARGET_NAME = 8;
  private final long CREDENTIAL_BLOB_SIZE = 32;
  private final long CREDENTIAL_BLOB = 40;
  private final long CREDENTIAL_PERSIST = 48;
  private final long CREDENTIAL_USER_NAME = 72;

  private final MethodHandle CRED_WRITE =
      NativeLibraries.downcall(
          ADVAPI32,
          "CredWriteW",
          FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT));
  private final MethodHandle CRED_READ =
      NativeLibraries.downcall(
          ADVAPI32,
          "CredReadW",
          FunctionDescriptor.of(
              Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT, Layouts.C_INT, Layouts.C_POINTER));
  private final MethodHandle CRED_DELETE =
      NativeLibraries.downcall(
          ADVAPI32,
          "CredDeleteW",
          FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT, Layouts.C_INT));
  private final MethodHandle CRED_FREE =
      NativeLibraries.downcall(ADVAPI32, "CredFree", FunctionDescriptor.ofVoid(Layouts.C_POINTER));
  private final MethodHandle GET_LAST_ERROR =
      NativeLibraries.downcall(KERNEL32, "GetLastError", FunctionDescriptor.of(Layouts.C_INT));

  /**
   * The bytes of the generic credential {@code target}, or {@code null} if none.
   *
   * @throws IllegalStateException If {@code CredReadW} failed for another reason.
   */
  @SneakyThrows
  byte[] read(String target) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Layouts.C_POINTER);
      int read =
          (int) CRED_READ.invokeExact(Credentials.wide(arena, target), CRED_TYPE_GENERIC, 0, out);
      if (read == 0) {
        int error = Credentials.lastError();
        if (error == ERROR_NOT_FOUND) {
          return null;
        }
        throw new IllegalStateException("CredReadW failed, error " + error);
      }
      MemorySegment credential = out.get(Layouts.C_POINTER, 0).reinterpret(CREDENTIAL_SIZE);
      try {
        int size = credential.get(Layouts.C_INT, CREDENTIAL_BLOB_SIZE);
        MemorySegment blob = credential.get(Layouts.C_POINTER, CREDENTIAL_BLOB);
        return size == 0 ? new byte[0] : blob.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
      } finally {
        CRED_FREE.invokeExact(credential);
      }
    }
  }

  /**
   * Writes the generic credential {@code target} with {@code blob}, in place of the one there, kept
   * for this user across logons. {@code userName} is what the Credential Manager shows.
   *
   * @throws IllegalStateException If {@code CredWriteW} failed.
   */
  @SneakyThrows
  void write(String target, String userName, byte[] blob) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment credential = arena.allocate(CREDENTIAL_SIZE, 8);
      credential.fill((byte) 0);
      credential.set(Layouts.C_INT, CREDENTIAL_TYPE, CRED_TYPE_GENERIC);
      credential.set(Layouts.C_POINTER, CREDENTIAL_TARGET_NAME, Credentials.wide(arena, target));
      credential.set(Layouts.C_INT, CREDENTIAL_BLOB_SIZE, blob.length);
      if (blob.length > 0) {
        credential.set(
            Layouts.C_POINTER, CREDENTIAL_BLOB, arena.allocateFrom(ValueLayout.JAVA_BYTE, blob));
      }
      credential.set(Layouts.C_INT, CREDENTIAL_PERSIST, CRED_PERSIST_LOCAL_MACHINE);
      credential.set(Layouts.C_POINTER, CREDENTIAL_USER_NAME, Credentials.wide(arena, userName));
      if ((int) CRED_WRITE.invokeExact(credential, 0) == 0) {
        throw new IllegalStateException("CredWriteW failed, error " + Credentials.lastError());
      }
    }
  }

  /**
   * Deletes the generic credential {@code target}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalStateException If {@code CredDeleteW} failed for another reason.
   */
  @SneakyThrows
  boolean delete(String target) {
    try (Arena arena = Arena.ofConfined()) {
      if ((int) CRED_DELETE.invokeExact(Credentials.wide(arena, target), CRED_TYPE_GENERIC, 0)
          != 0) {
        return true;
      }
      int error = Credentials.lastError();
      if (error == ERROR_NOT_FOUND) {
        return false;
      }
      throw new IllegalStateException("CredDeleteW failed, error " + error);
    }
  }

  /** {@code text} as a string of Windows: UTF-16 and a terminating zero. */
  private MemorySegment wide(Arena arena, String text) {
    return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
  }

  @SneakyThrows
  private int lastError() {
    return (int) GET_LAST_ERROR.invokeExact();
  }
}
