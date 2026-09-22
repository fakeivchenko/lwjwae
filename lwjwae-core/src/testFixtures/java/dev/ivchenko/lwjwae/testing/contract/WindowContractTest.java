package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.WindowPosition;
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

  /**
   * Whether the platform lets a client place its window and read the position back. Wayland
   * doesn't: there, the test only checks that the calls return.
   */
  protected boolean canPlaceWindows() {
    return true;
  }

  @Test
  void windowOpensWhereAskedAndMoves() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder()
            .title("lwjwae :: position")
            .width(400)
            .height(300)
            .x(120)
            .y(80)
            .build();
    try (ApplicationBackend backend = Application.create(parameters)) {
      backend.show();
      if (this.canPlaceWindows()) {
        awaitPosition(backend, 120, 80);
        Assertions.assertEquals(new WindowPosition(120, 80), backend.position());
      }
      Screenshots.capture("window-opened-at-120-80");

      backend.position(200, 160);
      if (this.canPlaceWindows()) {
        awaitPosition(backend, 200, 160);
        Assertions.assertEquals(new WindowPosition(200, 160), backend.position());
      }
      Screenshots.capture("window-moved-to-200-160");

      backend.center();
      Thread.sleep(300);
      if (this.canPlaceWindows()) {
        Assertions.assertNotEquals(200, backend.position().x(), "center must move the window");
      }
      Screenshots.capture("window-centered");
    }
  }

  @Test
  void windowOpensCentered() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().width(400).height(300).x(0).y(0).centered(true).build();
    try (ApplicationBackend backend = Application.create(parameters)) {
      backend.show();
      Thread.sleep(300);
      if (this.canPlaceWindows()) {
        WindowPosition position = backend.position();
        Assertions.assertTrue(position.x() > 0 && position.y() > 0, "centered wins over x and y");
      }
      Screenshots.capture("window-opened-centered");
    }
  }

  /**
   * Waits for the window manager to apply a move. Window managers apply a request asynchronously,
   * and some shift the frame by the size of its decorations, so the check is within a margin.
   */
  private static void awaitPosition(ApplicationBackend backend, int x, int y)
      throws InterruptedException {
    for (int attempt = 0; attempt < 50 && !near(backend, x, y); attempt++) {
      // noinspection BusyWait
      Thread.sleep(50);
    }
  }

  private static boolean near(ApplicationBackend backend, int x, int y) {
    WindowPosition position = backend.position();
    return Math.abs(position.x() - x) <= 2 && Math.abs(position.y() - y) <= 2;
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
