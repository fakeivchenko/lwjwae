package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.WindowParameters;
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
        script.contains(
            "window." + BridgeProtocol.PAGE_API + " = { listen, once, emit, open, close };"));
    Assertions.assertTrue(script.contains("call(\"" + BridgeProtocol.EVENT_CALL + "\""));
    Assertions.assertTrue(script.contains("call(\"" + BridgeProtocol.OPEN_CALL + "\""));
    Assertions.assertTrue(script.contains("call(\"" + BridgeProtocol.CLOSE_CALL + "\""));
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
    Assertions.assertEquals(new Event("moved", 0, "3,4" + sep + "tail", true, null), typed);
    Assertions.assertEquals(
        new Event("plain", 0, "", false, null),
        BridgeProtocol.parseEvent("0" + sep + "plain" + sep));
    Assertions.assertNull(BridgeProtocol.parseEvent("0" + sep + "only-two"));
  }

  @Test
  void parseWindowParametersReadsEveryFieldAndAppliesDefaults() {
    String sep = BridgeProtocol.SEPARATOR;
    WindowParameters full =
        BridgeProtocol.parseWindowParameters(
            String.join(sep, "Docs", "640", "480", "10", "20", "1", "https://x", "app/i.html"));
    Assertions.assertEquals("Docs", full.title());
    Assertions.assertEquals(640, full.width());
    Assertions.assertEquals(480, full.height());
    Assertions.assertEquals(10, full.x());
    Assertions.assertEquals(20, full.y());
    Assertions.assertTrue(full.centered());
    Assertions.assertEquals("https://x", full.url());
    Assertions.assertEquals("app/i.html", full.resource());

    WindowParameters empty = BridgeProtocol.parseWindowParameters(sep.repeat(7));
    Assertions.assertEquals(WindowParameters.createDefault(), empty);

    Assertions.assertNull(BridgeProtocol.parseWindowParameters("garbage"));
    Assertions.assertNull(
        BridgeProtocol.parseWindowParameters(String.join(sep, "", "wide", "", "", "", "", "", "")));
  }

  @Test
  void checkIdentifierAcceptsJavaScriptNamesOnly() {
    Assertions.assertDoesNotThrow(() -> BridgeProtocol.checkIdentifier("$_ok9"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> BridgeProtocol.checkIdentifier("not valid"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> BridgeProtocol.checkIdentifier("lwjwae:emit"));
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
