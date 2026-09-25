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
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageButtons;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.MessageLevel;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.event.WindowEventType;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.LocalPages;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
