package dev.ivchenko.lwjwae.testing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;

/** Draws small PNG images for the tests of the tray, so no image file has to ship. */
@UtilityClass
public class Icons {
  /** Returns a {@code size} by {@code size} PNG: a filled circle of {@code color}. */
  public byte[] circle(int size, Color color) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(color);
      graphics.fillOval(1, 1, size - 2, size - 2);
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }
}
