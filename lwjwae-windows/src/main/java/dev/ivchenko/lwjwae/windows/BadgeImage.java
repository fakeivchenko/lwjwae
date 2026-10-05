package dev.ivchenko.lwjwae.windows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import lombok.experimental.UtilityClass;

/**
 * Draws the badge of a count, a red circle with the number in white, as a PNG for the overlay icon
 * of a taskbar button.
 *
 * <p>The taskbar has no badge of its own for a desktop application, only an overlay icon, so the
 * number is drawn here: with no toolkit for images at hand, the digits come from a bitmap font of
 * five by seven, scaled up, and the edge of the circle is smoothed by sampling each pixel sixteen
 * times. From 10 on, the badge reads {@code 9+}, as the counts of Windows do in so little room.
 */
@UtilityClass
class BadgeImage {
  private final int SIZE = 32;
  private final int RED = 0xFFC42B1C;
  private final int WHITE = 0xFFFFFFFF;

  /** The rows of each glyph, five bits wide, the leftmost pixel in the highest bit. */
  private final int[][] GLYPHS = {
    {0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110},
    {0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110},
    {0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111},
    {0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110},
    {0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010},
    {0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110},
    {0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110},
    {0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000},
    {0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110},
    {0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100},
    {0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000}
  };

  /** The PNG of the badge of {@code count}, which is 1 or more. */
  byte[] png(int count) {
    int[] pixels = new int[SIZE * SIZE];
    BadgeImage.drawCircle(pixels);
    String text = count > 9 ? "9+" : Integer.toString(count);
    int scale = text.length() == 1 ? 3 : 2;
    int width = text.length() * 5 * scale + (text.length() - 1) * scale;
    int left = (SIZE - width) / 2;
    int top = (SIZE - 7 * scale) / 2;
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      int[] glyph = GLYPHS[character == '+' ? 10 : character - '0'];
      BadgeImage.drawGlyph(pixels, glyph, left + index * 6 * scale, top, scale);
    }
    return BadgeImage.encode(pixels);
  }

  private void drawCircle(int[] pixels) {
    double radius = SIZE / 2.0 - 0.5;
    double center = SIZE / 2.0;
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        int inside = 0;
        for (int sample = 0; sample < 16; sample++) {
          double dx = x + (sample % 4 + 0.5) / 4 - center;
          double dy = y + (sample / 4 + 0.5) / 4 - center;
          if (dx * dx + dy * dy <= radius * radius) {
            inside++;
          }
        }
        int alpha = inside * 255 / 16;
        pixels[y * SIZE + x] = (alpha << 24) | (RED & 0xFFFFFF);
      }
    }
  }

  private void drawGlyph(int[] pixels, int[] glyph, int left, int top, int scale) {
    for (int row = 0; row < glyph.length; row++) {
      for (int column = 0; column < 5; column++) {
        if ((glyph[row] & (1 << (4 - column))) == 0) {
          continue;
        }
        for (int y = 0; y < scale; y++) {
          for (int x = 0; x < scale; x++) {
            pixels[(top + row * scale + y) * SIZE + left + column * scale + x] = WHITE;
          }
        }
      }
    }
  }

  /** Writes {@code pixels}, ARGB by rows, as a PNG of 8-bit RGBA without filters. */
  private byte[] encode(int[] pixels) {
    byte[] raw = new byte[SIZE * (SIZE * 4 + 1)];
    int offset = 0;
    for (int y = 0; y < SIZE; y++) {
      raw[offset++] = 0;
      for (int x = 0; x < SIZE; x++) {
        int pixel = pixels[y * SIZE + x];
        raw[offset++] = (byte) (pixel >> 16);
        raw[offset++] = (byte) (pixel >> 8);
        raw[offset++] = (byte) pixel;
        raw[offset++] = (byte) (pixel >>> 24);
      }
    }
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (Deflater deflater = new Deflater()) {
      deflater.setInput(raw);
      deflater.finish();
      byte[] buffer = new byte[4096];
      while (!deflater.finished()) {
        compressed.write(buffer, 0, deflater.deflate(buffer));
      }
    }

    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    ByteBuffer header = ByteBuffer.allocate(13);
    header.putInt(SIZE).putInt(SIZE).put((byte) 8).put((byte) 6).put((byte) 0);
    header.put((byte) 0).put((byte) 0);
    BadgeImage.chunk(png, "IHDR", header.array());
    BadgeImage.chunk(png, "IDAT", compressed.toByteArray());
    BadgeImage.chunk(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private void chunk(ByteArrayOutputStream png, String type, byte[] data) {
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    png.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
    png.writeBytes(name);
    png.writeBytes(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    png.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
  }
}
