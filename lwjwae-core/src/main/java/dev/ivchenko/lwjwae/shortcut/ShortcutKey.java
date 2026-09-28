package dev.ivchenko.lwjwae.shortcut;

import java.util.List;
import java.util.Locale;

/**
 * The key of a {@link Shortcut}: a letter, a digit, a function key, or one of the keys that every
 * keyboard layout puts in the same place.
 *
 * <p>Punctuation is left out: where it sits, and which keys a layout shifts to reach it, differs
 * from one layout to the next, and a shortcut on it would work on some keyboards only. A letter is
 * the key that types it in the layout that is active when the shortcut is bound.
 */
public enum ShortcutKey {
  A("A"),
  B("B"),
  C("C"),
  D("D"),
  E("E"),
  F("F"),
  G("G"),
  H("H"),
  I("I"),
  J("J"),
  K("K"),
  L("L"),
  M("M"),
  N("N"),
  O("O"),
  P("P"),
  Q("Q"),
  R("R"),
  S("S"),
  T("T"),
  U("U"),
  V("V"),
  W("W"),
  X("X"),
  Y("Y"),
  Z("Z"),
  DIGIT_0("0"),
  DIGIT_1("1"),
  DIGIT_2("2"),
  DIGIT_3("3"),
  DIGIT_4("4"),
  DIGIT_5("5"),
  DIGIT_6("6"),
  DIGIT_7("7"),
  DIGIT_8("8"),
  DIGIT_9("9"),
  F1("F1"),
  F2("F2"),
  F3("F3"),
  F4("F4"),
  F5("F5"),
  F6("F6"),
  F7("F7"),
  F8("F8"),
  F9("F9"),
  F10("F10"),
  F11("F11"),
  F12("F12"),
  F13("F13"),
  F14("F14"),
  F15("F15"),
  F16("F16"),
  F17("F17"),
  F18("F18"),
  F19("F19"),
  F20("F20"),
  F21("F21"),
  F22("F22"),
  F23("F23"),
  F24("F24"),
  SPACE("Space"),
  ENTER("Enter", "Return"),
  TAB("Tab"),
  ESCAPE("Escape", "Esc"),
  BACKSPACE("Backspace"),
  DELETE("Delete", "Del"),
  INSERT("Insert", "Ins"),
  HOME("Home"),
  END("End"),
  PAGE_UP("PageUp"),
  PAGE_DOWN("PageDown"),
  UP("Up"),
  DOWN("Down"),
  LEFT("Left"),
  RIGHT("Right");

  private final String label;
  private final List<String> aliases;

  ShortcutKey(String label, String... aliases) {
    this.label = label;
    this.aliases = List.of(aliases);
  }

  /** The name of the key in a shortcut, for example {@code PageUp}. */
  public String label() {
    return this.label;
  }

  /** Whether this is one of {@code F1} to {@code F24}. */
  public boolean isFunctionKey() {
    return this.ordinal() >= F1.ordinal() && this.ordinal() <= F24.ordinal();
  }

  /** The key of a name, in any case, or {@code null} for a name that isn't one. */
  public static ShortcutKey ofLabel(String name) {
    for (ShortcutKey key : values()) {
      if (key.label.equalsIgnoreCase(name)) {
        return key;
      }
      for (String alias : key.aliases) {
        if (alias.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
          return key;
        }
      }
    }
    return null;
  }
}
