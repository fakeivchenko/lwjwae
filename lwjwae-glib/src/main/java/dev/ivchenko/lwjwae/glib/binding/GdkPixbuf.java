package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to gdk-pixbuf: decoding a PNG into pixels.
 *
 * <p>GTK 3 and GTK 4 both depend on gdk-pixbuf, so it is there whichever toolkit runs, and it
 * touches no display. {@code GdkTexture}, which GTK 4 prefers, lives in {@code libgtk-4} and would
 * tie this code to one toolkit.
 */
@UtilityClass
public class GdkPixbuf {
  private final SymbolLookup PIXBUF =
      NativeLibraries.load("libgdk_pixbuf-2.0.so.0", "libgdk_pixbuf-2.0.so");

  private final MethodHandle NEW_FROM_STREAM =
      NativeLibraries.downcall(
          PIXBUF, "gdk_pixbuf_new_from_stream", Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle GET_WIDTH =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_width", Signatures.INT_POINTER);
  private final MethodHandle GET_HEIGHT =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_height", Signatures.INT_POINTER);
  private final MethodHandle GET_ROWSTRIDE =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_rowstride", Signatures.INT_POINTER);
  private final MethodHandle GET_N_CHANNELS =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_n_channels", Signatures.INT_POINTER);
  private final MethodHandle GET_BITS_PER_SAMPLE =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_bits_per_sample", Signatures.INT_POINTER);
  private final MethodHandle GET_PIXELS =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_get_pixels", Signatures.POINTER_POINTER);
  private final MethodHandle SAVE_TO_BUFFERV =
      NativeLibraries.downcall(PIXBUF, "gdk_pixbuf_save_to_bufferv", Signatures.INT_POINTER_X7);

  /**
   * Decodes {@code png}, or any other format that gdk-pixbuf reads, into a pixbuf that the caller
   * gives back with {@link Glib#unref}.
   *
   * @throws IllegalArgumentException If gdk-pixbuf can't read the image.
   */
  @SneakyThrows
  public MemorySegment decode(byte[] png) {
    MemorySegment stream = Glib.memoryInputStream(Glib.copyToNative(png), png.length);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment pixbuf =
          (MemorySegment) NEW_FROM_STREAM.invokeExact(stream, MemorySegment.NULL, error);
      if (pixbuf.equals(MemorySegment.NULL)) {
        throw new IllegalArgumentException(
            "Not an image gdk-pixbuf can read: "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      return pixbuf;
    } finally {
      Glib.unref(stream);
    }
  }

  /**
   * Encodes {@code pixbuf} as PNG, through {@code gdk_pixbuf_save_to_bufferv}, the form without
   * variadic options.
   *
   * @throws IllegalStateException If gdk-pixbuf can't encode it.
   */
  @SneakyThrows
  public byte[] encodePng(MemorySegment pixbuf) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(Signatures.C_POINTER);
      MemorySegment size = arena.allocate(Signatures.C_LONG);
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment none = arena.allocate(Signatures.C_POINTER);
      int saved =
          (int)
              SAVE_TO_BUFFERV.invokeExact(
                  pixbuf, buffer, size, arena.allocateFrom("png"), none, none, error);
      if (saved == 0) {
        throw new IllegalStateException(
            "gdk-pixbuf could not encode a PNG: "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      MemorySegment data = buffer.get(Signatures.C_POINTER, 0);
      try {
        return data.reinterpret(size.get(Signatures.C_LONG, 0)).toArray(ValueLayout.JAVA_BYTE);
      } finally {
        Glib.free(data);
      }
    }
  }

  /**
   * Decodes {@code png} into ARGB32 pixels in network byte order, the form of a StatusNotifierItem
   * pixmap.
   *
   * <p>A pixbuf holds 8-bit samples in RGB or RGBA order, with straight alpha, and rows that may be
   * padded to {@code rowstride}; the loop moves alpha to the front and drops the padding.
   *
   * @throws IllegalArgumentException If gdk-pixbuf can't read the image.
   */
  @SneakyThrows
  public Pixmap argb32(byte[] png) {
    MemorySegment pixbuf = GdkPixbuf.decode(png);
    try {
      int width = (int) GET_WIDTH.invokeExact(pixbuf);
      int height = (int) GET_HEIGHT.invokeExact(pixbuf);
      int rowstride = (int) GET_ROWSTRIDE.invokeExact(pixbuf);
      int channels = (int) GET_N_CHANNELS.invokeExact(pixbuf);
      int bits = (int) GET_BITS_PER_SAMPLE.invokeExact(pixbuf);
      if (bits != 8 || channels < 3) {
        throw new IllegalArgumentException(
            "Unsupported pixel format: " + channels + " channels of " + bits + " bits");
      }
      MemorySegment pixels =
          ((MemorySegment) GET_PIXELS.invokeExact(pixbuf))
              .reinterpret((long) rowstride * (height - 1) + (long) width * channels);
      byte[] argb = new byte[width * height * 4];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          long source = (long) y * rowstride + (long) x * channels;
          int target = (y * width + x) * 4;
          argb[target] =
              channels == 4 ? pixels.get(ValueLayout.JAVA_BYTE, source + 3) : (byte) 0xFF;
          argb[target + 1] = pixels.get(ValueLayout.JAVA_BYTE, source);
          argb[target + 2] = pixels.get(ValueLayout.JAVA_BYTE, source + 1);
          argb[target + 3] = pixels.get(ValueLayout.JAVA_BYTE, source + 2);
        }
      }
      return new Pixmap(width, height, argb);
    } finally {
      Glib.unref(pixbuf);
    }
  }
}
