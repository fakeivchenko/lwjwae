package dev.ivchenko.lwjwae.taskbar;

/**
 * What the icon of the application in the taskbar or the Dock shows besides itself: a progress and
 * a count. {@link dev.ivchenko.lwjwae.Application#taskbar()} hands it out.
 *
 * <p>Both belong to the application, not to a window: every window of it shows them where the
 * platform shows them per window, a window opened later too, and they stay until the next call or
 * the end of the application.
 *
 * <p>Every method is safe to call from any thread. Once the application is closed, the methods that
 * change what the icon shows throw {@link IllegalStateException}; the ones that read it don't.
 */
public interface Taskbar {
  /**
   * Shows {@code progress} on the icon of the application, until the next call; {@link
   * TaskbarProgress#none()} takes it away.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The button of every window in the taskbar, through {@code ITaskbarList3}, in
   *       green, yellow for {@link ProgressState#PAUSED}, and red for {@link ProgressState#ERROR};
   *       a window opened later shows it too.
   *   <li>macOS: A bar over the icon in the Dock, the same in every state; an indeterminate one
   *       stands still, since the Dock draws the icon only when it changes.
   *   <li>Linux, GTK 3: The {@code com.canonical.Unity.LauncherEntry} signal over D-Bus, for the
   *       desktop entry named after the program, {@code NAME.desktop}, as the Gradle plugin of
   *       lwjwae installs it. The task manager of KDE Plasma and the dock of Ubuntu show it; an
   *       indeterminate one shows no bar, and an error asks for attention.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws IllegalStateException If the application is closed.
   */
  void progress(TaskbarProgress progress);

  /** Shows the progress {@code value}, from 0 to 1, on the icon: {@link TaskbarProgress#of}. */
  default void progress(double value) {
    this.progress(TaskbarProgress.of(value));
  }

  /** The progress on the icon of the application. */
  TaskbarProgress progress();

  /**
   * Shows {@code count} on the icon of the application, such as the number of unread messages,
   * until the next call; 0 takes it away.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A red circle with the number over the button of every window in the taskbar,
   *       through {@code SetOverlayIcon}; {@code 9+} from 10 on. The taskbar shows it only with its
   *       small buttons off, which is the default.
   *   <li>macOS: The badge of the icon in the Dock.
   *   <li>Linux, GTK 3: The count of {@code com.canonical.Unity.LauncherEntry}, as for {@link
   *       #progress(TaskbarProgress)}.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws IllegalArgumentException If {@code count} is negative.
   * @throws IllegalStateException If the application is closed.
   */
  void badgeCount(int count);

  /** The count on the icon of the application, 0 for none. */
  int badgeCount();
}
