package dev.ivchenko.lwjwae;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WindowParametersTest {
  @Test
  void defaultsFillEveryComponent() {
    WindowParameters parameters = WindowParameters.createDefault();
    Assertions.assertEquals(CloseAction.CLOSE, parameters.closeAction());
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.size().width());
    Assertions.assertEquals(768, parameters.size().height());
    Assertions.assertFalse(parameters.hasPosition());
    Assertions.assertFalse(parameters.centered());
    Assertions.assertNull(parameters.url());
    Assertions.assertNull(parameters.resource());
  }

  @Test
  void nonPositiveSizesAndBlankTextFallBackToDefaults() {
    WindowParameters parameters =
        WindowParameters.builder().title("  ").size(0, -5).url(" ").resource("").build();
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.size().width());
    Assertions.assertEquals(768, parameters.size().height());
    Assertions.assertNull(parameters.url());
    Assertions.assertNull(parameters.resource());
  }

  @Test
  void positionIsTheWindowManagersUnlessGiven() {
    Assertions.assertFalse(WindowParameters.createDefault().hasPosition());
    WindowParameters placed = WindowParameters.builder().position(10, 20).build();
    Assertions.assertTrue(placed.hasPosition());
    Assertions.assertEquals(new WindowPosition(10, 20), placed.position());
  }

  @Test
  void dimensionThatIsNotPositiveTakesTheDefault() {
    Assertions.assertEquals(
        new WindowSize(640, 768), WindowParameters.builder().size(640, 0).build().size());
    Assertions.assertEquals(
        new WindowSize(1024, 480),
        WindowParameters.builder().size(new WindowSize(-1, 480)).build().size());
  }

  @Test
  void theBuilderTakesTheLimitsAsModelsOrNumbers() {
    WindowParameters parameters =
        WindowParameters.builder()
            .minimumSize(320, 240)
            .maximumSize(new WindowSize(0, 900))
            .build();
    Assertions.assertEquals(new WindowSize(320, 240), parameters.minimumSize());
    Assertions.assertEquals(new WindowSize(0, 900), parameters.maximumSize());
  }

  @Test
  void toBuilderKeepsUnchangedComponents() {
    WindowParameters base = WindowParameters.builder().title("Docs").size(640, 480).build();
    WindowParameters changed = base.toBuilder().position(5, 6).build();
    Assertions.assertEquals("Docs", changed.title());
    Assertions.assertEquals(new WindowSize(640, 480), changed.size());
    Assertions.assertEquals(new WindowPosition(5, 6), changed.position());
  }
}
