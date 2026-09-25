package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import lombok.experimental.UtilityClass;

/**
 * Every native signature that the Windows backend binds, in one place.
 *
 * <p>Win32 types map to four layouts: {@code HANDLE}, {@code HWND}, and pointers are {@code void*};
 * {@code UINT}, {@code DWORD}, {@code BOOL}, and {@code HRESULT} are 32-bit; and the pointer-sized
 * integers {@code WPARAM}, {@code LPARAM}, {@code LRESULT}, and {@code LONG_PTR} are 64-bit. COM
 * methods are ordinary C functions whose first argument is the object, so they share these shapes.
 *
 * <p>Loading this class has no side effects. It opens no library and creates no {@link
 * java.lang.foreign.Linker}.
 */
@UtilityClass
public class Signatures {
  /**
   * {@code int}, {@code UINT}, {@code DWORD}, {@code BOOL}, {@code HRESULT}, and {@code LSTATUS}.
   */
  public final ValueLayout.OfInt C_INT = Layouts.C_INT;

  /** {@code WPARAM}, {@code LPARAM}, {@code LRESULT}, and {@code LONG_PTR}: pointer-sized. */
  public final ValueLayout.OfLong C_LONG_PTR = Layouts.C_LONG_LONG;

  /** {@code WORD}, {@code ATOM}. */
  public final ValueLayout.OfShort C_SHORT = Layouts.C_SHORT;

  /** C {@code bool}. */
  public final ValueLayout.OfBoolean C_BOOL = Layouts.C_BOOL;

  /** Any {@code T*}. */
  public final AddressLayout C_POINTER = Layouts.C_POINTER;

  /** {@code struct MONITORINFO { DWORD cbSize; RECT rcMonitor; RECT rcWork; DWORD dwFlags; }}. */
  public final MemoryLayout MONITORINFO =
      MemoryLayout.structLayout(
          C_INT.withName("cbSize"),
          C_INT.withName("monitorLeft"),
          C_INT.withName("monitorTop"),
          C_INT.withName("monitorRight"),
          C_INT.withName("monitorBottom"),
          C_INT.withName("workLeft"),
          C_INT.withName("workTop"),
          C_INT.withName("workRight"),
          C_INT.withName("workBottom"),
          C_INT.withName("flags"));

  /**
   * {@code struct MONITORINFOEXW}: {@link #MONITORINFO} followed by {@code WCHAR szDevice[32]}, the
   * name of the display device.
   */
  public final MemoryLayout MONITORINFOEX =
      MemoryLayout.structLayout(
          MONITORINFO.withName("info"),
          MemoryLayout.sequenceLayout(32, C_SHORT).withName("device"));

  /** {@code struct RECT { LONG left, top, right, bottom; }}. */
  public final MemoryLayout RECT =
      MemoryLayout.structLayout(
          C_INT.withName("left"),
          C_INT.withName("top"),
          C_INT.withName("right"),
          C_INT.withName("bottom"));

  /** {@code struct POINT { LONG x, y; }}. */
  public final MemoryLayout POINT =
      MemoryLayout.structLayout(C_INT.withName("x"), C_INT.withName("y"));

  /**
   * {@code struct MSG { HWND hwnd; UINT message; WPARAM wParam; LPARAM lParam; DWORD time; POINT
   * pt; }}.
   */
  public final MemoryLayout MSG =
      MemoryLayout.structLayout(
          C_POINTER.withName("hwnd"),
          C_INT.withName("message"),
          MemoryLayout.paddingLayout(4),
          C_LONG_PTR.withName("wParam"),
          C_LONG_PTR.withName("lParam"),
          C_INT.withName("time"),
          POINT.withName("pt"),
          MemoryLayout.paddingLayout(4));

  /** {@code struct WNDCLASSEXW}. */
  public final MemoryLayout WNDCLASSEXW =
      MemoryLayout.structLayout(
          C_INT.withName("cbSize"),
          C_INT.withName("style"),
          C_POINTER.withName("lpfnWndProc"),
          C_INT.withName("cbClsExtra"),
          C_INT.withName("cbWndExtra"),
          C_POINTER.withName("hInstance"),
          C_POINTER.withName("hIcon"),
          C_POINTER.withName("hCursor"),
          C_POINTER.withName("hbrBackground"),
          C_POINTER.withName("lpszMenuName"),
          C_POINTER.withName("lpszClassName"),
          C_POINTER.withName("hIconSm"));

  /** {@code GUID}: 16 bytes. */
  public final MemoryLayout GUID = MemoryLayout.sequenceLayout(16, Layouts.C_CHAR);

  /**
   * {@code NOTIFYICONDATAW}, the full structure of Windows Vista and later: 976 bytes on x64. Every
   * field is declared, padding included, so that {@code cbSize} is the size the shell expects; a
   * shorter size makes it fall back to an older layout.
   */
  public final StructLayout NOTIFYICONDATAW =
      MemoryLayout.structLayout(
          C_INT.withName("cbSize"),
          MemoryLayout.paddingLayout(4),
          C_POINTER.withName("hWnd"),
          C_INT.withName("uID"),
          C_INT.withName("uFlags"),
          C_INT.withName("uCallbackMessage"),
          MemoryLayout.paddingLayout(4),
          C_POINTER.withName("hIcon"),
          MemoryLayout.sequenceLayout(128, Layouts.C_SHORT).withName("szTip"),
          C_INT.withName("dwState"),
          C_INT.withName("dwStateMask"),
          MemoryLayout.sequenceLayout(256, Layouts.C_SHORT).withName("szInfo"),
          C_INT.withName("uVersion"),
          MemoryLayout.sequenceLayout(64, Layouts.C_SHORT).withName("szInfoTitle"),
          C_INT.withName("dwInfoFlags"),
          MemoryLayout.sequenceLayout(16, Layouts.C_CHAR).withName("guidItem"),
          C_POINTER.withName("hBalloonIcon"));

  // --- shapes ---

  /** {@code void f(T*)}. */
  public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

  /** {@code int f(void)}. */
  public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

  /** {@code BOOL f(DWORD, DWORD, BOOL)}: {@code AttachThreadInput}. */
  public final FunctionDescriptor INT_INT_INT_INT =
      FunctionDescriptor.of(C_INT, C_INT, C_INT, C_INT);

  /** {@code HRESULT f(T*, U*, DWORD, V*, W*)}: {@code CoCreateInstance}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER);

  /** {@code HRESULT f(T*, U*, V*, W*)}: {@code SHCreateItemFromParsingName}. */
  public final FunctionDescriptor INT_POINTER_X4 =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code int f(T*, U*, V*, UINT)}: {@code MessageBoxW}. */
  public final FunctionDescriptor INT_POINTER_POINTER_POINTER_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT);

  /** {@code SHORT f(int)}: {@code GetAsyncKeyState}. */
  public final FunctionDescriptor SHORT_INT = FunctionDescriptor.of(C_SHORT, C_INT);

  /** {@code BOOL f(T*, UINT, UINT)}: {@code EnableMenuItem}. */
  public final FunctionDescriptor INT_POINTER_INT_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT);

  /** {@code int f(int)}: {@code GetSystemMetrics}. */
  public final FunctionDescriptor INT_INT = FunctionDescriptor.of(C_INT, C_INT);

  /** {@code int f(int, T*)}: {@code Shell_NotifyIconW}. */
  public final FunctionDescriptor INT_INT_POINTER = FunctionDescriptor.of(C_INT, C_INT, C_POINTER);

  /** {@code int f(T*, int, long, U*)}: {@code AppendMenuW}. */
  public final FunctionDescriptor INT_POINTER_INT_LONG_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_LONG_PTR, C_POINTER);

  /** {@code int f(T*, int, long, long)}: {@code PostMessageW}. */
  public final FunctionDescriptor INT_POINTER_INT_LONG_LONG =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_LONG_PTR, C_LONG_PTR);

  /** {@code int f(T*, int, int, int, int, U*, V*)}: {@code TrackPopupMenu}. */
  public final FunctionDescriptor INT_POINTER_INT_X4_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_POINTER, C_POINTER);

  /** {@code T* f(void)}: {@code CreatePopupMenu}. */
  public final FunctionDescriptor POINTER_VOID = FunctionDescriptor.of(C_POINTER);

  /** {@code T* f(U*, V*, W*, X*, Y*, int)}: {@code ShellExecuteW}. */
  public final FunctionDescriptor POINTER_POINTER_X5_INT =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT);

  /** {@code T* f(U*, int, int, int, int, int, int)}: {@code CreateIconFromResourceEx}. */
  public final FunctionDescriptor POINTER_POINTER_INT_X6 =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT);

  /**
   * {@code int f(T*)}: {@code HRESULT (this)}, {@code BOOL f(HWND)}, and {@code ULONG
   * AddRef(this)}.
   */
  public final FunctionDescriptor INT_POINTER = FunctionDescriptor.of(C_INT, C_POINTER);

  /** {@code int f(T*, int)}. */
  public final FunctionDescriptor INT_POINTER_INT = FunctionDescriptor.of(C_INT, C_POINTER, C_INT);

  /** {@code int f(T*, U*)}: {@code HRESULT (this, arg)}. */
  public final FunctionDescriptor INT_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER);

  /**
   * {@code BOOL f(HDC, LPCRECT, MONITORENUMPROC, LPARAM)}: {@code EnumDisplayMonitors}, and {@code
   * BOOL f(HMONITOR, HDC, LPRECT, LPARAM)}: the {@code MONITORENUMPROC} that it calls.
   */
  public final FunctionDescriptor INT_POINTER_X3_LONG =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_LONG_PTR);

  /** {@code HRESULT f(HMONITOR, int, UINT*, UINT*)}: {@code GetDpiForMonitor}. */
  public final FunctionDescriptor INT_POINTER_INT_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_POINTER);

  /** {@code int f(T*, U*, V*)}: {@code HRESULT (this, arg, arg)} and {@code QueryInterface}. */
  public final FunctionDescriptor INT_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code int f(T*, U*, int)}: {@code AddWebResourceRequestedFilter} and {@code GetWindowTextW}.
   */
  public final FunctionDescriptor INT_POINTER_POINTER_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT);

  /** {@code int f(T*, int, U*)}: every {@code *CompletedHandler::Invoke(this, HRESULT, arg)}. */
  public final FunctionDescriptor INT_POINTER_INT_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER);

  /** {@code int f(T*, int, int, int)}: {@code AdjustWindowRectEx}. */
  public final FunctionDescriptor INT_POINTER_INT_INT_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT);

  /** {@code int f(T*, U*, int, int)}: {@code GetMessageW}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT);

  /** {@code int f(T*, U*, int, int, int)}: {@code PeekMessageW}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_INT_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT);

  /** {@code int f(int, int, long, long)}: {@code PostThreadMessageW}. */
  public final FunctionDescriptor INT_INT_INT_LONG_LONG =
      FunctionDescriptor.of(C_INT, C_INT, C_INT, C_LONG_PTR, C_LONG_PTR);

  /** {@code int f(T*, U*, int, int, int, int, int)}: {@code SetWindowPos}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_X5 =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT);

  /** {@code int f(T*, U*, V*, int, W*, X*, Y*)}: {@code RegGetValueW}. */
  public final FunctionDescriptor INT_POINTER_X3_INT_POINTER_X3 =
      FunctionDescriptor.of(
          C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

  /** {@code LSTATUS RegSetKeyValueW(HKEY, LPCWSTR, LPCWSTR, DWORD, LPCVOID, DWORD)}. */
  public final FunctionDescriptor INT_POINTER_X3_INT_POINTER_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_INT);

  /** {@code int f(T*, U*, int, V*, W*, X*)}: {@code CreateWebResourceResponse}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_POINTER_X3 =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

  /** {@code HRESULT f(T*, UINT64, U*)}: {@code ICoreWebView2Environment12::CreateSharedBuffer}. */
  public final FunctionDescriptor INT_POINTER_LONG_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG_PTR, C_POINTER);

  /** {@code HRESULT f(T*, U*, ULONG, V*)}: {@code ISequentialStream::Read}. */
  public final FunctionDescriptor INT_POINTER_POINTER_INT_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER);

  /** {@code HRESULT f(T*, LARGE_INTEGER, DWORD, U*)}: {@code IStream::Seek}. */
  public final FunctionDescriptor INT_POINTER_LONG_INT_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG_PTR, C_INT, C_POINTER);

  /** {@code HRESULT f(T*, ULARGE_INTEGER)}: {@code IStream::SetSize}. */
  public final FunctionDescriptor INT_POINTER_LONG =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG_PTR);

  /** {@code HRESULT f(T*, U*, ULARGE_INTEGER, V*, W*)}: {@code IStream::CopyTo}. */
  public final FunctionDescriptor INT_POINTER_POINTER_LONG_POINTER_POINTER =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG_PTR, C_POINTER, C_POINTER);

  /** {@code HRESULT f(T*, ULARGE_INTEGER, ULARGE_INTEGER, DWORD)}: {@code IStream::LockRegion}. */
  public final FunctionDescriptor INT_POINTER_LONG_LONG_INT =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG_PTR, C_LONG_PTR, C_INT);

  /** {@code int f(T*, RECT)}: {@code ICoreWebView2Controller::put_Bounds}, by value. */
  public final FunctionDescriptor INT_POINTER_RECT = FunctionDescriptor.of(C_INT, C_POINTER, RECT);

  /** {@code int f(bool, int, T*, U*, V*)}: {@code CreateWebViewEnvironmentWithOptionsInternal}. */
  public final FunctionDescriptor INT_BOOL_INT_POINTER_X3 =
      FunctionDescriptor.of(C_INT, C_BOOL, C_INT, C_POINTER, C_POINTER, C_POINTER);

  /** {@code long f(T*, int, long, long)}: {@code WNDPROC} and {@code DefWindowProcW}. */
  public final FunctionDescriptor LONG_POINTER_INT_LONG_LONG =
      FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT, C_LONG_PTR, C_LONG_PTR);

  /**
   * {@code long f(T*, int)}: {@code GetWindowLongPtrW}. {@code DispatchMessageW} has one argument.
   */
  public final FunctionDescriptor LONG_POINTER_INT =
      FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT);

  /** {@code long f(T*)}: {@code DispatchMessageW}. */
  public final FunctionDescriptor LONG_POINTER = FunctionDescriptor.of(C_LONG_PTR, C_POINTER);

  /** {@code long f(T*, int, long)}: {@code SetWindowLongPtrW}. */
  public final FunctionDescriptor LONG_POINTER_INT_LONG =
      FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT, C_LONG_PTR);

  /** {@code short f(T*)}: {@code RegisterClassExW}. */
  public final FunctionDescriptor SHORT_POINTER = FunctionDescriptor.of(C_SHORT, C_POINTER);

  /** {@code T* f(U*)}: {@code GetModuleHandleW}. */
  public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

  /** {@code T* f(U*, V*)}: {@code GetProcAddress}, {@code LoadCursorW}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER);

  /** {@code T* f(U*, int)}: {@code SHCreateMemStream}. */
  public final FunctionDescriptor POINTER_POINTER_INT =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT);

  /** {@code T* f(U*, V*, int)}: {@code LoadLibraryExW}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_INT =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_INT);

  /**
   * {@code T* f(int, U*, V*, int, int, int, int, int, W*, X*, Y*, Z*)}: {@code CreateWindowExW}.
   */
  public final FunctionDescriptor CREATE_WINDOW_EX =
      FunctionDescriptor.of(
          C_POINTER, C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT, C_POINTER,
          C_POINTER, C_POINTER, C_POINTER);
}
