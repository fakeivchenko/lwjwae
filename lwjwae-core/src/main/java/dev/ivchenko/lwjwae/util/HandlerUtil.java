package dev.ivchenko.lwjwae.util;

import lombok.experimental.UtilityClass;

/**
 * Runs the handlers that an application gives the tray and the notifications.
 *
 * <p>A native toolkit reports a click on its UI thread, and a handler that ran there could block
 * the toolkit or deadlock on a call that the UI thread has to answer. Every such handler therefore
 * runs on a virtual thread of its own, where it may block or call back into a window, and a failure
 * goes to {@link ThrowableUtil#report} instead of into native code.
 */
@UtilityClass
public class HandlerUtil {
  /** Runs {@code handler} on a new virtual thread. A {@code null} handler does nothing. */
  public void runOffTheUiThread(Runnable handler) {
    if (handler == null) {
      return;
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                handler.run();
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            });
  }
}
