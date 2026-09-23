package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeProviders;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests backend discovery over the three fake providers that are registered on the test classpath.
 */
class ApplicationTest {
  @AfterEach
  void restoreProviders() {
    FakeProviders.supported = true;
    System.clearProperty(Application.BACKEND_PROPERTY);
  }

  @Test
  void listsEveryProviderOnTheClasspath() {
    List<String> names =
        Application.providers().stream().map(BackendProvider::name).sorted().toList();
    Assertions.assertEquals(
        List.of("fake-fallback", "fake-other-platform", "fake-preferred"), names);
  }

  @Test
  void picksTheHighestPrioritySupportedProvider() {
    // fake-other-platform has the highest priority of all but is unsupported here.
    Assertions.assertEquals("fake-preferred", Application.provider().orElseThrow().name());
  }

  @Test
  void theBackendPropertyOverridesThePriority() {
    System.setProperty(Application.BACKEND_PROPERTY, "fake-fallback");
    Assertions.assertEquals("fake-fallback", Application.provider().orElseThrow().name());

    System.setProperty(Application.BACKEND_PROPERTY, "fake-other-platform");
    BackendNotAvailableException failure =
        Assertions.assertThrows(BackendNotAvailableException.class, Application::create);
    Assertions.assertTrue(
        failure.getMessage().contains("fake-other-platform"), failure.getMessage());
  }

  @Test
  void createHandsTheParametersToTheProvider() {
    ApplicationParameters parameters =
        ApplicationParameters.builder().devServerUrl("http://localhost:5173").build();

    try (Application application = Application.create(parameters)) {
      Assertions.assertInstanceOf(FakeApplication.class, application);
      Assertions.assertSame(parameters, application.parameters());
      Assertions.assertTrue(application.windows().isEmpty(), "create opens no window");
    }
  }

  @Test
  void failsWithNamedListWhenNothingSupportsThisMachine() {
    FakeProviders.supported = false;

    BackendNotAvailableException failure =
        Assertions.assertThrows(BackendNotAvailableException.class, Application::create);
    Assertions.assertTrue(
        failure.getMessage().contains("fake-preferred: does not support "), failure.getMessage());
    Assertions.assertTrue(
        failure.getMessage().contains("fake-other-platform: does not support "),
        failure.getMessage());
  }
}
