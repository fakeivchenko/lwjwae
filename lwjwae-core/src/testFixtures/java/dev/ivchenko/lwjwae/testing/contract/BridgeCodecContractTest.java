package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.Shape;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * What every {@link BridgeCodec} must do on the Java side, whatever its format.
 *
 * <p>A codec module subclasses this test and names its codec. The page half can't be run here,
 * because the JVM has no JavaScript engine; {@link BridgeContractTest} covers it against a real
 * engine, with the codec registered on the test classpath of the module.
 */
public abstract class BridgeCodecContractTest {
  /** Returns the codec class that the module registers in {@code META-INF/services}. */
  protected abstract Class<? extends BridgeCodec> codecType();

  /**
   * Returns a fresh codec with its defaults, the one that {@link BridgeCodec#discover()} would
   * build.
   */
  protected abstract BridgeCodec codec();

  @Test
  void isDiscoveredAsTheRegisteredService() {
    BridgeCodec discovered = BridgeCodec.discover().orElseThrow();
    Assertions.assertInstanceOf(this.codecType(), discovered);
  }

  @Test
  void roundTripsFlatRecord() {
    BridgeCodec codec = this.codec();
    Point point = new Point(1, -2);
    Assertions.assertEquals(point, codec.decode(codec.encode(point), Point.class));
  }

  @Test
  void roundTripsNestingCollectionsAndTextThatNeedsEscaping() {
    BridgeCodec codec = this.codec();
    Shape shape = new Shape("a \"b\" \\ c\nпривет 世界", List.of(new Point(0, 0), new Point(10, 20)));
    Assertions.assertEquals(shape, codec.decode(codec.encode(shape), Shape.class));
  }

  @Test
  void rejectsTextThatIsNotTheType() {
    BridgeCodec codec = this.codec();
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> codec.decode("nope", Point.class));
  }

  @Test
  void shipsPageHalfWithBothOperations() {
    String page = this.codec().pageScript();
    Assertions.assertFalse(page.isBlank(), "pageScript() must return a JavaScript expression");
    Assertions.assertTrue(page.contains("encode"), page);
    Assertions.assertTrue(page.contains("decode"), page);
  }
}
