package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.testing.FakeApplicationBackend;
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
        Application.providers().stream().map(ApplicationBackendProvider::name).sorted().toList();
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
  void createHandsTheParametersToTheProviderAndNavigates() {
    ApplicationParameters parameters =
        ApplicationParameters.builder().title("Docs").url("https://example.com").build();

    try (ApplicationBackend backend = Application.create(parameters)) {
      FakeApplicationBackend fake =
          Assertions.assertInstanceOf(FakeApplicationBackend.class, backend);
      Assertions.assertEquals("Docs", fake.title());
      Assertions.assertEquals(List.of("https://example.com"), fake.navigated);
    }
  }

  @Test
  void createWithoutUrlDoesNotNavigate() {
    try (ApplicationBackend backend = Application.create()) {
      Assertions.assertTrue(((FakeApplicationBackend) backend).navigated.isEmpty());
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
