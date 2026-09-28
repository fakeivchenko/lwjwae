package dev.ivchenko.lwjwae.glib.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DecorationLayoutUtilTest {
  @Test
  void dropsOnlyTheButtonsAskedForAndKeepsTheSides() {
    Assertions.assertEquals(
        "icon:close", DecorationLayoutUtil.without("icon:minimize,maximize,close", true, true));
    Assertions.assertEquals(
        "close,maximize:", DecorationLayoutUtil.without("close,minimize,maximize:", true, false));
    Assertions.assertEquals("menu:close", DecorationLayoutUtil.without("menu:close", true, true));
    Assertions.assertEquals("menu:minimize,close", DecorationLayoutUtil.without(null, false, true));
  }
}
