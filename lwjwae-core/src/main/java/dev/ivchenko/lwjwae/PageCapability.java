package dev.ivchenko.lwjwae;

/**
 * What the page of a window may do through {@code window.lwjwae} beyond its own window, granted by
 * {@link ApplicationParameters#pageCapabilities()}.
 *
 * <p>Every trusted page can call the functions that the application binds, the handlers that it
 * registers, and the events that it listens to: the application chose those one by one. It can also
 * read the theme and the state of its window, follow a link to the browser of the system, and move
 * and resize a window without a frame from its drag regions, which only follows the pointer of the
 * user. Everything else is a capability, and a page has none of them unless the application grants
 * it. A script that runs in the page, from a library that the application bundles or from a hole in
 * the page, then reaches no further than the application decided, rather than the whole file system
 * through the store or the clipboard of the user.
 *
 * <p>A call that needs a capability that the application didn't grant fails on the page with an
 * {@code RpcError} of status 403 and code {@code capability}, whose message names it; an event that
 * needs one doesn't reach the page.
 */
public enum PageCapability {
  /**
   * {@code lwjwae.window.minimize}, {@code maximize}, {@code restore}, {@code toggleMaximize},
   * {@code fullscreen}, and {@code close}, and {@code lwjwae.close}: changes of the own window that
   * no pointer asks for.
   */
  WINDOW,

  /** {@code lwjwae.open}: windows of the application's own pages. */
  OPEN_WINDOWS,

  /** {@code lwjwae.dialog}: the open, save, and message dialogs of the platform. */
  DIALOGS,

  /** {@code lwjwae.clipboard}: the text of the clipboard of the system, both ways. */
  CLIPBOARD,

  /** {@code lwjwae.menu.popup}: context menus. */
  MENUS,

  /** {@code lwjwae.taskbar}: the progress and the badge on the icon of the application. */
  TASKBAR,

  /** {@code lwjwae.downloads}: the downloads of the window and their paths, and their cancel. */
  DOWNLOADS,

  /** {@code lwjwae.files}: the paths of the files that the user drops on the window. */
  FILES,

  /** {@code lwjwae.store}: SQL on the store of the application, which keeps to its file. */
  STORE,

  /**
   * {@code lwjwae.updates}: finding, downloading, and installing the updates of the application.
   */
  UPDATES
}
