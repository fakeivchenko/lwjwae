package dev.ivchenko.lwjwae.bridge;

import java.util.concurrent.CompletableFuture;
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

  @Test
  void callWhoseBodyIsNotBase64IsDroppedNotThrown() {
    MessageRpcCalls calls =
        new MessageRpcCalls(null, _ -> CompletableFuture.completedFuture(null), "token");
    String message =
        String.join(
            BridgeProtocol.SEPARATOR,
            MessageRpcExchange.TAG,
            "token",
            "doc",
            "1",
            "name",
            "",
            "b",
            "not base64!");
    Thread thread = Thread.currentThread();
    Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
    thread.setUncaughtExceptionHandler((_, _) -> {});
    try {
      Assertions.assertNull(calls.receive(message));
    } finally {
      thread.setUncaughtExceptionHandler(handler);
    }
  }
}
