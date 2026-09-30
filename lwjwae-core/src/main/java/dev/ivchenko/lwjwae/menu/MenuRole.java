package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * An entry that every platform has and does its own way: the editing commands of the page, and what
 * a window and the application do.
 *
 * <p>The editing roles act on the page in the window that has the keyboard, the way its own keys
 * do, and their keys stay with the page: the menu only shows them. The other roles act on that
 * window or on the application, and their keys belong to the menu.
 */
public enum MenuRole {
  /** Undoes the last edit of the page. */
  UNDO("Undo", "CmdOrCtrl+Z"),

  /** Redoes what Undo undid. */
  REDO("Redo", PlatformUtil.isWindows() ? "Ctrl+Y" : "CmdOrCtrl+Shift+Z"),

  /** Cuts the selection of the page. */
  CUT("Cut", "CmdOrCtrl+X"),

  /** Copies the selection of the page. */
  COPY("Copy", "CmdOrCtrl+C"),

  /** Pastes into the page. */
  PASTE("Paste", "CmdOrCtrl+V"),

  /** Selects everything that the focus of the page can select. */
  SELECT_ALL("Select All", "CmdOrCtrl+A"),

  /** Asks the window to close, as its close button does: it may hide instead, or refuse. */
  CLOSE_WINDOW("Close Window", "CmdOrCtrl+W"),

  /** Minimizes the window. */
  MINIMIZE("Minimize", PlatformUtil.isMacOs() ? "Cmd+M" : null),

  /** Turns full screen on or off. */
  FULLSCREEN("Toggle Full Screen", PlatformUtil.isMacOs() ? "Ctrl+Cmd+F" : "F11"),

  /** Quits the application, see {@link dev.ivchenko.lwjwae.Application#quit()}. */
  QUIT(PlatformUtil.isWindows() ? "Exit" : "Quit", PlatformUtil.isWindows() ? null : "CmdOrCtrl+Q");

  private final String label;
  private final Shortcut accelerator;

  MenuRole(String label, String accelerator) {
    this.label = label;
    this.accelerator = accelerator == null ? null : Shortcut.parse(accelerator);
  }

  /** The label of the entry on this platform. */
  public String label() {
    return this.label;
  }

  /** The keys of the entry on this platform, or {@code null} for none. */
  public Shortcut accelerator() {
    return this.accelerator;
  }

  /** Whether the role edits the page, and its keys stay with the page. */
  public boolean isEditing() {
    return switch (this) {
      case UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL -> true;
      default -> false;
    };
  }
}
