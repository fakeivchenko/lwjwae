package dev.ivchenko.lwjwae.tray;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TrayIconTest {
  @Test
  void theIconComesFromTheResourcesOfTheApplication() {
    TrayIcon icon = TrayIcon.builder().icon("fixtures/dot.png").build();

    Assertions.assertArrayEquals(ResourceUtil.read("fixtures/dot.png"), icon.icon());
  }

  @Test
  void missingIconResourceFailsAtOnce() {
    Assertions.assertThrows(
        ResourceNotFoundException.class, () -> TrayIcon.builder().icon("fixtures/missing.png"));
  }

  @Test
  void theMenuTakesItsEntriesOneByOne() {
    TrayMenuItem show = new TrayMenuItem("Show", () -> {});
    TrayMenuItem quit = new TrayMenuItem("Quit", () -> {});

    TrayIcon icon =
        TrayIcon.builder().icon(new byte[] {1}).menu(show, TrayMenuItem.separator(), quit).build();

    Assertions.assertEquals(3, icon.menu().size());
    Assertions.assertEquals(List.of(show, quit), List.of(icon.menu().get(0), icon.menu().get(2)));
  }
}
