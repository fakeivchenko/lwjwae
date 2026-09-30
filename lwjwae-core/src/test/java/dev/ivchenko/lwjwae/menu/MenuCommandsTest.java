package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MenuCommandsTest {
  @Test
  void entriesThatDoSomethingAreNumberedDepthFirstFromOne() {
    ActionMenuItem open = MenuItem.of("Open", () -> {});
    ActionMenuItem again = MenuItem.of("Open", () -> {});
    CheckMenuItem wrap = MenuItem.checkbox("Wrap", true, _ -> {});
    RoleMenuItem quit = MenuItem.role(MenuRole.QUIT);
    MenuCommands commands =
        new MenuCommands(
            List.of(
                MenuItem.submenu("File", open, MenuItem.separator(), again),
                MenuItem.submenu("View", MenuItem.submenu("More", wrap), quit)));

    Assertions.assertEquals(1, commands.id(open));
    Assertions.assertEquals(2, commands.id(again), "equal entries are two entries");
    Assertions.assertEquals(3, commands.id(wrap));
    Assertions.assertEquals(4, commands.id(quit));
    Assertions.assertSame(wrap, commands.item(3));
    Assertions.assertNull(commands.item(0));
    Assertions.assertNull(commands.item(5));
    Assertions.assertEquals(0, commands.id(commands.items().getFirst()), "a submenu has none");
  }

  @Test
  void pickFlipsTheCheckMark() {
    MenuCommands commands = new MenuCommands(List.of(MenuItem.checkbox("Wrap", true, _ -> {})));
    Assertions.assertTrue(commands.isChecked(1));
    Assertions.assertFalse(commands.toggle(1));
    Assertions.assertFalse(commands.isChecked(1));
    Assertions.assertTrue(commands.toggle(1));
  }

  @Test
  void keysPickOnlyWhatTheUserCouldReach() {
    MenuCommands commands =
        new MenuCommands(
            List.of(
                MenuItem.submenu(
                    "File",
                    MenuItem.of("Save", "Ctrl+S", () -> {}),
                    MenuItem.of("Print", "Ctrl+P", () -> {}).withEnabled(false),
                    MenuItem.role(MenuRole.COPY),
                    MenuItem.role(MenuRole.CLOSE_WINDOW)),
                MenuItem.submenu("Gone", MenuItem.of("Find", "Ctrl+F", () -> {}))
                    .withEnabled(false)));

    Assertions.assertEquals(1, commands.acceleratorId(Shortcut.parse("Ctrl+S")));
    Assertions.assertEquals(0, commands.acceleratorId(Shortcut.parse("Ctrl+P")), "disabled");
    Assertions.assertEquals(0, commands.acceleratorId(Shortcut.parse("Ctrl+F")), "in a gone menu");
    Assertions.assertFalse(
        commands.accelerators().containsKey(MenuRole.COPY.accelerator()),
        "the keys of an editing role stay with the page");
    Assertions.assertEquals(4, commands.acceleratorId(MenuRole.CLOSE_WINDOW.accelerator()));
  }
}
