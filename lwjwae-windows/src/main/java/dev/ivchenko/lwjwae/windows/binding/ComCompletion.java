package dev.ivchenko.lwjwae.windows.binding;

import java.lang.foreign.MemorySegment;

/**
 * The Java side of {@code HRESULT Invoke(HRESULT errorCode, T* result)}, the shape of every
 * WebView2 {@code *CompletedHandler}. The Java side never needs to fail the call, so the handler
 * returns nothing and the stub always answers {@code S_OK}.
 */
@FunctionalInterface
public interface ComCompletion {
  /**
   * Called by WebView2 on the UI thread when the operation completes.
   *
   * @param hresult The outcome. Negative means failure.
   * @param result The created object, or {@code NULL} on failure. Borrowed: call {@link Com#addRef}
   *     to keep it.
   */
  void invoke(int hresult, MemorySegment result);
}
