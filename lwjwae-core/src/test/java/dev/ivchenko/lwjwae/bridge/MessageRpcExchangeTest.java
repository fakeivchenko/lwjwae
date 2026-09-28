package dev.ivchenko.lwjwae.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MessageRpcExchangeTest {
  @Test
  void answerTravelsAsTextOnlyInUtf8() {
    Assertions.assertTrue(MessageRpcExchange.isText("text/plain"));
    Assertions.assertTrue(MessageRpcExchange.isText("application/json"));
    Assertions.assertTrue(MessageRpcExchange.isText("text/html; charset=UTF-8"));
    Assertions.assertTrue(MessageRpcExchange.isText("application/octet-stream; charset=\"utf-8\""));
    Assertions.assertFalse(MessageRpcExchange.isText("text/csv; charset=windows-1251"));
    Assertions.assertFalse(MessageRpcExchange.isText("text/plain; charset=ISO-8859-1; x=y"));
    Assertions.assertFalse(MessageRpcExchange.isText("image/png"));
  }
}
