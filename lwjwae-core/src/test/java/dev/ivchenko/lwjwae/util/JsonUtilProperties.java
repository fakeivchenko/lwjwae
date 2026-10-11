package dev.ivchenko.lwjwae.util;

import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Assertions;

/** What holds for every text and every value, which the examples of {@link JsonUtilTest} sample. */
class JsonUtilProperties {
  @Property
  void everyStringComesBackAsItWas(@ForAll String text) {
    Assertions.assertEquals(text, JsonUtil.parse(JsonUtil.write(text)));
  }

  @Property
  void writtenStringIsSafeInsideScript(@ForAll String text) {
    String written = JsonUtil.write(text);
    Assertions.assertTrue(written.startsWith("\"") && written.endsWith("\""), written);
    for (char c : written.substring(1, written.length() - 1).toCharArray()) {
      Assertions.assertFalse(c < 0x20 || c == ' ' || c == ' ', written);
    }
  }

  @Property
  void listsAndObjectsOfTextComeBackAsTheyWere(@ForAll("documents") Object document) {
    Assertions.assertEquals(document, JsonUtil.parse(JsonUtil.write(document)));
  }

  /** Anything at all is JSON or an {@link IllegalArgumentException}, never another failure. */
  @Property
  void anyTextIsReadOrRefused(@ForAll String text) {
    try {
      JsonUtil.parse(text);
    } catch (IllegalArgumentException _) {
      // Not JSON: the one way to fail.
    }
  }

  @Provide
  Arbitrary<Object> documents() {
    Arbitrary<String> texts = Arbitraries.strings().ofMaxLength(20);
    Arbitrary<List<String>> lists = texts.list().ofMaxSize(5);
    Arbitrary<Map<String, String>> maps = Arbitraries.maps(texts, texts).ofMaxSize(5);
    return Arbitraries.oneOf(texts, lists, maps, maps.list().ofMaxSize(3));
  }
}
