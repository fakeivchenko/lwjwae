package dev.ivchenko.lwjwae.instance.internal;

import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.event.OpenEvent;
import dev.ivchenko.lwjwae.instance.SecondInstanceEvent;
import dev.ivchenko.lwjwae.instance.SingleInstance;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.ShortTemporaryDirectories;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceModuleTest {
  @Test
  void secondInstanceBringsTheOldestWindowForwardAndReachesTheListeners(
      @TempDir(factory = ShortTemporaryDirectories.class) Path directory) throws Exception {
    try (FakeApplication application = InstanceModuleTest.application()) {
      final FakeWindow oldest = application.openFake();
      final FakeWindow newer = application.openFake();
      InstanceModuleTest.serve(application, directory);
      BlockingQueue<SecondInstanceEvent> heard = new LinkedBlockingQueue<>();
      SingleInstance.of(application).onSecondInstance(heard::add);

      SecondInstanceEvent second =
          new SecondInstanceEvent(List.of("notes.txt"), Path.of("/home/user"));
      Assertions.assertTrue(InstanceLock.claim(directory, "app", second).isEmpty());

      Assertions.assertEquals(second, heard.poll(), "heard before the second process ended");
      Assertions.assertTrue(oldest.isFocused(), "the oldest window comes forward");
      Assertions.assertFalse(newer.isFocused());
    }
  }

  @Test
  void startBeforeAnyListenerReachesTheFirstOne(
      @TempDir(factory = ShortTemporaryDirectories.class) Path directory) {
    try (FakeApplication application = InstanceModuleTest.application()) {
      InstanceModuleTest.serve(application, directory);
      SecondInstanceEvent early = new SecondInstanceEvent(List.of("early"), Path.of("/"));
      Assertions.assertTrue(InstanceLock.claim(directory, "app", early).isEmpty());

      List<SecondInstanceEvent> heard = new CopyOnWriteArrayList<>();
      SingleInstance.of(application).onSecondInstance(heard::add);

      Assertions.assertEquals(List.of(early), heard);
    }
  }

  @Test
  void quitGivesTheNameUp(@TempDir(factory = ShortTemporaryDirectories.class) Path directory) {
    try (FakeApplication application = InstanceModuleTest.application()) {
      InstanceModuleTest.serve(application, directory);
      application.quit();
    }

    try (InstanceLock next =
        InstanceLock.claim(directory, "app", InstanceModuleTest.start()).orElseThrow()) {
      Assertions.assertNotNull(next);
    }
  }

  @Test
  void requestsToOpenComeFromEveryLaterStart(
      @TempDir(factory = ShortTemporaryDirectories.class) Path directory) throws Exception {
    Path note = Files.writeString(directory.resolve("note.txt"), "text");
    try (FakeApplication application = InstanceModuleTest.application()) {
      InstanceModuleTest.serve(application, directory);
      BlockingQueue<OpenEvent> heard = new LinkedBlockingQueue<>();
      application.onOpen(heard::add);

      SecondInstanceEvent second =
          new SecondInstanceEvent(List.of("note.txt", "notes://second"), directory);
      Assertions.assertTrue(InstanceLock.claim(directory, "app", second).isEmpty());
      Assertions.assertEquals(
          new OpenEvent(List.of(URI.create("notes://second")), List.of(note)),
          heard.poll(5, TimeUnit.SECONDS));

      SecondInstanceEvent plain = new SecondInstanceEvent(List.of("--flag"), directory);
      Assertions.assertTrue(InstanceLock.claim(directory, "app", plain).isEmpty());
      Assertions.assertNull(
          heard.poll(200, TimeUnit.MILLISECONDS), "a start with nothing to open is no request");
    }
  }

  /** A fake application with this module started, as {@code Application.create} starts it. */
  private static FakeApplication application() {
    return FakeApplication.withModules(ApplicationParameters.builder().build());
  }

  /** Claims "app" in {@code directory} for {@code application}, as SingleInstance.create does. */
  private static void serve(FakeApplication application, Path directory) {
    application
        .module(InstanceModule.class)
        .serve(InstanceLock.claim(directory, "app", InstanceModuleTest.start()).orElseThrow());
  }

  private static SecondInstanceEvent start() {
    return new SecondInstanceEvent(List.of(), Path.of("/"));
  }
}
