package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.PresentedDialog;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class MenuTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;
  private static final String GROUP = BridgeProtocol.GROUP_SEPARATOR;
  private static final String RECORD = BridgeProtocol.RECORD_SEPARATOR;

  @Test
  void everyWindowShowsTheMenuOfTheApplicationUnlessItHasItsOwn() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow first = application.openFake();
      SubmenuItem file = MenuItem.submenu("File", MenuItem.of("Open", () -> {}));
      application.menu(file);
      Assertions.assertEquals(List.of(file), first.menuBars.getLast().items());

      SubmenuItem own = MenuItem.submenu("Own");
      FakeWindow second =
          (FakeWindow) application.open(WindowParameters.builder().menu(own).build());
      Assertions.assertEquals(List.of(own), second.menu());
      Assertions.assertEquals(List.of(own), second.menuBars.getLast().items());

      SubmenuItem edit = MenuItem.editMenu();
      application.menu(edit);
      Assertions.assertEquals(List.of(edit), first.menuBars.getLast().items());
      Assertions.assertEquals(List.of(own), second.menuBars.getLast().items());

      second.useApplicationMenu();
      Assertions.assertEquals(List.of(edit), second.menuBars.getLast().items());
    }
  }

  @Test
  void menuBarHoldsSubmenusOnly() {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> application.menu(MenuItem.of("Open", () -> {})));
    }
  }

  @Test
  void pickRunsTheActionFlipsTheMarkAndPlaysTheRole() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      LinkedBlockingQueue<Object> heard = new LinkedBlockingQueue<>();
      window.menu(
          MenuItem.submenu(
              "File",
              MenuItem.of("Open", () -> heard.add("open")),
              MenuItem.checkbox("Wrap", false, heard::add),
              MenuItem.role(MenuRole.COPY)));

      window.pickFromMenuBar(1);
      Assertions.assertEquals("open", heard.poll(5, TimeUnit.SECONDS));
      window.pickFromMenuBar(2);
      Assertions.assertEquals(true, heard.poll(5, TimeUnit.SECONDS));
      Assertions.assertTrue(window.menuBar().isChecked(2));
      window.pickFromMenuBar(3);
      window.awaitUiThread();
      Assertions.assertEquals(List.of(MenuRole.COPY), window.edits);
    }
  }

  @Test
  void showContextMenuDoesWhatThePickedEntryDoes() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      LinkedBlockingQueue<String> heard = new LinkedBlockingQueue<>();
      CompletableFuture<Void> shown =
          window.showContextMenu(MenuItem.of("Rename", () -> heard.add("rename")));
      PresentedDialog menu = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertEquals(Optional.empty(), window.menuPlaces.getLast(), "at the pointer");
      menu.answer(1);
      shown.get(5, TimeUnit.SECONDS);
      Assertions.assertEquals("rename", heard.poll(5, TimeUnit.SECONDS));

      CompletableFuture<Void> abandoned = window.showContextMenu(MenuItem.of("Gone", () -> {}));
      PresentedDialog closing = window.dialogs.poll(5, TimeUnit.SECONDS);
      abandoned.cancel(false);
      for (int attempt = 0; attempt < 50 && !closing.closed().get(); attempt++) {
        Thread.sleep(50);
      }
      Assertions.assertTrue(closing.closed().get(), "a canceled menu closes");
    }
  }

  @Test
  void pagePopsUpMenuAndGetsIdOfThePick() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      String records =
          String.join(
              RECORD,
              String.join(GROUP, "0", "action", "rename", "Rename", "1", "0", "Ctrl+R", ""),
              String.join(GROUP, "0", "separator", "", "", "1", "0", "", ""),
              String.join(GROUP, "0", "submenu", "more", "More", "1", "0", "", ""),
              String.join(GROUP, "1", "checkbox", "wrap", "Wrap", "1", "1", "", ""),
              String.join(GROUP, "1", "role", "copy", "", "1", "0", "", "copy"),
              String.join(GROUP, "0", "action", "last", "Last", "0", "0", "", ""));
      window.call(1, BridgeProtocol.MENU_CALL, String.join(SEP, "popup", "10", "20", records));
      PresentedDialog menu = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertEquals(Optional.of(new WindowPosition(10, 20)), window.menuPlaces.getLast());
      MenuCommands commands = (MenuCommands) menu.parameters();
      Assertions.assertEquals(
          List.of("Rename", "separator", "More", "Last"),
          commands.items().stream()
              .map(item -> Optional.ofNullable(MenuCommands.label(item)).orElse("separator"))
              .toList());
      SubmenuItem more = (SubmenuItem) commands.items().get(2);
      Assertions.assertEquals(2, more.items().size());
      Assertions.assertFalse(MenuCommands.isEnabled(commands.items().get(3)));
      menu.answer(2);
      Assertions.assertEquals("wrap", window.awaitReply(1).body());

      window.call(2, BridgeProtocol.MENU_CALL, String.join(SEP, "popup", "", "", records));
      window.dialogs.poll(5, TimeUnit.SECONDS).answer(0);
      Assertions.assertEquals("", window.awaitReply(2).body(), "nothing picked");

      window.call(3, BridgeProtocol.MENU_CALL, String.join(SEP, "popup", "", "", "0" + GROUP));
      Assertions.assertEquals(400, window.awaitReply(3).status());
    }
  }

  @Test
  void rightClickOpensTheContextMenuOfTheWindow() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.call(1, BridgeProtocol.MENU_CALL, String.join(SEP, "context", "1", "2"));
      Assertions.assertEquals("", window.awaitReply(1).body(), "no menu, nothing opens");

      LinkedBlockingQueue<String> heard = new LinkedBlockingQueue<>();
      window.contextMenu(MenuItem.of("Reload", () -> heard.add("reload")));
      Assertions.assertTrue(
          window.evaluated.getLast().contains(".contextMenu = true"), "the page learns of it");
      window.call(2, BridgeProtocol.MENU_CALL, String.join(SEP, "context", "1", "2"));
      window.dialogs.poll(5, TimeUnit.SECONDS).answer(1);
      Assertions.assertEquals("", window.awaitReply(2).body());
      Assertions.assertEquals("reload", heard.poll(5, TimeUnit.SECONDS));

      window.contextMenu(List.of());
      Assertions.assertTrue(window.evaluated.getLast().contains(".contextMenu = false"));
    }
  }
}
