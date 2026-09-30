package dev.ivchenko.lwjwae.menu;

import java.util.Objects;

/**
 * An entry that does what {@link MenuRole} does on the platform, such as Copy.
 *
 * @param role What the entry does.
 * @param label The text of the entry, or {@code null} for {@link MenuRole#label()}.
 */
public record RoleMenuItem(MenuRole role, String label) implements MenuItem {
  public RoleMenuItem {
    Objects.requireNonNull(role, "role");
    if (label == null || label.isBlank()) {
      label = role.label();
    }
  }
}
