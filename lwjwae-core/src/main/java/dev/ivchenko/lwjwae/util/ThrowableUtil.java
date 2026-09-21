package dev.ivchenko.lwjwae.util;

import lombok.experimental.UtilityClass;

/**
 * Last-resort handling for throwables that must not escape.
 *
 * <p>Native toolkits call back into Java on their own threads. Letting a Java throwable unwind into
 * C is undefined behavior, so every callback and every listener invocation reports failures here
 * instead of propagating them.
 */
@UtilityClass
public class ThrowableUtil {
  /** Passes {@code t} to the uncaught exception handler of the current thread and returns. */
  public void report(Throwable t) {
    Thread current = Thread.currentThread();
    current.getUncaughtExceptionHandler().uncaughtException(current, t);
  }
}
