package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.testing.Point;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The extra guarantees of a codec whose format is JSON: the exact text of simple values, so that
 * two JSON codecs are interchangeable, and a page half built on {@code JSON.stringify} and {@code
 * JSON.parse}.
 */
public abstract class JsonBridgeCodecContractTest extends BridgeCodecContractTest {
  @Test
  void writesCanonicalJsonForRecord() {
    Assertions.assertEquals("{\"x\":1,\"y\":2}", this.codec().encode(new Point(1, 2)));
  }

  @Test
  void readsJsonWithWhitespaceAndAnyFieldOrder() {
    Assertions.assertEquals(
        new Point(3, 4), this.codec().decode(" { \"y\" : 4 , \"x\" : 3 } ", Point.class));
  }

  @Test
  void encodesNullAsTheJsonLiteral() {
    Assertions.assertEquals("null", this.codec().encode(null));
  }

  @Test
  void pageHalfUsesTheBuiltInJsonObject() {
    String page = this.codec().pageScript();
    Assertions.assertTrue(page.contains("JSON.stringify"), page);
    Assertions.assertTrue(page.contains("JSON.parse"), page);
  }
}
