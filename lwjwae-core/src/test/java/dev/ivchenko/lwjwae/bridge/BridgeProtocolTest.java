package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.WindowEdge;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.event.Event;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BridgeProtocolTest {
  @Test
  void separatorIsNotNul() {
    Assertions.assertEquals(String.valueOf((char) 0x1f), BridgeProtocol.SEPARATOR);
  }

  @Test
  void bootstrapEmbedsTheTransportTheTokenAndTheTrustedOrigins() {
    String script =
        BridgeProtocol.bootstrapScript(
            "(m) => host.post(m)",
            "fakeCodec",
            "{base:\"app://local/__lwjwae/rpc/\"}",
            "secret",
            List.of("app://local", "http://localhost:5173"),
            List.of(WindowEdge.TOP, WindowEdge.TOP_LEFT));
    Assertions.assertTrue(script.contains("const resizeEdges = [\"top\",\"top-left\"];"));
    Assertions.assertTrue(script.contains("const post = (m) => host.post(m);"));
    Assertions.assertTrue(script.contains("const codec = fakeCodec;"));
    Assertions.assertTrue(script.contains("const rpc = {base:\"app://local/__lwjwae/rpc/\"};"));
    Assertions.assertTrue(script.contains("const token = trusted ? \"secret\" : null;"));
    Assertions.assertTrue(
        script.contains("const trustedOrigins = [\"app://local\",\"http://localhost:5173\"];"));
    Assertions.assertTrue(
        script.contains("window." + BridgeProtocol.CHANNEL + " = { receive, bound };"));
    Assertions.assertTrue(
        script.contains(
            "window."
                + BridgeProtocol.PAGE_API
                + " = { listen, once, emit, open, close, openExternal, call: callRpc, invoke:"
                + " rpcInvoke, RpcError, window: windowApi, dialog };"));
    for (String reserved :
        List.of(
            BridgeProtocol.EVENT_CALL,
            BridgeProtocol.EVENTS_CALL,
            BridgeProtocol.OPEN_CALL,
            BridgeProtocol.CLOSE_CALL,
            BridgeProtocol.CONTROL_CALL,
            BridgeProtocol.DIALOG_CALL,
            BridgeProtocol.WINDOW_EVENT)) {
      Assertions.assertTrue(script.contains("\"" + reserved + "\""), reserved);
    }
    for (String placeholder :
        List.of("${channel}", "${post}", "${rpc}", "${token}", "${trusted}")) {
      Assertions.assertFalse(script.contains(placeholder), placeholder + " is filled");
    }
    Assertions.assertTrue(
        script.contains("if (window." + BridgeProtocol.CHANNEL + ") return;"),
        "must be safe to inject twice");
  }

  @Test
  void bindingPublishesWindowFunction() {
    Assertions.assertEquals(
        "window[\"reverse\"] = (payload) => window.__lwjwaeBridge.bound(\"reverse\", payload,"
            + " false);",
        BridgeProtocol.bindingScript("reverse"));
    Assertions.assertEquals(
        "window[\"typed\"] = (payload) => window.__lwjwaeBridge.bound(\"typed\", payload, true);",
        BridgeProtocol.bindingScript("typed", true));
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
            String.join(
                sep,
                "Docs",
                "640",
                "480",
                "10",
                "20",
                "1",
                "https://x",
                "app/i.html",
                "0",
                "0",
                "0",
                "0"));
    Assertions.assertEquals("Docs", full.title());
    Assertions.assertEquals(640, full.width());
    Assertions.assertEquals(480, full.height());
    Assertions.assertEquals(10, full.x());
    Assertions.assertEquals(20, full.y());
    Assertions.assertTrue(full.centered());
    Assertions.assertEquals("https://x", full.url());
    Assertions.assertEquals("app/i.html", full.resource());
    Assertions.assertFalse(full.decorated());
    Assertions.assertFalse(full.closable());
    Assertions.assertFalse(full.minimizable());
    Assertions.assertFalse(full.maximizable());

    WindowParameters empty = BridgeProtocol.parseWindowParameters(sep.repeat(11));
    Assertions.assertEquals(WindowParameters.createDefault(), empty);

    Assertions.assertNull(BridgeProtocol.parseWindowParameters("garbage"));
    Assertions.assertNull(
        BridgeProtocol.parseWindowParameters(
            String.join(sep, "", "wide", "", "", "", "", "", "", "", "", "", "")));
  }

  @Test
  void checkIdentifierAcceptsJavaScriptNamesOnly() {
    Assertions.assertDoesNotThrow(() -> BridgeProtocol.checkIdentifier("$_ok9"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> BridgeProtocol.checkIdentifier("not valid"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> BridgeProtocol.checkIdentifier("lwjwae:emit"));
  }
}
