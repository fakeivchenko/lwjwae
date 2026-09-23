package dev.ivchenko.lwjwae;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WindowParametersTest {
  @Test
  void defaultsFillEveryComponent() {
    WindowParameters parameters = WindowParameters.createDefault();
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.width());
    Assertions.assertEquals(768, parameters.height());
    Assertions.assertFalse(parameters.hasPosition());
    Assertions.assertFalse(parameters.centered());
    Assertions.assertNull(parameters.url());
    Assertions.assertNull(parameters.resource());
  }

  @Test
  void nonPositiveSizesAndBlankTextFallBackToDefaults() {
    WindowParameters parameters =
        WindowParameters.builder().title("  ").width(0).height(-5).url(" ").resource("").build();
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.width());
    Assertions.assertEquals(768, parameters.height());
    Assertions.assertNull(parameters.url());
    Assertions.assertNull(parameters.resource());
  }

  @Test
  void positionNeedsBothCoordinates() {
    Assertions.assertFalse(WindowParameters.builder().x(10).build().hasPosition());
    Assertions.assertNull(WindowParameters.builder().x(10).build().x());
    WindowParameters placed = WindowParameters.builder().x(10).y(20).build();
    Assertions.assertTrue(placed.hasPosition());
    Assertions.assertEquals(10, placed.x());
    Assertions.assertEquals(20, placed.y());
  }

  @Test
  void toBuilderKeepsUnchangedComponents() {
    WindowParameters base = WindowParameters.builder().title("Docs").width(640).build();
    WindowParameters changed = base.toBuilder().height(480).build();
    Assertions.assertEquals("Docs", changed.title());
    Assertions.assertEquals(640, changed.width());
    Assertions.assertEquals(480, changed.height());
  }
}
