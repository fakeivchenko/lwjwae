package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.windows.binding.Gdiplus;
import dev.ivchenko.lwjwae.windows.binding.Kernel32;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.awt.Color;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WindowsClipboardTest {
  @BeforeEach
  void requireWindows() {
    Assumptions.assumeTrue(PlatformUtil.isWindows(), "The clipboard of Windows: Windows only");
  }

  @Test
  void bitmapOfAnotherApplicationComesBackAsPng() throws Exception {
    WindowsDispatcher dispatcher = WindowsDispatcher.instance();
    WindowsClipboard clipboard = new WindowsClipboard(dispatcher);
    // What Print Screen leaves: a bitmap and nothing else.
    dispatcher.run(
        () -> {
          MemorySegment bitmap = Gdiplus.hbitmapFromPng(Icons.circle(40, Color.BLUE));
          Assertions.assertTrue(User32.openClipboard(dispatcher.messageWindow()));
          try {
            User32.emptyClipboard();
            Assertions.assertTrue(
                User32.setClipboardData(User32.CF_BITMAP, bitmap), "" + Kernel32.lastError());
          } finally {
            User32.closeClipboard();
          }
        });

    byte[] png = clipboard.readImage().get(10, TimeUnit.SECONDS).orElseThrow();

    Assertions.assertArrayEquals(
        new byte[] {(byte) 0x89, 'P', 'N', 'G'}, Arrays.copyOf(png, 4), "a PNG");
    ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
    Assertions.assertEquals(40, header.getInt(), "width");
    Assertions.assertEquals(40, header.getInt(), "height");
    Assertions.assertEquals(
        0x49454E44,
        ByteBuffer.wrap(png, png.length - 8, 4).getInt(),
        "ends with IEND, without the rest of the global memory");
  }
}
