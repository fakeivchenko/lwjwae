package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Bindings to {@code advapi32.dll}: reading and writing the registry. */
@UtilityClass
public class Advapi32 {
  private final SymbolLookup ADVAPI32 = NativeLibraries.load("advapi32.dll");

  public final MemorySegment HKEY_LOCAL_MACHINE = MemorySegment.ofAddress(0x80000002L);
  public final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0x80000001L);

  /** {@code RRF_RT_REG_SZ}. */
  private final int REG_SZ_ONLY = 0x2;

  /** {@code RRF_RT_REG_DWORD}. */
  private final int REG_DWORD_ONLY = 0x10;

  /** {@code REG_SZ}. */
  private final int REG_SZ = 1;

  private final int ERROR_SUCCESS = 0;
  private final int BUFFER_CHARS = 2048;

  /** {@code CRED_TYPE_GENERIC}: a credential that only the application that wrote it reads. */
  private final int CRED_TYPE_GENERIC = 1;

  /** {@code CRED_PERSIST_LOCAL_MACHINE}: kept across logons, for this user on this machine. */
  private final int CRED_PERSIST_LOCAL_MACHINE = 2;

  /** {@code ERROR_NOT_FOUND}: no credential of that name. */
  private final int ERROR_NOT_FOUND = 1168;

  // CREDENTIALW on x64
  private final long CREDENTIAL_SIZE = 80;
  private final long CREDENTIAL_TYPE = 4;
  private final long CREDENTIAL_TARGET_NAME = 8;
  private final long CREDENTIAL_BLOB_SIZE = 32;
  private final long CREDENTIAL_BLOB = 40;
  private final long CREDENTIAL_PERSIST = 48;
  private final long CREDENTIAL_USER_NAME = 72;

  private final MethodHandle CRED_WRITE =
      NativeLibraries.downcall(ADVAPI32, "CredWriteW", Signatures.INT_POINTER_INT);
  private final MethodHandle CRED_READ =
      NativeLibraries.downcall(ADVAPI32, "CredReadW", Signatures.INT_POINTER_INT_INT_POINTER);
  private final MethodHandle CRED_DELETE =
      NativeLibraries.downcall(ADVAPI32, "CredDeleteW", Signatures.INT_POINTER_INT_INT);
  private final MethodHandle CRED_FREE =
      NativeLibraries.downcall(ADVAPI32, "CredFree", Signatures.VOID_POINTER);
  private final MethodHandle REG_SET_KEY_VALUE =
      NativeLibraries.downcall(
          ADVAPI32, "RegSetKeyValueW", Signatures.INT_POINTER_X3_INT_POINTER_INT);
  private final MethodHandle REG_GET_VALUE =
      NativeLibraries.downcall(ADVAPI32, "RegGetValueW", Signatures.INT_POINTER_X3_INT_POINTER_X3);

  /** A {@code REG_SZ} value, or empty if the key or the value doesn't exist. */
  @SneakyThrows
  public Optional<String> readString(MemorySegment root, String subKey, String value) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(BUFFER_CHARS * 2L);
      MemorySegment size = arena.allocate(Signatures.C_INT);
      size.set(Signatures.C_INT, 0, BUFFER_CHARS * 2);
      int status =
          (int)
              REG_GET_VALUE.invokeExact(
                  root,
                  Wide.allocate(arena, subKey),
                  Wide.allocate(arena, value),
                  REG_SZ_ONLY,
                  MemorySegment.NULL,
                  buffer,
                  size);
      return status == ERROR_SUCCESS ? Optional.of(Wide.read(buffer)) : Optional.empty();
    }
  }

  /** A {@code REG_DWORD} value, or empty if the key or the value doesn't exist. */
  @SneakyThrows
  public OptionalInt readDword(MemorySegment root, String subKey, String value) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(Signatures.C_INT);
      MemorySegment size = arena.allocate(Signatures.C_INT);
      size.set(Signatures.C_INT, 0, Integer.BYTES);
      int status =
          (int)
              REG_GET_VALUE.invokeExact(
                  root,
                  Wide.allocate(arena, subKey),
                  Wide.allocate(arena, value),
                  REG_DWORD_ONLY,
                  MemorySegment.NULL,
                  buffer,
                  size);
      return status == ERROR_SUCCESS
          ? OptionalInt.of(buffer.get(Signatures.C_INT, 0))
          : OptionalInt.empty();
    }
  }

  /**
   * Writes a {@code REG_SZ} value, creating {@code subKey} if it doesn't exist.
   *
   * @throws IllegalStateException If the registry refuses the write.
   */
  @SneakyThrows
  public void writeString(MemorySegment root, String subKey, String value, String data) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment bytes = Wide.allocate(arena, data);
      int status =
          (int)
              REG_SET_KEY_VALUE.invokeExact(
                  root,
                  Wide.allocate(arena, subKey),
                  Wide.allocate(arena, value),
                  REG_SZ,
                  bytes,
                  (int) bytes.byteSize());
      if (status != ERROR_SUCCESS) {
        throw new IllegalStateException(
            "Could not write " + subKey + "\\" + value + ": error " + status);
      }
    }
  }

  /**
   * The bytes of the generic credential {@code target}, or {@code null} if none.
   *
   * @throws IllegalStateException If {@code CredReadW} failed for another reason.
   */
  @SneakyThrows
  public byte[] readCredential(String target) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      int read =
          (int) CRED_READ.invokeExact(Wide.allocate(arena, target), CRED_TYPE_GENERIC, 0, out);
      if (read == 0) {
        int error = Kernel32.lastError();
        if (error == ERROR_NOT_FOUND) {
          return null;
        }
        throw new IllegalStateException("CredReadW failed, error " + error);
      }
      MemorySegment credential = out.get(Signatures.C_POINTER, 0).reinterpret(CREDENTIAL_SIZE);
      try {
        int size = credential.get(Signatures.C_INT, CREDENTIAL_BLOB_SIZE);
        MemorySegment blob = credential.get(Signatures.C_POINTER, CREDENTIAL_BLOB);
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
  public void writeCredential(String target, String userName, byte[] blob) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment credential = arena.allocate(CREDENTIAL_SIZE, 8);
      credential.fill((byte) 0);
      credential.set(Signatures.C_INT, CREDENTIAL_TYPE, CRED_TYPE_GENERIC);
      credential.set(Signatures.C_POINTER, CREDENTIAL_TARGET_NAME, Wide.allocate(arena, target));
      credential.set(Signatures.C_INT, CREDENTIAL_BLOB_SIZE, blob.length);
      if (blob.length > 0) {
        credential.set(
            Signatures.C_POINTER, CREDENTIAL_BLOB, arena.allocateFrom(ValueLayout.JAVA_BYTE, blob));
      }
      credential.set(Signatures.C_INT, CREDENTIAL_PERSIST, CRED_PERSIST_LOCAL_MACHINE);
      credential.set(Signatures.C_POINTER, CREDENTIAL_USER_NAME, Wide.allocate(arena, userName));
      if ((int) CRED_WRITE.invokeExact(credential, 0) == 0) {
        throw new IllegalStateException("CredWriteW failed, error " + Kernel32.lastError());
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
  public boolean deleteCredential(String target) {
    try (Arena arena = Arena.ofConfined()) {
      if ((int) CRED_DELETE.invokeExact(Wide.allocate(arena, target), CRED_TYPE_GENERIC, 0) != 0) {
        return true;
      }
      int error = Kernel32.lastError();
      if (error == ERROR_NOT_FOUND) {
        return false;
      }
      throw new IllegalStateException("CredDeleteW failed, error " + error);
    }
  }
}
