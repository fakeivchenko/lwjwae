package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An entry with a check mark that the user turns on and off.
 *
 * <p>The menu flips the mark itself when the user picks the entry, and hands the new state to
 * {@code onToggle}: the program doesn't have to set the menu again to keep the mark right. Setting
 * the menu again starts from {@code checked}.
 *
 * @param label The text of the entry.
 * @param accelerator The keys that pick the entry too, see {@link ActionMenuItem#accelerator()}.
 * @param enabled Whether the user can pick the entry.
 * @param checked Whether the mark is on when the menu is set.
 * @param onToggle Gets the state after a pick, or {@code null} for nothing.
 */
public record CheckMenuItem(
    String label,
    Shortcut accelerator,
    boolean enabled,
    boolean checked,
    Consumer<Boolean> onToggle)
    implements MenuItem {
  public CheckMenuItem {
    Objects.requireNonNull(label, "label");
  }

  /** This entry with {@code accelerator}, written as {@link Shortcut#parse} reads it. */
  public CheckMenuItem withAccelerator(String accelerator) {
    return new CheckMenuItem(
        this.label, Shortcut.parse(accelerator), this.enabled, this.checked, this.onToggle);
  }

  /** This entry, enabled or not. */
  public CheckMenuItem withEnabled(boolean enabled) {
    return new CheckMenuItem(this.label, this.accelerator, enabled, this.checked, this.onToggle);
  }
}
