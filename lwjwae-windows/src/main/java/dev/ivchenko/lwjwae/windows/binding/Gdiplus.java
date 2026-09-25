package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the flat API of GDI+: turning a PNG into a bitmap of GDI and back.
 *
 * <p>The clipboard of Windows holds images as bitmaps of GDI, which a PNG has to become, and which
 * have to become a PNG again on the way out. GDI+ decodes and encodes both through plain C
 * functions, where the Windows Imaging Component would take COM interfaces. It's started once, on
 * the first call, and stays for the process. Every function here runs on the UI thread.
 */
@UtilityClass
public class Gdiplus {
  private final SymbolLookup GDIPLUS = NativeLibraries.load("gdiplus.dll");

  private final MethodHandle STARTUP =
      NativeLibraries.downcall(GDIPLUS, "GdiplusStartup", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle CREATE_BITMAP_FROM_STREAM =
      NativeLibraries.downcall(
          GDIPLUS, "GdipCreateBitmapFromStream", Signatures.INT_POINTER_POINTER);
  private final MethodHandle CREATE_HBITMAP_FROM_BITMAP =
      NativeLibraries.downcall(
          GDIPLUS, "GdipCreateHBITMAPFromBitmap", Signatures.INT_POINTER_POINTER_INT);
  private final MethodHandle CREATE_BITMAP_FROM_HBITMAP =
      NativeLibraries.downcall(
          GDIPLUS, "GdipCreateBitmapFromHBITMAP", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle SAVE_IMAGE_TO_STREAM =
      NativeLibraries.downcall(GDIPLUS, "GdipSaveImageToStream", Signatures.INT_POINTER_X4);
  private final MethodHandle DISPOSE_IMAGE =
      NativeLibraries.downcall(GDIPLUS, "GdipDisposeImage", Signatures.INT_POINTER);

  /**
   * {@code struct GdiplusStartupInput { UINT32 GdiplusVersion; DebugEventProc DebugEventCallback;
   * BOOL SuppressBackgroundThread; BOOL SuppressExternalCodecs; }}.
   */
  private final MemoryLayout STARTUP_INPUT =
      MemoryLayout.structLayout(
          Signatures.C_INT.withName("version"),
          MemoryLayout.paddingLayout(4),
          Signatures.C_POINTER.withName("debugEventCallback"),
          Signatures.C_INT.withName("suppressBackgroundThread"),
          Signatures.C_INT.withName("suppressExternalCodecs"));

  /** The class of the PNG encoder of GDI+. */
  private final MemorySegment PNG_ENCODER = Com.guid("557cf406-1a04-11d3-9a73-0000f81ef32e");

  /** The background of a bitmap without alpha, where a PNG had transparent pixels: white. */
  private final int BACKGROUND = 0xFFFFFFFF;

  /** Whether GDI+ was started; the UI thread only. */
  private boolean started;

  /**
   * An {@code HBITMAP} of {@code png}, which the caller hands to the clipboard or deletes.
   *
   * @throws IllegalArgumentException If GDI+ can't read the image.
   */
  @SneakyThrows
  public MemorySegment hbitmapFromPng(byte[] png) {
    Gdiplus.start();
    MemorySegment stream = Shlwapi.memoryStream(png);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment bitmap = arena.allocate(Signatures.C_POINTER);
      int status = (int) CREATE_BITMAP_FROM_STREAM.invokeExact(stream, bitmap);
      if (status != 0) {
        throw new IllegalArgumentException("Not an image GDI+ can read, status " + status);
      }
      MemorySegment image = bitmap.get(Signatures.C_POINTER, 0);
      try {
        MemorySegment hbitmap = arena.allocate(Signatures.C_POINTER);
        status = (int) CREATE_HBITMAP_FROM_BITMAP.invokeExact(image, hbitmap, BACKGROUND);
        if (status != 0) {
          throw new IllegalStateException("GdipCreateHBITMAPFromBitmap failed, status " + status);
        }
        return hbitmap.get(Signatures.C_POINTER, 0);
      } finally {
        int _ = (int) DISPOSE_IMAGE.invokeExact(image);
      }
    } finally {
      Com.release(stream);
    }
  }

  /**
   * {@code hbitmap}, which the caller keeps, encoded as PNG.
   *
   * @throws IllegalStateException If GDI+ can't read or encode it.
   */
  @SneakyThrows
  public byte[] pngFromHbitmap(MemorySegment hbitmap) {
    Gdiplus.start();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment bitmap = arena.allocate(Signatures.C_POINTER);
      int status =
          (int) CREATE_BITMAP_FROM_HBITMAP.invokeExact(hbitmap, MemorySegment.NULL, bitmap);
      if (status != 0) {
        throw new IllegalStateException("GdipCreateBitmapFromHBITMAP failed, status " + status);
      }
      MemorySegment image = bitmap.get(Signatures.C_POINTER, 0);
      MemorySegment stream = Ole32.memoryStream();
      try {
        status =
            (int) SAVE_IMAGE_TO_STREAM.invokeExact(image, stream, PNG_ENCODER, MemorySegment.NULL);
        if (status != 0) {
          throw new IllegalStateException("GdipSaveImageToStream failed, status " + status);
        }
        return Kernel32.readGlobal(Ole32.streamMemory(stream));
      } finally {
        Com.release(stream);
        int _ = (int) DISPOSE_IMAGE.invokeExact(image);
      }
    }
  }

  @SneakyThrows
  private void start() {
    if (started) {
      return;
    }
    MemorySegment input = NativeLibraries.ARENA.allocate(STARTUP_INPUT);
    input.set(Signatures.C_INT, 0, 1);
    MemorySegment token = NativeLibraries.ARENA.allocate(Signatures.C_POINTER);
    int status = (int) STARTUP.invokeExact(token, input, MemorySegment.NULL);
    if (status != 0) {
      throw new IllegalStateException("GdiplusStartup failed, status " + status);
    }
    started = true;
  }
}
