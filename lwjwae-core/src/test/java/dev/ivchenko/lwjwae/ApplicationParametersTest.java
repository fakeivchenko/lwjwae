package dev.ivchenko.lwjwae;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ApplicationParametersTest {
  @AfterEach
  void clearProperty() {
    System.clearProperty(ApplicationParameters.DEV_SERVER_URL_PROPERTY);
  }

  @Test
  void defaultsFillEveryComponent() {
    ApplicationParameters parameters = ApplicationParameters.createDefault();
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.width());
    Assertions.assertEquals(768, parameters.height());
    Assertions.assertNull(parameters.url());
    Assertions.assertFalse(parameters.isDevelopment());
    Assertions.assertNull(parameters.codec(), "no codec module on this classpath");
  }

  @Test
  void nonPositiveSizesAndBlankTitleFallBackToDefaults() {
    ApplicationParameters parameters =
        ApplicationParameters.builder().title("  ").width(0).height(-5).build();
    Assertions.assertEquals("Application", parameters.title());
    Assertions.assertEquals(1024, parameters.width());
    Assertions.assertEquals(768, parameters.height());
  }

  @Test
  void blankDevServerUrlMeansNone() {
    Assertions.assertFalse(
        ApplicationParameters.builder().devServerUrl("   ").build().isDevelopment());
  }

  @Test
  void devServerUrlComesFromTheSystemPropertyWhenNotSet() {
    System.setProperty(ApplicationParameters.DEV_SERVER_URL_PROPERTY, "http://localhost:5173");

    Assertions.assertEquals(
        "http://localhost:5173", ApplicationParameters.createDefault().devServerUrl());
    Assertions.assertEquals(
        "http://localhost:9999",
        ApplicationParameters.builder()
            .devServerUrl("http://localhost:9999")
            .build()
            .devServerUrl(),
        "an explicit value wins over the property");
  }

  @Test
  void toBuilderKeepsUnchangedComponents() {
    ApplicationParameters base = ApplicationParameters.builder().title("Docs").width(640).build();
    ApplicationParameters changed = base.toBuilder().height(480).build();
    Assertions.assertEquals("Docs", changed.title());
    Assertions.assertEquals(640, changed.width());
    Assertions.assertEquals(480, changed.height());
  }
}
