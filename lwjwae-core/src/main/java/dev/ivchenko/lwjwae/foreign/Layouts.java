package dev.ivchenko.lwjwae.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;
import lombok.experimental.UtilityClass;

/**
 * C type layouts that every backend shares.
 *
 * <p>Only types whose width doesn't depend on the data model are declared here. In particular,
 * there's no {@code C_LONG}: a C {@code long} is 64-bit on Unix LP64 and 32-bit on Windows LLP64,
 * so each backend declares the width that its own headers use.
 *
 * <p>Loading this class has no side effects. It creates no {@link java.lang.foreign.Linker} and
 * opens no library, so signature tables built on top of it are safe to inspect at build time.
 */
@UtilityClass
public class Layouts {
  /** {@code char}, {@code int8_t}, {@code BYTE}. */
  public final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;

  /** {@code short}, {@code int16_t}, {@code WORD}. */
  public final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

  /** {@code int}, {@code int32_t}. */
  public final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

  /** {@code long long}, {@code int64_t}, and {@code size_t}/{@code ssize_t} on 64-bit targets. */
  public final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

  /** {@code double}. */
  public final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

  /** C {@code bool}, {@code BOOLEAN}: one byte. */
  public final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;

  /** Any {@code T*}. */
  public final AddressLayout C_POINTER = ValueLayout.ADDRESS;
}
