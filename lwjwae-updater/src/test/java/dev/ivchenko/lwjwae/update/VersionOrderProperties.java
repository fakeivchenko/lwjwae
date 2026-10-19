package dev.ivchenko.lwjwae.update;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Assertions;

/**
 * The order of versions is a total order, whatever the strings: a manifest of another tool must not
 * make the updater see a version as newer and older at once.
 */
class VersionOrderProperties {
  @Property
  void everyVersionIsItself(@ForAll("versions") String version) {
    Assertions.assertEquals(0, VersionOrder.compare(version, version));
  }

  @Property
  void swappingTheVersionsSwapsTheOrder(
      @ForAll("versions") String first, @ForAll("versions") String second) {
    Assertions.assertEquals(
        Integer.signum(VersionOrder.compare(first, second)),
        -Integer.signum(VersionOrder.compare(second, first)));
  }

  @Property
  void orderCarriesOver(
      @ForAll("versions") String first,
      @ForAll("versions") String second,
      @ForAll("versions") String third) {
    if (VersionOrder.compare(first, second) <= 0 && VersionOrder.compare(second, third) <= 0) {
      Assertions.assertTrue(
          VersionOrder.compare(first, third) <= 0, first + " " + second + " " + third);
    }
  }

  @Property
  void anyStringCompares(@ForAll String first, @ForAll String second) {
    Assertions.assertEquals(
        Integer.signum(VersionOrder.compare(first, second)),
        -Integer.signum(VersionOrder.compare(second, first)));
  }

  @Provide
  Arbitrary<String> versions() {
    Arbitrary<Integer> numbers = Arbitraries.integers().between(0, 12);
    Arbitrary<String> release =
        Combinators.combine(numbers, numbers, numbers).as((a, b, c) -> a + "." + b + "." + c);
    Arbitrary<String> preRelease =
        Arbitraries.of("", "-rc.1", "-rc.2", "-alpha", "-beta.3", "-1", "-alpha.beta");
    Arbitrary<String> prefix = Arbitraries.of("", "v");
    Arbitrary<String> build = Arbitraries.of("", "+build.7");
    return Combinators.combine(prefix, release, preRelease, build)
        .as((p, r, q, b) -> p + r + q + b);
  }
}
