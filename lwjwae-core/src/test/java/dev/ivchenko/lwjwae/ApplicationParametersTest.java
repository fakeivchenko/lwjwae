package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.testing.PointCodec;
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
    Assertions.assertFalse(parameters.isDevelopment());
    Assertions.assertNull(parameters.codec(), "no codec module on this classpath");
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
    ApplicationParameters base =
        ApplicationParameters.builder().devServerUrl("http://localhost:5173").build();
    ApplicationParameters changed = base.toBuilder().codec(new PointCodec()).build();
    Assertions.assertEquals("http://localhost:5173", changed.devServerUrl());
    Assertions.assertNotNull(changed.codec());
  }
}
