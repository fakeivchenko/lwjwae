package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
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
}
