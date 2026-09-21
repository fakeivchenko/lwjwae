package dev.ivchenko.lwjwae.windows.binding;

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
  public final int SW_SHOW = 5;
  public final int GWL_STYLE = -16;
  public final int GWLP_USERDATA = -21;
  public final int SWP_NOSIZE = 0x0001;
  public final int SWP_NOMOVE = 0x0002;
  public final int SWP_NOZORDER = 0x0004;
  public final int SWP_NOACTIVATE = 0x0010;
  public final int SWP_FRAMECHANGED = 0x0020;
  public final int PM_NOREMOVE = 0x0000;
  public final int WM_DESTROY = 0x0002;
  public final int WM_SIZE = 0x0005;
  public final int WM_USER = 0x0400;
  public final int WM_APP = 0x8000;

  /** {@code COLOR_WINDOW + 1}: the class background brush that GTK-style applications use. */
  private final MemorySegment WINDOW_BACKGROUND = MemorySegment.ofAddress(5 + 1);

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
  private final MethodHandle SET_WINDOW_TEXT =
      NativeLibraries.downcall(USER32, "SetWindowTextW", Signatures.INT_POINTER_POINTER);
  private final MethodHandle GET_WINDOW_TEXT_LENGTH =
      NativeLibraries.downcall(USER32, "GetWindowTextLengthW", Signatures.INT_POINTER);
  private final MethodHandle GET_WINDOW_TEXT =
      NativeLibraries.downcall(USER32, "GetWindowTextW", Signatures.INT_POINTER_POINTER_INT);
  private final MethodHandle GET_CLIENT_RECT =
      NativeLibraries.downcall(USER32, "GetClientRect", Signatures.INT_POINTER_POINTER);
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

  /** A hidden top-level window of {@code className}. {@code show} makes it visible. */
  @SneakyThrows
  public MemorySegment createWindow(String className, String title, int width, int height) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hwnd =
          (MemorySegment)
              CREATE_WINDOW_EX.invokeExact(
                  0,
                  Wide.allocate(arena, className),
                  Wide.allocate(arena, title),
                  WS_OVERLAPPEDWINDOW,
                  CW_USEDEFAULT,
                  CW_USEDEFAULT,
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
