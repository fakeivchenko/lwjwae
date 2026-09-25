package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
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
  public final int WS_MINIMIZEBOX = 0x00020000;
  public final int CW_USEDEFAULT = 0x80000000;
  public final int SW_HIDE = 0;
  public final int SW_SHOW = 5;
  public final int SW_MAXIMIZE = 3;
  public final int SW_MINIMIZE = 6;
  public final int SW_RESTORE = 9;
  public final int GWL_STYLE = -16;
  public final int GWLP_USERDATA = -21;
  public final int SWP_NOSIZE = 0x0001;
  public final int SWP_NOMOVE = 0x0002;
  public final int SWP_NOZORDER = 0x0004;
  public final int SWP_NOACTIVATE = 0x0010;
  public final int SWP_FRAMECHANGED = 0x0020;
  public final int SWP_NOOWNERZORDER = 0x0200;
  private final int GWL_EXSTYLE = -20;
  private final long WS_EX_TOPMOST = 0x00000008;

  /** {@code sizeof(WINDOWPLACEMENT)}. */
  private final int WINDOWPLACEMENT_SIZE = 44;

  /** Where {@code ptMinTrackSize} and {@code ptMaxTrackSize} sit in a {@code MINMAXINFO}. */
  private final int MIN_TRACK_SIZE_OFFSET = 24;

  private final int MAX_TRACK_SIZE_OFFSET = 32;
  public final int MONITOR_DEFAULTTONEAREST = 2;

  /** {@code MONITORINFOF_PRIMARY}: the flag of the primary monitor in {@code MONITORINFO}. */
  private final int MONITORINFOF_PRIMARY = 1;

  public final int PM_NOREMOVE = 0x0000;
  public final int WM_DESTROY = 0x0002;
  public final int WM_MOVE = 0x0003;
  public final int WM_SIZE = 0x0005;
  public final int WM_ACTIVATE = 0x0006;
  public final int WM_CLOSE = 0x0010;
  public final int WM_GETMINMAXINFO = 0x0024;
  public final int WM_NCCALCSIZE = 0x0083;
  private final int WM_COMMAND = 0x0111;
  private final int WM_NCLBUTTONDOWN = 0x00A1;

  // --- hit-test codes of WM_NCHITTEST, what a press on the frame means ---
  public final int HTCAPTION = 2;
  public final int HTLEFT = 10;
  public final int HTRIGHT = 11;
  public final int HTTOP = 12;
  public final int HTTOPLEFT = 13;
  public final int HTTOPRIGHT = 14;
  public final int HTBOTTOM = 15;
  public final int HTBOTTOMLEFT = 16;
  public final int HTBOTTOMRIGHT = 17;

  private final int SC_CLOSE = 0xF060;
  private final int VK_LBUTTON = 0x01;
  private final int SM_SWAPBUTTON = 23;
  private final int VK_RBUTTON = 0x02;
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

  // --- MessageBoxW ---
  public final int MB_OK = 0x0;
  public final int MB_OKCANCEL = 0x1;
  public final int MB_YESNO = 0x4;
  public final int MB_ICONERROR = 0x10;
  public final int MB_ICONQUESTION = 0x20;
  public final int MB_ICONWARNING = 0x30;
  public final int MB_ICONINFORMATION = 0x40;
  public final int IDOK = 1;
  public final int IDCANCEL = 2;
  public final int IDYES = 6;

  /** {@code GW_ENABLEDPOPUP}: the enabled window that a window owns, a dialog over it. */
  private final int GW_ENABLEDPOPUP = 6;

  /** {@code HWND_MESSAGE}: the parent that makes a window message-only. */
  private final MemorySegment HWND_MESSAGE = MemorySegment.ofAddress(-3);

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
  private final MethodHandle GET_ASYNC_KEY_STATE =
      NativeLibraries.downcall(USER32, "GetAsyncKeyState", Signatures.SHORT_INT);
  private final MethodHandle RELEASE_CAPTURE =
      NativeLibraries.downcall(USER32, "ReleaseCapture", Signatures.INT_VOID);
  private final MethodHandle GET_SYSTEM_MENU =
      NativeLibraries.downcall(USER32, "GetSystemMenu", Signatures.POINTER_POINTER_INT);
  private final MethodHandle ENABLE_MENU_ITEM =
      NativeLibraries.downcall(USER32, "EnableMenuItem", Signatures.INT_POINTER_INT_INT);
  private final MethodHandle MESSAGE_BOX =
      NativeLibraries.downcall(USER32, "MessageBoxW", Signatures.INT_POINTER_POINTER_POINTER_INT);
  private final MethodHandle GET_WINDOW =
      NativeLibraries.downcall(USER32, "GetWindow", Signatures.POINTER_POINTER_INT);
  private final MethodHandle END_DIALOG =
      NativeLibraries.downcall(USER32, "EndDialog", Signatures.INT_POINTER_LONG);
  private final MethodHandle GET_SYSTEM_METRICS =
      NativeLibraries.downcall(USER32, "GetSystemMetrics", Signatures.INT_INT);
  private final MethodHandle CREATE_ICON_FROM_RESOURCE_EX =
      NativeLibraries.downcall(
          USER32, "CreateIconFromResourceEx", Signatures.POINTER_POINTER_INT_X6);
  private final MethodHandle DESTROY_ICON =
      NativeLibraries.downcall(USER32, "DestroyIcon", Signatures.INT_POINTER);
  private final MethodHandle IS_WINDOW_VISIBLE =
      NativeLibraries.downcall(USER32, "IsWindowVisible", Signatures.INT_POINTER);
  private final MethodHandle IS_ICONIC =
      NativeLibraries.downcall(USER32, "IsIconic", Signatures.INT_POINTER);
  private final MethodHandle IS_ZOOMED =
      NativeLibraries.downcall(USER32, "IsZoomed", Signatures.INT_POINTER);
  private final MethodHandle GET_FOREGROUND_WINDOW =
      NativeLibraries.downcall(USER32, "GetForegroundWindow", Signatures.POINTER_VOID);
  private final MethodHandle GET_WINDOW_THREAD_PROCESS_ID =
      NativeLibraries.downcall(USER32, "GetWindowThreadProcessId", Signatures.INT_POINTER_POINTER);
  private final MethodHandle ATTACH_THREAD_INPUT =
      NativeLibraries.downcall(USER32, "AttachThreadInput", Signatures.INT_INT_INT_INT);
  private final MethodHandle BRING_WINDOW_TO_TOP =
      NativeLibraries.downcall(USER32, "BringWindowToTop", Signatures.INT_POINTER);
  private final MethodHandle GET_WINDOW_PLACEMENT =
      NativeLibraries.downcall(USER32, "GetWindowPlacement", Signatures.INT_POINTER_POINTER);
  private final MethodHandle SET_WINDOW_PLACEMENT =
      NativeLibraries.downcall(USER32, "SetWindowPlacement", Signatures.INT_POINTER_POINTER);
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
  private final MethodHandle OPEN_CLIPBOARD =
      NativeLibraries.downcall(USER32, "OpenClipboard", Signatures.INT_POINTER);
  private final MethodHandle CLOSE_CLIPBOARD =
      NativeLibraries.downcall(USER32, "CloseClipboard", Signatures.INT_VOID);
  private final MethodHandle EMPTY_CLIPBOARD =
      NativeLibraries.downcall(USER32, "EmptyClipboard", Signatures.INT_VOID);
  private final MethodHandle SET_CLIPBOARD_DATA =
      NativeLibraries.downcall(USER32, "SetClipboardData", Signatures.POINTER_INT_POINTER);
  private final MethodHandle REGISTER_CLIPBOARD_FORMAT =
      NativeLibraries.downcall(USER32, "RegisterClipboardFormatW", Signatures.INT_POINTER);
  private final MethodHandle GET_CLIPBOARD_DATA =
      NativeLibraries.downcall(USER32, "GetClipboardData", Signatures.POINTER_INT);
  private final MethodHandle ENUM_DISPLAY_MONITORS =
      NativeLibraries.downcall(USER32, "EnumDisplayMonitors", Signatures.INT_POINTER_X3_LONG);
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
   * A hidden top-level window of {@code className}, with {@code style}, a subset of {@code
   * WS_OVERLAPPEDWINDOW}. {@code show} makes it visible. {@code x} and {@code y} place the frame;
   * {@code CW_USEDEFAULT} for both lets Windows choose. {@code topmost} creates it above the
   * windows that aren't, with {@code WS_EX_TOPMOST}: later, {@link #topmost(MemorySegment,
   * boolean)} works only for the process in the foreground.
   */
  @SneakyThrows
  public MemorySegment createWindow(
      String className,
      String title,
      int x,
      int y,
      int width,
      int height,
      int style,
      boolean topmost) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hwnd =
          (MemorySegment)
              CREATE_WINDOW_EX.invokeExact(
                  topmost ? (int) WS_EX_TOPMOST : 0,
                  Wide.allocate(arena, className),
                  Wide.allocate(arena, title),
                  style,
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
    long _ = User32.send(hwnd, WM_CLOSE, 0L, 0L);
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

  /**
   * A message-only window of {@code className}: never shown, never enumerated, it exists to receive
   * the messages posted to it, in the loop of a modal dialog as in the main one.
   */
  @SneakyThrows
  public MemorySegment createMessageWindow(String className) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hwnd =
          (MemorySegment)
              CREATE_WINDOW_EX.invokeExact(
                  0,
                  Wide.allocate(arena, className),
                  Wide.allocate(arena, className),
                  0,
                  0,
                  0,
                  0,
                  0,
                  HWND_MESSAGE,
                  MemorySegment.NULL,
                  Kernel32.moduleHandle(),
                  MemorySegment.NULL);
      if (hwnd.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("CreateWindowExW failed, error " + Kernel32.lastError());
      }
      return hwnd;
    }
  }

  /**
   * Calls {@code MessageBoxW} over {@code owner}: a modal dialog with its own loop, which returns
   * the {@code ID} of the button that closed it.
   */
  @SneakyThrows
  public int messageBox(MemorySegment owner, String text, String caption, int type) {
    try (Arena arena = Arena.ofConfined()) {
      return (int)
          MESSAGE_BOX.invokeExact(
              owner, Wide.allocate(arena, text), Wide.allocate(arena, caption), type);
    }
  }

  /**
   * Cancels the dialog that {@code owner} has up, as its Cancel button does: {@code WM_COMMAND}
   * with {@code IDCANCEL}. {@code IFileDialog::Close} answers {@code S_OK} from the loop of the
   * dialog and leaves it on screen. Nothing happens without a dialog.
   */
  @SneakyThrows
  public void cancelOwnedDialog(MemorySegment owner) {
    MemorySegment dialog = (MemorySegment) GET_WINDOW.invokeExact(owner, GW_ENABLEDPOPUP);
    if (!dialog.equals(MemorySegment.NULL) && !dialog.equals(owner)) {
      int _ = (int) POST_MESSAGE.invokeExact(dialog, WM_COMMAND, (long) IDCANCEL, 0L);
    }
  }

  /**
   * Closes the modal dialog that {@code owner} has up, a message box among them, as if its button
   * {@code result} was pressed, which a message box without a Cancel button needs. Call it on the
   * thread of the dialog, from a message that its loop dispatched; nothing happens without a
   * dialog.
   */
  @SneakyThrows
  public void endOwnedDialog(MemorySegment owner, int result) {
    MemorySegment dialog = (MemorySegment) GET_WINDOW.invokeExact(owner, GW_ENABLEDPOPUP);
    if (!dialog.equals(MemorySegment.NULL) && !dialog.equals(owner)) {
      int _ = (int) END_DIALOG.invokeExact(dialog, (long) result);
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

  /** Calls {@code ShowWindow} with one of the {@code SW_} commands. */
  @SneakyThrows
  public void showWindow(MemorySegment hwnd, int command) {
    int _ = (int) SHOW_WINDOW.invokeExact(hwnd, command);
  }

  /** Calls {@code IsIconic}: whether the window is minimized. */
  @SneakyThrows
  public boolean isMinimized(MemorySegment hwnd) {
    return (int) IS_ICONIC.invokeExact(hwnd) != 0;
  }

  /** Calls {@code IsZoomed}: whether the window is maximized. */
  @SneakyThrows
  public boolean isMaximized(MemorySegment hwnd) {
    return (int) IS_ZOOMED.invokeExact(hwnd) != 0;
  }

  /**
   * Whether {@code hwnd} is the window that the user works with, by {@code GetForegroundWindow}.
   */
  @SneakyThrows
  public boolean isForeground(MemorySegment hwnd) {
    return ((MemorySegment) GET_FOREGROUND_WINDOW.invokeExact()).address() == hwnd.address();
  }

  /**
   * Brings {@code hwnd} to the front and gives it the focus, from a process that may be in the
   * background.
   *
   * <p>{@code SetForegroundWindow} is granted only to the process that the user last worked with.
   * When it isn't, the thread of the window joins the input of the thread that owns the foreground
   * window for the time of the call, which puts the two on the same footing, and leaves again. This
   * is the one call that the application makes to come to the front on purpose, from its tray icon
   * or a second instance, so it's worth the step.
   */
  @SneakyThrows
  public void bringToFront(MemorySegment hwnd) {
    MemorySegment foreground = (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
    if (foreground.address() == hwnd.address()) {
      return;
    }
    int ours = Kernel32.currentThreadId();
    int theirs =
        foreground.equals(MemorySegment.NULL)
            ? ours
            : (int) GET_WINDOW_THREAD_PROCESS_ID.invokeExact(foreground, MemorySegment.NULL);
    boolean attached =
        theirs != ours && (int) ATTACH_THREAD_INPUT.invokeExact(theirs, ours, 1) != 0;
    try {
      int _ = (int) BRING_WINDOW_TO_TOP.invokeExact(hwnd);
      int _ = (int) SET_FOREGROUND_WINDOW.invokeExact(hwnd);
    } finally {
      if (attached) {
        int _ = (int) ATTACH_THREAD_INPUT.invokeExact(theirs, ours, 0);
      }
    }
  }

  /** Whether the window has {@code WS_EX_TOPMOST}: it stays above windows that don't. */
  @SneakyThrows
  public boolean isTopmost(MemorySegment hwnd) {
    return ((long) GET_WINDOW_LONG_PTR.invokeExact(hwnd, GWL_EXSTYLE) & WS_EX_TOPMOST) != 0;
  }

  /**
   * Puts the window into the topmost band of the z-order, or takes it out, without activating it.
   */
  @SneakyThrows
  public void topmost(MemorySegment hwnd, boolean topmost) {
    MemorySegment insertAfter = MemorySegment.ofAddress(topmost ? -1 : -2);
    int _ =
        (int)
            SET_WINDOW_POS.invokeExact(
                hwnd, insertAfter, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
  }

  /**
   * Calls {@code GetWindowPlacement}: the normal position, size, and state of the window, as the
   * bytes of a {@code WINDOWPLACEMENT} that {@link #placement(MemorySegment, byte[])} takes back.
   */
  @SneakyThrows
  public byte[] placement(MemorySegment hwnd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment placement = arena.allocate(WINDOWPLACEMENT_SIZE);
      placement.set(Signatures.C_INT, 0, WINDOWPLACEMENT_SIZE);
      int _ = (int) GET_WINDOW_PLACEMENT.invokeExact(hwnd, placement);
      return placement.toArray(ValueLayout.JAVA_BYTE);
    }
  }

  /** Calls {@code SetWindowPlacement} with what {@link #placement(MemorySegment)} returned. */
  @SneakyThrows
  public void placement(MemorySegment hwnd, byte[] placement) {
    try (Arena arena = Arena.ofConfined()) {
      int _ =
          (int)
              SET_WINDOW_PLACEMENT.invokeExact(
                  hwnd, arena.allocateFrom(ValueLayout.JAVA_BYTE, placement));
    }
  }

  /** Moves and resizes the frame of the window to cover {@code {left, top, right, bottom}}. */
  @SneakyThrows
  public void bounds(MemorySegment hwnd, int[] rect) {
    int _ =
        (int)
            SET_WINDOW_POS.invokeExact(
                hwnd,
                MemorySegment.NULL,
                rect[0],
                rect[1],
                rect[2] - rect[0],
                rect[3] - rect[1],
                SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_FRAMECHANGED);
  }

  /**
   * Writes the smallest and largest frame sizes that the user may resize the window to into the
   * {@code MINMAXINFO} of a {@code WM_GETMINMAXINFO}. {@code null} leaves a limit as it is.
   */
  public void sizeLimits(long minMaxInfo, int[] minimum, int[] maximum) {
    MemorySegment info = MemorySegment.ofAddress(minMaxInfo).reinterpret(40);
    if (minimum != null) {
      info.set(Signatures.C_INT, MIN_TRACK_SIZE_OFFSET, minimum[0]);
      info.set(Signatures.C_INT, MIN_TRACK_SIZE_OFFSET + 4, minimum[1]);
    }
    if (maximum != null) {
      info.set(Signatures.C_INT, MAX_TRACK_SIZE_OFFSET, maximum[0]);
      info.set(Signatures.C_INT, MAX_TRACK_SIZE_OFFSET + 4, maximum[1]);
    }
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

  /** {@code CF_UNICODETEXT}: text in UTF-16, the one text format that every application reads. */
  public final int CF_UNICODETEXT = 13;

  /** {@code CF_BITMAP}: an {@code HBITMAP}, from which Windows makes the DIB formats on request. */
  public final int CF_BITMAP = 2;

  /**
   * Opens the clipboard for {@code owner}, trying a few times over 100 milliseconds, since another
   * application may hold it for a moment.
   *
   * @return Whether it's open; {@link #closeClipboard()} closes it.
   */
  @SneakyThrows
  public boolean openClipboard(MemorySegment owner) {
    for (int attempt = 0; attempt < 10; attempt++) {
      if ((int) OPEN_CLIPBOARD.invokeExact(owner) != 0) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  /** Calls {@code CloseClipboard}. */
  @SneakyThrows
  public void closeClipboard() {
    int _ = (int) CLOSE_CLIPBOARD.invokeExact();
  }

  /** Empties the open clipboard and makes its owner the one that opened it. */
  @SneakyThrows
  public void emptyClipboard() {
    int _ = (int) EMPTY_CLIPBOARD.invokeExact();
  }

  /**
   * Adds {@code memory} in {@code format} to what the open clipboard has; the clipboard owns it
   * from then on.
   *
   * @return Whether the clipboard took it; the caller frees the memory when it didn't.
   */
  @SneakyThrows
  public boolean setClipboardData(int format, MemorySegment memory) {
    return !((MemorySegment) SET_CLIPBOARD_DATA.invokeExact(format, memory))
        .equals(MemorySegment.NULL);
  }

  /** The number of the clipboard format named {@code name}, registered on first use. */
  @SneakyThrows
  public int registerClipboardFormat(String name) {
    try (Arena arena = Arena.ofConfined()) {
      return (int) REGISTER_CLIPBOARD_FORMAT.invokeExact(Wide.allocate(arena, name));
    }
  }

  /** What the open clipboard has in {@code format}, which it keeps owning, or {@code NULL}. */
  @SneakyThrows
  public MemorySegment clipboardData(int format) {
    return (MemorySegment) GET_CLIPBOARD_DATA.invokeExact(format);
  }

  /**
   * Calls {@code callback}, a {@code MONITORENUMPROC}, for every monitor of the desktop, on the
   * calling thread, before it returns.
   */
  @SneakyThrows
  public void enumDisplayMonitors(MemorySegment callback) {
    int _ =
        (int)
            ENUM_DISPLAY_MONITORS.invokeExact(MemorySegment.NULL, MemorySegment.NULL, callback, 0L);
  }

  /** The monitor that holds most of the window, or the nearest one. */
  @SneakyThrows
  public MemorySegment monitorOf(MemorySegment hwnd) {
    return (MemorySegment) MONITOR_FROM_WINDOW.invokeExact(hwnd, MONITOR_DEFAULTTONEAREST);
  }

  /** What {@code GetMonitorInfoW} tells about {@code monitor}. */
  @SneakyThrows
  public MonitorInfo monitorInfo(MemorySegment monitor) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment info = arena.allocate(Signatures.MONITORINFOEX);
      info.set(Signatures.C_INT, 0, (int) Signatures.MONITORINFOEX.byteSize());
      int _ = (int) GET_MONITOR_INFO.invokeExact(monitor, info);
      int[] fields = info.asSlice(0, Signatures.MONITORINFO.byteSize()).toArray(Signatures.C_INT);
      return new MonitorInfo(
          new int[] {fields[1], fields[2], fields[3], fields[4]},
          new int[] {fields[5], fields[6], fields[7], fields[8]},
          (fields[9] & MONITORINFOF_PRIMARY) != 0,
          Wide.read(info.asSlice(Signatures.MONITORINFO.byteSize())));
    }
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

  /**
   * Resizes the window so that its client area is {@code width} by {@code height}. {@code titleBar}
   * is {@code false} for a window whose title bar {@link #removeTitleBar} takes away.
   */
  @SneakyThrows
  public void resizeClient(MemorySegment hwnd, int width, int height, boolean titleBar) {
    int[] frame = User32.frameSize(hwnd, width, height, titleBar);
    int _ =
        (int)
            SET_WINDOW_POS.invokeExact(
                hwnd,
                MemorySegment.NULL,
                0,
                0,
                frame[0],
                frame[1],
                SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
  }

  /**
   * {@code {width, height}} of the frame around a client area of {@code width} by {@code height},
   * with the current style of the window, by {@code AdjustWindowRectEx}. Without the title bar, the
   * frame has nothing above the client area.
   */
  @SneakyThrows
  public int[] frameSize(MemorySegment hwnd, int width, int height, boolean titleBar) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.RECT);
      RECT_RIGHT.set(rect, 0L, width);
      RECT_BOTTOM.set(rect, 0L, height);
      int _ = (int) ADJUST_WINDOW_RECT_EX.invokeExact(rect, (int) User32.style(hwnd), 0, 0);
      return new int[] {
        (int) RECT_RIGHT.get(rect, 0L) - (int) RECT_LEFT.get(rect, 0L),
        (int) RECT_BOTTOM.get(rect, 0L) - (titleBar ? (int) RECT_TOP.get(rect, 0L) : 0)
      };
    }
  }

  /**
   * Answers {@code WM_NCCALCSIZE} for a window without a title bar: the frame that {@code
   * DefWindowProc} works out, minus the part above the client area. The window keeps its resize
   * edges on the other sides, its shadow, and everything that its style gives a window with a title
   * bar: snapping, and the animations of minimize and maximize. A maximized window reaches past its
   * monitor by the width of its frame, which the client area leaves out at the top too.
   *
   * @param parameters The {@code NCCALCSIZE_PARAMS} of the message, whose first rectangle is the
   *     proposed window on the way in and the client area on the way out.
   * @return What the window procedure returns.
   */
  @SneakyThrows
  public long removeTitleBar(MemorySegment hwnd, long wordParameter, long parameters) {
    MemorySegment rect =
        MemorySegment.ofAddress(parameters).reinterpret(Signatures.RECT.byteSize());
    int windowLeft = (int) RECT_LEFT.get(rect, 0L);
    int windowTop = (int) RECT_TOP.get(rect, 0L);
    long _ = User32.defWindowProc(hwnd, WM_NCCALCSIZE, wordParameter, parameters);
    int frame = User32.isMaximized(hwnd) ? (int) RECT_LEFT.get(rect, 0L) - windowLeft : 0;
    RECT_TOP.set(rect, 0L, windowTop + frame);
    return 0;
  }

  /**
   * Grays out {@code Close} in the system menu of the window, which grays out the close button of
   * the title bar too and takes away {@code Alt+F4}.
   */
  @SneakyThrows
  public void disableClose(MemorySegment hwnd) {
    MemorySegment menu = (MemorySegment) GET_SYSTEM_MENU.invokeExact(hwnd, 0);
    int _ = (int) ENABLE_MENU_ITEM.invokeExact(menu, SC_CLOSE, MF_GRAYED);
  }

  /**
   * Hands the pointer to Windows, which moves or resizes the window the way a press on its frame at
   * {@code hitTest} does, until the button is released: {@code WM_NCLBUTTONDOWN}, posted after the
   * web view lets the pointer go. Does nothing once the primary button is up, as it may be after a
   * quick click: the loop would then wait for the next one.
   *
   * @param hitTest {@link #HTCAPTION} to move, {@link #HTLEFT} and the like to resize.
   */
  @SneakyThrows
  public void beginFrameDrag(MemorySegment hwnd, int hitTest) {
    int primary =
        (int) GET_SYSTEM_METRICS.invokeExact(SM_SWAPBUTTON) != 0 ? VK_RBUTTON : VK_LBUTTON;
    if (((short) GET_ASYNC_KEY_STATE.invokeExact(primary) & 0x8000) == 0) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment point = arena.allocate(Signatures.C_INT, 2);
      int _ = (int) GET_CURSOR_POS.invokeExact(point);
      long position =
          (point.getAtIndex(Signatures.C_INT, 0) & 0xFFFFL)
              | ((point.getAtIndex(Signatures.C_INT, 1) & 0xFFFFL) << 16);
      int _ = (int) RELEASE_CAPTURE.invokeExact();
      int _ = (int) POST_MESSAGE.invokeExact(hwnd, WM_NCLBUTTONDOWN, (long) hitTest, position);
    }
  }

  /** {@code {left, top, right, bottom}} of the whole monitor that holds most of the window. */
  @SneakyThrows
  public int[] monitorRect(MemorySegment hwnd) {
    MemorySegment monitor =
        (MemorySegment) MONITOR_FROM_WINDOW.invokeExact(hwnd, MONITOR_DEFAULTTONEAREST);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment info = arena.allocate(Signatures.MONITORINFO);
      info.set(Signatures.C_INT, 0, (int) Signatures.MONITORINFO.byteSize());
      int _ = (int) GET_MONITOR_INFO.invokeExact(monitor, info);
      return new int[] {
        info.get(Signatures.C_INT, 4),
        info.get(Signatures.C_INT, 8),
        info.get(Signatures.C_INT, 12),
        info.get(Signatures.C_INT, 16)
      };
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
