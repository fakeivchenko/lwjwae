package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import java.util.Objects;

/**
 * An entry that runs an action.
 *
 * @param label The text of the entry.
 * @param accelerator The keys that run the entry too while a window of the application has the
 *     keyboard, shown next to the label, or {@code null} for none. In a context menu, the keys are
 *     only shown.
 * @param enabled Whether the user can pick the entry. A disabled entry is drawn grayed out.
 * @param action What happens when the user picks the entry, or {@code null} for nothing.
 */
public record ActionMenuItem(String label, Shortcut accelerator, boolean enabled, Runnable action)
    implements MenuItem {
  public ActionMenuItem {
    Objects.requireNonNull(label, "label");
  }

  /** This entry with {@code accelerator}, written as {@link Shortcut#parse} reads it. */
  public ActionMenuItem withAccelerator(String accelerator) {
    return new ActionMenuItem(this.label, Shortcut.parse(accelerator), this.enabled, this.action);
  }

  /** This entry, enabled or not. */
  public ActionMenuItem withEnabled(boolean enabled) {
    return new ActionMenuItem(this.label, this.accelerator, enabled, this.action);
  }
}
