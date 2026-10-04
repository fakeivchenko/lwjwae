package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
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
  private final MethodHandle WINDOW_SET_STARTUP_ID =
      NativeLibraries.downcall(GTK, "gtk_window_set_startup_id", Signatures.VOID_POINTER_POINTER);
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
  private final MethodHandle WINDOW_GET_TRANSIENT_FOR =
      NativeLibraries.downcall(GTK, "gtk_window_get_transient_for", Signatures.POINTER_POINTER);
  private final MethodHandle WINDOW_GET_MODAL =
      NativeLibraries.downcall(GTK, "gtk_window_get_modal", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_HAS_GROUP =
      NativeLibraries.downcall(GTK, "gtk_window_has_group", Signatures.INT_POINTER);
  private final MethodHandle WINDOW_GET_GROUP =
      NativeLibraries.downcall(GTK, "gtk_window_get_group", Signatures.POINTER_POINTER);
  private final MethodHandle WINDOW_GROUP_NEW =
      NativeLibraries.downcall(GTK, "gtk_window_group_new", Signatures.POINTER_VOID);
  private final MethodHandle WINDOW_GROUP_ADD_WINDOW =
      NativeLibraries.downcall(GTK, "gtk_window_group_add_window", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_GROUP_LIST_WINDOWS =
      NativeLibraries.downcall(GTK, "gtk_window_group_list_windows", Signatures.POINTER_POINTER);
  private final MethodHandle WINDOW_SET_TITLEBAR =
      NativeLibraries.downcall(GTK, "gtk_window_set_titlebar", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WINDOW_SET_DECORATED =
      NativeLibraries.downcall(GTK, "gtk_window_set_decorated", Signatures.VOID_POINTER_INT);
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
  private final MethodHandle WIDGET_REMOVE_CSS_CLASS =
      NativeLibraries.downcall(GTK, "gtk_widget_remove_css_class", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SETTINGS_GET_DEFAULT =
      NativeLibraries.downcall(GTK, "gtk_settings_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_DEFAULT =
      NativeLibraries.downcall(GTK, "gdk_display_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_CLIPBOARD =
      NativeLibraries.downcall(GTK, "gdk_display_get_clipboard", Signatures.POINTER_POINTER);
  private final MethodHandle CLIPBOARD_SET_TEXT =
      NativeLibraries.downcall(GTK, "gdk_clipboard_set_text", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle CLIPBOARD_READ_TEXT_ASYNC =
      NativeLibraries.downcall(GTK, "gdk_clipboard_read_text_async", Signatures.VOID_POINTER_X4);
  private final MethodHandle CLIPBOARD_READ_TEXT_FINISH =
      NativeLibraries.downcall(
          GTK, "gdk_clipboard_read_text_finish", Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle CLIPBOARD_SET_TEXTURE =
      NativeLibraries.downcall(GTK, "gdk_clipboard_set_texture", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle CLIPBOARD_READ_TEXTURE_ASYNC =
      NativeLibraries.downcall(GTK, "gdk_clipboard_read_texture_async", Signatures.VOID_POINTER_X4);
  private final MethodHandle CLIPBOARD_READ_TEXTURE_FINISH =
      NativeLibraries.downcall(
          GTK, "gdk_clipboard_read_texture_finish", Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle TEXTURE_NEW_FROM_BYTES =
      NativeLibraries.downcall(
          GTK, "gdk_texture_new_from_bytes", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle TEXTURE_SAVE_TO_PNG_BYTES =
      NativeLibraries.downcall(GTK, "gdk_texture_save_to_png_bytes", Signatures.POINTER_POINTER);
  private final MethodHandle DISPLAY_GET_MONITORS =
      NativeLibraries.downcall(GTK, "gdk_display_get_monitors", Signatures.POINTER_POINTER);
  private final MethodHandle DISPLAY_GET_MONITOR_AT_SURFACE =
      NativeLibraries.downcall(
          GTK, "gdk_display_get_monitor_at_surface", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_GEOMETRY =
      NativeLibraries.downcall(GTK, "gdk_monitor_get_geometry", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_SCALE_FACTOR =
      NativeLibraries.downcall(GTK, "gdk_monitor_get_scale_factor", Signatures.INT_POINTER);
  // GTK 4.14 and later: the fractional scale of the monitor.
  private final MethodHandle MONITOR_GET_SCALE =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_monitor_get_scale").isPresent() ? GTK : null,
          "gdk_monitor_get_scale",
          Signatures.DOUBLE_POINTER);
  private final MethodHandle MONITOR_GET_MODEL =
      NativeLibraries.downcall(GTK, "gdk_monitor_get_model", Signatures.POINTER_POINTER);
  // GTK 4.10 and later: a description of the monitor for a person, such as "Built-in display".
  private final MethodHandle MONITOR_GET_DESCRIPTION =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_monitor_get_description").isPresent() ? GTK : null,
          "gdk_monitor_get_description",
          Signatures.POINTER_POINTER);
  private final MethodHandle MONITOR_GET_CONNECTOR =
      NativeLibraries.downcall(GTK, "gdk_monitor_get_connector", Signatures.POINTER_POINTER);
  private final MethodHandle X11_DISPLAY_GET_TYPE =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_x11_display_get_type").isPresent() ? GTK : null,
          "gdk_x11_display_get_type",
          Signatures.LONG_VOID);
  private final MethodHandle X11_DISPLAY_GET_XDISPLAY =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_x11_display_get_xdisplay").isPresent() ? GTK : null,
          "gdk_x11_display_get_xdisplay",
          Signatures.POINTER_POINTER);
  private final MethodHandle X11_DISPLAY_ERROR_TRAP_PUSH =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_x11_display_error_trap_push").isPresent() ? GTK : null,
          "gdk_x11_display_error_trap_push",
          Signatures.VOID_POINTER);
  private final MethodHandle X11_DISPLAY_ERROR_TRAP_POP =
      NativeLibraries.downcallIfPresent(
          GTK.find("gdk_x11_display_error_trap_pop").isPresent() ? GTK : null,
          "gdk_x11_display_error_trap_pop",
          Signatures.INT_POINTER);
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

  // --- menus of a window ---
  private final MethodHandle POPOVER_MENU_BAR_NEW_FROM_MODEL =
      NativeLibraries.downcall(
          GTK, "gtk_popover_menu_bar_new_from_model", Signatures.POINTER_POINTER);
  private final MethodHandle POPOVER_MENU_NEW_FROM_MODEL =
      NativeLibraries.downcall(GTK, "gtk_popover_menu_new_from_model", Signatures.POINTER_POINTER);
  private final MethodHandle BOX_APPEND =
      NativeLibraries.downcall(GTK, "gtk_box_append", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle BOX_PREPEND =
      NativeLibraries.downcall(GTK, "gtk_box_prepend", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle BOX_REMOVE =
      NativeLibraries.downcall(GTK, "gtk_box_remove", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WIDGET_SET_VEXPAND =
      NativeLibraries.downcall(GTK, "gtk_widget_set_vexpand", Signatures.VOID_POINTER_INT);
  private final MethodHandle WIDGET_SET_HALIGN =
      NativeLibraries.downcall(GTK, "gtk_widget_set_halign", Signatures.VOID_POINTER_INT);
  private final MethodHandle WIDGET_INSERT_ACTION_GROUP =
      NativeLibraries.downcall(
          GTK, "gtk_widget_insert_action_group", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle WIDGET_SET_PARENT =
      NativeLibraries.downcall(GTK, "gtk_widget_set_parent", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WIDGET_UNPARENT =
      NativeLibraries.downcall(GTK, "gtk_widget_unparent", Signatures.VOID_POINTER);
  private final MethodHandle WIDGET_TRANSLATE_COORDINATES =
      NativeLibraries.downcall(
          GTK, "gtk_widget_translate_coordinates", Signatures.GTK_WIDGET_TRANSLATE_COORDINATES);
  private final MethodHandle NATIVE_GET_SURFACE_TRANSFORM =
      NativeLibraries.downcall(
          GTK, "gtk_native_get_surface_transform", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle SHORTCUT_CONTROLLER_NEW =
      NativeLibraries.downcall(GTK, "gtk_shortcut_controller_new", Signatures.POINTER_VOID);
  private final MethodHandle SHORTCUT_CONTROLLER_ADD_SHORTCUT =
      NativeLibraries.downcall(
          GTK, "gtk_shortcut_controller_add_shortcut", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle EVENT_CONTROLLER_SET_PROPAGATION_PHASE =
      NativeLibraries.downcall(
          GTK, "gtk_event_controller_set_propagation_phase", Signatures.VOID_POINTER_INT);
  private final MethodHandle WIDGET_ADD_CONTROLLER =
      NativeLibraries.downcall(GTK, "gtk_widget_add_controller", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle DROP_CONTROLLER_MOTION_NEW =
      NativeLibraries.downcall(GTK, "gtk_drop_controller_motion_new", Signatures.POINTER_VOID);
  private final MethodHandle DROP_CONTROLLER_MOTION_GET_DROP =
      NativeLibraries.downcall(
          GTK, "gtk_drop_controller_motion_get_drop", Signatures.POINTER_POINTER);
  private final MethodHandle DROP_GET_FORMATS =
      NativeLibraries.downcall(GTK, "gdk_drop_get_formats", Signatures.POINTER_POINTER);
  private final MethodHandle CONTENT_FORMATS_CONTAIN_GTYPE =
      NativeLibraries.downcall(
          GTK, "gdk_content_formats_contain_gtype", Signatures.INT_POINTER_LONG);
  private final MethodHandle FILE_LIST_GET_TYPE =
      NativeLibraries.downcall(GTK, "gdk_file_list_get_type", Signatures.LONG_VOID);
  private final MethodHandle FILE_LIST_GET_FILES =
      NativeLibraries.downcall(GTK, "gdk_file_list_get_files", Signatures.POINTER_POINTER);
  private final MethodHandle DROP_READ_VALUE_ASYNC =
      NativeLibraries.downcall(
          GTK, "gdk_drop_read_value_async", Signatures.GDK_DROP_READ_VALUE_ASYNC);
  private final MethodHandle DROP_READ_VALUE_FINISH =
      NativeLibraries.downcall(
          GTK, "gdk_drop_read_value_finish", Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle VALUE_GET_BOXED =
      NativeLibraries.downcall(GTK, "g_value_get_boxed", Signatures.POINTER_POINTER);
  private final MethodHandle WIDGET_REMOVE_CONTROLLER =
      NativeLibraries.downcall(
          GTK, "gtk_widget_remove_controller", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SHORTCUT_TRIGGER_PARSE_STRING =
      NativeLibraries.downcall(
          GTK, "gtk_shortcut_trigger_parse_string", Signatures.POINTER_POINTER);
  private final MethodHandle NAMED_ACTION_NEW =
      NativeLibraries.downcall(GTK, "gtk_named_action_new", Signatures.POINTER_POINTER);
  private final MethodHandle SHORTCUT_NEW =
      NativeLibraries.downcall(GTK, "gtk_shortcut_new", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle POPOVER_SET_POINTING_TO =
      NativeLibraries.downcall(GTK, "gtk_popover_set_pointing_to", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle POPOVER_SET_HAS_ARROW =
      NativeLibraries.downcall(GTK, "gtk_popover_set_has_arrow", Signatures.VOID_POINTER_INT);
  private final MethodHandle POPOVER_POPUP =
      NativeLibraries.downcall(GTK, "gtk_popover_popup", Signatures.VOID_POINTER);
  private final MethodHandle POPOVER_POPDOWN =
      NativeLibraries.downcall(GTK, "gtk_popover_popdown", Signatures.VOID_POINTER);

  /** {@code GTK_PHASE_CAPTURE}: from the window down, before the widget with the focus. */
  private final int PHASE_CAPTURE = 1;

  /** {@code G_PRIORITY_DEFAULT}. */
  private final int G_PRIORITY_DEFAULT = 0;

  /** {@code GTK_ALIGN_START}. */
  private final int ALIGN_START = 1;

  /** {@code GdkRectangle}: x, y, width, height, each an {@code int}. */
  private final MemoryLayout RECTANGLE =
      MemoryLayout.structLayout(
          Signatures.C_INT, Signatures.C_INT, Signatures.C_INT, Signatures.C_INT);

  /** {@code GTK_ORIENTATION_HORIZONTAL}. */
  public final int ORIENTATION_HORIZONTAL = 0;

  /** {@code GTK_ORIENTATION_VERTICAL}. */
  public final int ORIENTATION_VERTICAL = 1;

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

  /**
   * Calls {@code gtk_window_set_startup_id}: the next present of the window hands {@code token} to
   * the compositor, which on Wayland is what lets the window take the focus.
   */
  @SneakyThrows
  public void windowSetStartupId(MemorySegment window, String token) {
    try (Arena arena = Arena.ofConfined()) {
      WINDOW_SET_STARTUP_ID.invokeExact(window, arena.allocateFrom(token));
    }
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

  /** The clipboard of the default display, which GDK owns. */
  @SneakyThrows
  public MemorySegment clipboard() {
    return (MemorySegment)
        DISPLAY_GET_CLIPBOARD.invokeExact((MemorySegment) DISPLAY_GET_DEFAULT.invokeExact());
  }

  /** Puts {@code text} on {@code clipboard}. */
  @SneakyThrows
  public void clipboardSetText(MemorySegment clipboard, String text) {
    try (Arena arena = Arena.ofConfined()) {
      CLIPBOARD_SET_TEXT.invokeExact(clipboard, arena.allocateFrom(text));
    }
  }

  /** Asks for the text of {@code clipboard}; {@code callback} gets the answer later. */
  @SneakyThrows
  public void clipboardReadTextAsync(
      MemorySegment clipboard, MemorySegment callback, MemorySegment userData) {
    CLIPBOARD_READ_TEXT_ASYNC.invokeExact(clipboard, MemorySegment.NULL, callback, userData);
  }

  /** The text that a read of {@code clipboard} found, or {@code null} for none. */
  @SneakyThrows
  public String clipboardReadTextFinish(MemorySegment clipboard, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
      MemorySegment text =
          (MemorySegment) CLIPBOARD_READ_TEXT_FINISH.invokeExact(clipboard, result, error);
      String _ = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
      return Glib.takeString(text);
    }
  }

  /**
   * Puts the image {@code png} on {@code clipboard}, as a {@code GdkTexture}, which GDK offers in
   * every image format that it writes.
   *
   * @throws IllegalArgumentException If GDK can't read the image.
   */
  @SneakyThrows
  public void clipboardSetImage(MemorySegment clipboard, byte[] png) {
    MemorySegment bytes = Glib.bytes(png);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
      MemorySegment texture = (MemorySegment) TEXTURE_NEW_FROM_BYTES.invokeExact(bytes, error);
      if (texture.equals(MemorySegment.NULL)) {
        throw new IllegalArgumentException(
            "Not an image GDK can read: "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      CLIPBOARD_SET_TEXTURE.invokeExact(clipboard, texture);
      Glib.unref(texture);
    } finally {
      Glib.unrefBytes(bytes);
    }
  }

  /** Asks for the image of {@code clipboard}; {@code callback} gets the answer later. */
  @SneakyThrows
  public void clipboardReadImageAsync(
      MemorySegment clipboard, MemorySegment callback, MemorySegment userData) {
    CLIPBOARD_READ_TEXTURE_ASYNC.invokeExact(clipboard, MemorySegment.NULL, callback, userData);
  }

  /** The image that a read of {@code clipboard} found, as PNG, or {@code null} for none. */
  @SneakyThrows
  public byte[] clipboardReadImageFinish(MemorySegment clipboard, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
      MemorySegment texture =
          (MemorySegment) CLIPBOARD_READ_TEXTURE_FINISH.invokeExact(clipboard, result, error);
      String _ = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
      if (texture.equals(MemorySegment.NULL)) {
        return null;
      }
      try {
        return Glib.takeBytes((MemorySegment) TEXTURE_SAVE_TO_PNG_BYTES.invokeExact(texture));
      } finally {
        Glib.unref(texture);
      }
    }
  }

  /**
   * The monitors of the default display, each with a reference that the caller gives back with
   * {@link Glib#unref}.
   */
  @SneakyThrows
  public List<MemorySegment> monitors() {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    return Glib.listItems((MemorySegment) DISPLAY_GET_MONITORS.invokeExact(display));
  }

  /**
   * The monitor that holds most of {@code surface}, with a reference that the caller gives back, or
   * {@code NULL} for a window that has no surface yet or that GDK can't place.
   */
  @SneakyThrows
  public MemorySegment monitorAt(MemorySegment surface) {
    if (surface.equals(MemorySegment.NULL)) {
      return MemorySegment.NULL;
    }
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    MemorySegment monitor =
        (MemorySegment) DISPLAY_GET_MONITOR_AT_SURFACE.invokeExact(display, surface);
    if (!monitor.equals(MemorySegment.NULL)) {
      Glib.ref(monitor);
    }
    return monitor;
  }

  /** {@code {x, y, width, height}} of the whole monitor, in the pixels of GDK. */
  @SneakyThrows
  public int[] monitorGeometry(MemorySegment monitor) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rectangle = arena.allocate(Signatures.C_INT, 4);
      MONITOR_GET_GEOMETRY.invokeExact(monitor, rectangle);
      return rectangle.toArray(Signatures.C_INT);
    }
  }

  /** The scale of the monitor: fractional from GTK 4.14 on, a whole number before. */
  @SneakyThrows
  public double monitorScale(MemorySegment monitor) {
    if (MONITOR_GET_SCALE != null) {
      return (double) MONITOR_GET_SCALE.invokeExact(monitor);
    }
    return (int) MONITOR_GET_SCALE_FACTOR.invokeExact(monitor);
  }

  /**
   * A name of the monitor for a person: its description from GTK 4.10 on, or else its connector,
   * such as {@code HDMI-1}, or else its model, or {@code null}.
   */
  @SneakyThrows
  public String monitorName(MemorySegment monitor) {
    if (MONITOR_GET_DESCRIPTION != null) {
      String description =
          NativeLibraries.string((MemorySegment) MONITOR_GET_DESCRIPTION.invokeExact(monitor));
      if (description != null && !description.isBlank()) {
        return description;
      }
    }
    String connector =
        NativeLibraries.string((MemorySegment) MONITOR_GET_CONNECTOR.invokeExact(monitor));
    return connector != null
        ? connector
        : NativeLibraries.string((MemorySegment) MONITOR_GET_MODEL.invokeExact(monitor));
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

  /**
   * Calls {@code gtk_window_set_decorated}: whether the window has a frame at all, the title bar,
   * the shadow, and the resize edges.
   */
  @SneakyThrows
  public void windowSetDecorated(MemorySegment window, boolean decorated) {
    WINDOW_SET_DECORATED.invokeExact(window, decorated ? 1 : 0);
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

  /**
   * Takes the background of the theme away from a window, which is a {@code background} CSS class
   * on it, so what its child leaves clear shows what is under the window. A surface of GTK 4 has an
   * alpha channel already.
   */
  @SneakyThrows
  public void windowClearBackground(MemorySegment window) {
    try (Arena arena = Arena.ofConfined()) {
      WIDGET_REMOVE_CSS_CLASS.invokeExact(window, arena.allocateFrom("background"));
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
    MemorySegment pointer = Gtk.defaultPointer();
    try (Arena arena = Arena.ofConfined()) {
      double[] position = Gtk.pressedPosition(arena, surface, pointer);
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
    MemorySegment pointer = Gtk.defaultPointer();
    try (Arena arena = Arena.ofConfined()) {
      double[] position = Gtk.pressedPosition(arena, surface, pointer);
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
   * desktop portal shows where GTK was told to use it, and GTK otherwise. The caller owns it.
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

  /** Calls {@code gtk_window_get_transient_for}: the parent, or {@code NULL}. */
  @SneakyThrows
  public MemorySegment windowTransientFor(MemorySegment window) {
    return (MemorySegment) WINDOW_GET_TRANSIENT_FOR.invokeExact(window);
  }

  /**
   * Puts {@code child} into the window group of {@code parent}, which gets a group of its own first
   * if it's still in the default one: a modal window keeps the user from the windows of its group
   * alone, and the default group holds every window of the process.
   */
  @SneakyThrows
  public void windowJoinGroupOf(MemorySegment child, MemorySegment parent) {
    MemorySegment group;
    if ((int) WINDOW_HAS_GROUP.invokeExact(parent) != 0) {
      group = (MemorySegment) WINDOW_GET_GROUP.invokeExact(parent);
    } else {
      group = (MemorySegment) WINDOW_GROUP_NEW.invokeExact();
      WINDOW_GROUP_ADD_WINDOW.invokeExact(group, parent);
      // The windows hold the group from now on.
      Glib.unref(group);
    }
    WINDOW_GROUP_ADD_WINDOW.invokeExact(group, child);
  }

  /**
   * Whether a modal window that is transient for {@code parent} is up in its window group, and so
   * keeps the user from it.
   */
  @SneakyThrows
  public boolean hasModalChild(MemorySegment parent) {
    if ((int) WINDOW_HAS_GROUP.invokeExact(parent) == 0) {
      return false;
    }
    MemorySegment group = (MemorySegment) WINDOW_GET_GROUP.invokeExact(parent);
    for (MemorySegment window :
        Glib.takeListItems((MemorySegment) WINDOW_GROUP_LIST_WINDOWS.invokeExact(group))) {
      if (Gtk.windowTransientFor(window).equals(parent)
          && (int) WINDOW_GET_MODAL.invokeExact(window) != 0
          && Gtk.isWidgetVisible(window)) {
        return true;
      }
    }
    return false;
  }

  /** Whether the default display is one of X11, where a client grabs keys itself. */
  @SneakyThrows
  public boolean isX11() {
    if (X11_DISPLAY_GET_TYPE == null) {
      return false;
    }
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    return Glib.typeCheckInstanceIsA(display, (long) X11_DISPLAY_GET_TYPE.invokeExact());
  }

  /** The Xlib {@code Display} of the default display, which {@link #isX11()} must be. */
  @SneakyThrows
  public MemorySegment xlibDisplay() {
    return (MemorySegment)
        X11_DISPLAY_GET_XDISPLAY.invokeExact((MemorySegment) DISPLAY_GET_DEFAULT.invokeExact());
  }

  /**
   * Runs {@code action} with the errors of X trapped instead of sent to the handler, which would
   * end the process, and returns the code of the error that it caused, or 0.
   */
  @SneakyThrows
  public int trapped(Runnable action) {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    X11_DISPLAY_ERROR_TRAP_PUSH.invokeExact(display);
    int error;
    try {
      action.run();
    } finally {
      error = (int) X11_DISPLAY_ERROR_TRAP_POP.invokeExact(display);
    }
    return error;
  }

  /** Connects {@code callback} to the {@code xevent} signal of the default display of X11. */
  @SneakyThrows
  public void connectXlibEvents(MemorySegment callback) {
    Glib.signalConnect(
        (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact(), "xevent", callback, MemorySegment.NULL);
  }

  /** Calls {@code gtk_popover_menu_bar_new_from_model}. */
  @SneakyThrows
  public MemorySegment popoverMenuBarNewFromModel(MemorySegment model) {
    return (MemorySegment) POPOVER_MENU_BAR_NEW_FROM_MODEL.invokeExact(model);
  }

  /** Calls {@code gtk_popover_menu_new_from_model}. */
  @SneakyThrows
  public MemorySegment popoverMenuNewFromModel(MemorySegment model) {
    return (MemorySegment) POPOVER_MENU_NEW_FROM_MODEL.invokeExact(model);
  }

  /** Calls {@code gtk_box_append}. */
  @SneakyThrows
  public void boxAppend(MemorySegment box, MemorySegment child) {
    BOX_APPEND.invokeExact(box, child);
  }

  /** Calls {@code gtk_box_prepend}. */
  @SneakyThrows
  public void boxPrepend(MemorySegment box, MemorySegment child) {
    BOX_PREPEND.invokeExact(box, child);
  }

  /** Calls {@code gtk_box_remove}, which drops the reference of the box to {@code child}. */
  @SneakyThrows
  public void boxRemove(MemorySegment box, MemorySegment child) {
    BOX_REMOVE.invokeExact(box, child);
  }

  /** Calls {@code gtk_widget_set_vexpand}. */
  @SneakyThrows
  public void widgetSetVexpand(MemorySegment widget, boolean expand) {
    WIDGET_SET_VEXPAND.invokeExact(widget, expand ? 1 : 0);
  }

  /**
   * Calls {@code gtk_widget_insert_action_group}: the actions of {@code group} go by {@code
   * prefix.name} for the widget and everything in it. A {@code NULL} group removes the prefix.
   */
  @SneakyThrows
  public void widgetInsertActionGroup(MemorySegment widget, String prefix, MemorySegment group) {
    try (Arena arena = Arena.ofConfined()) {
      WIDGET_INSERT_ACTION_GROUP.invokeExact(widget, arena.allocateFrom(prefix), group);
    }
  }

  /**
   * Adds a controller to {@code window} that runs the named actions of {@code shortcuts}, each a
   * pair of an accelerator such as {@code <Control>s} and an action such as {@code menu.item3},
   * before the widget with the focus sees the keys. The window owns the controller, which {@link
   * #widgetRemoveController} takes away.
   */
  @SneakyThrows
  public MemorySegment addShortcutController(MemorySegment window, List<String[]> shortcuts) {
    MemorySegment controller = (MemorySegment) SHORTCUT_CONTROLLER_NEW.invokeExact();
    EVENT_CONTROLLER_SET_PROPAGATION_PHASE.invokeExact(controller, PHASE_CAPTURE);
    try (Arena arena = Arena.ofConfined()) {
      for (String[] shortcut : shortcuts) {
        MemorySegment trigger =
            (MemorySegment)
                SHORTCUT_TRIGGER_PARSE_STRING.invokeExact(arena.allocateFrom(shortcut[0]));
        if (MemorySegment.NULL.equals(trigger)) {
          continue;
        }
        MemorySegment action =
            (MemorySegment) NAMED_ACTION_NEW.invokeExact(arena.allocateFrom(shortcut[1]));
        SHORTCUT_CONTROLLER_ADD_SHORTCUT.invokeExact(
            controller, (MemorySegment) SHORTCUT_NEW.invokeExact(trigger, action));
      }
    }
    WIDGET_ADD_CONTROLLER.invokeExact(window, controller);
    return controller;
  }

  /**
   * Adds a controller to {@code widget} that follows a drag over it without taking part in it, so
   * that the drop target of the widget itself still decides, and calls {@code onEnter} with {@code
   * userData} when a drag comes in. The widget owns the controller.
   */
  @SneakyThrows
  public MemorySegment addDropMotionController(
      MemorySegment widget, MemorySegment onEnter, MemorySegment userData) {
    MemorySegment controller = (MemorySegment) DROP_CONTROLLER_MOTION_NEW.invokeExact();
    Glib.signalConnect(controller, "enter", onEnter, userData);
    WIDGET_ADD_CONTROLLER.invokeExact(widget, controller);
    return controller;
  }

  /**
   * The {@code GdkDrop} of the drag that {@code controller} follows, if it offers a list of files,
   * or {@code NULL}.
   */
  @SneakyThrows
  public MemorySegment dropOfFiles(MemorySegment controller) {
    MemorySegment drop = (MemorySegment) DROP_CONTROLLER_MOTION_GET_DROP.invokeExact(controller);
    if (drop.equals(MemorySegment.NULL)) {
      return MemorySegment.NULL;
    }
    MemorySegment formats = (MemorySegment) DROP_GET_FORMATS.invokeExact(drop);
    long fileList = (long) FILE_LIST_GET_TYPE.invokeExact();
    return (int) CONTENT_FORMATS_CONTAIN_GTYPE.invokeExact(formats, fileList) != 0
        ? drop
        : MemorySegment.NULL;
  }

  /**
   * Reads the files of {@code drop} as a {@code GdkFileList}, and calls {@code callback} with
   * {@code userData} once they are read, on the GTK thread; {@link #dropReadFilesFinish} takes the
   * paths. The source can hand them over before the drop, as XDND and Wayland let it.
   */
  @SneakyThrows
  public void dropReadFilesAsync(
      MemorySegment drop, MemorySegment callback, MemorySegment userData) {
    long fileList = (long) FILE_LIST_GET_TYPE.invokeExact();
    DROP_READ_VALUE_ASYNC.invokeExact(
        drop, fileList, G_PRIORITY_DEFAULT, MemorySegment.NULL, callback, userData);
  }

  /** The local paths of the files that a read of {@code drop} found, empty for none or an error. */
  @SneakyThrows
  public List<String> dropReadFilesFinish(MemorySegment drop, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
      MemorySegment value = (MemorySegment) DROP_READ_VALUE_FINISH.invokeExact(drop, result, error);
      String _ = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
      if (value.equals(MemorySegment.NULL)) {
        return List.of();
      }
      MemorySegment list = (MemorySegment) VALUE_GET_BOXED.invokeExact(value);
      if (list.equals(MemorySegment.NULL)) {
        return List.of();
      }
      return Glib.takeFileListPaths((MemorySegment) FILE_LIST_GET_FILES.invokeExact(list));
    }
  }

  /** Calls {@code gtk_widget_remove_controller}. */
  @SneakyThrows
  public void widgetRemoveController(MemorySegment widget, MemorySegment controller) {
    WIDGET_REMOVE_CONTROLLER.invokeExact(widget, controller);
  }

  /**
   * Opens {@code popover}, a menu, with its top left corner at {@code x}, {@code y} in {@code
   * parent}, which it belongs to until {@link #widgetUnparent}.
   */
  @SneakyThrows
  public void popupAt(MemorySegment popover, MemorySegment parent, int x, int y) {
    WIDGET_SET_PARENT.invokeExact(popover, parent);
    POPOVER_SET_HAS_ARROW.invokeExact(popover, 0);
    WIDGET_SET_HALIGN.invokeExact(popover, ALIGN_START);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rectangle = arena.allocate(RECTANGLE);
      rectangle.set(Signatures.C_INT, 0, x);
      rectangle.set(Signatures.C_INT, 4, y);
      rectangle.set(Signatures.C_INT, 8, 1);
      rectangle.set(Signatures.C_INT, 12, 1);
      POPOVER_SET_POINTING_TO.invokeExact(popover, rectangle);
    }
    POPOVER_POPUP.invokeExact(popover);
  }

  /** Calls {@code gtk_popover_popdown}. */
  @SneakyThrows
  public void popoverPopdown(MemorySegment popover) {
    POPOVER_POPDOWN.invokeExact(popover);
  }

  /** Calls {@code gtk_widget_unparent}. */
  @SneakyThrows
  public void widgetUnparent(MemorySegment widget) {
    WIDGET_UNPARENT.invokeExact(widget);
  }

  /**
   * Where the pointer is in {@code widget}, a widget of {@code window}, as {@code {x, y}}. Wayland
   * tells a client where the pointer is only over its own surfaces.
   */
  @SneakyThrows
  public int[] pointerIn(MemorySegment window, MemorySegment widget) {
    MemorySegment pointer = Gtk.defaultPointer();
    MemorySegment surface = Gtk.windowSurface(window);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment x = arena.allocate(Signatures.C_DOUBLE);
      MemorySegment y = arena.allocate(Signatures.C_DOUBLE);
      MemorySegment mask = arena.allocate(Signatures.C_INT);
      int _ = (int) SURFACE_GET_DEVICE_POSITION.invokeExact(surface, pointer, x, y, mask);
      // The surface holds the shadow of the window too; the window starts inside it.
      MemorySegment offsetX = arena.allocate(Signatures.C_DOUBLE);
      MemorySegment offsetY = arena.allocate(Signatures.C_DOUBLE);
      NATIVE_GET_SURFACE_TRANSFORM.invokeExact(window, offsetX, offsetY);
      MemorySegment widgetX = arena.allocate(Signatures.C_DOUBLE);
      MemorySegment widgetY = arena.allocate(Signatures.C_DOUBLE);
      int _ =
          (int)
              WIDGET_TRANSLATE_COORDINATES.invokeExact(
                  window,
                  widget,
                  x.get(Signatures.C_DOUBLE, 0) - offsetX.get(Signatures.C_DOUBLE, 0),
                  y.get(Signatures.C_DOUBLE, 0) - offsetY.get(Signatures.C_DOUBLE, 0),
                  widgetX,
                  widgetY);
      return new int[] {
        (int) widgetX.get(Signatures.C_DOUBLE, 0), (int) widgetY.get(Signatures.C_DOUBLE, 0)
      };
    }
  }
}
