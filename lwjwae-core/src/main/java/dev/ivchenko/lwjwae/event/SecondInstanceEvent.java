package dev.ivchenko.lwjwae.event;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A second start of an application that runs as a single instance: what the new process was started
 * with. The new process handed it over and ended.
 *
 * @param arguments The arguments of the new process, as its {@code main} received them.
 * @param workingDirectory The working directory of the new process, which a relative path among the
 *     arguments is relative to.
 */
public record SecondInstanceEvent(List<String> arguments, Path workingDirectory) {
  public SecondInstanceEvent {
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(workingDirectory, "workingDirectory");
  }
}
