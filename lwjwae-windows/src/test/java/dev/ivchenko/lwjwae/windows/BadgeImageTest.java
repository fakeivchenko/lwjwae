package dev.ivchenko.lwjwae.windows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BadgeImageTest {
  @Test
  void badgeIsSquarePngOfRedCircleWithWhiteDigits() throws Exception {
    byte[] png = BadgeImage.png(7);
    Assertions.assertArrayEquals(
        new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, Arrays.copyOf(png, 8));
    ByteBuffer buffer = ByteBuffer.wrap(png);
    Assertions.assertEquals(32, buffer.getInt(16), "width");
    Assertions.assertEquals(32, buffer.getInt(20), "height");

    int idatLength = buffer.getInt(33);
    Inflater inflater = new Inflater();
    inflater.setInput(png, 41, idatLength);
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    while (!inflater.finished()) {
      raw.write(chunk, 0, inflater.inflate(chunk));
    }
    byte[] rows = raw.toByteArray();
    Assertions.assertEquals(32 * (32 * 4 + 1), rows.length);
    // The corner lies outside the circle; the middle of the circle is red or white.
    Assertions.assertEquals(0, rows[1 + 3], "a transparent corner");
    int middle = 16 * (32 * 4 + 1) + 1 + 2 * 4;
    Assertions.assertEquals((byte) 0xFF, rows[middle + 3], "an opaque circle");
  }

  @Test
  void tenAndMoreReadNinePlus() {
    Assertions.assertArrayEquals(BadgeImage.png(10), BadgeImage.png(250));
    Assertions.assertFalse(Arrays.equals(BadgeImage.png(9), BadgeImage.png(10)));
  }
}
