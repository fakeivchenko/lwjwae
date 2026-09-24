package dev.ivchenko.lwjwae;

import java.util.regex.Pattern;

/** What an RPC name may look like: it ends up in a URL path, so letters, digits, {@code . _ -}. */
final class RpcNames {
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");

  private RpcNames() {}

  /**
   * Checks {@code name}.
   *
   * @throws IllegalArgumentException If the name has any other character, or none.
   */
  static void check(String name) {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Not an RPC name: " + name);
    }
  }
}
