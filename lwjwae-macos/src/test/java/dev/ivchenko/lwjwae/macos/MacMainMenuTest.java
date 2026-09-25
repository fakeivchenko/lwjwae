package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MacMainMenuTest {
  @BeforeEach
  void requireMacOs() {
    Assumptions.assumeTrue(PlatformUtil.isMacOs(), "The menu bar of AppKit: macOS only");
  }

  @Test
  void theMenuBarCarriesTheShortcutsOfEditingAndQuitting() {
    try (Application _ = Application.create()) {
      Map<Long, String> shortcuts = MacDispatcher.instance().call(MacMainMenuTest::shortcuts);
      long command = AppKit.MODIFIER_COMMAND;
      Map<String, String> expected =
          Map.of(
              "copy:", "c",
              "paste:", "v",
              "cut:", "x",
              "selectAll:", "a",
              "undo:", "z",
              "redo:", "Z",
              "terminate:", "q",
              "hide:", "h",
              "performClose:", "w",
              "performMiniaturize:", "m");
      for (Map.Entry<String, String> entry : expected.entrySet()) {
        String action = entry.getKey();
        Assertions.assertEquals(
            entry.getValue() + "/" + command,
            shortcuts.get(ObjC.sel(action).address()),
            "the shortcut of " + action);
      }
      MemorySegment windowsMenu =
          MacDispatcher.instance().call(() -> ObjC.send(AppKit.application(), "windowsMenu"));
      Assertions.assertFalse(ObjC.isNull(windowsMenu), "AppKit lists the windows in a menu");
    }
  }

  @Test
  void quitQuitsTheApplicationAndLeavesTheProcessRunning() throws Exception {
    try (Application application = Application.create()) {
      final Window window = application.open(WindowParameters.builder().title("quit").build());
      Thread runner = new Thread(application::run, "run-caller");
      runner.start();

      MacDispatcher.instance()
          .run(() -> ObjC.sendVoid(AppKit.application(), "terminate:", MemorySegment.NULL));

      runner.join(TimeUnit.SECONDS.toMillis(30));
      Assertions.assertFalse(runner.isAlive(), "run() must return once Quit quit the application");
      Assertions.assertTrue(application.isClosed());
      Assertions.assertTrue(window.isClosed());
    }
  }

  /**
   * The key equivalent and its modifiers, as {@code key/modifiers}, of every item of the menu bar
   * that has one, by the address of its action.
   */
  private static Map<Long, String> shortcuts() {
    Map<Long, String> shortcuts = new HashMap<>();
    MacMainMenuTest.collect(AppKit.mainMenu(), shortcuts);
    return shortcuts;
  }

  private static void collect(MemorySegment menu, Map<Long, String> shortcuts) {
    long count = ObjC.sendLong(menu, "numberOfItems");
    for (long i = 0; i < count; i++) {
      MemorySegment item = ObjC.send(menu, "itemAtIndex:", i);
      MemorySegment submenu = ObjC.send(item, "submenu");
      if (!ObjC.isNull(submenu)) {
        MacMainMenuTest.collect(submenu, shortcuts);
        continue;
      }
      String key = Foundation.string(ObjC.send(item, "keyEquivalent"));
      if (key != null && !key.isEmpty()) {
        shortcuts.put(
            ObjC.send(item, "action").address(),
            key + "/" + ObjC.sendLong(item, "keyEquivalentModifierMask"));
      }
    }
  }
}
