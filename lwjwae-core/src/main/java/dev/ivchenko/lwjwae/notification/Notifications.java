package dev.ivchenko.lwjwae.notification;

/**
 * The desktop notifications of the application. {@link
 * dev.ivchenko.lwjwae.Application#notifications()} hands them out.
 *
 * <p>A notification belongs to the application. It stays when every window is closed, but it
 * doesn't keep {@link dev.ivchenko.lwjwae.Application#run()} running, and it goes away with the
 * application, because its buttons and its {@code onActivate} handler go with it.
 *
 * <p>Every method is safe to call from any thread.
 */
public interface Notifications {
  /**
   * Shows a desktop notification and returns the handle that takes it back.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A toast, filed under an ID made from {@link
   *       dev.ivchenko.lwjwae.ApplicationParameters#name()} that the library registers for the
   *       user. A toast that times out moves to Notification Center, where it can still be clicked,
   *       and under Do Not Disturb every toast goes there at once.
   *   <li>macOS: Only for an application packaged as an {@code .app} bundle with an identifier:
   *       under the {@code java} launcher or as a bare executable, the call throws. The first
   *       notification asks the user for permission; after a no, every call throws until the user
   *       allows them in System Settings.
   *   <li>Linux, GTK 3: Through {@code org.freedesktop.Notifications} over D-Bus; without a session
   *       bus or a notification server, the call throws. How much of the image and the buttons
   *       shows is up to the server.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws UnsupportedOperationException If this backend has no notifications yet, or the desktop
   *     has nothing that shows them.
   * @throws IllegalStateException If the application is closed.
   */
  NotificationHandle show(Notification notification);

  /** Shows a notification of {@code title} and {@code body}, and nothing else. */
  default NotificationHandle show(String title, String body) {
    return this.show(Notification.of(title, body));
  }
}
