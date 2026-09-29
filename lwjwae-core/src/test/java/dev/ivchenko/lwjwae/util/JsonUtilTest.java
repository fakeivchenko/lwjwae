package dev.ivchenko.lwjwae.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JsonUtilTest {
  @Test
  void readsEveryKindOfValue() {
    Object value =
        JsonUtil.parse(
            " {\"a\": 1, \"b\": -2.5e1, \"c\": [true, false, null], \"d\": \"x\\n\\u00e9\\\"\","
                + " \"e\": {}, \"f\": [], \"big\": 12345678901234567890} ");
    Assertions.assertEquals(
        Map.of(
            "a",
            1L,
            "b",
            -25.0,
            "c",
            Arrays.asList(true, false, null),
            "d",
            "x\né\"",
            "e",
            Map.of(),
            "f",
            List.of(),
            "big",
            1.2345678901234567e19),
        value);
  }

  @Test
  void writesWhatItReads() {
    String text = "{\"a\":[1,2.5,\"x\\\"y\",null,true],\"b\":{\"c\":\"\\n\"}}";
    Assertions.assertEquals(text, JsonUtil.write(JsonUtil.parse(text)));
    Assertions.assertEquals("null", JsonUtil.write(Double.NaN));
  }

  @Test
  void refusesWhatIsNotJson() {
    for (String text : List.of("", "{", "[1,]", "{\"a\" 1}", "tru", "1 2", "\"\\x\"")) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtil.parse(text), text);
    }
  }
}
