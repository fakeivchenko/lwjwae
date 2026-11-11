package dev.ivchenko.lwjwae.util;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ByteRangeTest {
  @Test
  void parsesEachFormOfOneRange() {
    Assertions.assertEquals(new ByteRange(0, 99), ByteRange.parse("bytes=0-99", 1000));
    Assertions.assertEquals(new ByteRange(500, 999), ByteRange.parse("bytes=500-", 1000));
    Assertions.assertEquals(new ByteRange(900, 999), ByteRange.parse("bytes=-100", 1000));
    Assertions.assertEquals(new ByteRange(10, 20), ByteRange.parse("Bytes=10-20", 1000));
  }

  @Test
  void cutsTheEndToTheResource() {
    Assertions.assertEquals(new ByteRange(900, 999), ByteRange.parse("bytes=900-5000", 1000));
    Assertions.assertEquals(new ByteRange(0, 999), ByteRange.parse("bytes=-5000", 1000));
    Assertions.assertEquals(
        new ByteRange(0, 999), ByteRange.parse("bytes=0-99999999999999999999", 1000));
  }

  @Test
  void rangePastTheEndIsNotSatisfiable() {
    Assertions.assertFalse(ByteRange.parse("bytes=1000-", 1000).isSatisfiable());
    Assertions.assertFalse(ByteRange.parse("bytes=-0", 1000).isSatisfiable());
    Assertions.assertFalse(ByteRange.parse("bytes=0-", 0).isSatisfiable());
    Assertions.assertFalse(ByteRange.parse("bytes=99999999999999999999-", 1000).isSatisfiable());
    Assertions.assertEquals(
        "bytes */1000", ByteRange.parse("bytes=1000-", 1000).contentRange(1000));
  }

  @Test
  void headerThatAsksForNoSingleRangeGetsTheWholeResource() {
    for (String header :
        new String[] {
          null, "", "bytes=", "bytes=-", "items=0-9", "bytes=9-0", "bytes=0-9,20-29", "bytes=a-9"
        }) {
      Assertions.assertNull(ByteRange.parse(header, 1000), header);
    }
  }

  @Test
  void describesTheBytesItCovers() {
    ByteRange range = ByteRange.parse("bytes=100-199", 1000);
    Assertions.assertEquals(100, range.length());
    Assertions.assertEquals("bytes 100-199/1000", range.contentRange(1000));
  }

  @Property
  void satisfiableRangeStaysInsideTheResource(
      @ForAll @LongRange(min = 0, max = 1 << 20) long first,
      @ForAll @LongRange(min = 0, max = 1 << 20) long last,
      @ForAll @LongRange(min = 0, max = 1 << 20) long size) {
    ByteRange range = ByteRange.parse("bytes=%d-%d".formatted(first, last), size);
    if (range != null && range.isSatisfiable()) {
      Assertions.assertTrue(range.first() >= 0 && range.last() < size);
      Assertions.assertTrue(range.length() > 0 && range.length() <= size);
    }
  }
}
