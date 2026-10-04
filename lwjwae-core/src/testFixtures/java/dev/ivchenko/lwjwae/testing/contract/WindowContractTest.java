package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.CloseAction;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageButtons;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.MessageLevel;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.FileDropEvent;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.event.WindowEventType;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.permission.PermissionDecision;
import dev.ivchenko.lwjwae.permission.PermissionKind;
import dev.ivchenko.lwjwae.permission.PermissionRequest;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.LocalPages;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.UserAgentUtil;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

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

      Tray tray = WindowContractTest.trayOrNull(application);
      try (Tray _ = tray) {
        if (tray == null) {
          // No tray on this backend: nothing could bring a hidden window back, so HIDE closes.
          window.requestClose();
          WindowContractTest.awaitTrue(
              window::isClosed, "without a tray icon, the user's close closes the window");
          return;
        }

        // What the close button of the title bar does: with HIDE and a tray icon, it only hides.
        window.requestClose();
        WindowContractTest.awaitTrue(
            () -> !window.isVisible(), "the user's close hides the window");
        Assertions.assertFalse(window.isClosed());
        Assertions.assertEquals(
            List.of(window), application.windows(), "a hidden window stays open");

        window.show();
        window.closeAction(CloseAction.CLOSE);
        window.requestClose();
        WindowContractTest.awaitTrue(
            window::isClosed, "with CLOSE, the user's close closes the window");
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
        WindowParameters.builder().title("lwjwae :: page").size(800, 600).build();

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
      Assertions.assertTrue(
          window.size().width() > 0 && window.size().height() > 0, "Window has no size");
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
      for (int attempt = 0; attempt < 50 && window.size().width() != 640; attempt++) {
        // noinspection BusyWait
        Thread.sleep(50);
      }
      Assertions.assertEquals(640, window.size().width());
      Assertions.assertEquals(480, window.size().height());
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

  /**
   * Whether a test may use the clipboard without a person at the keyboard. Wayland gives it only to
   * the client that the user gave the focus to with an input of theirs.
   */
  protected boolean canUseClipboardUnattended() {
    return true;
  }

  /** Whether a window that is on screen can be resized by its client. GTK 4 can't. */
  protected boolean canResizeShownWindows() {
    return true;
  }

  /**
   * Whether a test may bind a global shortcut without a person at the keyboard. The portal of
   * Wayland asks the user to confirm it.
   */
  protected boolean canBindShortcutsUnattended() {
    return true;
  }

  /**
   * Presses {@code shortcut} the way the keyboard would, for every application, and lets it go.
   *
   * @return Whether the backend test can press keys; without that, the test only binds and gives
   *     back.
   */
  protected boolean pressKeys(Shortcut shortcut) throws Exception {
    return false;
  }

  /**
   * Switches the desktop to {@code theme}, the way the user does, and returns once the switch is
   * made; the test then waits for the library to hear of it. A backend that can't switch the theme
   * of the machine it runs on leaves the default, and the test is skipped.
   *
   * @return Whether the desktop was switched.
   */
  protected boolean switchSystemTheme(SystemTheme theme) throws Exception {
    return false;
  }

  /**
   * Drags {@code files} from outside the process onto the window titled {@code title}, the way a
   * user drags them from a file manager, and returns once they are dropped.
   *
   * @return Whether the files were dropped; a backend that can't leaves the default, and the test
   *     is skipped.
   */
  protected boolean dropFiles(String title, List<Path> files) throws Exception {
    return false;
  }

  /** Whether the engine of the web view takes {@code prefers-color-scheme} from the desktop. */
  protected boolean engineFollowsTheDesktop() {
    return false;
  }

  /** Whether the menu bar is part of the window, and takes room from the page. Not on macOS. */
  protected boolean hasMenuBarInWindow() {
    return true;
  }

  /**
   * Picks the first entry of the menu that is open over the window, from the keyboard.
   *
   * @return Whether the backend test can press keys; without that, the test only opens menus and
   *     closes them from Java.
   */
  protected boolean pickFirstEntryOfOpenMenu() throws Exception {
    return false;
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
            .size(400, 300)
            .minimumSize(new WindowSize(500, 350))
            .build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      Assertions.assertEquals(new WindowSize(500, 350), window.minimumSize());
      WindowContractTest.awaitTrue(
          () -> window.size().width() >= 500 && window.size().height() >= 350,
          "the minimum must grow the window");

      window.size(300, 200);
      Thread.sleep(300);
      Assertions.assertTrue(
          window.size().width() >= 500 && window.size().height() >= 350,
          "a resize below the minimum stops at it: "
              + window.size().width()
              + "x"
              + window.size().height());

      if (this.hasMaximumSize()) {
        window.maximumSize(600, 450);
        Assertions.assertEquals(new WindowSize(600, 450), window.maximumSize());
        window.size(900, 700);
        Thread.sleep(300);
        Assertions.assertTrue(
            window.size().width() <= 600 && window.size().height() <= 450,
            "a resize beyond the maximum stops at it: "
                + window.size().width()
                + "x"
                + window.size().height());
      }

      window.minimumSize(0, 0);
      window.maximumSize(0, 0);
      Assertions.assertEquals(WindowSize.NONE, window.minimumSize());
      window.size(320, 240);
      WindowContractTest.awaitTrue(
          () -> window.size().width() < 500, "without limits the window shrinks again");
    }
  }

  @Test
  void windowMaximizesMinimizesAndRestores() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: state").size(400, 300).build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      // Whether this process may take the focus at all: Windows refuses it to one in the
      // background, and the test runner may be one.
      Thread.sleep(300);
      final boolean mayTakeFocus = this.canTakeFocus() && window.isFocused();

      window.maximize();
      WindowContractTest.awaitTrue(window::isMaximized, "maximize must maximize");
      Screenshots.capture("window-maximized");
      window.restore();
      WindowContractTest.awaitTrue(() -> !window.isMaximized(), "restore must bring the size back");

      window.minimize();
      if (this.canTellMinimized()) {
        WindowContractTest.awaitTrue(window::isMinimized, "minimize must minimize");
      }
      window.focus();
      WindowContractTest.awaitTrue(
          () -> !window.isMinimized(), "focus must bring a minimized window back");
      if (mayTakeFocus) {
        WindowContractTest.awaitTrue(
            window::isFocused, "focus must give the window the keyboard focus");
      }
    }
  }

  @Test
  void windowEntersAndLeavesFullscreen() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: full screen").size(400, 300).build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");

      window.fullscreen(true);
      WindowContractTest.awaitTrue(window::isFullscreen, "full screen must start");
      WindowContractTest.awaitTrue(
          () -> window.size().width() > 400, "full screen must cover more than the window did");
      Screenshots.capture("window-fullscreen");
      window.fullscreen(false);
      WindowContractTest.awaitTrue(() -> !window.isFullscreen(), "full screen must end");
      WindowContractTest.awaitTrue(
          () -> window.size().width() < 500, "the window must come back to its size");
    }
  }

  @Test
  void windowEventsReachJavaAndThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: events").size(400, 300).build());
      BlockingQueue<WindowEvent> heard = new LinkedBlockingQueue<>();
      window.onWindowEvent(heard::add);
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      Loads.eval(
          window,
          "window.__windowEvents = []; lwjwae.window.listen((event) =>"
              + " window.__windowEvents.push(event.type)); undefined;");

      window.maximize();
      WindowContractTest.awaitEvent(heard, WindowEventType.MAXIMIZED);
      window.restore();
      WindowContractTest.awaitEvent(heard, WindowEventType.UNMAXIMIZED);
      if (this.canResizeShownWindows()) {
        window.size(500, 350);
        WindowEvent resized = WindowContractTest.awaitEvent(heard, WindowEventType.RESIZED);
        Assertions.assertSame(window, resized.window());
      }
      String page = "";
      for (int attempt = 0; attempt < 50 && !page.contains("unmaximized"); attempt++) {
        Thread.sleep(100);
        page = Loads.eval(window, "window.__windowEvents.join()");
      }
      List<String> types = List.of(page.split(","));
      Assertions.assertTrue(
          types.indexOf("maximized") >= 0
              && types.indexOf("maximized") < types.indexOf("unmaximized"),
          "the page hears: " + page);
    }
  }

  @Test
  void windowOpensTheWayItClosed(@TempDir Path directory) throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().dataDirectory(directory).build();
    WindowParameters remembered =
        WindowParameters.builder()
            .title("lwjwae :: remembered")
            .size(400, 300)
            .stateKey("main")
            .build();
    try (Application application = Application.create(parameters)) {
      Window window = application.open(remembered);
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      if (this.canResizeShownWindows()) {
        window.size(520, 360);
        WindowContractTest.awaitTrue(() -> window.size().width() == 520, "the window must resize");
      }
      window.maximize();
      WindowContractTest.awaitTrue(window::isMaximized, "maximize must maximize");
      // The events that the state follows arrive on their own thread.
      Thread.sleep(300);
    }

    try (Application application = Application.create(parameters)) {
      Window window = application.open(remembered);
      window.show();
      WindowContractTest.awaitTrue(
          window::isMaximized, "a window that closed maximized must open maximized");
      window.restore();
      if (this.canResizeShownWindows()) {
        WindowContractTest.awaitTrue(
            () -> window.size().width() == 520 && window.size().height() == 360,
            "restore must bring back the size from before: " + window.size().width());
      }
    }
  }

  @Test
  void windowWithoutTitleBarIsControlledFromThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder()
                  .title("lwjwae :: frameless")
                  .size(400, 300)
                  .decorated(false)
                  .build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Screenshots.capture("window-frameless");

      Loads.eval(
          window,
          "lwjwae.window.state().then((state) => window.__state = JSON.stringify(state));"
              + " undefined;");
      String state = Loads.awaitValue(window, "window.__state");
      Assertions.assertTrue(state.contains("\"maximized\":false"), state);
      Assertions.assertTrue(state.contains("\"resizable\":true"), state);

      Loads.eval(window, "lwjwae.window.maximize(); undefined;");
      WindowContractTest.awaitTrue(window::isMaximized, "the page must maximize its window");
      Loads.eval(window, "lwjwae.window.toggleMaximize(); undefined;");
      WindowContractTest.awaitTrue(
          () -> !window.isMaximized(), "the page must bring its window back");

      // With no button down, a move has nothing to follow and must leave the window as it is.
      Loads.eval(
          window,
          "lwjwae.window.startMove().then(() => window.__moved = 'done', (error) =>"
              + " window.__moved = String(error)); undefined;");
      Assertions.assertEquals("done", Loads.awaitValue(window, "window.__moved"));
      Assertions.assertFalse(window.isClosed());
      Assertions.assertFalse(window.isMaximized());
    }
  }

  @Test
  void userAgentNamesTheApplicationAndLwjwae() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().name("lwjwae contract").build();
    try (Application application = Application.create(parameters)) {
      Window window = application.open(WindowParameters.of("lwjwae :: user agent", 400, 300));
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      loaded.get(30, TimeUnit.SECONDS);
      String userAgent = Loads.awaitValue(window, "navigator.userAgent");
      Assertions.assertTrue(userAgent.startsWith("Mozilla/5.0 "), userAgent);
      Assertions.assertTrue(
          userAgent.endsWith(" lwjwae-contract lwjwae/" + UserAgentUtil.version()), userAgent);
    }
  }

  @Test
  void resourcesOfAnyNameAndSizeLoadAndTheCodeStaysOut() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(WindowParameters.builder().title("lwjwae :: resources").build());
      for (String name : List.of("with space", "кириллица")) {
        final var loaded = Loads.expectFinished(window);
        window.loadResource("test-app/" + name + ".html");
        loaded.get(30, TimeUnit.SECONDS);
        Assertions.assertEquals(name, Loads.awaitValue(window, "document.title"));
      }

      Loads.eval(
          window,
          "fetch('large.txt').then((response) => response.text()).then((text) =>"
              + " window.__large = text.length + ' ' + text.endsWith('end\\n')); undefined;");
      Assertions.assertEquals(
          (62 * 20000 + 4) + " true", Loads.awaitValue(window, "window.__large"));

      Loads.eval(
          window,
          "fetch('/dev/ivchenko/lwjwae/Application.class').then((response) => window.__code ="
              + " response.ok ? 'served' : 'refused', () => window.__code = 'refused');"
              + " undefined;");
      Assertions.assertEquals("refused", Loads.awaitValue(window, "window.__code"));
    }
  }

  @Test
  void transparentWindowShowsThePageOverTheDesktop() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder()
                  .title("lwjwae :: transparent")
                  .size(400, 300)
                  .decorated(false)
                  .transparent(true)
                  .build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/transparent.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Screenshots.capture("window-transparent");

      Assertions.assertEquals("transparent", Loads.awaitValue(window, "document.title"));
      Assertions.assertEquals(
          "rgba(0, 0, 0, 0)",
          Loads.awaitValue(window, "getComputedStyle(document.body).backgroundColor"));

      // Without a frame, the client area is the whole window, maximized or not.
      window.maximize();
      WindowContractTest.awaitTrue(window::isMaximized, "a window without a frame maximizes");
      window.restore();
      WindowContractTest.awaitTrue(() -> !window.isMaximized(), "and comes back");
      if (this.canResizeShownWindows()) {
        WindowContractTest.awaitTrue(
            () -> window.size().equals(new WindowSize(400, 300)),
            "the size of a window without a frame is its page: " + window.size());
      }
    }
  }

  @Test
  void windowWithoutButtonsKeepsWhatJavaAsksFor() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder()
            .title("lwjwae :: buttons")
            .size(400, 300)
            .closable(false)
            .minimizable(false)
            .maximizable(false)
            .build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Screenshots.capture("window-without-buttons");
      Assertions.assertEquals("lwjwae :: buttons", window.title());

      window.requestClose();
      Thread.sleep(500);
      Assertions.assertFalse(window.isClosed(), "a window that isn't closable refuses the user");

      window.maximize();
      WindowContractTest.awaitTrue(
          window::isMaximized, "Java maximizes a window without a maximize button");
      window.restore();
      WindowContractTest.awaitTrue(() -> !window.isMaximized(), "and brings it back");

      window.close();
      Assertions.assertTrue(window.isClosed(), "Java closes a window without a close button");
    }
  }

  @Test
  void pageAsksForTheCameraAndTheHandlerDecides() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(WindowParameters.builder().title("lwjwae :: permissions").build());
      BlockingQueue<PermissionRequest> asked = new LinkedBlockingQueue<>();
      AtomicReference<PermissionDecision> answer = new AtomicReference<>(PermissionDecision.DENY);
      window.permissionHandler(
          request -> {
            asked.add(request);
            return answer.get();
          });
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);

      String ask =
          "navigator.mediaDevices.getUserMedia({ video: true })"
              + ".then(() => window.__media = 'granted', e => window.__media = e.name); undefined;";
      Loads.eval(window, ask);
      PermissionRequest first = asked.poll(10, TimeUnit.SECONDS);
      if (first == null) {
        String failure = Loads.awaitValue(window, "window.__media");
        Assumptions.assumeFalse(
            failure.equals("NotFoundError") || failure.equals("OverconstrainedError"),
            "no capture device here, so the engine fails before it asks: " + failure);
        Assertions.fail("the handler is never asked; the page saw " + failure);
      }
      Assertions.assertEquals(PermissionKind.CAMERA, first.kind());
      Assertions.assertEquals(
          "NotAllowedError",
          Loads.awaitValue(window, "window.__media"),
          "a denial fails the call as a user's no does");

      // No device behind the permission here, so a grant ends in "no device", never in a denial.
      answer.set(PermissionDecision.GRANT);
      Loads.eval(window, "window.__media = undefined; " + ask);
      Assertions.assertEquals(PermissionKind.CAMERA, asked.poll(10, TimeUnit.SECONDS).kind());
      Assertions.assertNotEquals(
          "NotAllowedError", Loads.awaitValue(window, "window.__media"), "a grant passes");
    }
  }

  @Test
  void theThemeOfTheDesktopReachesJavaAndThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open(WindowParameters.builder().title("lwjwae :: theme").build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);

      SystemTheme original = application.theme();
      Loads.eval(
          window,
          "lwjwae.theme.current().then((t) => window.__current = t);"
              + " lwjwae.theme.listen((t) => window.__heard = t); undefined;");
      Assertions.assertEquals(
          original.pageName(),
          Loads.awaitValue(window, "window.__current"),
          "the page reads the theme that Java reads");

      SystemTheme other = original == SystemTheme.DARK ? SystemTheme.LIGHT : SystemTheme.DARK;
      BlockingQueue<SystemTheme> heard = new LinkedBlockingQueue<>();
      application.onThemeChange(heard::add);
      boolean switched = this.switchSystemTheme(other);
      try {
        Assumptions.assumeTrue(switched, "this machine can't switch its theme in a test");
        Assertions.assertEquals(other, heard.poll(10, TimeUnit.SECONDS), "Java hears the switch");
        Assertions.assertEquals(other, application.theme());
        Assertions.assertEquals(
            other.pageName(), Loads.awaitValue(window, "window.__heard"), "and so does the page");
        Loads.eval(window, "lwjwae.theme.current().then((t) => window.__after = t); undefined;");
        Assertions.assertEquals(other.pageName(), Loads.awaitValue(window, "window.__after"));
        if (this.engineFollowsTheDesktop()) {
          WindowContractTest.awaitTrue(
              () -> {
                try {
                  return Loads.eval(
                          window, "String(matchMedia('(prefers-color-scheme: dark)').matches)")
                      .equals(String.valueOf(other == SystemTheme.DARK));
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              },
              "the engine matches the desktop");
        }
      } finally {
        if (switched) {
          this.switchSystemTheme(original);
        }
      }
    }
  }

  @Test
  void windowTakesItsIconFromTheParametersAndFromJava() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder()
                  .title("lwjwae :: icon")
                  .icon(Icons.circle(256, Color.ORANGE))
                  .build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Screenshots.capture("window-icon");

      window.icon(Icons.circle(64, Color.GREEN));
      window.icon((byte[]) null);
      window.icon(Icons.circle(32, Color.BLUE));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> window.icon("not a PNG".getBytes()));
      Assertions.assertTrue(window.isVisible(), "the window lives through its icons");
    }
  }

  @Test
  void pageZoomScalesThePageAndStaysThroughNavigations() throws Exception {
    try (LocalPages pages = new LocalPages();
        Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: zoom").size(800, 600).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(1.0, window.zoom(), 0.001);
      int width = WindowContractTest.viewportWidth(window);

      window.zoom(2.0);
      Assertions.assertEquals(2.0, window.zoom(), 0.001);
      WindowContractTest.awaitTrue(
          () -> Math.abs(WindowContractTest.viewportWidth(window) - width / 2) <= 2,
          "at 200% the page has half the width in CSS pixels");

      // Another page, and another origin: the zoom belongs to the window, not to the page.
      String url = pages.page("/zoom.html", "<!DOCTYPE html><html><body>zoom</body></html>");
      final var next = Loads.expectFinished(window);
      window.navigate(url);
      next.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(2.0, window.zoom(), 0.001);
      WindowContractTest.awaitTrue(
          () -> Math.abs(WindowContractTest.viewportWidth(window) - width / 2) <= 2,
          "the zoom stays through the navigation");

      window.zoom(0.5);
      WindowContractTest.awaitTrue(
          () -> Math.abs(WindowContractTest.viewportWidth(window) - width * 2) <= 4,
          "at 50% the page has twice the width");
      window.zoom(1.0);
      WindowContractTest.awaitTrue(
          () -> Math.abs(WindowContractTest.viewportWidth(window) - width) <= 2, "and back");
      Assertions.assertThrows(IllegalArgumentException.class, () -> window.zoom(10));
    }
  }

  private static int viewportWidth(Window window) {
    try {
      return Integer.parseInt(Loads.eval(window, "String(window.innerWidth)"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void filesDroppedOnThePageReachJavaAndThePageWithTheirPaths(@TempDir Path directory)
      throws Exception {
    Path first = Files.writeString(directory.resolve("a file with spaces.txt"), "one");
    Path second = Files.writeString(directory.resolve("odd #1 & 100% name.txt"), "two");
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: drop").size(600, 400).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      BlockingQueue<FileDropEvent> dropped = new LinkedBlockingQueue<>();
      window.onFileDrop(dropped::add);
      Loads.eval(
          window,
          "lwjwae.files.listen((drop) => window.__dropped = drop.paths.join('|')); undefined;");
      final String page = window.url();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Thread.sleep(500);

      boolean done = this.dropFiles("lwjwae :: drop", List.of(first, second));
      Assumptions.assumeTrue(done, "this machine can't drag files in a test");

      FileDropEvent event = dropped.poll(15, TimeUnit.SECONDS);
      Assertions.assertNotNull(event, "Java hears the drop");
      Assertions.assertEquals(List.of(first, second), event.paths());
      Assertions.assertTrue(
          event.x() > 0 && event.y() > 0 && event.x() < 600 && event.y() < 400, "inside the page");
      Assertions.assertEquals(
          first + "|" + second,
          Loads.awaitValue(window, "window.__dropped"),
          "and so does the page");
      Assertions.assertEquals(page, window.url(), "the window doesn't open the file");
    }
  }

  @Test
  void linksThatLeaveTheApplicationGoToTheHandler() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open(WindowParameters.builder().title("lwjwae :: links").build());
      BlockingQueue<String> left = new LinkedBlockingQueue<>();
      window.externalLinkHandler(left::add);
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      final String page = window.url();

      Loads.eval(
          window,
          "const link = document.createElement('a'); link.href = 'https://example.com/away';"
              + " document.body.append(link); link.click(); undefined;");
      Assertions.assertEquals("https://example.com/away", left.poll(10, TimeUnit.SECONDS));
      Loads.eval(window, "window.open('mailto:someone@example.com'); undefined;");
      Assertions.assertEquals("mailto:someone@example.com", left.poll(10, TimeUnit.SECONDS));
      Loads.eval(
          window,
          "lwjwae.openExternal('https://example.com/asked').then(() => window.__asked = 'done');"
              + " undefined;");
      Assertions.assertEquals("https://example.com/asked", left.poll(10, TimeUnit.SECONDS));
      Assertions.assertEquals("done", Loads.awaitValue(window, "window.__asked"));

      Assertions.assertEquals(page, window.url(), "the window stays on its page");

      // A new window of the application itself: a web view has no tabs, so it opens in place.
      Loads.eval(
          window,
          "const blank = document.createElement('a'); blank.href = 'index.html?blank';"
              + " blank.target = '_blank'; document.body.append(blank); blank.click(); undefined;");
      WindowContractTest.awaitTrue(
          () -> window.url().endsWith("?blank"), "the new window must open in place");
      Assertions.assertEquals(List.of(window), application.windows(), "and no other opens");
    }
  }

  @Test
  void dialogsShowAndCancellingTheFutureClosesThem() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: dialogs").size(640, 480).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);

      List<CompletableFuture<?>> dialogs =
          List.of(
              window.showOpenDialog(
                  OpenDialogParameters.builder()
                      .title("lwjwae :: open")
                      .multiple(true)
                      .fileTypes(List.of(FileType.of("Images", "png", "jpg")))
                      .build()),
              window.showOpenDialog(OpenDialogParameters.builder().directories(true).build()),
              window.showSaveDialog(SaveDialogParameters.builder().fileName("lwjwae.txt").build()),
              window.showMessageDialog(
                  MessageDialogParameters.builder()
                      .title("lwjwae :: message")
                      .message("A question")
                      .detail("With a detail")
                      .level(MessageLevel.QUESTION)
                      .buttons(MessageButtons.YES_NO)
                      .build()));
      String[] names = {"open", "folder", "save", "message"};
      for (int index = 0; index < dialogs.size(); index++) {
        CompletableFuture<?> dialog = dialogs.get(index);
        Thread.sleep(1000);
        Screenshots.capture("dialog-" + names[index]);
        Assertions.assertFalse(dialog.isDone(), names[index] + " waits for the user");
        dialog.cancel(false);
        // The UI thread runs work while the dialog goes: a modal loop mustn't hold it up.
        Assertions.assertEquals("2", Loads.eval(window, "String(1 + 1)"), names[index]);
      }

      // A page that aborts its call closes the dialog too.
      Loads.eval(
          window,
          "window.__aborted = undefined; const controller = new AbortController();"
              + " lwjwae.dialog.message({ message: 'Abort me', signal: controller.signal })"
              + ".catch((error) => window.__aborted = error.name);"
              + " setTimeout(() => controller.abort(), 1000); undefined;");
      Assertions.assertEquals("AbortError", Loads.awaitValue(window, "window.__aborted"));
      Assertions.assertEquals("4", Loads.eval(window, "String(2 + 2)"));

      CompletableFuture<Boolean> left =
          window.showMessageDialog(MessageDialogParameters.of("Left open"));
      Thread.sleep(500);
      window.close();
      Assertions.assertTrue(left.isCancelled(), "a closed window cancels its dialogs");
    }
  }

  @Test
  void menuBarTakesRoomAndItsKeysPickItsEntries() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: menu bar").size(640, 480).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      window.focus();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      int without = Integer.parseInt(Loads.eval(window, "String(window.innerHeight)"));

      BlockingQueue<Object> heard = new LinkedBlockingQueue<>();
      application.menu(
          MenuItem.submenu(
              "File",
              MenuItem.of("Ping", "Ctrl+Alt+P", () -> heard.add("ping")),
              MenuItem.checkbox("Mark", false, heard::add).withAccelerator("Ctrl+Alt+M"),
              MenuItem.separator(),
              MenuItem.submenu("More", MenuItem.of("Deep", () -> {}))),
          MenuItem.editMenu(),
          MenuItem.windowMenu());
      Assertions.assertEquals(3, window.menu().size());
      if (this.hasMenuBarInWindow()) {
        WindowContractTest.awaitTrue(
            () -> WindowContractTest.innerHeight(window) < without, "the bar takes room");
      }
      Thread.sleep(500);
      Screenshots.capture("menu-bar");

      if (this.canTakeFocus() && this.pressKeys(Shortcut.parse("Ctrl+Alt+P"))) {
        Assertions.assertEquals("ping", heard.poll(10, TimeUnit.SECONDS), "the keys pick it");
        this.pressKeys(Shortcut.parse("Ctrl+Alt+M"));
        Assertions.assertEquals(true, heard.poll(10, TimeUnit.SECONDS), "the mark goes on");
        this.pressKeys(Shortcut.parse("Ctrl+Alt+M"));
        Assertions.assertEquals(false, heard.poll(10, TimeUnit.SECONDS), "and off");
      }

      window.menu(List.of());
      Assertions.assertEquals(List.of(), window.menu());
      if (this.hasMenuBarInWindow()) {
        WindowContractTest.awaitTrue(
            () -> WindowContractTest.innerHeight(window) == without, "no bar, no room");
      }
      window.useApplicationMenu();
      Assertions.assertEquals(3, window.menu().size());
      application.menu(List.of());
      if (this.hasMenuBarInWindow()) {
        WindowContractTest.awaitTrue(
            () -> WindowContractTest.innerHeight(window) == without, "the bar goes away");
      }
    }
  }

  private static int innerHeight(Window window) {
    try {
      return Integer.parseInt(Loads.eval(window, "String(window.innerHeight)"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void contextMenusOpenFromJavaAndThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: context menu").size(640, 480).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      window.focus();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");

      CompletableFuture<Void> shown =
          window.showContextMenu(
              MenuItem.of("First", () -> {}),
              MenuItem.separator(),
              MenuItem.checkbox("Checked", true, _ -> {}),
              MenuItem.submenu("More", MenuItem.of("Deep", () -> {})));
      Thread.sleep(1000);
      Screenshots.capture("context-menu");
      Assertions.assertFalse(shown.isDone(), "the menu waits for the user");
      shown.cancel(false);
      Assertions.assertEquals("2", Loads.eval(window, "String(1 + 1)"), "the page goes on");

      // A page that aborts its call closes the menu too, right after the one of Java closed.
      Assertions.assertEquals("AbortError", WindowContractTest.pageAbortsItsMenu(window));
      Assertions.assertEquals("4", Loads.eval(window, "String(2 + 2)"));

      Loads.eval(
          window,
          "lwjwae.menu.popup([{ id: 'first', label: 'First' }, { id: 'second', label: 'Second'"
              + " }], { x: 20, y: 20 }).then((id) => window.__picked = String(id)); undefined;");
      Thread.sleep(1000);
      if (this.pickFirstEntryOfOpenMenu()) {
        Assertions.assertEquals("first", Loads.awaitValue(window, "window.__picked"));

        BlockingQueue<String> heard = new LinkedBlockingQueue<>();
        window.contextMenu(MenuItem.of("Reload", () -> heard.add("reload")));
        Loads.eval(
            window,
            "document.body.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true,"
                + " cancelable: true, clientX: 30, clientY: 30 })); undefined;");
        Thread.sleep(1000);
        this.pickFirstEntryOfOpenMenu();
        Assertions.assertEquals(
            "reload", heard.poll(10, TimeUnit.SECONDS), "a right click opens the menu of Java");

        if (this.canUseClipboardUnattended()) {
          String text = "lwjwae copied " + System.nanoTime();
          Loads.eval(
              window,
              "const input = document.createElement('input'); input.value = '"
                  + text
                  + "'; document.body.append(input); input.focus(); input.select(); undefined;");
          window.showContextMenu(MenuItem.role(MenuRole.COPY));
          Thread.sleep(1000);
          this.pickFirstEntryOfOpenMenu();
          WindowContractTest.awaitTrue(
              () -> application.clipboard().readText().join().filter(text::equals).isPresent(),
              "Copy copies the selection of the page");
        }
      } else {
        window.close();
      }
    }
  }

  @Test
  void iconShowsTheProgressAndTheBadge() throws Exception {
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: taskbar").size(400, 300).build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      application.progress(0.4);
      application.badgeCount(3);
      Assertions.assertEquals(TaskbarProgress.of(0.4), application.progress());
      Assertions.assertEquals(3, application.badgeCount());
      Thread.sleep(500);
      Screenshots.capture("taskbar-progress");
      for (TaskbarProgress progress :
          List.of(
              TaskbarProgress.indeterminate(),
              TaskbarProgress.paused(0.6),
              TaskbarProgress.error(0.8),
              TaskbarProgress.none())) {
        application.progress(progress);
      }
      application.badgeCount(12);
      Thread.sleep(500);
      Screenshots.capture("taskbar-badge");
      application.badgeCount(0);

      // A window opened later shows what the application shows.
      application.progress(0.7);
      Window later = application.open(WindowParameters.builder().title("lwjwae :: later").build());
      later.show();
      WindowContractTest.awaitTrue(later::isVisible, "the window must show");
      Assertions.assertEquals(TaskbarProgress.of(0.7), application.progress());
    }
  }

  @Test
  void thePageQueriesTheStoreOfTheApplication(@TempDir Path directory) throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().name("lwjwae-store").dataDirectory(directory).build();
    try (Application application = Application.create(parameters)) {
      application.store().executeScript("CREATE TABLE tasks (title TEXT UNIQUE, hours INTEGER)");
      application.store().execute("INSERT INTO tasks VALUES (?, ?)", "from Java", 1);
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: store").size(400, 300).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      Loads.eval(
          window,
          "(async () => { const store = lwjwae.store; const inserted = await store.execute('INSERT"
              + " INTO tasks VALUES (?, ?)', ['a', 2]); const added = await store.transaction(["
              + " { sql: \"INSERT INTO tasks VALUES ('b', 3) RETURNING title\" } ]); const sums ="
              + " await store.query('SELECT sum(hours) AS hours FROM tasks WHERE hours > :min',"
              + " { min: 1 }); let conflict; try { await store.execute('INSERT INTO tasks VALUES"
              + " (?, 0)', ['a']); } catch (error) { conflict = error.code; } window.__store ="
              + " JSON.stringify({ inserted, added, sums, conflict }); })(); undefined;");
      Assertions.assertEquals(
          "{\"inserted\":{\"changes\":1,\"lastInsertRowId\":2},\"added\":[[{\"title\":\"b\"}]],"
              + "\"sums\":[{\"hours\":5}],\"conflict\":\"constraint\"}",
          Loads.awaitValue(window, "window.__store"));
      Assertions.assertEquals(
          List.of(Map.of("n", 3L)),
          application.store().query("SELECT count(*) AS n FROM tasks"),
          "one store");
    }
  }

  /**
   * Opens a menu from the page that the page aborts after a second, and returns how its call ended:
   * {@code AbortError}, or what it resolved with.
   */
  private static String pageAbortsItsMenu(Window window) throws Exception {
    Loads.eval(
        window,
        "window.__aborted = undefined; { const controller = new AbortController();"
            + " lwjwae.menu.popup([{ id: 'one', label: 'One' }], { x: 20, y: 20, signal:"
            + " controller.signal }).then((id) => window.__aborted = 'resolved with ' + id,"
            + " (error) => window.__aborted = error.name);"
            + " setTimeout(() => controller.abort(), 1000); } undefined;");
    return Loads.awaitValue(window, "window.__aborted");
  }

  @Test
  void cookiesGoBothWaysBetweenJavaAndThePage() throws Exception {
    try (LocalPages pages = new LocalPages();
        Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: cookies").size(400, 300).build());
      window.show();
      Cookies cookies = application.cookies();
      cookies
          .set(Cookie.builder().name("fromJava").value("1").domain("127.0.0.1").build())
          .get(10, TimeUnit.SECONDS);
      String url = pages.page("/cookies.html", "<!DOCTYPE html><html><body>cookies</body></html>");
      final var loaded = Loads.expectFinished(window);
      window.navigate(url);
      loaded.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(
          "fromJava=1", Loads.eval(window, "document.cookie"), "the page reads the cookie of Java");

      Loads.eval(window, "document.cookie = 'fromPage=2; path=/'; undefined;");
      Map<String, String> seen = WindowContractTest.cookieValues(cookies.get(url));
      Assertions.assertEquals(Map.of("fromJava", "1", "fromPage", "2"), seen);

      Cookie fromJava =
          cookies.get(url).get(10, TimeUnit.SECONDS).stream()
              .filter(cookie -> cookie.name().equals("fromJava"))
              .findFirst()
              .orElseThrow();
      Assertions.assertEquals("/", fromJava.path());
      Assertions.assertTrue(fromJava.isSession());
      cookies.delete(fromJava).get(10, TimeUnit.SECONDS);
      Assertions.assertEquals(
          Map.of("fromPage", "2"), WindowContractTest.cookieValues(cookies.get(url)));
      Assertions.assertEquals(
          Map.of("fromPage", "2"), WindowContractTest.cookieValues(cookies.getAll()));

      cookies.clear().get(10, TimeUnit.SECONDS);
      Assertions.assertEquals(Map.of(), WindowContractTest.cookieValues(cookies.getAll()));
      // The page keeps a cache of its cookies, which the engine updates when its network process
      // tells it: the page sees the change a moment later.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!Loads.eval(window, "document.cookie").isEmpty() && System.nanoTime() < deadline) {
        Thread.sleep(100);
      }
      Assertions.assertEquals("", Loads.eval(window, "document.cookie"));
    }
  }

  /** The names and values of the cookies that {@code pending} lists. */
  private static Map<String, String> cookieValues(CompletableFuture<List<Cookie>> pending)
      throws Exception {
    Map<String, String> values = new TreeMap<>();
    pending.get(10, TimeUnit.SECONDS).forEach(cookie -> values.put(cookie.name(), cookie.value()));
    return values;
  }

  /** Waits for an event of {@code type}, passing over the others, and returns it. */
  private static WindowEvent awaitEvent(BlockingQueue<WindowEvent> heard, WindowEventType type)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      WindowEvent event = heard.poll(100, TimeUnit.MILLISECONDS);
      if (event != null && event.type() == type) {
        return event;
      }
    }
    throw new AssertionError("No " + type + " event");
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
  void secondInstanceHandsItsArgumentsOverAndBringsTheWindowBack(@TempDir Path directory)
      throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder()
            .name("lwjwae-contract-" + UUID.randomUUID())
            .dataDirectory(directory)
            .build();
    try (Application application =
        Application.createSingleInstance(parameters, "first").orElseThrow()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: single").size(400, 300).build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      window.minimize();
      if (this.canTellMinimized()) {
        WindowContractTest.awaitTrue(window::isMinimized, "minimize must minimize");
      }
      BlockingQueue<SecondInstanceEvent> heard = new LinkedBlockingQueue<>();
      application.onSecondInstance(heard::add);

      Optional<Application> second = Application.createSingleInstance(parameters, "two", "--x");

      Assertions.assertTrue(second.isEmpty(), "the second start gets no application");
      SecondInstanceEvent start = heard.poll(10, TimeUnit.SECONDS);
      Assertions.assertNotNull(start, "the running instance must hear of the second start");
      Assertions.assertEquals(List.of("two", "--x"), start.arguments());
      Assertions.assertEquals(Path.of("").toAbsolutePath(), start.workingDirectory());
      Assertions.assertEquals(List.of(window), application.windows());
      WindowContractTest.awaitTrue(
          () -> !window.isMinimized(), "the second start must bring the window back");
    }

    try (Application next = Application.createSingleInstance(parameters).orElseThrow()) {
      Assertions.assertFalse(next.isClosed(), "a closed instance gives the name up");
    }
  }

  @Test
  void globalShortcutRunsItsHandlerUntilGivenBack() throws Exception {
    Assumptions.assumeTrue(
        this.canBindShortcutsUnattended(), "the desktop asks the user to confirm a shortcut");
    Shortcut shortcut = Shortcut.parse("Ctrl+Alt+Shift+F11");
    try (Application application = Application.create()) {
      BlockingQueue<Boolean> heard = new LinkedBlockingQueue<>();
      Runnable handler = () -> heard.add(Thread.currentThread().isVirtual());
      EventSubscription binding = application.globalShortcut(shortcut, handler);
      Assertions.assertThrows(
          ShortcutUnavailableException.class,
          () -> application.globalShortcut(shortcut, handler),
          "a shortcut is bound once");

      if (this.pressKeys(shortcut)) {
        Assertions.assertEquals(
            Boolean.TRUE, heard.poll(10, TimeUnit.SECONDS), "the press runs the handler");
      }
      binding.unlisten();
      binding.unlisten();
      if (this.pressKeys(shortcut)) {
        Assertions.assertNull(heard.poll(1, TimeUnit.SECONDS), "a shortcut given back stays quiet");
      }

      application.globalShortcut(shortcut, handler);
      if (this.pressKeys(shortcut)) {
        Assertions.assertEquals(
            Boolean.TRUE, heard.poll(10, TimeUnit.SECONDS), "bound again, the press is heard");
      }
    }
  }

  @Test
  void textGoesThroughTheClipboardBetweenJavaAndThePage() throws Exception {
    Assumptions.assumeTrue(
        this.canUseClipboardUnattended(), "Wayland gives the clipboard to the focused client");
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: clipboard").size(400, 300).build());
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      window.show();
      loaded.get(30, TimeUnit.SECONDS);
      window.focus();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Clipboard clipboard = application.clipboard();

      String fromJava = "lwjwae from Java " + System.nanoTime();
      clipboard.writeText(fromJava);
      Assertions.assertEquals(
          Optional.of(fromJava), clipboard.readText().get(10, TimeUnit.SECONDS));
      Loads.eval(
          window,
          "lwjwae.clipboard.readText().then(text => { window.__read = text; }); undefined;");
      Assertions.assertEquals(fromJava, Loads.awaitValue(window, "window.__read"));

      String fromPage = "lwjwae from the page " + System.nanoTime();
      Loads.eval(
          window,
          "lwjwae.clipboard.writeText('"
              + fromPage
              + "').then(() => { window.__written = true; }); undefined;");
      Assertions.assertEquals("true", Loads.awaitValue(window, "window.__written"));
      Assertions.assertEquals(
          Optional.of(fromPage), clipboard.readText().get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void imageGoesThroughTheClipboardAsPng() throws Exception {
    Assumptions.assumeTrue(
        this.canUseClipboardUnattended(), "Wayland gives the clipboard to the focused client");
    try (Application application = Application.create()) {
      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: clipboard image").size(400, 300).build());
      window.show();
      window.focus();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Clipboard clipboard = application.clipboard();

      clipboard.writeImage(Icons.circle(48, Color.ORANGE));
      byte[] read = clipboard.readImage().get(10, TimeUnit.SECONDS).orElseThrow();
      Assertions.assertArrayEquals(
          new byte[] {(byte) 0x89, 'P', 'N', 'G'},
          Arrays.copyOf(read, 4),
          "the image comes back as PNG");
      ByteBuffer header = ByteBuffer.wrap(read, 16, 8);
      Assertions.assertEquals(48, header.getInt(), "width");
      Assertions.assertEquals(48, header.getInt(), "height");

      clipboard.writeText("no image any more");
      Assertions.assertEquals(Optional.empty(), clipboard.readImage().get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void screensDescribeTheDesktopAndOneHoldsTheWindow() throws Exception {
    try (Application application = Application.create()) {
      List<Screen> screens = application.screens();
      Assertions.assertFalse(screens.isEmpty(), "a desktop has a screen");
      for (Screen screen : screens) {
        Assertions.assertTrue(screen.bounds().width() > 0, screen.toString());
        Assertions.assertTrue(screen.bounds().height() > 0, screen.toString());
        Assertions.assertEquals(
            screen.workArea(),
            screen.workArea().intersection(screen.bounds()),
            "the work area lies on its screen: " + screen);
        Assertions.assertTrue(screen.scale() >= 1, screen.toString());
      }
      Assertions.assertTrue(screens.contains(application.primaryScreen()));
      Assertions.assertEquals(application.primaryScreen(), screens.getFirst());

      Window window =
          application.open(
              WindowParameters.builder().title("lwjwae :: screens").size(400, 300).build());
      window.show();
      WindowContractTest.awaitTrue(window::isVisible, "the window must show");
      Screen holder = window.screen();
      Assertions.assertTrue(
          screens.stream().anyMatch(screen -> screen.bounds().equals(holder.bounds())),
          "the window is on one of the screens: " + holder);
      if (this.canPlaceWindows()) {
        WindowPosition position = window.position();
        Assertions.assertTrue(
            screens.stream()
                .anyMatch(screen -> screen.bounds().contains(position.x() + 10, position.y() + 10)),
            "a screen holds the window at " + position);
      }
    }
  }

  @Test
  void windowOpensWhereAskedAndMoves() throws Exception {
    WindowParameters parameters =
        WindowParameters.builder()
            .title("lwjwae :: position")
            .size(400, 300)
            .position(120, 80)
            .build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      window.show();
      if (this.canPlaceWindows()) {
        WindowContractTest.awaitPosition(window, 120, 80);
        Assertions.assertEquals(new WindowPosition(120, 80), window.position());
      }
      Screenshots.capture("window-opened-at-120-80");

      window.position(200, 160);
      if (this.canPlaceWindows()) {
        WindowContractTest.awaitPosition(window, 200, 160);
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
        WindowParameters.builder().size(400, 300).position(0, 0).centered(true).build();
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
    for (int attempt = 0; attempt < 50 && !WindowContractTest.near(window, x, y); attempt++) {
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
