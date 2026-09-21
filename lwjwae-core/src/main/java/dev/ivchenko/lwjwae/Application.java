package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.exception.BackendNotAvailableException;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The entry point. Creates an application window with the backend that fits the machine it runs on.
 *
 * <pre>{@code
 * try (ApplicationBackend application = Application.create(ApplicationParameters.builder()
 *         .title("Docs")
 *         .url("https://example.com")
 *         .build())) {
 *     application.run();
 * }
 * }</pre>
 *
 * <p>Backends are found with {@link ServiceLoader}, so the runtime classpath decides the selection.
 * Put the backend artifact for your platform on the classpath, and no code changes.
 */
@UtilityClass
public class Application {
  /**
   * The system property that names the backend to use. It overrides {@link
   * ApplicationBackendProvider#priority}.
   */
  public final String BACKEND_PROPERTY = "lwjwae.backend";

  /** The environment variable with the same meaning as {@link #BACKEND_PROPERTY}. */
  public final String BACKEND_VARIABLE = "LWJWAE_BACKEND";

  /**
   * Creates a window with {@link ApplicationParameters#createDefault()}.
   *
   * @throws BackendNotAvailableException If no backend supports this machine.
   */
  public ApplicationBackend create() {
    return create(ApplicationParameters.createDefault());
  }

  /**
   * Creates a window with the supported backend of the highest priority, and navigates to {@link
   * ApplicationParameters#url()} when one is set.
   *
   * @throws BackendNotAvailableException If no backend supports this machine.
   */
  public ApplicationBackend create(ApplicationParameters parameters) {
    ApplicationBackendProvider provider =
        provider().orElseThrow(() -> new BackendNotAvailableException(noBackendMessage()));
    ApplicationBackend backend = provider.create(parameters);
    if (parameters.url() != null) {
      backend.navigate(parameters.url());
    }
    return backend;
  }

  /**
   * Returns the backend that {@link #create} would use, if any.
   *
   * <p>Normally, this is the supported provider of the highest priority. {@code
   * -Dlwjwae.backend=NAME} or the {@code LWJWAE_BACKEND} environment variable names one explicitly
   * instead, for example to try a fallback on a machine that also has the native one. The setting
   * applies only if that provider supports the machine.
   */
  public Optional<ApplicationBackendProvider> provider() {
    String requested = requestedBackend();
    return providers().stream()
        .filter(ApplicationBackendProvider::isSupported)
        .filter(provider -> requested == null || provider.name().equals(requested))
        .max(Comparator.comparingInt(ApplicationBackendProvider::priority));
  }

  /** Returns every backend on the classpath, supported or not. Useful for diagnostics. */
  public List<ApplicationBackendProvider> providers() {
    List<ApplicationBackendProvider> found = new ArrayList<>();
    ServiceLoader.load(
            ApplicationBackendProvider.class, ApplicationBackendProvider.class.getClassLoader())
        .forEach(found::add);
    return List.copyOf(found);
  }

  private String requestedBackend() {
    String name = System.getProperty(BACKEND_PROPERTY, System.getenv(BACKEND_VARIABLE));
    return name == null || name.isBlank() ? null : name.strip();
  }

  private String noBackendMessage() {
    List<ApplicationBackendProvider> providers = providers();
    String requested = requestedBackend();
    if (requested != null) {
      return "Backend '"
          + requested
          + "' (from -D"
          + BACKEND_PROPERTY
          + " / "
          + BACKEND_VARIABLE
          + ") is not on the classpath or does not support this machine. Found: "
          + providers.stream()
              .map(ApplicationBackendProvider::name)
              .collect(Collectors.joining(", "));
    }
    if (providers.isEmpty()) {
      return "No backend on the classpath. Add one, for example lwjwae-gtk on Linux.";
    }
    String rejected =
        providers.stream()
            .map(provider -> "  " + provider.name() + ": " + provider.unsupportedReason())
            .collect(Collectors.joining(System.lineSeparator()));
    return "No backend supports this machine ("
        + PlatformUtil.osName()
        + ")."
        + System.lineSeparator()
        + rejected;
  }
}
