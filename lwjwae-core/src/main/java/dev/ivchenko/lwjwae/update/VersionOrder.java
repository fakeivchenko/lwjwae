package dev.ivchenko.lwjwae.update;

import java.math.BigInteger;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The order of versions, as Semantic Versioning has it: {@code 1.10.0} after {@code 1.9.2}, and a
 * pre-release such as {@code 2.0.0-rc.1} before its release.
 *
 * <p>A missing part counts as 0, so {@code 1.2} and {@code 1.2.0} are one version, and build
 * metadata after a {@code +} doesn't count. A part that isn't a number compares as text, after the
 * numbers, which keeps any version string comparable.
 */
@UtilityClass
class VersionOrder {
  private final Pattern NUMBER = Pattern.compile("\\d+");

  /** Negative when {@code first} comes before {@code second}, 0 for one version, else positive. */
  int compare(String first, String second) {
    String[] a = VersionOrder.withoutBuild(first).split("-", 2);
    String[] b = VersionOrder.withoutBuild(second).split("-", 2);
    int release = VersionOrder.compareParts(a[0].split("\\."), b[0].split("\\."), true);
    if (release != 0) {
      return release;
    }
    if (a.length == 1 || b.length == 1) {
      return Integer.compare(b.length, a.length);
    }
    return VersionOrder.compareParts(a[1].split("\\."), b[1].split("\\."), false);
  }

  private String withoutBuild(String version) {
    int plus = version.indexOf('+');
    return (plus < 0 ? version : version.substring(0, plus)).strip();
  }

  /**
   * Compares part by part. A release pads the shorter one with zeros; a pre-release with fewer
   * parts comes first.
   */
  private int compareParts(String[] a, String[] b, boolean padWithZeros) {
    for (int index = 0; index < Math.max(a.length, b.length); index++) {
      if (index >= a.length || index >= b.length) {
        if (!padWithZeros) {
          return Integer.compare(a.length, b.length);
        }
      }
      String left = index < a.length ? a[index] : "0";
      String right = index < b.length ? b[index] : "0";
      int order = VersionOrder.comparePart(left, right);
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  private int comparePart(String left, String right) {
    boolean leftNumber = NUMBER.matcher(left).matches();
    boolean rightNumber = NUMBER.matcher(right).matches();
    if (leftNumber && rightNumber) {
      return new BigInteger(left).compareTo(new BigInteger(right));
    }
    if (leftNumber != rightNumber) {
      return leftNumber ? -1 : 1;
    }
    return left.compareTo(right);
  }
}
