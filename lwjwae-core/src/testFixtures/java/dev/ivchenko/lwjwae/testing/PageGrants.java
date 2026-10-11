package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.PageCapability;
import java.util.EnumSet;
import lombok.experimental.UtilityClass;

/**
 * Parameters for tests whose pages use all of {@code window.lwjwae}: an application grants its
 * pages none of the {@link PageCapability capabilities} unless it says so, and a test of the
 * clipboard of the page, say, is about the clipboard, not about the grant.
 */
@UtilityClass
public class PageGrants {
  /** The defaults, with every page capability granted. */
  public ApplicationParameters every() {
    return PageGrants.every(ApplicationParameters.createDefault());
  }

  /** {@code parameters}, with every page capability granted. */
  public ApplicationParameters every(ApplicationParameters parameters) {
    return parameters.toBuilder().pageCapabilities(EnumSet.allOf(PageCapability.class)).build();
  }
}
