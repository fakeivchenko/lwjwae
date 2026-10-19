package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.event.OpenEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceTest {
  @Test
  void secondStartHandsItsArgumentsOverAndGetsNoApplication(@TempDir Path directory)
      throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder()
            .name("lwjwae-instance-" + UUID.randomUUID())
            .dataDirectory(directory)
            .build();
    try (Application application =
        SingleInstance.create(parameters, "notes://first").orElseThrow()) {
      BlockingQueue<OpenEvent> opened = new LinkedBlockingQueue<>();
      application.onOpen(opened::add);
      Assertions.assertEquals(
          new OpenEvent(List.of(URI.create("notes://first")), List.of()),
          opened.poll(),
          "the arguments of the first process are a request to open too");
      BlockingQueue<SecondInstanceEvent> heard = new LinkedBlockingQueue<>();
      SingleInstance.of(application).onSecondInstance(heard::add);

      Optional<Application> second = SingleInstance.create(parameters, "two", "--x");

      Assertions.assertTrue(second.isEmpty(), "the second start gets no application");
      SecondInstanceEvent start = heard.poll(10, TimeUnit.SECONDS);
      Assertions.assertNotNull(start, "the running instance must hear of the second start");
      Assertions.assertEquals(List.of("two", "--x"), start.arguments());
      Assertions.assertEquals(Path.of("").toAbsolutePath(), start.workingDirectory());
    }

    try (Application next = SingleInstance.create(parameters).orElseThrow()) {
      Assertions.assertFalse(next.isClosed(), "a closed instance gives the name up");
    }
  }

  @Test
  void singleInstanceNeedsTheNameOfTheApplication() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SingleInstance.create(ApplicationParameters.builder().build()));
  }
}
