package dev.ivchenko.lwjwae.glib;

import java.lang.foreign.MemorySegment;

/** Finishes an asynchronous call of GIO: what its {@code GAsyncResult} says. */
@FunctionalInterface
interface AsyncResultReader<T> {
  T read(MemorySegment result) throws Throwable;
}
