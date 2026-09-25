package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import dev.ivchenko.lwjwae.testing.ShortTemporaryDirectories;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class InstanceLockTest {
  private static final SecondInstanceEvent FIRST =
      new SecondInstanceEvent(List.of(), Path.of("/first"));
  private static final SecondInstanceEvent SECOND =
      new SecondInstanceEvent(List.of("open", "ünïcode file.txt", ""), Path.of("/second"));

  @TempDir(factory = ShortTemporaryDirectories.class)
  Path directory;

  @Test
  void firstClaimHoldsTheNameAndLaterOneHandsItsStartOver() throws Exception {
    try (InstanceLock lock = InstanceLock.claim(this.directory, "app", FIRST).orElseThrow()) {
      BlockingQueue<SecondInstanceEvent> heard = new LinkedBlockingQueue<>();
      lock.serve(heard::add);

      Optional<InstanceLock> second = InstanceLock.claim(this.directory, "app", SECOND);

      Assertions.assertTrue(second.isEmpty(), "the name is taken");
      Assertions.assertEquals(SECOND, heard.poll(), "handled before the claim returned");
    }
  }

  @Test
  void anotherNameIsAnotherInstance() {
    try (InstanceLock one = InstanceLock.claim(this.directory, "one", FIRST).orElseThrow();
        InstanceLock two = InstanceLock.claim(this.directory, "two", FIRST).orElseThrow()) {
      Assertions.assertNotSame(one, two);
    }
  }

  @Test
  void closedClaimLetsTheNextProcessHaveTheName() {
    try (InstanceLock first = InstanceLock.claim(this.directory, "app", FIRST).orElseThrow()) {
      first.serve(_ -> Assertions.fail("closed"));
    }

    try (InstanceLock next = InstanceLock.claim(this.directory, "app", SECOND).orElseThrow()) {
      Assertions.assertNotNull(next);
    }
  }

  @Test
  void socketLeftBehindIsReplaced() throws Exception {
    try (InstanceLock first = InstanceLock.claim(this.directory, "app", FIRST).orElseThrow()) {
      Assertions.assertNotNull(first);
    }
    try (Stream<Path> files = Files.list(this.directory)) {
      Assertions.assertTrue(
          files.anyMatch(file -> file.toString().endsWith(".sock")),
          "the socket file stays when its process ends");
    }

    try (InstanceLock next = InstanceLock.claim(this.directory, "app", SECOND).orElseThrow()) {
      BlockingQueue<SecondInstanceEvent> heard = new LinkedBlockingQueue<>();
      next.serve(heard::add);
      Assertions.assertTrue(InstanceLock.claim(this.directory, "app", FIRST).isEmpty());
      Assertions.assertEquals(FIRST, heard.poll());
    }
  }

  @Test
  void failingHandlerStillAnswersSoTheLaterProcessEnds() {
    try (InstanceLock lock = InstanceLock.claim(this.directory, "app", FIRST).orElseThrow()) {
      lock.serve(
          _ -> {
            throw new IllegalStateException("handler exploded");
          });

      Assertions.assertTrue(InstanceLock.claim(this.directory, "app", SECOND).isEmpty());
    }
  }
}
