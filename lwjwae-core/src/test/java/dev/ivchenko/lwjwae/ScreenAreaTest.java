package dev.ivchenko.lwjwae;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ScreenAreaTest {
  @Test
  void containsItsTopLeftButNotItsBottomRight() {
    ScreenArea area = new ScreenArea(-1920, 0, 1920, 1080);

    Assertions.assertTrue(area.contains(-1920, 0));
    Assertions.assertTrue(area.contains(-1, 1079));
    Assertions.assertFalse(area.contains(0, 0));
    Assertions.assertFalse(area.contains(-1920, 1080));
  }

  @Test
  void intersectionIsTheCommonPartOrEmpty() {
    ScreenArea screen = new ScreenArea(0, 0, 1920, 1080);

    Assertions.assertEquals(
        new ScreenArea(1800, 1000, 120, 80),
        screen.intersection(new ScreenArea(1800, 1000, 400, 300)));
    ScreenArea apart = screen.intersection(new ScreenArea(3000, 0, 100, 100));
    Assertions.assertEquals(0, apart.width());
  }

  @Test
  void screenFillsItsDefaults() {
    Screen screen = new Screen(null, new ScreenArea(0, 0, 10, 10), null, 0, true);

    Assertions.assertEquals("", screen.name());
    Assertions.assertEquals(screen.bounds(), screen.workArea());
    Assertions.assertEquals(1, screen.scale());
    Assertions.assertEquals(new WindowPosition(0, 0), screen.bounds().position());
    Assertions.assertEquals(new WindowSize(10, 10), screen.bounds().size());
  }
}
