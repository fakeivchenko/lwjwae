package dev.ivchenko.lwjwae.glib;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Starts an asynchronous call of GIO on the GTK thread, with {@code data} to pass to its {@code
 * GAsyncReadyCallback}, and {@code arena} for the memory that the start needs.
 */
@FunctionalInterface
interface AsyncCall {
  void start(Arena arena, MemorySegment data) throws Throwable;
}
