package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Listens on the session bus, as a dock does, with {@code dbus-monitor}. */
@Tag("display")
@Timeout(30)
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
    try (BufferedReader output =
        new BufferedReader(
            new InputStreamReader(monitor.getInputStream(), StandardCharsets.UTF_8))) {
      // The first line is the name that the monitor got: from then on, it hears the signals.
      Assertions.assertNotNull(output.readLine());
      LauncherEntry entry = new LauncherEntry("notes.desktop");
      entry.count(3);
      entry.progress(TaskbarProgress.of(0.25));
      StringBuilder heard = new StringBuilder();
      for (String line = output.readLine(); line != null; line = output.readLine()) {
        heard.append(line.strip()).append('\n');
        if (line.contains("double 0.25")) {
          break;
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
    }
  }
}
