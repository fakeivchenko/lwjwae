package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

/** The processes of the system that an installer runs. */
@UtilityClass
class UpdateProcesses {
  /**
   * Starts {@code command} with its input and output let go, so it outlives the application: no
   * process waits for its pipes.
   */
  void startDetached(String... command) throws IOException {
    new ProcessBuilder(command)
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .getOutputStream()
        .close();
  }

  /**
   * Runs {@code command} to its end and returns what it wrote, its errors included.
   *
   * @throws IOException If it can't start, or ends with another status than 0.
   */
  String run(String... command) throws IOException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output;
    try (InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    try {
      int status = process.waitFor();
      if (status != 0) {
        throw new IOException(String.join(" ", command) + " ended with " + status + ": " + output);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for " + command[0], e);
    }
    return output;
  }
}
