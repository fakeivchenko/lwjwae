package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Loads a real site over the internet: TLS, redirects, and a heavy page. This test isn't part of
 * any default run, because it depends on a network and on a third party. Run it with {@code
 * ./gradlew networkTest}.
 */
@Tag(Tags.DISPLAY)
@Tag(Tags.NETWORK)
@Timeout(120)
public abstract class NetworkContractTest extends DisplayContractTest {
  @Test
  void rendersGoogle() throws Exception {
    ApplicationParameters parameters = ApplicationParameters.builder().title("google.com").build();
    try (ApplicationBackend backend = Application.create(parameters)) {
      final var loaded = Loads.expectFinished(backend);
      backend.show();
      backend.navigate("https://www.google.com/");
      loaded.get(90, TimeUnit.SECONDS);

      Assertions.assertTrue(Loads.eval(backend, "document.title").toLowerCase().contains("google"));
      Assertions.assertEquals(
          "true", Loads.eval(backend, "String(document.body.innerText.length > 0)"));
      Screenshots.capture("network-google");
    }
  }
}
