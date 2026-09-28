package dev.ivchenko.lwjwae.shortcut;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A combination of keys: the modifiers held down, and the key pressed with them.
 *
 * <p>{@link #parse} reads the combination as it is written, the modifiers first and the key last,
 * joined with {@code +}, in any case:
 *
 * <pre>{@code
 * Shortcut.parse("Ctrl+Shift+K")
 * Shortcut.parse("CmdOrCtrl+Alt+Space")
 * Shortcut.parse("F13")
 * }</pre>
 *
 * <p>{@code CmdOrCtrl} is {@code Command} on macOS and {@code Ctrl} elsewhere, which is where each
 * platform puts the shortcuts of an application. A key other than a function key needs a modifier,
 * because a shortcut takes its key away from every application: a letter alone could no longer be
 * typed.
 *
 * @param modifiers The keys held down. Default: none.
 * @param key The key pressed.
 */
public record Shortcut(Set<ShortcutModifier> modifiers, ShortcutKey key) {
  public Shortcut {
    Objects.requireNonNull(key, "key");
    modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
    if (modifiers.isEmpty() && !key.isFunctionKey()) {
      throw new IllegalArgumentException(
          "A shortcut on " + key.label() + " needs a modifier, or no application could type it");
    }
  }

  /** The shortcut of {@code key} with {@code modifiers}. */
  public static Shortcut of(ShortcutKey key, ShortcutModifier... modifiers) {
    return new Shortcut(Set.of(modifiers), key);
  }

  /**
   * Reads a shortcut such as {@code Ctrl+Shift+K}. The modifiers are {@code Ctrl} or {@code
   * Control}, {@code Alt} or {@code Option}, {@code Shift}, {@code Meta}, {@code Super}, {@code
   * Win}, {@code Cmd}, or {@code Command}, and {@code CmdOrCtrl} or {@code CommandOrControl}; the
   * key is a {@link ShortcutKey#label()}.
   *
   * @throws IllegalArgumentException If the text isn't a shortcut.
   */
  public static Shortcut parse(String text) {
    Objects.requireNonNull(text, "text");
    String[] parts = text.split("\\+", -1);
    Set<ShortcutModifier> modifiers = EnumSet.noneOf(ShortcutModifier.class);
    for (int index = 0; index < parts.length - 1; index++) {
      ShortcutModifier modifier = Shortcut.modifier(parts[index].trim());
      if (modifier == null) {
        throw new IllegalArgumentException(
            "Not a modifier: '" + parts[index].trim() + "' in '" + text + "'");
      }
      modifiers.add(modifier);
    }
    String name = parts[parts.length - 1].trim();
    ShortcutKey key = ShortcutKey.ofLabel(name);
    if (key == null) {
      throw new IllegalArgumentException("Not a key: '" + name + "' in '" + text + "'");
    }
    return new Shortcut(modifiers, key);
  }

  private static ShortcutModifier modifier(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "ctrl", "control" -> ShortcutModifier.CONTROL;
      case "alt", "option" -> ShortcutModifier.ALT;
      case "shift" -> ShortcutModifier.SHIFT;
      case "meta", "super", "win", "cmd", "command" -> ShortcutModifier.META;
      case "cmdorctrl", "commandorcontrol" ->
          PlatformUtil.isMacOs() ? ShortcutModifier.META : ShortcutModifier.CONTROL;
      default -> null;
    };
  }

  /** Whether the shortcut holds {@code modifier} down. */
  public boolean has(ShortcutModifier modifier) {
    return this.modifiers.contains(modifier);
  }

  /**
   * The shortcut as {@link #parse} reads it, the modifiers in a fixed order: {@code Ctrl+Shift+K}.
   */
  @Override
  public String toString() {
    String held =
        this.modifiers.stream()
            .sorted()
            .map(ShortcutModifier::label)
            .collect(Collectors.joining("+"));
    return held.isEmpty() ? this.key.label() : held + "+" + this.key.label();
  }
}
