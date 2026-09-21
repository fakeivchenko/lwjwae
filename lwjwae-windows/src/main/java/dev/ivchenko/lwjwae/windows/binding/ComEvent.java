package dev.ivchenko.lwjwae.windows.binding;

import java.lang.foreign.MemorySegment;

/**
 * The Java side of {@code HRESULT Invoke(T* sender, U* args)}, the shape of every WebView2 {@code
 * *EventHandler}. For the return value, see {@link ComCompletion}.
 */
@FunctionalInterface
public interface ComEvent {
  /**
   * Called by WebView2 on the UI thread when the event fires.
   *
   * @param sender The object that raised the event. Borrowed.
   * @param arguments The event arguments. Borrowed, and valid only during the call.
   */
  void invoke(MemorySegment sender, MemorySegment arguments);
}
