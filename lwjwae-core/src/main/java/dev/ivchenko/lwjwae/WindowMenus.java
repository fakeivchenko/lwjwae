package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.PageMenu;
import dev.ivchenko.lwjwae.menu.ActionMenuItem;
import dev.ivchenko.lwjwae.menu.CheckMenuItem;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.menu.Menus;
import dev.ivchenko.lwjwae.menu.RoleMenuItem;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The menus of one window: the bar that it shows, its context menu, and the menus that it opens at
 * the pointer, for Java and for its page.
 *
 * <p>The menu bar is built anew from the menu of the moment on the UI thread, so the last change
 * wins whatever the order of the calls; a window without a bar of its own follows the one of the
 * application. A menu at the pointer is shown as a dialog of the window, see {@link
 * AbstractWindow#showDialog}, so that it closes with the window. The core does what an entry does,
 * whichever menu it is in: the backend only reports the number of the entry picked.
 */
final class WindowMenus implements Menus {
  private final AbstractWindow window;

  /** The menu bar of this window alone, or {@code null} for the one of the application. */
  private volatile List<MenuItem> own;

  private volatile MenuCommands shownBar = MenuCommands.empty();
  private volatile MenuCommands contextCommands;

  WindowMenus(AbstractWindow window) {
    this.window = window;
  }

  @Override
  public List<MenuItem> bar() {
    List<MenuItem> own = this.own;
    return own != null ? own : this.window.application().menu();
  }

  @Override
  public void bar(List<MenuItem> items) {
    List<MenuItem> bar = WindowMenus.requireBar(items);
    this.window.checkOpen();
    this.own = bar;
    this.refreshBar();
  }

  @Override
  public void useApplicationBar() {
    this.window.checkOpen();
    this.own = null;
    this.refreshBar();
  }

  @Override
  public void context(List<MenuItem> items) {
    Objects.requireNonNull(items, "items");
    this.window.checkOpen();
    this.contextCommands = items.isEmpty() ? null : new MenuCommands(items);
    // Once for documents loaded from now on, once for the document already on screen.
    String flag =
        "if (window.%s) window.%s.contextMenu = %b;"
            .formatted(BridgeProtocol.CHANNEL, BridgeProtocol.CHANNEL, !items.isEmpty());
    this.window.injectOnDocumentStart(flag);
    this.window.eval(flag);
  }

  @Override
  public CompletableFuture<Void> popup(List<MenuItem> items) {
    return WindowMenus.following(this.popUp(new MenuCommands(items), null), _ -> null);
  }

  /** Starts with the menu bar {@code items}, or the one of the application for {@code null}. */
  void initialize(List<MenuItem> items) {
    this.own = items == null ? null : WindowMenus.requireBar(items);
    if (!this.bar().isEmpty()) {
      this.refreshBar();
    }
  }

  /** The menu of the application changed: the window shows it, unless it has one of its own. */
  void applicationBarChanged() {
    if (this.own == null) {
      this.refreshBar();
    }
  }

  /** The menu bar that the window shows. */
  MenuCommands shownBar() {
    return this.shownBar;
  }

  /** Builds the bar from the menu of this moment, on the UI thread, so the last change wins. */
  private void refreshBar() {
    this.window
        .dispatcher()
        .run(
            () -> {
              if (this.window.isClosed()) {
                return;
              }
              MenuCommands commands = new MenuCommands(this.bar());
              this.shownBar = commands;
              this.window.presentMenuBar(commands);
            });
  }

  /**
   * A copy of {@code items} as a menu bar.
   *
   * @throws IllegalArgumentException If an entry isn't a submenu.
   */
  static List<MenuItem> requireBar(List<MenuItem> items) {
    Objects.requireNonNull(items, "items");
    for (MenuItem item : items) {
      if (!(item instanceof SubmenuItem)) {
        throw new IllegalArgumentException("A menu bar holds submenus only, not " + item);
      }
    }
    return List.copyOf(items);
  }

  /**
   * Opens {@code commands} at {@code place} in the page, or at the pointer for {@code null}, and
   * completes with the number of the entry picked, 0 for none, once that entry has started.
   */
  private CompletableFuture<Integer> popUp(MenuCommands commands, WindowPosition place) {
    CompletableFuture<Integer> shown =
        this.window.showDialog(
            completion -> this.window.presentContextMenu(commands, place, completion));
    return WindowMenus.following(
        shown,
        id -> {
          this.picked(commands, id);
          return id;
        });
  }

  /**
   * {@code source} with its result turned by {@code then}, where canceling the result cancels
   * {@code source} too, which closes the menu, as canceling {@code source} itself does.
   */
  private static <T, R> CompletableFuture<R> following(
      CompletableFuture<T> source, Function<T, R> then) {
    CompletableFuture<R> result = source.thenApply(then);
    result.whenComplete(
        (_, _) -> {
          if (result.isCancelled()) {
            source.cancel(false);
          }
        });
    return result;
  }

  /**
   * Does what the entry {@code id} of {@code commands} does: runs its action off the UI thread,
   * flips its check mark, or plays its role. 0 and a number of no entry do nothing.
   */
  void picked(MenuCommands commands, int id) {
    switch (commands.item(id)) {
      case ActionMenuItem action -> HandlerUtil.runOffTheUiThread(action.action());
      case CheckMenuItem check -> {
        boolean state = commands.toggle(id);
        if (check.onToggle() != null) {
          HandlerUtil.runOffTheUiThread(() -> check.onToggle().accept(state));
        }
      }
      case RoleMenuItem role -> this.play(role.role());
      case null, default -> {}
    }
  }

  /**
   * Plays {@code role} on the window. An editing role waits for the menu to close, which gives the
   * keyboard back to the page, on the UI thread; the rest run off it, like an action.
   */
  private void play(MenuRole role) {
    if (role.isEditing()) {
      this.window
          .dispatcher()
          .post(
              () -> {
                if (!this.window.isClosed()) {
                  this.window.performEditing(role);
                }
              });
      return;
    }
    HandlerUtil.runOffTheUiThread(
        switch (role) {
          case CLOSE_WINDOW -> this.window::requestClose;
          case MINIMIZE -> this.window::minimize;
          case FULLSCREEN -> () -> this.window.fullscreen(!this.window.isFullscreen());
          case QUIT -> this.window.application()::quit;
          case UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL ->
              throw new IllegalStateException("An editing role: " + role);
        });
  }

  /**
   * {@code window.lwjwae.menu} and the context menu of the page: the body is {@code context} or
   * {@code popup}, the place, and for a popup the entries, see {@link BridgeProtocol#parseMenu}. A
   * popup answers with the ID that the page gave the entry picked, empty for none. A page that
   * abandons the call closes the menu.
   */
  void serve(RpcCall call) throws Exception {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 4);
    if (parts.length < 3) {
      throw RpcException.badRequest("malformed-menu", "Malformed menu");
    }
    WindowPosition place;
    try {
      place =
          parts[1].isEmpty() || parts[2].isEmpty()
              ? null
              : new WindowPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    } catch (NumberFormatException _) {
      throw RpcException.badRequest(
          "malformed-menu", "Malformed place: " + parts[1] + ", " + parts[2]);
    }
    MenuCommands commands;
    Map<MenuItem, String> pageIds;
    switch (parts[0]) {
      case "context" -> {
        commands = this.contextCommands;
        pageIds = Map.of();
      }
      case "popup" -> {
        this.window.require(PageCapability.MENUS);
        PageMenu menu = parts.length < 4 ? null : BridgeProtocol.parseMenu(parts[3]);
        if (menu == null) {
          throw RpcException.badRequest("malformed-menu", "Malformed entries");
        }
        commands = new MenuCommands(menu.items());
        pageIds = menu.pageIds();
      }
      default -> throw RpcException.badRequest("malformed-menu", "Malformed menu: " + parts[0]);
    }
    if (commands == null || commands.isEmpty()) {
      call.reply("");
      return;
    }
    CompletableFuture<Integer> picked = this.popUp(commands, place);
    // A page that gives the call up interrupts this thread.
    try {
      if (call.isCancelled()) {
        throw new InterruptedException();
      }
      MenuItem item = commands.item(picked.get());
      call.reply(item == null ? "" : pageIds.getOrDefault(item, ""));
    } catch (InterruptedException e) {
      picked.cancel(false);
      throw e;
    }
  }
}
