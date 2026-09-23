package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of {@code user32.dll} that the backend needs: one top-level window per
 * webview, and the message loop of the thread.
 */
@UtilityClass
public class User32 {
  private final SymbolLookup USER32 = NativeLibraries.load("user32.dll");

  public final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
  public final int WS_THICKFRAME = 0x00040000;
  public final int WS_MAXIMIZEBOX = 0x00010000;
  public final int CW_USEDEFAULT = 0x80000000;
  public final int SW_HIDE = 0;
  public final int SW_SHOW = 5;
  public final int GWL_STYLE = -16;
  public final int GWLP_USERDATA = -21;
  public final int SWP_NOSIZE = 0x0001;
  public final int SWP_NOMOVE = 0x0002;
  public final int SWP_NOZORDER = 0x0004;
  public final int SWP_NOACTIVATE = 0x0010;
  public final int SWP_FRAMECHANGED = 0x0020;
  public final int MONITOR_DEFAULTTONEAREST = 2;
  public final int PM_NOREMOVE = 0x0000;
  public final int WM_DESTROY = 0x0002;
  public final int WM_SIZE = 0x0005;
  public final int WM_CLOSE = 0x0010;
  public final int WM_NULL = 0x0000;
  public final int WM_CONTEXTMENU = 0x007B;
  public final int WM_LBUTTONUP = 0x0202;
  public final int WM_RBUTTONUP = 0x0205;

  /** {@code MF_STRING}, {@code MF_GRAYED}, {@code MF_SEPARATOR}: the kinds of menu entry. */
  public final int MF_STRING = 0x0000;

  public final int MF_GRAYED = 0x0001;
  public final int MF_SEPARATOR = 0x0800;

  /**
   * {@code TPM_RETURNCMD | TPM_RIGHTBUTTON | TPM_NONOTIFY}: the menu answers with the entry picked.
   */
  public final int TPM_RETURNCMD_RIGHTBUTTON = 0x0100 | 0x0002 | 0x0080;

  /** {@code SM_CXSMICON}, {@code SM_CYSMICON}: the size of a small icon at the current DPI. */
  public final int SM_CXSMICON = 49;

  public final int SM_CYSMICON = 50;
  public final int WM_USER = 0x0400;
  public final int WM_APP = 0x8000;

  /** {@code COLOR_WINDOW + 1}: the class background brush that GTK-style applications use. */
  private final MemorySegment WINDOW_BACKGROUND = MemorySegment.ofAddress(5 + 1);

  /** {@code WS_EX_TOOLWINDOW}: no taskbar button, no Alt+Tab entry. */
  private final int WS_EX_TOOLWINDOW = 0x00000080;

  /** The icon resource format version that {@code CreateIconFromResourceEx} expects. */
  private final int ICON_RESOURCE_VERSION = 0x00030000;

  /** {@code IDC_ARROW}. */
  private final MemorySegment IDC_ARROW = MemorySegment.ofAddress(32512);

  /**
   * The resource ID of the application icon, when the executable carries one (see {@code app.rc}).
   */
  private final MemorySegment APPLICATION_ICON = MemorySegment.ofAddress(1);

  private final VarHandle RECT_LEFT =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("left"));
  private final VarHandle RECT_TOP =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("top"));
  private final VarHandle RECT_RIGHT =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("right"));
  private final VarHandle RECT_BOTTOM =
      Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("bottom"));
  private final VarHandle MSG_MESSAGE =
      Signatures.MSG.varHandle(MemoryLayout.PathElement.groupElement("message"));

  private final MethodHandle REGISTER_CLASS_EX =
      NativeLibraries.downcall(USER32, "RegisterClassExW", Signatures.SHORT_POINTER);
  private final MethodHandle CREATE_WINDOW_EX =
      NativeLibraries.downcall(USER32, "CreateWindowExW", Signatures.CREATE_WINDOW_EX);
  private final MethodHandle DEF_WINDOW_PROC =
      NativeLibraries.downcall(USER32, "DefWindowProcW", Signatures.LONG_POINTER_INT_LONG_LONG);
  private final MethodHandle SHOW_WINDOW =
      NativeLibraries.downcall(USER32, "ShowWindow", Signatures.INT_POINTER_INT);
  private final MethodHandle DESTROY_WINDOW =
      NativeLibraries.downcall(USER32, "DestroyWindow", Signatures.INT_POINTER);
  private final MethodHandle SEND_MESSAGE =
      NativeLibraries.downcall(USER32, "SendMessageW", Signatures.LONG_POINTER_INT_LONG_LONG);
  private final MethodHandle POST_MESSAGE =
      NativeLibraries.downcall(USER32, "PostMessageW", Signatures.INT_POINTER_INT_LONG_LONG);
  private final MethodHandle REGISTER_WINDOW_MESSAGE =
      NativeLibraries.downcall(USER32, "RegisterWindowMessageW", Signatures.INT_POINTER);
  private final MethodHandle CREATE_POPUP_MENU =
      NativeLibraries.downcall(USER32, "CreatePopupMenu", Signatures.POINTER_VOID);
  private final MethodHandle APPEND_MENU =
      NativeLibraries.downcall(USER32, "AppendMenuW", Signatures.INT_POINTER_INT_LONG_POINTER);
  private final MethodHandle TRACK_POPUP_MENU =
      NativeLibraries.downcall(
          USER32, "TrackPopupMenu", Signatures.INT_POINTER_INT_X4_POINTER_POINTER);
  private final MethodHandle DESTROY_MENU =
      NativeLibraries.downcall(USER32, "DestroyMenu", Signatures.INT_POINTER);
  private final MethodHandle GET_CURSOR_POS =
      NativeLibraries.downcall(USER32, "GetCursorPos", Signatures.INT_POINTER);
  private final MethodHandle GET_SYSTEM_METRICS =
      NativeLibraries.downcall(USER32, "GetSystemMetrics", Signatures.INT_INT);
  private final MethodHandle CREATE_ICON_FROM_RESOURCE_EX =
      NativeLibraries.downcall(
          USER32, "CreateIconFromResourceEx", Signatures.POINTER_POINTER_INT_X6);
  private final MethodHandle DESTROY_ICON =
      NativeLibraries.downcall(USER32, "DestroyIcon", Signatures.INT_POINTER);
  private final MethodHandle IS_WINDOW_VISIBLE =
      NativeLibraries.downcall(USER32, "IsWindowVisible", Signatures.INT_POINTER);
  private final MethodHandle SET_FOREGROUND_WINDOW =
      NativeLibraries.downcall(USER32, "SetForegroundWindow", Signatures.INT_POINTER);
  private final MethodHandle SET_WINDOW_TEXT =
      NativeLibraries.downcall(USER32, "SetWindowTextW", Signatures.INT_POINTER_POINTER);
  private final MethodHandle GET_WINDOW_TEXT_LENGTH =
      NativeLibraries.downcall(USER32, "GetWindowTextLengthW", Signatures.INT_POINTER);
  private final MethodHandle GET_WINDOW_TEXT =
      NativeLibraries.downcall(USER32, "GetWindowTextW", Signatures.INT_POINTER_POINTER_INT);
  private final MethodHandle GET_CLIENT_RECT =
      NativeLibraries.downcall(USER32, "GetClientRect", Signatures.INT_POINTER_POINTER);
  private final MethodHandle GET_WINDOW_RECT =
      NativeLibraries.downcall(USER32, "GetWindowRect", Signatures.INT_POINTER_POINTER);
  private final MethodHandle MONITOR_FROM_WINDOW =
      NativeLibraries.downcall(USER32, "MonitorFromWindow", Signatures.POINTER_POINTER_INT);
  private final MethodHandle GET_MONITOR_INFO =
      NativeLibraries.downcall(USER32, "GetMonitorInfoW", Signatures.INT_POINTER_POINTER);
  private final MethodHandle SET_WINDOW_POS =
      NativeLibraries.downcall(USER32, "SetWindowPos", Signatures.INT_POINTER_POINTER_INT_X5);
  private final MethodHandle GET_WINDOW_LONG_PTR =
      NativeLibraries.downcall(USER32, "GetWindowLongPtrW", Signatures.LONG_POINTER_INT);
  private final MethodHandle SET_WINDOW_LONG_PTR =
      NativeLibraries.downcall(USER32, "SetWindowLongPtrW", Signatures.LONG_POINTER_INT_LONG);
  private final MethodHandle ADJUST_WINDOW_RECT_EX =
      NativeLibraries.downcall(USER32, "AdjustWindowRectEx", Signatures.INT_POINTER_INT_INT_INT);
  private final MethodHandle GET_MESSAGE =
      NativeLibraries.downcall(USER32, "GetMessageW", Signatures.INT_POINTER_POINTER_INT_INT);
  private final MethodHandle PEEK_MESSAGE =
      NativeLibraries.downcall(USER32, "PeekMessageW", Signatures.INT_POINTER_POINTER_INT_INT_INT);
  private final MethodHandle TRANSLATE_MESSAGE =
      NativeLibraries.downcall(USER32, "TranslateMessage", Signatures.INT_POINTER);
  private final MethodHandle DISPATCH_MESSAGE =
      NativeLibraries.downcall(USER32, "DispatchMessageW", Signatures.LONG_POINTER);
  private final MethodHandle POST_THREAD_MESSAGE =
      NativeLibraries.downcall(USER32, "PostThreadMessageW", Signatures.INT_INT_INT_LONG_LONG);
  private final MethodHandle LOAD_CURSOR =
      NativeLibraries.downcall(USER32, "LoadCursorW", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle LOAD_ICON =
      NativeLibraries.downcall(USER32, "LoadIconW", Signatures.POINTER_POINTER_POINTER);

  /**
   * Registers a window class in which {@code windowProc} handles every window. Windows of the class
   * show the icon resource of the executable when there is one, as in a native image built with
   * {@code app.rc}, and the stock icon when there isn't, as under {@code java.exe}.
   */
  @SneakyThrows
  public void registerClass(String className, MemorySegment windowProc) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment module = Kernel32.moduleHandle();
      MemorySegment icon = (MemorySegment) LOAD_ICON.invokeExact(module, APPLICATION_ICON);
      MemorySegment wndClass = arena.allocate(Signatures.WNDCLASSEXW);
      wndClass.set(Signatures.C_INT, 0, (int) Signatures.WNDCLASSEXW.byteSize());
      wndClass.set(Signatures.C_POINTER, 8, windowProc);
      wndClass.set(Signatures.C_POINTER, 24, module);
      wndClass.set(Signatures.C_POINTER, 32, icon);
      wndClass.set(
          Signatures.C_POINTER,
          40,
          (MemorySegment) LOAD_CURSOR.invokeExact(MemorySegment.NULL, IDC_ARROW));
      wndClass.set(Signatures.C_POINTER, 48, WINDOW_BACKGROUND);
      wndClass.set(Signatures.C_POINTER, 64, Wide.allocate(arena, className));
      wndClass.set(Signatures.C_POINTER, 72, icon);
      short atom = (short) REGISTER_CLASS_EX.invokeExact(wndClass);
      if (atom == 0) {
        throw new IllegalStateException("RegisterClassExW failed, error " + Kernel32.lastError());
      }
    }
  }

  /**
   * A hidden top-level window of {@code className}. {@code show} makes it visible. {@code x} and
   * {@code y} place the frame; {@code CW_USEDEFAULT} for both lets Windows choose.
   */
  @SneakyThrows
  public MemorySegment createWindow(
      String className, String title, int x, int y, int width, int height) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hwnd =
          (MemorySegment)
              CREATE_WINDOW_EX.invokeExact(
                  0,
                  Wide.allocate(arena, className),
                  Wide.allocate(arena, title),
                  WS_OVERLAPPEDWINDOW,
                  x,
                  y,
                  width,
                  height,
                  MemorySegment.NULL,
                  MemorySegment.NULL,
                  Kernel32.moduleHandle(),
                  MemorySegment.NULL);
      if (hwnd.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("CreateWindowExW failed, error " + Kernel32.lastError());
      }
      return hwnd;
    }
  }

  /** Calls {@code DefWindowProcW}: the default handling of a message. */
  @SneakyThrows
  public long defWindowProc(
      MemorySegment hwnd, int message, long wordParameter, long longParameter) {
    return (long) DEF_WINDOW_PROC.invokeExact(hwnd, message, wordParameter, longParameter);
  }

  /** Calls {@code ShowWindow(hwnd, SW_SHOW)}. */
  @SneakyThrows
  public void show(MemorySegment hwnd) {
    int _ = (int) SHOW_WINDOW.invokeExact(hwnd, SW_SHOW);
  }

  /** Calls {@code ShowWindow(hwnd, SW_HIDE)}: the window leaves the screen and the taskbar. */
  @SneakyThrows
  public void hide(MemorySegment hwnd) {
    int _ = (int) SHOW_WINDOW.invokeExact(hwnd, SW_HIDE);
  }

  /**
   * Sends {@code WM_CLOSE}, the message of the close button of the title bar, through the window
   * procedure. Called on the thread of the window, it runs the procedure before it returns.
   */
  public void requestClose(MemorySegment hwnd) {
    long _ = send(hwnd, WM_CLOSE, 0L, 0L);
  }

  /**
   * Calls {@code SendMessageW}: runs the window procedure of {@code hwnd} with the message and
   * returns its answer. From another thread, it waits until the thread of the window handles it.
   */
  @SneakyThrows
  public long send(MemorySegment hwnd, int message, long wordParameter, long longParameter) {
    return (long) SEND_MESSAGE.invokeExact(hwnd, message, wordParameter, longParameter);
  }

  /** Calls {@code PostMessageW}: queues {@code message} for {@code hwnd} and returns at once. */
  @SneakyThrows
  public void post(MemorySegment hwnd, int message) {
    int _ = (int) POST_MESSAGE.invokeExact(hwnd, message, 0L, 0L);
  }

  /** Calls {@code RegisterWindowMessageW}: the process-wide number of a named message. */
  @SneakyThrows
  public int registerMessage(String name) {
    try (Arena arena = Arena.ofConfined()) {
      return (int) REGISTER_WINDOW_MESSAGE.invokeExact(Wide.allocate(arena, name));
    }
  }

  /**
   * A window of {@code className} that is never shown and has no taskbar button: a tool window,
   * which is what a tray icon needs to receive messages. It is a top-level window rather than a
   * message-only one, because only top-level windows hear broadcasts such as {@code
   * TaskbarCreated}.
   */
  @SneakyThrows
  public MemorySegment createHiddenToolWindow(String className) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hwnd =
          (MemorySegment)
              CREATE_WINDOW_EX.invokeExact(
                  WS_EX_TOOLWINDOW,
                  Wide.allocate(arena, className),
                  Wide.allocate(arena, className),
                  0,
                  0,
                  0,
                  0,
                  0,
                  MemorySegment.NULL,
                  MemorySegment.NULL,
                  Kernel32.moduleHandle(),
                  MemorySegment.NULL);
      if (hwnd.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("CreateWindowExW failed, error " + Kernel32.lastError());
      }
      return hwnd;
    }
  }

  /** Calls {@code CreatePopupMenu}: an empty menu, which {@link #destroyMenu} frees. */
  @SneakyThrows
  public MemorySegment createPopupMenu() {
    return (MemorySegment) CREATE_POPUP_MENU.invokeExact();
  }

  /**
   * Calls {@code AppendMenuW} with a text entry that {@link #trackPopupMenu} answers with {@code
   * id}.
   */
  @SneakyThrows
  public void appendMenuItem(MemorySegment menu, int id, String label, boolean enabled) {
    try (Arena arena = Arena.ofConfined()) {
      int flags = MF_STRING | (enabled ? 0 : MF_GRAYED);
      int _ = (int) APPEND_MENU.invokeExact(menu, flags, (long) id, Wide.allocate(arena, label));
    }
  }

  /** Calls {@code AppendMenuW} with a separator. */
  @SneakyThrows
  public void appendMenuSeparator(MemorySegment menu) {
    int _ = (int) APPEND_MENU.invokeExact(menu, MF_SEPARATOR, 0L, MemorySegment.NULL);
  }

  /**
   * Shows {@code menu} at the mouse pointer and waits for the user.
   *
   * @return The ID of the entry picked, or 0 when the menu was dismissed.
   */
  @SneakyThrows
  public int trackPopupMenu(MemorySegment menu, MemorySegment owner) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment point = arena.allocate(Signatures.POINT);
      int _ = (int) GET_CURSOR_POS.invokeExact(point);
      return (int)
          TRACK_POPUP_MENU.invokeExact(
              menu,
              TPM_RETURNCMD_RIGHTBUTTON,
              point.get(Signatures.C_INT, 0),
              point.get(Signatures.C_INT, 4),
              0,
              owner,
              MemorySegment.NULL);
    }
  }

  /** Calls {@code DestroyMenu}. */
  @SneakyThrows
  public void destroyMenu(MemorySegment menu) {
    int _ = (int) DESTROY_MENU.invokeExact(menu);
  }

  /**
   * Makes a small icon from PNG bytes with {@code CreateIconFromResourceEx}, which takes a PNG as
   * an icon image since Windows Vista. The size is the small-icon size of the current DPI.
   *
   * @throws IllegalArgumentException If Windows can't read the image.
   */
  @SneakyThrows
  public MemorySegment iconFromPng(byte[] png) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment bytes = arena.allocateFrom(Layouts.C_CHAR, png);
      int width = (int) GET_SYSTEM_METRICS.invokeExact(SM_CXSMICON);
      int height = (int) GET_SYSTEM_METRICS.invokeExact(SM_CYSMICON);
      MemorySegment icon =
          (MemorySegment)
              CREATE_ICON_FROM_RESOURCE_EX.invokeExact(
                  bytes, png.length, 1, ICON_RESOURCE_VERSION, width, height, 0);
      if (icon.equals(MemorySegment.NULL)) {
        throw new IllegalArgumentException(
            "CreateIconFromResourceEx can't read the image, error " + Kernel32.lastError());
      }
      return icon;
    }
  }

  /** Calls {@code DestroyIcon}. */
  @SneakyThrows
  public void destroyIcon(MemorySegment icon) {
    int _ = (int) DESTROY_ICON.invokeExact(icon);
  }

  /** Calls {@code IsWindowVisible}. */
  @SneakyThrows
  public boolean isVisible(MemorySegment hwnd) {
    return (int) IS_WINDOW_VISIBLE.invokeExact(hwnd) != 0;
  }

  /**
   * Calls {@code SetForegroundWindow}. Windows grants it only to the process the user last used, so
   * a window shown from the tray menu comes to the front, and one shown from a background timer may
   * only flash in the taskbar.
   */
  @SneakyThrows
  public void setForeground(MemorySegment hwnd) {
    int _ = (int) SET_FOREGROUND_WINDOW.invokeExact(hwnd);
  }

  /** {@code DestroyWindow}: sends {@code WM_DESTROY} synchronously before returning. */
  @SneakyThrows
  public void destroy(MemorySegment hwnd) {
    int _ = (int) DESTROY_WINDOW.invokeExact(hwnd);
  }

  /** Calls {@code SetWindowTextW}. {@code null} clears the title. */
  @SneakyThrows
  public void setTitle(MemorySegment hwnd, String title) {
    try (Arena arena = Arena.ofConfined()) {
      int _ =
          (int) SET_WINDOW_TEXT.invokeExact(hwnd, Wide.allocate(arena, title == null ? "" : title));
    }
  }

  /** Calls {@code GetWindowTextW}. */
  @SneakyThrows
  public String title(MemorySegment hwnd) {
    int length = (int) GET_WINDOW_TEXT_LENGTH.invokeExact(hwnd);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate((length + 1) * 2L);
      int _ = (int) GET_WINDOW_TEXT.invokeExact(hwnd, buffer, length + 1);
      return Wide.read(buffer);
    }
  }

  /** {@code {width, height}} of the client area. */
  @SneakyThrows
  public int[] clientSize(MemorySegment hwnd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.RECT);
      int _ = (int) GET_CLIENT_RECT.invokeExact(hwnd, rect);
      return new int[] {
        (int) RECT_RIGHT.get(rect, 0L) - (int) RECT_LEFT.get(rect, 0L),
        (int) RECT_BOTTOM.get(rect, 0L) - (int) RECT_TOP.get(rect, 0L)
      };
    }
  }

  /** {@code {left, top, right, bottom}} of the window frame, in screen coordinates. */
  @SneakyThrows
  public int[] windowRect(MemorySegment hwnd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.RECT);
      int _ = (int) GET_WINDOW_RECT.invokeExact(hwnd, rect);
      return new int[] {
        (int) RECT_LEFT.get(rect, 0L),
        (int) RECT_TOP.get(rect, 0L),
        (int) RECT_RIGHT.get(rect, 0L),
        (int) RECT_BOTTOM.get(rect, 0L)
      };
    }
  }

  /** Moves the frame of the window to {@code x}, {@code y} without resizing or raising it. */
  @SneakyThrows
  public void move(MemorySegment hwnd, int x, int y) {
    int _ =
        (int)
            SET_WINDOW_POS.invokeExact(
                hwnd, MemorySegment.NULL, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
  }

  /**
   * {@code {left, top, right, bottom}} of the work area of the monitor that holds most of the
   * window: the monitor minus the taskbar.
   */
  @SneakyThrows
  public int[] workArea(MemorySegment hwnd) {
    MemorySegment monitor =
        (MemorySegment) MONITOR_FROM_WINDOW.invokeExact(hwnd, MONITOR_DEFAULTTONEAREST);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment info = arena.allocate(Signatures.MONITORINFO);
      info.set(Signatures.C_INT, 0, (int) Signatures.MONITORINFO.byteSize());
      int _ = (int) GET_MONITOR_INFO.invokeExact(monitor, info);
      return new int[] {
        info.get(Signatures.C_INT, 20),
        info.get(Signatures.C_INT, 24),
        info.get(Signatures.C_INT, 28),
        info.get(Signatures.C_INT, 32)
      };
    }
  }

  /** Resizes the window so that its client area is {@code width} by {@code height}. */
  @SneakyThrows
  public void resizeClient(MemorySegment hwnd, int width, int height) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.RECT);
      RECT_RIGHT.set(rect, 0L, width);
      RECT_BOTTOM.set(rect, 0L, height);
      int _ = (int) ADJUST_WINDOW_RECT_EX.invokeExact(rect, (int) style(hwnd), 0, 0);
      int outerWidth = (int) RECT_RIGHT.get(rect, 0L) - (int) RECT_LEFT.get(rect, 0L);
      int outerHeight = (int) RECT_BOTTOM.get(rect, 0L) - (int) RECT_TOP.get(rect, 0L);
      int _ =
          (int)
              SET_WINDOW_POS.invokeExact(
                  hwnd,
                  MemorySegment.NULL,
                  0,
                  0,
                  outerWidth,
                  outerHeight,
                  SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
    }
  }

  /** Calls {@code GetWindowLongPtrW(hwnd, GWL_STYLE)}. */
  @SneakyThrows
  public long style(MemorySegment hwnd) {
    return (long) GET_WINDOW_LONG_PTR.invokeExact(hwnd, GWL_STYLE);
  }

  /**
   * Calls {@code SetWindowLongPtrW(hwnd, GWL_STYLE, style)} and redraws the frame, so the change
   * shows.
   */
  @SneakyThrows
  public void style(MemorySegment hwnd, long style) {
    long _ = (long) SET_WINDOW_LONG_PTR.invokeExact(hwnd, GWL_STYLE, style);
    int _ =
        (int)
            SET_WINDOW_POS.invokeExact(
                hwnd,
                MemorySegment.NULL,
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
  }

  /** The per-window {@code GWLP_USERDATA} slot, where a window records the backend that owns it. */
  @SneakyThrows
  public long userData(MemorySegment hwnd) {
    return (long) GET_WINDOW_LONG_PTR.invokeExact(hwnd, GWLP_USERDATA);
  }

  /** Writes the {@code GWLP_USERDATA} slot of a window. */
  @SneakyThrows
  public void userData(MemorySegment hwnd, long value) {
    long _ = (long) SET_WINDOW_LONG_PTR.invokeExact(hwnd, GWLP_USERDATA, value);
  }

  /**
   * Forces the message queue of the calling thread into existence. Until a thread has called a
   * message function, {@code PostThreadMessage} to the thread fails, and the first wake-up would be
   * lost.
   */
  @SneakyThrows
  public void ensureMessageQueue() {
    try (Arena arena = Arena.ofConfined()) {
      int _ =
          (int)
              PEEK_MESSAGE.invokeExact(
                  arena.allocate(Signatures.MSG),
                  MemorySegment.NULL,
                  WM_USER,
                  WM_USER,
                  PM_NOREMOVE);
    }
  }

  /** {@code GetMessageW}; {@code false} on {@code WM_QUIT} or error. */
  @SneakyThrows
  public boolean getMessage(MemorySegment message) {
    return (int) GET_MESSAGE.invokeExact(message, MemorySegment.NULL, 0, 0) > 0;
  }

  /** Reads {@code message} from a {@code MSG} struct filled by {@link #getMessage}. */
  public int messageId(MemorySegment message) {
    return (int) MSG_MESSAGE.get(message, 0L);
  }

  /**
   * Calls {@code TranslateMessage} and {@code DispatchMessageW}: routes a message to its window
   * procedure.
   */
  @SneakyThrows
  public void dispatch(MemorySegment message) {
    int _ = (int) TRANSLATE_MESSAGE.invokeExact(message);
    long _ = (long) DISPATCH_MESSAGE.invokeExact(message);
  }

  /** Posts a message to the queue of a thread. This method is safe to call from any thread. */
  @SneakyThrows
  public void postThreadMessage(int threadId, int message) {
    int _ = (int) POST_THREAD_MESSAGE.invokeExact(threadId, message, 0L, 0L);
  }
}
