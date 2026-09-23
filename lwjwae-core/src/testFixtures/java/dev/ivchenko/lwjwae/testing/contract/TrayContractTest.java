package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Puts an icon in the tray of a real desktop and drives it through its handle.
 *
 * <p>No test can see the panel, so what the contract checks is the lifecycle: every call returns,
 * every change is accepted, the handle closes on its own and with its application, a closed handle
 * refuses further calls, and an icon keeps the application running with no window open. Whether the
 * icon is drawn is a matter for the screenshots.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class TrayContractTest extends DisplayContractTest {
  private static final byte[] GREEN = Icons.circle(32, new Color(0x34, 0xd3, 0x99));
  private static final byte[] RED = Icons.circle(32, new Color(0xfb, 0x71, 0x85));

  @Test
  void trayTakesEveryChangeAndClosesOnItsOwn() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      TrayIcon icon =
          TrayIcon.builder()
              .icon(GREEN)
              .tooltip("lwjwae :: tray")
              .menu(
                  List.of(
                      new TrayMenuItem("Show", window::show),
                      new TrayMenuItem("Disabled", () -> {}, false),
                      TrayMenuItem.separator(),
                      new TrayMenuItem("Quit", application::quit)))
              .onActivate(window::show)
              .build();

      try (Tray tray = application.tray(icon)) {
        Assertions.assertFalse(tray.isClosed());
        Thread.sleep(300);

        tray.icon(RED);
        tray.tooltip("lwjwae :: tray (red)");
        tray.menu(List.of(new TrayMenuItem("Only entry", () -> {})));
        tray.menu(List.of());
        tray.tooltip(null);
        Thread.sleep(300);

        tray.close();
        Assertions.assertTrue(tray.isClosed());
        tray.close();
        Assertions.assertThrows(IllegalStateException.class, () -> tray.tooltip("late"));
      }
    }
  }

  @Test
  void trayClosesWithItsApplication() {
    Tray tray;
    try (Application application = Application.create()) {
      tray = application.tray(TrayIcon.builder().icon(GREEN).build());
      Assertions.assertFalse(tray.isClosed());
    }
    Assertions.assertTrue(tray.isClosed(), "The tray must go away with its application");
  }

  @Test
  void trayKeepsTheApplicationRunningWithoutWindows() throws Exception {
    try (Application application = Application.create()) {
      Tray tray = application.tray(TrayIcon.builder().icon(GREEN).build());
      CompletableFuture<Void> running = CompletableFuture.runAsync(application::run);
      Thread.sleep(500);
      Assertions.assertFalse(running.isDone(), "a tray icon keeps run() going with no window open");

      tray.close();
      running.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void anIconNeedsAnImage() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> TrayIcon.builder().tooltip("no image").build());
  }
}
