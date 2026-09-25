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
 * The notification area of the taskbar, through {@code Shell_NotifyIconW}, and URLs opened where
 * the system opens them, through {@code ShellExecuteW}.
 *
 * <p>An icon there belongs to a window: the shell reports clicks as a message to it, and removes
 * the icon when it goes. Every call builds a full {@code NOTIFYICONDATAW} with the fields that
 * {@code uFlags} names; the shell ignores the others. Must be called on the thread of the window.
 */
@UtilityClass
public class Shell32 {
  private final SymbolLookup SHELL32 = NativeLibraries.load("shell32.dll");

  /** {@code NIM_ADD}. */
  private final int NIM_ADD = 0;

  /** {@code NIM_MODIFY}. */
  private final int NIM_MODIFY = 1;

  /** {@code NIM_DELETE}. */
  private final int NIM_DELETE = 2;

  /** {@code NIF_MESSAGE}: {@code uCallbackMessage} is valid. */
  private final int NIF_MESSAGE = 0x1;

  /** {@code NIF_ICON}: {@code hIcon} is valid. */
  public final int NIF_ICON = 0x2;

  /** {@code NIF_TIP}: {@code szTip} is valid. */
  public final int NIF_TIP = 0x4;

  /** The characters of {@code szTip}, the terminating zero included. */
  private final int TIP_LENGTH = 128;

  /** {@code SW_SHOWNORMAL}. */
  private final int SHOW_NORMAL = 1;

  /** The highest {@code HINSTANCE} of {@code ShellExecuteW} that stands for an error. */
  private final long SHELL_EXECUTE_ERROR = 32;

  private final MethodHandle SH_CREATE_ITEM_FROM_PARSING_NAME =
      NativeLibraries.downcall(SHELL32, "SHCreateItemFromParsingName", Signatures.INT_POINTER_X4);
  private final MethodHandle SHELL_EXECUTE =
      NativeLibraries.downcall(SHELL32, "ShellExecuteW", Signatures.POINTER_POINTER_X5_INT);
  private final MethodHandle SHELL_NOTIFY_ICON =
      NativeLibraries.downcall(SHELL32, "Shell_NotifyIconW", Signatures.INT_INT_POINTER);

  private final VarHandle CB_SIZE = Shell32.field("cbSize");
  private final VarHandle HWND = Shell32.field("hWnd");
  private final VarHandle ID = Shell32.field("uID");
  private final VarHandle FLAGS = Shell32.field("uFlags");
  private final VarHandle CALLBACK_MESSAGE = Shell32.field("uCallbackMessage");
  private final VarHandle ICON = Shell32.field("hIcon");
  private final long TIP_OFFSET =
      Signatures.NOTIFYICONDATAW.byteOffset(MemoryLayout.PathElement.groupElement("szTip"));

  /**
   * Calls {@code SHCreateItemFromParsingName}: the {@code IShellItem} of a path, which the caller
   * releases.
   *
   * @throws dev.ivchenko.lwjwae.windows.exception.ComCallFailedException If there's no such path.
   */
  @SneakyThrows
  public MemorySegment shellItem(String path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "SHCreateItemFromParsingName",
          (int)
              SH_CREATE_ITEM_FROM_PARSING_NAME.invokeExact(
                  Wide.allocate(arena, path), MemorySegment.NULL, FileDialog.IID_SHELL_ITEM, out));
      return Com.pointerAt(out);
    }
  }

  /**
   * Opens {@code url} with the {@code open} verb: in the browser, or the mail client for {@code
   * mailto:}. Call it on a thread that initialized COM, which the shell may use for the handler.
   *
   * @throws IllegalStateException If the shell refuses, with the error code that it answers.
   */
  @SneakyThrows
  public void open(String url) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment result =
          (MemorySegment)
              SHELL_EXECUTE.invokeExact(
                  MemorySegment.NULL,
                  Wide.allocate(arena, "open"),
                  Wide.allocate(arena, url),
                  MemorySegment.NULL,
                  MemorySegment.NULL,
                  SHOW_NORMAL);
      if (result.address() <= SHELL_EXECUTE_ERROR) {
        throw new IllegalStateException(
            "ShellExecuteW could not open " + url + ", error " + result.address());
      }
    }
  }

  /**
   * Adds the icon {@code id} of {@code hwnd}, which receives {@code callbackMessage} with the mouse
   * message in {@code lParam} for every click.
   *
   * @throws IllegalStateException If the shell refuses, for example with no taskbar running.
   */
  public void add(MemorySegment hwnd, int id, int callbackMessage, MemorySegment icon, String tip) {
    Shell32.notify(NIM_ADD, hwnd, id, NIF_MESSAGE | NIF_ICON | NIF_TIP, callbackMessage, icon, tip);
  }

  /**
   * Changes the parts of the icon that {@code flags} names: {@link #NIF_ICON}, {@link #NIF_TIP}.
   */
  public void modify(MemorySegment hwnd, int id, int flags, MemorySegment icon, String tip) {
    Shell32.notify(NIM_MODIFY, hwnd, id, flags, 0, icon, tip);
  }

  /** Removes the icon. A missing icon is not an error: the shell may have dropped it already. */
  @SneakyThrows
  public void delete(MemorySegment hwnd, int id) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment data = Shell32.header(arena, hwnd, id, 0);
      int _ = (int) SHELL_NOTIFY_ICON.invokeExact(NIM_DELETE, data);
    }
  }

  @SneakyThrows
  private void notify(
      int command,
      MemorySegment hwnd,
      int id,
      int flags,
      int callbackMessage,
      MemorySegment icon,
      String tip) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment data = Shell32.header(arena, hwnd, id, flags);
      CALLBACK_MESSAGE.set(data, 0L, callbackMessage);
      ICON.set(data, 0L, icon == null ? MemorySegment.NULL : icon);
      String text = tip == null ? "" : tip;
      if (text.length() > TIP_LENGTH - 1) {
        text = text.substring(0, TIP_LENGTH - 1);
      }
      for (int i = 0; i < text.length(); i++) {
        data.set(Signatures.C_SHORT, TIP_OFFSET + 2L * i, (short) text.charAt(i));
      }
      if ((int) SHELL_NOTIFY_ICON.invokeExact(command, data) == 0) {
        throw new IllegalStateException(
            "Shell_NotifyIconW(" + command + ") failed, error " + Kernel32.lastError());
      }
    }
  }

  private MemorySegment header(Arena arena, MemorySegment hwnd, int id, int flags) {
    MemorySegment data = arena.allocate(Signatures.NOTIFYICONDATAW);
    CB_SIZE.set(data, 0L, (int) Signatures.NOTIFYICONDATAW.byteSize());
    HWND.set(data, 0L, hwnd);
    ID.set(data, 0L, id);
    FLAGS.set(data, 0L, flags);
    return data;
  }

  private VarHandle field(String name) {
    return Signatures.NOTIFYICONDATAW.varHandle(MemoryLayout.PathElement.groupElement(name));
  }
}
