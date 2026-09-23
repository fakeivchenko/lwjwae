package dev.ivchenko.lwjwae;

/**
 * What a window does when the user closes it from its title bar or with the shortcut of the
 * desktop.
 *
 * <p>Only the user's request goes through this choice. {@link Window#close()} from Java and {@code
 * window.lwjwae.close()} from the page always close, because code that asks for a close means it.
 */
public enum CloseAction {
  /** Closes the window: the native window is destroyed, and the window leaves the application. */
  CLOSE,

  /**
   * Hides the window instead, the way an application that lives in the tray keeps its window
   * around. The window stays open, so it keeps {@link Application#run()} going, and {@link
   * Window#show()} brings it back as it was, page and state included.
   *
   * <p>The window hides only while the application has a tray icon up, the one way back to a window
   * that the user can't see; with none, the close button closes the window as {@link #CLOSE} does.
   * Otherwise the last window would turn into a process that runs on with nothing on screen and
   * nothing to click.
   */
  HIDE
}
