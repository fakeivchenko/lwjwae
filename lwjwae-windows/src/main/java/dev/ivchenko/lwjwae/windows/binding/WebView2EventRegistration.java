package dev.ivchenko.lwjwae.windows.binding;

import java.lang.foreign.MemorySegment;

/**
 * One of the {@code add_*} methods of {@code ICoreWebView2}, for example {@link
 * WebView2#onNavigationStarting}.
 */
@FunctionalInterface
public interface WebView2EventRegistration {
  /** Subscribes {@code handler}, a {@link ComCallback#event}, to the event on {@code webView}. */
  void add(MemorySegment webView, MemorySegment handler);
}
