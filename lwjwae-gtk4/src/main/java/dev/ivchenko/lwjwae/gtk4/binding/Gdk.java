package dev.ivchenko.lwjwae.gtk4.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of GDK 4 that the backend needs: decoding a PNG into pixels.
 *
 * <p>GDK 4 is part of {@code libgtk-4}, so there is no library of its own to load. The functions
 * here touch no display and are safe on any thread.
 */
@UtilityClass
public class Gdk {
  private final SymbolLookup GTK = NativeLibraries.load("libgtk-4.so.1", "libgtk-4.so");
  private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");

  private final MethodHandle BYTES_NEW =
      NativeLibraries.downcall(GLIB, "g_bytes_new", Signatures.POINTER_POINTER_LONG);
  private final MethodHandle BYTES_UNREF =
      NativeLibraries.downcall(GLIB, "g_bytes_unref", Signatures.VOID_POINTER);
  private final MethodHandle TEXTURE_NEW_FROM_BYTES =
      NativeLibraries.downcall(
          GTK, "gdk_texture_new_from_bytes", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle TEXTURE_GET_WIDTH =
      NativeLibraries.downcall(GTK, "gdk_texture_get_width", Signatures.INT_POINTER);
  private final MethodHandle TEXTURE_GET_HEIGHT =
      NativeLibraries.downcall(GTK, "gdk_texture_get_height", Signatures.INT_POINTER);
  private final MethodHandle TEXTURE_DOWNLOAD =
      NativeLibraries.downcall(GTK, "gdk_texture_download", Signatures.VOID_POINTER_POINTER_LONG);

  /**
   * Decodes {@code png} into ARGB32 pixels in network byte order and without premultiplied alpha,
   * the form of a StatusNotifierItem pixmap: {@code {width, height}} and then the pixels.
   *
   * <p>{@code gdk_texture_download} writes the native form of Cairo instead, {@code
   * CAIRO_FORMAT_ARGB32}: one 32-bit word per pixel in host byte order, with the color
   * premultiplied by alpha. The loop turns each pixel around and divides the alpha back out.
   *
   * @throws IllegalArgumentException If GDK can't read the image.
   */
  @SneakyThrows
  public Pixmap argb32(byte[] png) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_BYTE, png);
      MemorySegment bytes = (MemorySegment) BYTES_NEW.invokeExact(data, (long) png.length);
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment texture;
      try {
        texture = (MemorySegment) TEXTURE_NEW_FROM_BYTES.invokeExact(bytes, error);
      } finally {
        BYTES_UNREF.invokeExact(bytes);
      }
      if (texture.equals(MemorySegment.NULL)) {
        throw new IllegalArgumentException(
            "Not an image GDK can read: "
                + Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0)));
      }
      try {
        int width = (int) TEXTURE_GET_WIDTH.invokeExact(texture);
        int height = (int) TEXTURE_GET_HEIGHT.invokeExact(texture);
        long stride = width * 4L;
        MemorySegment pixels = arena.allocate(stride * height);
        TEXTURE_DOWNLOAD.invokeExact(texture, pixels, stride);
        byte[] argb = new byte[width * height * 4];
        for (int pixel = 0; pixel < width * height; pixel++) {
          int word = pixels.get(ValueLayout.JAVA_INT_UNALIGNED, pixel * 4L);
          int alpha = word >>> 24;
          argb[pixel * 4] = (byte) alpha;
          argb[pixel * 4 + 1] = (byte) unpremultiply((word >>> 16) & 0xFF, alpha);
          argb[pixel * 4 + 2] = (byte) unpremultiply((word >>> 8) & 0xFF, alpha);
          argb[pixel * 4 + 3] = (byte) unpremultiply(word & 0xFF, alpha);
        }
        return new Pixmap(width, height, argb);
      } finally {
        Glib.unref(texture);
      }
    }
  }

  private int unpremultiply(int channel, int alpha) {
    return alpha == 0 ? 0 : Math.min(255, (channel * 255 + alpha / 2) / alpha);
  }
}
