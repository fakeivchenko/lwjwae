package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.LocalPages;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Opens a real window and renders a real {@code http://} page that's served from this machine.
 *
 * <p>A backend module subclasses this test, so the same expectations hold on every platform. The
 * only platform-specific fact is which backend class {@link Application} is expected to select.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class WindowContractTest extends DisplayContractTest {
  /**
   * Returns the backend that {@link Application#create} must select on the machine that runs the
   * tests.
   */
  protected abstract Class<? extends ApplicationBackend> expectedBackendType();

  @Test
  void opensWindowAndRendersPage() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().title("lwjwae :: page").width(800).height(600).build();

    try (LocalPages pages = new LocalPages();
        ApplicationBackend backend = Application.create(parameters)) {
      Assertions.assertInstanceOf(
          this.expectedBackendType(), backend, "Unexpected backend for this platform");
      String url =
          pages.page(
              "/hello.html",
              "<!DOCTYPE html><html><head><title>Local"
                  + " page</title></head><body><h1>Rendered</h1></body></html>");

      final var loaded = Loads.expectFinished(backend);
      backend.show();
      backend.navigate(url);

      LoadEvent event = loaded.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(url, event.url());
      Assertions.assertEquals(url, backend.url());
      Assertions.assertEquals("Local page", Loads.eval(backend, "document.title"));
      Assertions.assertEquals(
          "Rendered", Loads.eval(backend, "document.querySelector('h1').textContent"));
      Screenshots.capture("window-local-page");

      Assertions.assertEquals("lwjwae :: page", backend.title());
      Assertions.assertTrue(
          backend.engine().matches(".+ \\d+(\\.\\d+)+"), "engine: " + backend.engine());
      Assertions.assertTrue(backend.width() > 0 && backend.height() > 0, "Window has no size");
    }
  }

  @Test
  void windowPropertiesRoundTrip() throws Exception {
    try (ApplicationBackend backend =
        Application.create(ApplicationParameters.builder().title("before").build())) {
      backend.show();

      backend.title("after");
      Assertions.assertEquals("after", backend.title());

      Assertions.assertTrue(backend.isResizable());
      backend.resizable(false);
      Assertions.assertFalse(backend.isResizable());

      backend.devToolsEnabled(true);
      Assertions.assertTrue(backend.isDevToolsEnabled());
      backend.devToolsEnabled(false);
      Assertions.assertFalse(backend.isDevToolsEnabled());

      backend.resizable(true);
      backend.size(640, 480);
      // Toolkits apply the request asynchronously and offer no completion signal; polling is the
      // point.
      for (int attempt = 0; attempt < 50 && backend.width() != 640; attempt++) {
        // noinspection BusyWait
        Thread.sleep(50);
      }
      Assertions.assertEquals(640, backend.width());
      Assertions.assertEquals(480, backend.height());
    }
  }

  @Test
  void htmlReplacesThePage() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.html("<!DOCTYPE html><html><head><title>Inline</title></head><body>hi</body></html>");
      loaded.get(30, TimeUnit.SECONDS);

      Assertions.assertEquals("Inline", Loads.eval(backend, "document.title"));
      Assertions.assertEquals("hi", Loads.eval(backend, "document.body.textContent"));
    }
  }
}
