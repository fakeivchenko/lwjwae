package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of GTK 4 that the backend needs.
 *
 * <p>Every function here must be called on the GTK thread. The class itself enforces nothing,
 * because {@link dev.ivchenko.lwjwae.gtk4.Gtk4Dispatcher} is the only intended caller.
 *
 * <p>GTK 4 has no placement API at all: no {@code gtk_window_move} and no way to read where a
 * window is, on X11 as on Wayland, so nothing here places a window.
 */
@UtilityClass
public class Gtk {
  private final SymbolLookup GTK = NativeLibraries.load("libgtk-4.so.1", "libgtk-4.so");

  private final MethodHandle INIT_CHECK =
      NativeLibraries.downcall(GTK, "gtk_init_check", Signatures.INT_VOID);
  private final MethodHandle WINDOW_NEW =
      NativeLibraries.downcall(GTK, "gtk_window_new", Signatures.POINTER_VOID);
  private final MethodHandle WINDOW_SET_CHILD =
      NativeLibraries.downcall(GTK, "gtk_window_set_child", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_TITLE =
      NativeLibraries.downcall(GTK, "gtk_window_set_title", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_GET_TITLE =
      NativeLibraries.downcall(GTK, "gtk_window_get_title", Signatures.POINTER_POINTER);
  private final MethodHandle WINDOW_SET_DEFAULT_SIZE =
      NativeLibraries.downcall(GTK, "gtk_window_set_default_size", Signatures.VOID_POINTER_INT_INT);
  private final MethodHandle WINDOW_GET_DEFAULT_SIZE =
      NativeLibraries.downcall(
          GTK, "gtk_window_get_default_size", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_RESIZABLE =
      NativeLibraries.downcall(GTK, "gtk_window_set_resizable", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_GET_RESIZABLE =
      NativeLibraries.downcall(GTK, "gtk_window_get_resizable", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_MINIMIZE =
      NativeLibraries.downcall(GTK, "gtk_window_minimize", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_UNMINIMIZE =
      NativeLibraries.downcall(GTK, "gtk_window_unminimize", Signatures.VOID_POINTER);
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
  private final MethodHandle WINDOW_IS_FULLSCREEN =
      NativeLibraries.downcall(GTK, "gtk_window_is_fullscreen", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_IS_ACTIVE =
      NativeLibraries.downcall(GTK, "gtk_window_is_active", Signatures.INT_POINTER);
  private final MethodHandle WIDGET_GET_WIDTH =
      NativeLibraries.downcall(GTK, "gtk_widget_get_width", Signatures.INT_POINTER);
  private final MethodHandle WIDGET_GET_HEIGHT =
      NativeLibraries.downcall(GTK, "gtk_widget_get_height", Signatures.INT_POINTER);
  private final MethodHandle WIDGET_SET_SIZE_REQUEST =
      NativeLibraries.downcall(GTK, "gtk_widget_set_size_request", Signatures.VOID_POINTER_INT_INT);
  private final MethodHandle NATIVE_GET_SURFACE =
      NativeLibraries.downcall(GTK, "gtk_native_get_surface", Signatures.POINTER_POINTER);
  private final MethodHandle TOPLEVEL_GET_STATE =
      NativeLibraries.downcall(GTK, "gdk_toplevel_get_state", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_PRESENT =
      NativeLibraries.downcall(GTK, "gtk_window_present", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_CLOSE =
      NativeLibraries.downcall(GTK, "gtk_window_close", Signatures.VOID_POINTER);
  private final MethodHandle WINDOW_DESTROY =
      NativeLibraries.downcall(GTK, "gtk_window_destroy", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_SET_VISIBLE =
      NativeLibraries.downcall(GTK, "gtk_widget_set_visible", Signatures.VOID_POINTER_INT);
  private final MethodHandle WIDGET_GET_VISIBLE =
      NativeLibraries.downcall(GTK, "gtk_widget_get_visible", Signatures.INT_POINTER);
  // --- dialogs ---
  private final MethodHandle FILE_CHOOSER_NATIVE_NEW =
      NativeLibraries.downcall(
          GTK, "gtk_file_chooser_native_new", Signatures.GTK_FILE_CHOOSER_NATIVE_NEW);
  private final MethodHandle NATIVE_DIALOG_SHOW =
      NativeLibraries.downcall(GTK, "gtk_native_dialog_show", Signatures.VOID_POINTER);
  private final MethodHandle NATIVE_DIALOG_HIDE =
      NativeLibraries.downcall(GTK, "gtk_native_dialog_hide", Signatures.VOID_POINTER);
  private final MethodHandle FILE_CHOOSER_SET_SELECT_MULTIPLE =
      NativeLibraries.downcall(
          GTK, "gtk_file_chooser_set_select_multiple", Signatures.VOID_POINTER_INT);
  private final MethodHandle FILE_CHOOSER_SET_CURRENT_FOLDER =
      NativeLibraries.downcall(
          GTK, "gtk_file_chooser_set_current_folder", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle FILE_CHOOSER_SET_CURRENT_NAME =
      NativeLibraries.downcall(
          GTK, "gtk_file_chooser_set_current_name", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle FILE_CHOOSER_ADD_FILTER =
      NativeLibraries.downcall(GTK, "gtk_file_chooser_add_filter", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle FILE_CHOOSER_GET_FILES =
      NativeLibraries.downcall(GTK, "gtk_file_chooser_get_files", Signatures.POINTER_POINTER);
  private final MethodHandle FILE_FILTER_NEW =
      NativeLibraries.downcall(GTK, "gtk_file_filter_new", Signatures.POINTER_VOID);
  private final MethodHandle FILE_FILTER_SET_NAME =
      NativeLibraries.downcall(GTK, "gtk_file_filter_set_name", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle FILE_FILTER_ADD_PATTERN =
      NativeLibraries.downcall(GTK, "gtk_file_filter_add_pattern", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle MESSAGE_DIALOG_GET_TYPE =
      NativeLibraries.downcall(GTK, "gtk_message_dialog_get_type", Signatures.LONG_VOID);
  private final MethodHandle MESSAGE_TYPE_GET_TYPE =
      NativeLibraries.downcall(GTK, "gtk_message_type_get_type", Signatures.LONG_VOID);
  private final MethodHandle DIALOG_ADD_BUTTON =
      NativeLibraries.downcall(
          GTK, "gtk_dialog_add_button", Signatures.POINTER_POINTER_POINTER_INT);
  private final MethodHandle DIALOG_SET_DEFAULT_RESPONSE =
      NativeLibraries.downcall(GTK, "gtk_dialog_set_default_response", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_SET_TRANSIENT_FOR =
      NativeLibraries.downcall(
          GTK, "gtk_window_set_transient_for", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_MODAL =
      NativeLibraries.downcall(GTK, "gtk_window_set_modal", Signatures.VOID_POINTER_INT);
  private final MethodHandle WINDOW_SET_TITLEBAR =
      NativeLibraries.downcall(GTK, "gtk_window_set_titlebar", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_DELETABLE =
      NativeLibraries.downcall(GTK, "gtk_window_set_deletable", Signatures.VOID_POINTER_INT);
  private final MethodHandle BOX_NEW =
      NativeLibraries.downcall(GTK, "gtk_box_new", Signatures.POINTER_INT_INT);
  private final MethodHandle HEADER_BAR_NEW =
      NativeLibraries.downcall(GTK, "gtk_header_bar_new", Signatures.POINTER_VOID);
  private final MethodHandle HEADER_BAR_SET_DECORATION_LAYOUT =
      NativeLibraries.downcall(
          GTK, "gtk_header_bar_set_decoration_layout", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WIDGET_ADD_CSS_CLASS =
      NativeLibraries.downcall(GTK, "gtk_widget_add_css_class", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SETTINGS_GET_DEFAULT =
      NativeLibraries.downcall(GTK, "gtk_settings_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_DEFAULT =
      NativeLibraries.downcall(GTK, "gdk_display_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_DEFAULT_SEAT =
      NativeLibraries.downcall(GTK, "gdk_display_get_default_seat", Signatures.POINTER_POINTER);
  private final MethodHandle SEAT_GET_POINTER =
      NativeLibraries.downcall(GTK, "gdk_seat_get_pointer", Signatures.POINTER_POINTER);
  private final MethodHandle SURFACE_GET_DEVICE_POSITION =
      NativeLibraries.downcall(
          GTK, "gdk_surface_get_device_position", Signatures.GDK_SURFACE_GET_DEVICE_POSITION);
  private final MethodHandle TOPLEVEL_BEGIN_MOVE =
      NativeLibraries.downcall(GTK, "gdk_toplevel_begin_move", Signatures.GDK_TOPLEVEL_BEGIN_MOVE);
  private final MethodHandle TOPLEVEL_BEGIN_RESIZE =
      NativeLibraries.downcall(
          GTK, "gdk_toplevel_begin_resize", Signatures.GDK_TOPLEVEL_BEGIN_RESIZE);

  /** {@code GTK_ORIENTATION_HORIZONTAL}. */
  public final int ORIENTATION_HORIZONTAL = 0;

  /** {@code GDK_SURFACE_EDGE_NORTH_WEST}; the other edges follow clockwise from the west ones. */
  public final int EDGE_NORTH_WEST = 0;

  /** {@code GDK_SURFACE_EDGE_NORTH}. */
  public final int EDGE_NORTH = 1;

  /** {@code GDK_SURFACE_EDGE_NORTH_EAST}. */
  public final int EDGE_NORTH_EAST = 2;

  /** {@code GDK_SURFACE_EDGE_WEST}. */
  public final int EDGE_WEST = 3;

  /** {@code GDK_SURFACE_EDGE_EAST}. */
  public final int EDGE_EAST = 4;

  /** {@code GDK_SURFACE_EDGE_SOUTH_WEST}. */
  public final int EDGE_SOUTH_WEST = 5;

  /** {@code GDK_SURFACE_EDGE_SOUTH}. */
  public final int EDGE_SOUTH = 6;

  /** {@code GDK_SURFACE_EDGE_SOUTH_EAST}. */
  public final int EDGE_SOUTH_EAST = 7;

  /** {@code GDK_BUTTON1_MASK}: the first button is down. */
  private final int BUTTON1_MASK = 1 << 8;

  /** {@code GDK_CURRENT_TIME}. */
  private final int CURRENT_TIME = 0;

  /**
   * Calls {@code gtk_init_check()}: initializes GTK on the calling thread, which becomes the GTK
   * thread for the rest of the process.
   *
   * @return True if GTK opened a display; false otherwise.
   */
  @SneakyThrows
  public boolean initialize() {
    return (int) INIT_CHECK.invokeExact() != 0;
  }

  /**
   * Calls {@code gtk_window_new}. Unlike every other widget, a toplevel window is owned by GTK, not
   * floating: it lives until {@link #windowDestroy}.
   */
  @SneakyThrows
  public MemorySegment windowNew() {
    return (MemorySegment) WINDOW_NEW.invokeExact();
  }

  /** Calls {@code gtk_window_set_child}: the window takes the only child it has. */
  @SneakyThrows
  public void windowSetChild(MemorySegment window, MemorySegment child) {
    WINDOW_SET_CHILD.invokeExact(window, child);
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

  /**
   * Calls {@code gtk_window_set_default_size}. In GTK 4, this is also how a window that is already
   * on screen is resized: there is no {@code gtk_window_resize} anymore.
   */
  @SneakyThrows
  public void windowSetDefaultSize(MemorySegment window, int width, int height) {
    WINDOW_SET_DEFAULT_SIZE.invokeExact(window, width, height);
  }

  /**
   * Returns {@code {width, height}} from {@code gtk_window_get_default_size}. GTK 4 keeps the
   * default size in step with the size of the window, including resizes by the user.
   */
  @SneakyThrows
  public int[] windowGetDefaultSize(MemorySegment window) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment width = arena.allocate(Signatures.C_INT);
      MemorySegment height = arena.allocate(Signatures.C_INT);
      WINDOW_GET_DEFAULT_SIZE.invokeExact(window, width, height);
      return new int[] {width.get(Signatures.C_INT, 0), height.get(Signatures.C_INT, 0)};
    }
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

  /** Calls {@code gtk_window_present}: shows the window and asks the desktop to raise it. */
  @SneakyThrows
  public void windowPresent(MemorySegment window) {
    WINDOW_PRESENT.invokeExact(window);
  }

  /**
   * Calls {@code gtk_window_close}: emits the {@code close-request} that the close button of the
   * title bar sends, so the window's own handler decides, and destroys the window unless it says
   * no.
   */
  @SneakyThrows
  public void windowClose(MemorySegment window) {
    WINDOW_CLOSE.invokeExact(window);
  }

  /** Calls {@code gtk_window_destroy}: emits {@code destroy} synchronously before returning. */
  @SneakyThrows
  public void windowDestroy(MemorySegment window) {
    WINDOW_DESTROY.invokeExact(window);
  }

  /** Calls {@code gtk_widget_set_visible}: hides a widget and keeps it, or shows it again. */
  @SneakyThrows
  public void widgetSetVisible(MemorySegment widget, boolean visible) {
    WIDGET_SET_VISIBLE.invokeExact(widget, visible ? 1 : 0);
  }

  /** Calls {@code gtk_widget_get_visible}: whether the widget is shown, not hidden. */
  @SneakyThrows
  public boolean isWidgetVisible(MemorySegment widget) {
    return (int) WIDGET_GET_VISIBLE.invokeExact(widget) != 0;
  }

  /** {@code GDK_TOPLEVEL_STATE_MINIMIZED}. */
  private final int STATE_MINIMIZED = 1;

  /** Calls {@code gtk_window_minimize} or {@code gtk_window_unminimize}. */
  @SneakyThrows
  public void windowSetMinimized(MemorySegment window, boolean minimized) {
    if (minimized) {
      WINDOW_MINIMIZE.invokeExact(window);
    } else {
      WINDOW_UNMINIMIZE.invokeExact(window);
    }
  }

  /**
   * Whether the surface of the window is minimized, by {@code gdk_toplevel_get_state}; {@code
   * false} for a window that has no surface yet.
   */
  @SneakyThrows
  public boolean isWindowMinimized(MemorySegment window) {
    MemorySegment surface = (MemorySegment) NATIVE_GET_SURFACE.invokeExact(window);
    return !surface.equals(MemorySegment.NULL)
        && ((int) TOPLEVEL_GET_STATE.invokeExact(surface) & STATE_MINIMIZED) != 0;
  }

  /** Calls {@code gtk_window_maximize} or {@code gtk_window_unmaximize}. */
  @SneakyThrows
  public void windowSetMaximized(MemorySegment window, boolean maximized) {
    if (maximized) {
      WINDOW_MAXIMIZE.invokeExact(window);
    } else {
      WINDOW_UNMAXIMIZE.invokeExact(window);
    }
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

  /** Calls {@code gtk_window_is_fullscreen}. */
  @SneakyThrows
  public boolean isWindowFullscreen(MemorySegment window) {
    return (int) WINDOW_IS_FULLSCREEN.invokeExact(window) != 0;
  }

  /** Calls {@code gtk_window_is_active}: whether the window has the keyboard focus. */
  @SneakyThrows
  public boolean isWindowActive(MemorySegment window) {
    return (int) WINDOW_IS_ACTIVE.invokeExact(window) != 0;
  }

  /**
   * Calls {@code gtk_widget_set_size_request}: the widget, and so the window around it, gets no
   * smaller. {@code -1} leaves a dimension to the natural size.
   */
  @SneakyThrows
  public void widgetSetSizeRequest(MemorySegment widget, int width, int height) {
    WIDGET_SET_SIZE_REQUEST.invokeExact(widget, width, height);
  }

  /**
   * {@code {width, height}} that the widget was last allocated, by {@code gtk_widget_get_width} and
   * {@code gtk_widget_get_height}; zero for a widget that was never shown.
   */
  @SneakyThrows
  public int[] widgetSize(MemorySegment widget) {
    return new int[] {
      (int) WIDGET_GET_WIDTH.invokeExact(widget), (int) WIDGET_GET_HEIGHT.invokeExact(widget)
    };
  }

  /** Calls {@code gtk_native_get_surface}: the surface of a realized window. */
  @SneakyThrows
  public MemorySegment windowSurface(MemorySegment window) {
    return (MemorySegment) NATIVE_GET_SURFACE.invokeExact(window);
  }

  /**
   * Calls {@code gtk_window_set_titlebar}: the widget takes the place of the title bar, and the
   * window draws its own decorations. Before the window is shown.
   */
  @SneakyThrows
  public void windowSetTitlebar(MemorySegment window, MemorySegment titlebar) {
    WINDOW_SET_TITLEBAR.invokeExact(window, titlebar);
  }

  /** Calls {@code gtk_window_set_deletable}: whether the title bar has a close button. */
  @SneakyThrows
  public void windowSetDeletable(MemorySegment window, boolean deletable) {
    WINDOW_SET_DELETABLE.invokeExact(window, deletable ? 1 : 0);
  }

  /** Calls {@code gtk_box_new}. */
  @SneakyThrows
  public MemorySegment boxNew(int orientation, int spacing) {
    return (MemorySegment) BOX_NEW.invokeExact(orientation, spacing);
  }

  /** Calls {@code gtk_header_bar_new}: a bar that shows the title of its window. */
  @SneakyThrows
  public MemorySegment headerBarNew() {
    return (MemorySegment) HEADER_BAR_NEW.invokeExact();
  }

  /** Calls {@code gtk_header_bar_set_decoration_layout}: which window buttons, on which side. */
  @SneakyThrows
  public void headerBarSetDecorationLayout(MemorySegment headerBar, String layout) {
    try (Arena arena = Arena.ofConfined()) {
      HEADER_BAR_SET_DECORATION_LAYOUT.invokeExact(headerBar, arena.allocateFrom(layout));
    }
  }

  /** Calls {@code gtk_widget_add_css_class}. */
  @SneakyThrows
  public void widgetAddCssClass(MemorySegment widget, String cssClass) {
    try (Arena arena = Arena.ofConfined()) {
      WIDGET_ADD_CSS_CLASS.invokeExact(widget, arena.allocateFrom(cssClass));
    }
  }

  /** Calls {@code gtk_settings_get_default}: the settings of the desktop, which GTK owns. */
  @SneakyThrows
  public MemorySegment settingsGetDefault() {
    return (MemorySegment) SETTINGS_GET_DEFAULT.invokeExact();
  }

  /**
   * Hands the pointer to the window manager, which moves the window until the first button is
   * released: {@code gdk_toplevel_begin_move} with the pointer of the default seat, where it is on
   * the surface. Does nothing once the button is up, as it may be after a quick click: a window
   * manager of X11 would start a move all the same and keep it until the next click.
   */
  @SneakyThrows
  public void toplevelBeginMove(MemorySegment surface) {
    MemorySegment pointer = defaultPointer();
    try (Arena arena = Arena.ofConfined()) {
      double[] position = pressedPosition(arena, surface, pointer);
      if (position != null) {
        TOPLEVEL_BEGIN_MOVE.invokeExact(
            surface, pointer, 1, position[0], position[1], CURRENT_TIME);
      }
    }
  }

  /**
   * The same as {@link #toplevelBeginMove} for a resize from a {@code GdkSurfaceEdge}: {@code
   * gdk_toplevel_begin_resize}.
   */
  @SneakyThrows
  public void toplevelBeginResize(MemorySegment surface, int edge) {
    MemorySegment pointer = defaultPointer();
    try (Arena arena = Arena.ofConfined()) {
      double[] position = pressedPosition(arena, surface, pointer);
      if (position != null) {
        TOPLEVEL_BEGIN_RESIZE.invokeExact(
            surface, edge, pointer, 1, position[0], position[1], CURRENT_TIME);
      }
    }
  }

  @SneakyThrows
  private MemorySegment defaultPointer() {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    MemorySegment seat = (MemorySegment) DISPLAY_GET_DEFAULT_SEAT.invokeExact(display);
    return (MemorySegment) SEAT_GET_POINTER.invokeExact(seat);
  }

  /** Where the pointer is on the surface, or {@code null} when its first button is up. */
  @SneakyThrows
  private double[] pressedPosition(Arena arena, MemorySegment surface, MemorySegment device) {
    MemorySegment x = arena.allocate(Signatures.C_DOUBLE);
    MemorySegment y = arena.allocate(Signatures.C_DOUBLE);
    MemorySegment mask = arena.allocate(Signatures.C_INT);
    int _ = (int) SURFACE_GET_DEVICE_POSITION.invokeExact(surface, device, x, y, mask);
    if ((mask.get(Signatures.C_INT, 0) & BUTTON1_MASK) == 0) {
      return null;
    }
    return new double[] {x.get(Signatures.C_DOUBLE, 0), y.get(Signatures.C_DOUBLE, 0)};
  }

  /** {@code GTK_FILE_CHOOSER_ACTION_OPEN}. */
  public final int FILE_CHOOSER_ACTION_OPEN = 0;

  /** {@code GTK_FILE_CHOOSER_ACTION_SAVE}. */
  public final int FILE_CHOOSER_ACTION_SAVE = 1;

  /** {@code GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER}. */
  public final int FILE_CHOOSER_ACTION_SELECT_FOLDER = 2;

  /** {@code GTK_RESPONSE_ACCEPT}: the native file chooser picked something. */
  public final int RESPONSE_ACCEPT = -3;

  /** {@code GTK_RESPONSE_OK}. */
  public final int RESPONSE_OK = -5;

  /** {@code GTK_RESPONSE_CANCEL}. */
  public final int RESPONSE_CANCEL = -6;

  /** {@code GTK_RESPONSE_YES}. */
  public final int RESPONSE_YES = -8;

  /** {@code GTK_RESPONSE_NO}. */
  public final int RESPONSE_NO = -9;

  /**
   * Calls {@code gtk_file_chooser_native_new} with the default labels: a file chooser that the
   * desktop portal shows inside a sandbox, and GTK outside one. The caller owns it.
   *
   * @param title The title, or {@code null} for GTK's own.
   */
  @SneakyThrows
  public MemorySegment fileChooserNativeNew(String title, MemorySegment parent, int action) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text = title == null ? MemorySegment.NULL : arena.allocateFrom(title);
      return (MemorySegment)
          FILE_CHOOSER_NATIVE_NEW.invokeExact(
              text, parent, action, MemorySegment.NULL, MemorySegment.NULL);
    }
  }

  /** Calls {@code gtk_native_dialog_show}: shows it and returns; {@code response} answers. */
  @SneakyThrows
  public void nativeDialogShow(MemorySegment dialog) {
    NATIVE_DIALOG_SHOW.invokeExact(dialog);
  }

  /** Calls {@code gtk_native_dialog_hide}: closes it without a {@code response}. */
  @SneakyThrows
  public void nativeDialogHide(MemorySegment dialog) {
    NATIVE_DIALOG_HIDE.invokeExact(dialog);
  }

  /** Calls {@code gtk_file_chooser_set_select_multiple}. */
  @SneakyThrows
  public void fileChooserSetSelectMultiple(MemorySegment chooser, boolean multiple) {
    FILE_CHOOSER_SET_SELECT_MULTIPLE.invokeExact(chooser, multiple ? 1 : 0);
  }

  /** Calls {@code gtk_file_chooser_set_current_folder} with a {@code GFile} of {@code folder}. */
  @SneakyThrows
  public void fileChooserSetCurrentFolder(MemorySegment chooser, String folder) {
    MemorySegment file = Glib.fileForPath(folder);
    try {
      int _ = (int) FILE_CHOOSER_SET_CURRENT_FOLDER.invokeExact(chooser, file, MemorySegment.NULL);
    } finally {
      Glib.unref(file);
    }
  }

  /** Calls {@code gtk_file_chooser_set_current_name}: the name that a save dialog proposes. */
  @SneakyThrows
  public void fileChooserSetCurrentName(MemorySegment chooser, String name) {
    try (Arena arena = Arena.ofConfined()) {
      FILE_CHOOSER_SET_CURRENT_NAME.invokeExact(chooser, arena.allocateFrom(name));
    }
  }

  /**
   * Adds a filter named {@code name} that shows the files that match any of {@code patterns}, glob
   * patterns such as {@code *.png}.
   */
  @SneakyThrows
  public void fileChooserAddFilter(MemorySegment chooser, String name, List<String> patterns) {
    MemorySegment filter = (MemorySegment) FILE_FILTER_NEW.invokeExact();
    try (Arena arena = Arena.ofConfined()) {
      FILE_FILTER_SET_NAME.invokeExact(filter, arena.allocateFrom(name));
      for (String pattern : patterns) {
        FILE_FILTER_ADD_PATTERN.invokeExact(filter, arena.allocateFrom(pattern));
      }
    }
    FILE_CHOOSER_ADD_FILTER.invokeExact(chooser, filter);
    // The chooser holds a reference of its own; the new filter came with one for the caller.
    Glib.unref(filter);
  }

  /** The local paths that the chooser picked, from {@code gtk_file_chooser_get_files}. */
  @SneakyThrows
  public List<String> fileChooserPaths(MemorySegment chooser) {
    return Glib.takeFilePaths((MemorySegment) FILE_CHOOSER_GET_FILES.invokeExact(chooser));
  }

  /** The {@code GType} of {@code GtkMessageDialog}. */
  @SneakyThrows
  public long messageDialogType() {
    return (long) MESSAGE_DIALOG_GET_TYPE.invokeExact();
  }

  /** The {@code GType} of the {@code GtkMessageType} enum. */
  @SneakyThrows
  public long messageTypeType() {
    return (long) MESSAGE_TYPE_GET_TYPE.invokeExact();
  }

  /** Calls {@code gtk_dialog_add_button}: a button that answers with {@code response}. */
  @SneakyThrows
  public void dialogAddButton(MemorySegment dialog, String label, int response) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment _ =
          (MemorySegment)
              DIALOG_ADD_BUTTON.invokeExact(dialog, arena.allocateFrom(label), response);
    }
  }

  /** Calls {@code gtk_dialog_set_default_response}: the button that Enter presses. */
  @SneakyThrows
  public void dialogSetDefaultResponse(MemorySegment dialog, int response) {
    DIALOG_SET_DEFAULT_RESPONSE.invokeExact(dialog, response);
  }

  /** Calls {@code gtk_window_set_transient_for}: the dialog stays over {@code parent}. */
  @SneakyThrows
  public void windowSetTransientFor(MemorySegment window, MemorySegment parent) {
    WINDOW_SET_TRANSIENT_FOR.invokeExact(window, parent);
  }

  /** Calls {@code gtk_window_set_modal}. */
  @SneakyThrows
  public void windowSetModal(MemorySegment window, boolean modal) {
    WINDOW_SET_MODAL.invokeExact(window, modal ? 1 : 0);
  }
}
