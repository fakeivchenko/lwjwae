package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Listens on the session bus, as a dock does, with {@code dbus-monitor}.
 *
 * <p>The monitor says nothing when it's ready: with a match rule, some versions of D-Bus print the
 * name that it got and some don't. So the entry sends its signal again until the monitor hears it,
 * and the output is read on a thread of its own, which a read that never returns can't hold the
 * test on.
 */
@Tag("display")
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class LauncherEntryTest {
  @Test
  void updateCarriesTheWholeStateToTheDock() throws Exception {
    Assumptions.assumeTrue(
        System.getenv("DBUS_SESSION_BUS_ADDRESS") != null, "needs a session bus to listen on");
    Process monitor =
        new ProcessBuilder(
                "dbus-monitor",
                "--session",
                "type='signal',interface='com.canonical.Unity.LauncherEntry'")
            .redirectErrorStream(true)
            .start();
    BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    Thread reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (BufferedReader output =
                      new BufferedReader(
                          new InputStreamReader(
                              monitor.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line = output.readLine(); line != null; line = output.readLine()) {
                      lines.add(line.strip());
                    }
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
    try {
      LauncherEntry entry = new LauncherEntry("notes.desktop");
      StringBuilder heard = new StringBuilder();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (!heard.toString().contains("double 0.25") && System.nanoTime() < deadline) {
        entry.count(3);
        entry.progress(TaskbarProgress.of(0.25));
        for (String line = lines.poll(200, TimeUnit.MILLISECONDS);
            line != null;
            line = lines.poll(50, TimeUnit.MILLISECONDS)) {
          heard.append(line).append('\n');
        }
      }
      String text = heard.toString();
      Assertions.assertTrue(text.contains("member=Update"), text);
      Assertions.assertTrue(text.contains("string \"application://notes.desktop\""), text);
      Assertions.assertTrue(text.contains("int64 3"), text);
      Assertions.assertTrue(text.contains("double 0.25"), text);
    } finally {
      monitor.destroy();
      monitor.waitFor(5, TimeUnit.SECONDS);
      reader.join(TimeUnit.SECONDS.toMillis(5));
    }
  }
}
