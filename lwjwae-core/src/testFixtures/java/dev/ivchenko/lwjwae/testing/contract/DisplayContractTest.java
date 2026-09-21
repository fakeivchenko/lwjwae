package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.testing.DisplayAssumptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

/**
 * The base of every contract that opens a window. It runs only on the platform of the module, and
 * only when that platform has a display to draw on.
 */
public abstract class DisplayContractTest {
  /** Checks whether the machine that runs the tests is the platform of this module. */
  protected abstract boolean isThisPlatform();

  @BeforeEach
  void requirePlatformAndDisplay() {
    Assumptions.assumeTrue(this.isThisPlatform(), "Not this backend's platform");
    DisplayAssumptions.assumeDisplay();
  }
}
