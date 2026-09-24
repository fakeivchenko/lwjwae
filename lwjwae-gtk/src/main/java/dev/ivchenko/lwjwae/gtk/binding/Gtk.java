package dev.ivchenko.lwjwae.gtk.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of GTK 3 that the backend needs.
 *
 * <p>Every function here must be called on the GTK thread. The class itself enforces nothing,
 * because {@link dev.ivchenko.lwjwae.gtk.GtkDispatcher} is the only intended caller.
 */
@UtilityClass
public class Gtk {
  private final SymbolLookup GTK = NativeLibraries.load("libgtk-3.so.0", "libgtk-3.so");

  /** {@code GTK_WINDOW_TOPLEVEL}. */
  public final int WINDOW_TOPLEVEL = 0;

  /** {@code GTK_WIN_POS_CENTER}: the window opens in the middle of the screen. */
  public final int WIN_POS_CENTER = 1;

  private final MethodHandle INIT_CHECK =
      NativeLibraries.downcall(GTK, "gtk_init_check", Signatures.INT_POINTER_POINTER);
  private final MethodHandle MAIN = NativeLibraries.downcall(GTK, "gtk_main", Signatures.VOID_VOID);
  private final MethodHandle WINDOW_NEW =
      NativeLibraries.downcall(GTK, "gtk_window_new", Signatures.POINTER_INT);
  private final MethodHandle WINDOW_SET_TITLE =
      NativeLibraries.downcall(GTK, "gtk_window_set_title", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_GET_TITLE =
      NativeLibraries.downcall(GTK, "gtk_window_get_title", Signatures.POINTER_POINTER);
  private final MethodHandle WINDOW_SET_DEFAULT_SIZE =
      NativeLibraries.downcall(GTK, "gtk_window_set_default_size", Signatures.VOID_POINTER_INT_INT);
  private final MethodHandle WINDOW_RESIZE =
      NativeLibraries.downcall(GTK, "gtk_window_resize", Signatures.VOID_POINTER_INT_INT);
  private final MethodHandle WINDOW_GET_SIZE =
      NativeLibraries.downcall(GTK, "gtk_window_get_size", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WINDOW_MOVE =
      NativeLibraries.downcall(GTK, "gtk_window_move", Signatures.VOID_POINTER_INT_INT);
  private final MethodHandle WINDOW_GET_POSITION =
      NativeLibraries.downcall(
          GTK, "gtk_window_get_position", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_POSITION =
      NativeLibraries.downcall(GTK, "gtk_window_set_position", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_SET_RESIZABLE =
      NativeLibraries.downcall(GTK, "gtk_window_set_resizable", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_GET_RESIZABLE =
      NativeLibraries.downcall(GTK, "gtk_window_get_resizable", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_ICONIFY =
      NativeLibraries.downcall(GTK, "gtk_window_iconify", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_DEICONIFY =
      NativeLibraries.downcall(GTK, "gtk_window_deiconify", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_MAXIMIZE =
      NativeLibraries.downcall(GTK, "gtk_window_maximize", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_UNMAXIMIZE =
      NativeLibraries.downcall(GTK, "gtk_window_unmaximize", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_IS_MAXIMIZED =
      NativeLibraries.downcall(GTK, "gtk_window_is_maximized", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_FULLSCREEN =
      NativeLibraries.downcall(GTK, "gtk_window_fullscreen", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_UNFULLSCREEN =
      NativeLibraries.downcall(GTK, "gtk_window_unfullscreen", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_SET_KEEP_ABOVE =
      NativeLibraries.downcall(GTK, "gtk_window_set_keep_above", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_IS_ACTIVE =
      NativeLibraries.downcall(GTK, "gtk_window_is_active", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_SET_GEOMETRY_HINTS =
      NativeLibraries.downcall(
          GTK, "gtk_window_set_geometry_hints", Signatures.VOID_POINTER_POINTER_POINTER_INT);
  private final MethodHandle CONTAINER_ADD =
      NativeLibraries.downcall(GTK, "gtk_container_add", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WIDGET_GET_WINDOW =
      NativeLibraries.downcall(GTK, "gtk_widget_get_window", Signatures.POINTER_POINTER);
  private final MethodHandle WIDGET_SHOW_ALL =
      NativeLibraries.downcall(GTK, "gtk_widget_show_all", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_DESTROY =
      NativeLibraries.downcall(GTK, "gtk_widget_destroy", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_HIDE =
      NativeLibraries.downcall(GTK, "gtk_widget_hide", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_GET_VISIBLE =
      NativeLibraries.downcall(GTK, "gtk_widget_get_visible", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_PRESENT =
      NativeLibraries.downcall(GTK, "gtk_window_present", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_CLOSE =
      NativeLibraries.downcall(GTK, "gtk_window_close", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_SET_SENSITIVE =
      NativeLibraries.downcall(GTK, "gtk_widget_set_sensitive", Signatures.VOID_POINTER_INT);

  // --- menus, for the tray ---
  private final MethodHandle MENU_NEW =
      NativeLibraries.downcall(GTK, "gtk_menu_new", Signatures.POINTER_VOID);
  private final MethodHandle MENU_ITEM_NEW_WITH_LABEL =
      NativeLibraries.downcall(GTK, "gtk_menu_item_new_with_label", Signatures.POINTER_POINTER);
  private final MethodHandle SEPARATOR_MENU_ITEM_NEW =
      NativeLibraries.downcall(GTK, "gtk_separator_menu_item_new", Signatures.POINTER_VOID);
  private final MethodHandle MENU_SHELL_APPEND =
      NativeLibraries.downcall(GTK, "gtk_menu_shell_append", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle MENU_POPUP_AT_POINTER =
      NativeLibraries.downcall(GTK, "gtk_menu_popup_at_pointer", Signatures.VOID_POINTER_POINTER);

  // --- GtkStatusIcon: the tray without a StatusNotifier host ---
  private final MethodHandle STATUS_ICON_NEW_FROM_FILE =
      NativeLibraries.downcall(GTK, "gtk_status_icon_new_from_file", Signatures.POINTER_POINTER);
  private final MethodHandle STATUS_ICON_SET_FROM_FILE =
      NativeLibraries.downcall(
          GTK, "gtk_status_icon_set_from_file", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle STATUS_ICON_SET_TOOLTIP_TEXT =
      NativeLibraries.downcall(
          GTK, "gtk_status_icon_set_tooltip_text", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle STATUS_ICON_SET_VISIBLE =
      NativeLibraries.downcall(GTK, "gtk_status_icon_set_visible", Signatures.VOID_POINTER_INT);

  /**
   * Calls {@code gtk_init_check(NULL, NULL)}: initializes GTK on the calling thread, which becomes
   * the GTK thread for the rest of the process.
   *
   * @return True if GTK opened a display; false otherwise.
   */
  @SneakyThrows
  public boolean initialize() {
    return (int) INIT_CHECK.invokeExact(MemorySegment.NULL, MemorySegment.NULL) != 0;
  }

  /** Calls {@code gtk_main()}: runs the event loop on the calling thread and doesn't return. */
  @SneakyThrows
  public void main() {
    MAIN.invokeExact();
  }

  /** Calls {@code gtk_window_new}. The widget is floating until a container takes it. */
  @SneakyThrows
  public MemorySegment windowNew(int type) {
    return (MemorySegment) WINDOW_NEW.invokeExact(type);
  }

  /** Calls {@code gtk_window_set_title}. {@code null} clears the title. */
  @SneakyThrows
  public void windowSetTitle(MemorySegment window, String title) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = title == null ? MemorySegment.NULL : arena.allocateFrom(title);
      WINDOW_SET_TITLE.invokeExact(window, value);
    }
  }

  /** Calls {@code gtk_window_get_title}. */
  @SneakyThrows
  public String windowGetTitle(MemorySegment window) {
    return NativeLibraries.string((MemorySegment) WINDOW_GET_TITLE.invokeExact(window));
  }

  /** Calls {@code gtk_window_set_default_size}: the size that the window opens with. */
  @SneakyThrows
  public void windowSetDefaultSize(MemorySegment window, int width, int height) {
    WINDOW_SET_DEFAULT_SIZE.invokeExact(window, width, height);
  }

  /** Calls {@code gtk_window_resize}: resizes a window that is already realized. */
  @SneakyThrows
  public void windowResize(MemorySegment window, int width, int height) {
    WINDOW_RESIZE.invokeExact(window, width, height);
  }

  /** Returns {@code {width, height}} of the window. */
  @SneakyThrows
  public int[] windowGetSize(MemorySegment window) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment width = arena.allocate(Signatures.C_INT);
      MemorySegment height = arena.allocate(Signatures.C_INT);
      WINDOW_GET_SIZE.invokeExact(window, width, height);
      return new int[] {width.get(Signatures.C_INT, 0), height.get(Signatures.C_INT, 0)};
    }
  }

  /**
   * Calls {@code gtk_window_move}: the position of the frame, from the top left of the screen.
   * Before the window is mapped, this is where it opens. On Wayland, this call does nothing.
   */
  @SneakyThrows
  public void windowMove(MemorySegment window, int x, int y) {
    WINDOW_MOVE.invokeExact(window, x, y);
  }

  /**
   * Returns {@code {x, y}} of the window frame. On Wayland, {@code {0, 0}}: the toolkit has no way
   * to know.
   */
  @SneakyThrows
  public int[] windowGetPosition(MemorySegment window) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment x = arena.allocate(Signatures.C_INT);
      MemorySegment y = arena.allocate(Signatures.C_INT);
      WINDOW_GET_POSITION.invokeExact(window, x, y);
      return new int[] {x.get(Signatures.C_INT, 0), y.get(Signatures.C_INT, 0)};
    }
  }

  /**
   * Calls {@code gtk_window_set_position} with a {@code GtkWindowPosition}: how the window is
   * placed when it is first mapped.
   */
  @SneakyThrows
  public void windowSetPosition(MemorySegment window, int position) {
    WINDOW_SET_POSITION.invokeExact(window, position);
  }

  /** Calls {@code gtk_window_set_resizable}. */
  @SneakyThrows
  public void windowSetResizable(MemorySegment window, boolean resizable) {
    WINDOW_SET_RESIZABLE.invokeExact(window, resizable ? 1 : 0);
  }

  /** Calls {@code gtk_window_get_resizable}. */
  @SneakyThrows
  public boolean isWindowResizable(MemorySegment window) {
    return (int) WINDOW_GET_RESIZABLE.invokeExact(window) != 0;
  }

  /** {@code GDK_HINT_MIN_SIZE}. */
  private final int HINT_MIN_SIZE = 1 << 1;

  /** {@code GDK_HINT_MAX_SIZE}. */
  private final int HINT_MAX_SIZE = 1 << 2;

  /**
   * {@code struct GdkGeometry}: the size limits first, then the base size, the increments, the
   * aspect ratios, and the gravity, none of which the backend sets.
   */
  private final MemoryLayout GEOMETRY =
      MemoryLayout.structLayout(
          MemoryLayout.sequenceLayout(8, Signatures.C_INT).withName("sizes"),
          MemoryLayout.sequenceLayout(2, ValueLayout.JAVA_DOUBLE).withName("aspects"),
          Signatures.C_INT.withName("gravity"),
          MemoryLayout.paddingLayout(4));

  /** Calls {@code gtk_window_iconify}. */
  @SneakyThrows
  public void windowIconify(MemorySegment window) {
    WINDOW_ICONIFY.invokeExact(window);
  }

  /** Calls {@code gtk_window_deiconify}. */
  @SneakyThrows
  public void windowDeiconify(MemorySegment window) {
    WINDOW_DEICONIFY.invokeExact(window);
  }

  /** Calls {@code gtk_window_maximize}. */
  @SneakyThrows
  public void windowMaximize(MemorySegment window) {
    WINDOW_MAXIMIZE.invokeExact(window);
  }

  /** Calls {@code gtk_window_unmaximize}. */
  @SneakyThrows
  public void windowUnmaximize(MemorySegment window) {
    WINDOW_UNMAXIMIZE.invokeExact(window);
  }

  /** Calls {@code gtk_window_is_maximized}. */
  @SneakyThrows
  public boolean isWindowMaximized(MemorySegment window) {
    return (int) WINDOW_IS_MAXIMIZED.invokeExact(window) != 0;
  }

  /** Calls {@code gtk_window_fullscreen} or {@code gtk_window_unfullscreen}. */
  @SneakyThrows
  public void windowSetFullscreen(MemorySegment window, boolean fullscreen) {
    if (fullscreen) {
      WINDOW_FULLSCREEN.invokeExact(window);
    } else {
      WINDOW_UNFULLSCREEN.invokeExact(window);
    }
  }

  /** Calls {@code gtk_window_set_keep_above}. */
  @SneakyThrows
  public void windowSetKeepAbove(MemorySegment window, boolean above) {
    WINDOW_SET_KEEP_ABOVE.invokeExact(window, above ? 1 : 0);
  }

  /** Calls {@code gtk_window_is_active}: whether the window has the keyboard focus. */
  @SneakyThrows
  public boolean isWindowActive(MemorySegment window) {
    return (int) WINDOW_IS_ACTIVE.invokeExact(window) != 0;
  }

  /**
   * Calls {@code gtk_window_set_geometry_hints} with the size limits of the window, which replace
   * the ones set before. Zero in a dimension means no limit there.
   */
  @SneakyThrows
  public void windowSetSizeLimits(
      MemorySegment window, int minWidth, int minHeight, int maxWidth, int maxHeight) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment geometry = arena.allocate(GEOMETRY);
      int[] sizes = {
        minWidth,
        minHeight,
        maxWidth == 0 ? Short.MAX_VALUE : maxWidth,
        maxHeight == 0 ? Short.MAX_VALUE : maxHeight
      };
      MemorySegment.copy(sizes, 0, geometry, Signatures.C_INT, 0, sizes.length);
      int hints =
          (minWidth > 0 || minHeight > 0 ? HINT_MIN_SIZE : 0)
              | (maxWidth > 0 || maxHeight > 0 ? HINT_MAX_SIZE : 0);
      WINDOW_SET_GEOMETRY_HINTS.invokeExact(window, MemorySegment.NULL, geometry, hints);
    }
  }

  /** Calls {@code gtk_container_add}: the container sinks the floating reference of the child. */
  @SneakyThrows
  public void containerAdd(MemorySegment container, MemorySegment child) {
    CONTAINER_ADD.invokeExact(container, child);
  }

  /**
   * Calls {@code gtk_widget_get_window}: the {@code GdkWindow} of a realized widget, or {@code
   * NULL} before that.
   */
  @SneakyThrows
  public MemorySegment widgetGetWindow(MemorySegment widget) {
    return (MemorySegment) WIDGET_GET_WINDOW.invokeExact(widget);
  }

  /** Calls {@code gtk_widget_show_all}: shows the widget and everything inside it. */
  @SneakyThrows
  public void widgetShowAll(MemorySegment widget) {
    WIDGET_SHOW_ALL.invokeExact(widget);
  }

  /** Calls {@code gtk_widget_destroy}: emits {@code destroy} synchronously before returning. */
  @SneakyThrows
  public void widgetDestroy(MemorySegment widget) {
    WIDGET_DESTROY.invokeExact(widget);
  }

  /** Calls {@code gtk_widget_hide}: unmaps the widget and keeps it. */
  @SneakyThrows
  public void widgetHide(MemorySegment widget) {
    WIDGET_HIDE.invokeExact(widget);
  }

  /** Calls {@code gtk_widget_get_visible}: whether the widget is shown, not hidden. */
  @SneakyThrows
  public boolean isWidgetVisible(MemorySegment widget) {
    return (int) WIDGET_GET_VISIBLE.invokeExact(widget) != 0;
  }

  /**
   * Calls {@code gtk_window_close}: queues the {@code delete-event} that the close button of the
   * title bar sends, so the window's own handler decides.
   */
  @SneakyThrows
  public void windowClose(MemorySegment window) {
    WINDOW_CLOSE.invokeExact(window);
  }

  /** Calls {@code gtk_window_present}: shows the window and asks the desktop to raise it. */
  @SneakyThrows
  public void windowPresent(MemorySegment window) {
    WINDOW_PRESENT.invokeExact(window);
  }

  /** Calls {@code gtk_widget_set_sensitive}: an insensitive widget is grayed out. */
  @SneakyThrows
  public void widgetSetSensitive(MemorySegment widget, boolean sensitive) {
    WIDGET_SET_SENSITIVE.invokeExact(widget, sensitive ? 1 : 0);
  }

  /** Calls {@code gtk_menu_new}. The menu is floating until something takes it. */
  @SneakyThrows
  public MemorySegment menuNew() {
    return (MemorySegment) MENU_NEW.invokeExact();
  }

  /** Calls {@code gtk_menu_item_new_with_label}. */
  @SneakyThrows
  public MemorySegment menuItemNewWithLabel(String label) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) MENU_ITEM_NEW_WITH_LABEL.invokeExact(arena.allocateFrom(label));
    }
  }

  /** Calls {@code gtk_separator_menu_item_new}. */
  @SneakyThrows
  public MemorySegment separatorMenuItemNew() {
    return (MemorySegment) SEPARATOR_MENU_ITEM_NEW.invokeExact();
  }

  /** Calls {@code gtk_menu_shell_append}: the menu sinks the floating reference of the item. */
  @SneakyThrows
  public void menuShellAppend(MemorySegment menu, MemorySegment item) {
    MENU_SHELL_APPEND.invokeExact(menu, item);
  }

  /** Calls {@code gtk_menu_popup_at_pointer} for the event being handled. */
  @SneakyThrows
  public void menuPopupAtPointer(MemorySegment menu) {
    MENU_POPUP_AT_POINTER.invokeExact(menu, MemorySegment.NULL);
  }

  /**
   * Calls {@code gtk_status_icon_new_from_file}. Deprecated in GTK since 3.14, but still in the
   * library, and the one tray that works without a StatusNotifier host.
   */
  @SneakyThrows
  public MemorySegment statusIconNewFromFile(String path) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) STATUS_ICON_NEW_FROM_FILE.invokeExact(arena.allocateFrom(path));
    }
  }

  /** Calls {@code gtk_status_icon_set_from_file}. */
  @SneakyThrows
  public void statusIconSetFromFile(MemorySegment icon, String path) {
    try (Arena arena = Arena.ofConfined()) {
      STATUS_ICON_SET_FROM_FILE.invokeExact(icon, arena.allocateFrom(path));
    }
  }

  /** Calls {@code gtk_status_icon_set_tooltip_text}. {@code null} clears the text. */
  @SneakyThrows
  public void statusIconSetTooltipText(MemorySegment icon, String text) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = text == null ? MemorySegment.NULL : arena.allocateFrom(text);
      STATUS_ICON_SET_TOOLTIP_TEXT.invokeExact(icon, value);
    }
  }

  /** Calls {@code gtk_status_icon_set_visible}. */
  @SneakyThrows
  public void statusIconSetVisible(MemorySegment icon, boolean visible) {
    STATUS_ICON_SET_VISIBLE.invokeExact(icon, visible ? 1 : 0);
  }
}
