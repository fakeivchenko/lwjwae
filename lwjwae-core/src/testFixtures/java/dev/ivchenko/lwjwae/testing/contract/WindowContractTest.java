package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.CloseAction;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.LocalPages;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Opens a real window and renders a real {@code http://} page that's served from this machine.
 *
 * <p>A backend module subclasses this test, so the same expectations hold on every platform. The
 * only platform-specific facts are which application and window classes the backend provides.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class WindowContractTest extends DisplayContractTest {
  /** The application class that {@link Application#create} must produce on this machine. */
  protected abstract Class<? extends Application> expectedApplicationType();

  /** The window class that the application of this backend opens. */
  protected abstract Class<? extends Window> expectedWindowType();

  @Test
  void hiddenWindowComesBackAndTheCloseActionDecidesTheUsersClose() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder()
                  .title("lwjwae :: hide")
                  .closeAction(CloseAction.HIDE)
                  .build());
      window.show();
      Assertions.assertTrue(window.isVisible());
      Assertions.assertEquals(CloseAction.HIDE, window.closeAction());

      window.hide();
      Assertions.assertFalse(window.isVisible());
      window.show();
      Assertions.assertTrue(window.isVisible(), "show() brings a hidden window back");

      Tray tray = trayOrNull(application);
      try (Tray _ = tray) {
        if (tray == null) {
          // No tray on this backend: nothing could bring a hidden window back, so HIDE closes.
          window.requestClose();
          awaitTrue(window::isClosed, "without a tray icon, the user's close closes the window");
          return;
        }

        // What the close button of the title bar does: with HIDE and a tray icon, it only hides.
        window.requestClose();
        awaitTrue(() -> !window.isVisible(), "the user's close hides the window");
        Assertions.assertFalse(window.isClosed());
        Assertions.assertEquals(
            List.of(window), application.windows(), "a hidden window stays open");

        window.show();
        window.closeAction(CloseAction.CLOSE);
        window.requestClose();
        awaitTrue(window::isClosed, "with CLOSE, the user's close closes the window");
        Assertions.assertTrue(application.windows().isEmpty());
      }
    }
  }

  /** A tray icon, the way back to a hidden window, or {@code null} on a backend without a tray. */
  private static Tray trayOrNull(Application application) {
    try {
      return application.tray(TrayIcon.builder().icon(Icons.circle(32, Color.GREEN)).build());
    } catch (UnsupportedOperationException _) {
      return null;
    }
  }

  private static void awaitTrue(BooleanSupplier condition, String message)
      throws InterruptedException {
    for (int attempt = 0; attempt < 100; attempt++) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    Assertions.fail(message);
  }

  @Test
  void opensWindowAndRendersPage() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder().title("lwjwae :: page").width(800).height(600).build();

    try (LocalPages pages = new LocalPages();
        Application application = Application.create()) {
      Assertions.assertInstanceOf(
          this.expectedApplicationType(), application, "Unexpected backend for this platform");
      Window window = application.open(parameters);
      Assertions.assertInstanceOf(
          this.expectedWindowType(), window, "Unexpected window for this platform");
      Assertions.assertSame(application, window.application());
      Assertions.assertEquals(List.of(window), application.windows());
      String url =
          pages.page(
              "/hello.html",
              "<!DOCTYPE html><html><head><title>Local"
                  + " page</title></head><body><h1>Rendered</h1></body></html>");

      final var loaded = Loads.expectFinished(window);
      window.show();
      window.navigate(url);

      LoadEvent event = loaded.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(url, event.url());
      Assertions.assertEquals(url, window.url());
      Assertions.assertEquals("Local page", Loads.eval(window, "document.title"));
      Assertions.assertEquals(
          "Rendered", Loads.eval(window, "document.querySelector('h1').textContent"));
      Screenshots.capture("window-local-page");

      Assertions.assertEquals("lwjwae :: page", window.title());
      Assertions.assertTrue(
          application.engine().matches(".+ \\d+(\\.\\d+)+"), "engine: " + application.engine());
      Assertions.assertTrue(window.width() > 0 && window.height() > 0, "Window has no size");
    }
  }

  @Test
  void windowPropertiesRoundTrip() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open(WindowParameters.builder().title("before").build());
      window.show();

      window.title("after");
      Assertions.assertEquals("after", window.title());

      Assertions.assertTrue(window.isResizable());
      window.resizable(false);
      Assertions.assertFalse(window.isResizable());

      window.devToolsEnabled(true);
      Assertions.assertTrue(window.isDevToolsEnabled());
      window.devToolsEnabled(false);
      Assertions.assertFalse(window.isDevToolsEnabled());

      window.resizable(true);
      if (!this.canResizeShownWindows()) {
        return;
      }
      window.size(640, 480);
      // Toolkits apply the request asynchronously and offer no completion signal; polling is the
      // point.
      for (int attempt = 0; attempt < 50 && window.width() != 640; attempt++) {
        // noinspection BusyWait
        Thread.sleep(50);
      }
      Assertions.assertEquals(640, window.width());
      Assertions.assertEquals(480, window.height());
    }
  }

  /**
   * Whether the platform lets a client place its window and read the position back. Wayland
   * doesn't: there, the test only checks that the calls return.
   */
  protected boolean canPlaceWindows() {
    return true;
  }

  /** Whether the platform tells a client that its window is minimized. Wayland doesn't. */
  protected boolean canTellMinimized() {
    return true;
  }

  /**
   * Whether a client may take the keyboard focus for its own window. Wayland lets only the
   * compositor decide that.
   */
  protected boolean canTakeFocus() {
    return true;
  }

  /** Whether a window that is on screen can be resized by its client. GTK 4 on Wayland can't. */
  protected boolean canResizeShownWindows() {
    return true;
  }

  /** Whether the toolkit can keep a window above the others. GTK 4 can't. */
  protected boolean canKeepOnTop() {
    return true;
  }

  /** Whether the toolkit can limit the size of a window from above. GTK 4 can't. */
  protected boolean hasMaximumSize() {
    return true;
  }

  @Test
  void sizeLimitsResizeTheWindowIntoThem() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder()
            .title("lwjwae :: limits")
            .width(400)
            .height(300)
            .minimumSize(new WindowSize(500, 350))
            .build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      Assertions.assertEquals(new WindowSize(500, 350), window.minimumSize());
      awaitTrue(
          () -> window.width() >= 500 && window.height() >= 350,
          "the minimum must grow the window");

      window.size(300, 200);
      Thread.sleep(300);
      Assertions.assertTrue(
          window.width() >= 500 && window.height() >= 350,
          "a resize below the minimum stops at it: " + window.width() + "x" + window.height());

      if (this.hasMaximumSize()) {
        window.maximumSize(600, 450);
        Assertions.assertEquals(new WindowSize(600, 450), window.maximumSize());
        window.size(900, 700);
        Thread.sleep(300);
        Assertions.assertTrue(
            window.width() <= 600 && window.height() <= 450,
            "a resize beyond the maximum stops at it: " + window.width() + "x" + window.height());
      }

      window.minimumSize(0, 0);
      window.maximumSize(0, 0);
      Assertions.assertEquals(WindowSize.NONE, window.minimumSize());
      window.size(320, 240);
      awaitTrue(() -> window.width() < 500, "without limits the window shrinks again");
    }
  }

  @Test
  void windowMaximizesMinimizesAndRestores() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: state").width(400).height(300).build());
      window.show();
      awaitTrue(window::isVisible, "the window must show");

      window.maximize();
      awaitTrue(window::isMaximized, "maximize must maximize");
      Screenshots.capture("window-maximized");
      window.restore();
      awaitTrue(() -> !window.isMaximized(), "restore must bring the size back");

      window.minimize();
      if (this.canTellMinimized()) {
        awaitTrue(window::isMinimized, "minimize must minimize");
      }
      window.focus();
      awaitTrue(() -> !window.isMinimized(), "focus must bring a minimized window back");
      if (this.canTakeFocus()) {
        awaitTrue(window::isFocused, "focus must give the window the keyboard focus");
      }
    }
  }

  @Test
  void windowEntersAndLeavesFullscreen() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder()
                  .title("lwjwae :: full screen")
                  .width(400)
                  .height(300)
                  .build());
      window.show();
      awaitTrue(window::isVisible, "the window must show");

      window.fullscreen(true);
      awaitTrue(window::isFullscreen, "full screen must start");
      awaitTrue(() -> window.width() > 400, "full screen must cover more than the window did");
      Screenshots.capture("window-fullscreen");
      window.fullscreen(false);
      awaitTrue(() -> !window.isFullscreen(), "full screen must end");
      awaitTrue(() -> window.width() < 500, "the window must come back to its size");
    }
  }

  @Test
  void alwaysOnTopRoundTrips() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder().title("lwjwae :: on top").alwaysOnTop(true).build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      Assertions.assertEquals(this.canKeepOnTop(), window.isAlwaysOnTop());
      window.alwaysOnTop(false);
      Assertions.assertFalse(window.isAlwaysOnTop());
    }
  }

  @Test
  void windowOpensWhereAskedAndMoves() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder()
            .title("lwjwae :: position")
            .width(400)
            .height(300)
            .x(120)
            .y(80)
            .build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      if (this.canPlaceWindows()) {
        awaitPosition(window, 120, 80);
        Assertions.assertEquals(new WindowPosition(120, 80), window.position());
      }
      Screenshots.capture("window-opened-at-120-80");

      window.position(200, 160);
      if (this.canPlaceWindows()) {
        awaitPosition(window, 200, 160);
        Assertions.assertEquals(new WindowPosition(200, 160), window.position());
      }
      Screenshots.capture("window-moved-to-200-160");

      window.center();
      Thread.sleep(300);
      if (this.canPlaceWindows()) {
        Assertions.assertNotEquals(200, window.position().x(), "center must move the window");
      }
      Screenshots.capture("window-centered");
    }
  }

  @Test
  void windowOpensCentered() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder().width(400).height(300).x(0).y(0).centered(true).build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      Thread.sleep(300);
      if (this.canPlaceWindows()) {
        WindowPosition position = window.position();
        Assertions.assertTrue(position.x() > 0 && position.y() > 0, "centered wins over x and y");
      }
      Screenshots.capture("window-opened-centered");
    }
  }

  /**
   * Waits for the window manager to apply a move. Window managers apply a request asynchronously,
   * and some shift the frame by the size of its decorations, so the check is within a margin.
   */
  private static void awaitPosition(Window window, int x, int y) throws InterruptedException {
    for (int attempt = 0; attempt < 50 && !near(window, x, y); attempt++) {
      // noinspection BusyWait
      Thread.sleep(50);
    }
  }

  private static boolean near(Window window, int x, int y) {
    WindowPosition position = window.position();
    return Math.abs(position.x() - x) <= 2 && Math.abs(position.y() - y) <= 2;
  }

  @Test
  void htmlReplacesThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.html("<!DOCTYPE html><html><head><title>Inline</title></head><body>hi</body></html>");
      loaded.get(30, TimeUnit.SECONDS);

      Assertions.assertEquals("Inline", Loads.eval(window, "document.title"));
      Assertions.assertEquals("hi", Loads.eval(window, "document.body.textContent"));
    }
  }
}
