package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.event.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BridgeProtocolTest {
  @Test
  void separatorIsNotNul() {
    Assertions.assertEquals(String.valueOf((char) 0x1f), BridgeProtocol.SEPARATOR);
  }

  @Test
  void parseSplitsIntoThreeFieldsAndKeepsSeparatorsInThePayload() {
    String sep = BridgeProtocol.SEPARATOR;
    BridgeMessage message = BridgeProtocol.parse("7" + sep + "echo" + sep + "a" + sep + "b");
    Assertions.assertEquals(new BridgeMessage(7, "echo", "a" + sep + "b"), message);
    Assertions.assertEquals(
        new BridgeMessage(1, "empty", ""), BridgeProtocol.parse("1" + sep + "empty" + sep));
  }

  @Test
  void parseRejectsMalformedMessages() {
    String sep = BridgeProtocol.SEPARATOR;
    Assertions.assertNull(BridgeProtocol.parse("no separators"));
    Assertions.assertNull(BridgeProtocol.parse("1" + sep + "only-two"));
    Assertions.assertNull(BridgeProtocol.parse("x" + sep + "name" + sep + "payload"));
  }

  @Test
  void bootstrapEmbedsTheTransportAndTheChannel() {
    String script = BridgeProtocol.bootstrapScript("(m) => host.post(m)", "fakeCodec");
    Assertions.assertTrue(script.contains("const post = (m) => host.post(m);"));
    Assertions.assertTrue(script.contains("const codec = fakeCodec;"));
    Assertions.assertTrue(script.contains("window." + BridgeProtocol.CHANNEL + " = {"));
    Assertions.assertTrue(
        script.contains("window." + BridgeProtocol.PAGE_API + " = { listen, once, emit };"));
    Assertions.assertTrue(script.contains("call(\"" + BridgeProtocol.EVENT_CALL + "\""));
    Assertions.assertTrue(
        script.contains("if (window." + BridgeProtocol.CHANNEL + ") return;"),
        "must be safe to inject twice");
  }

  @Test
  void bindingPublishesWindowFunction() {
    Assertions.assertEquals(
        "window[\"reverse\"] = (payload) => window.__lwjwaeBridge.call(\"reverse\", payload,"
            + " false);",
        BridgeProtocol.bindingScript("reverse"));
    Assertions.assertEquals(
        "window[\"typed\"] = (payload) => window.__lwjwaeBridge.call(\"typed\", payload, true);",
        BridgeProtocol.bindingScript("typed", true));
  }

  @Test
  void emitQuotesTheNameAndThePayload() {
    Assertions.assertEquals(
        "window.__lwjwaeBridge.deliver(\"tick\", \"{\\\"n\\\":1}\", true);",
        BridgeProtocol.emitScript("tick", "{\"n\":1}", true));
    Assertions.assertEquals(
        "window.__lwjwaeBridge.deliver(\"tick\", \"plain\", false);",
        BridgeProtocol.emitScript("tick", "plain", false));
  }

  @Test
  void parseEventReadsTheTypedFlagTheNameAndThePayload() {
    String sep = BridgeProtocol.SEPARATOR;
    Event typed = BridgeProtocol.parseEvent("1" + sep + "moved" + sep + "3,4" + sep + "tail");
    Assertions.assertEquals(new Event("moved", 0, "3,4" + sep + "tail", true), typed);
    Assertions.assertEquals(
        new Event("plain", 0, "", false), BridgeProtocol.parseEvent("0" + sep + "plain" + sep));
    Assertions.assertNull(BridgeProtocol.parseEvent("0" + sep + "only-two"));
  }

  @Test
  void resolveAndRejectQuoteTheirArguments() {
    Assertions.assertEquals(
        "window.__lwjwaeBridge.settle(7, \"a \\\"b\\\"\", null);",
        BridgeProtocol.resolveScript(7, "a \"b\""));
    Assertions.assertEquals(
        "window.__lwjwaeBridge.settle(7, null, \"boom\");", BridgeProtocol.rejectScript(7, "boom"));
    Assertions.assertEquals(
        "window.__lwjwaeBridge.settle(7, null, \"Handler failed\");",
        BridgeProtocol.rejectScript(7, null));
  }
}
