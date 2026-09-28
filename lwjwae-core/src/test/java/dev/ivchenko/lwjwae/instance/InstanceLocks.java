package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import java.nio.file.Path;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/** Claims in a directory of the test, for the tests of other packages. */
@UtilityClass
public class InstanceLocks {
  /**
   * {@link InstanceLock#claim(String, SecondInstanceEvent)} with the socket in {@code directory}.
   */
  public Optional<InstanceLock> claim(Path directory, String name, SecondInstanceEvent start) {
    return InstanceLock.claim(directory, name, start);
  }
}
