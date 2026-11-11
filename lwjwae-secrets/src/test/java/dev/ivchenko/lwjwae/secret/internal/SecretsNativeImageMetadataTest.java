package dev.ivchenko.lwjwae.secret.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.io.InputStream;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The reachability metadata of the module lists every downcall that the store of this platform
 * binds, so that a native image of an application calls it.
 *
 * <p>The module binds a store on each of three platforms, and only the one of the machine that runs
 * the test can load, so the metadata holds the downcalls of all three, and each run checks that the
 * ones of its platform are among them. The runs on the three platforms of CI check them all.
 */
class SecretsNativeImageMetadataTest {
  private static final String METADATA =
      "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-secrets/reachability-metadata.json";

  @Test
  void metadataListsTheDowncallsOfTheStoreOfThisPlatform() throws Exception {
    Set<FunctionDescriptor> before = new HashSet<>(NativeLibraries.downcalls());
    Class<?> binding =
        PlatformUtil.isWindows()
            ? Credentials.class
            : PlatformUtil.isMacOs() ? Keychain.class : Libsecret.class;
    Class.forName(binding.getName(), true, binding.getClassLoader());

    Set<String> bound =
        NativeLibraries.downcalls().stream()
            .filter(descriptor -> !before.contains(descriptor))
            .map(SecretsNativeImageMetadataTest::signature)
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> registered;
    try (InputStream in =
        SecretsNativeImageMetadataTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      Assertions.assertNotNull(in, "Missing " + METADATA);
      JsonNode downcalls = new ObjectMapper().readTree(in).path("foreign").path("downcalls");
      registered =
          StreamSupport.stream(downcalls.spliterator(), false)
              .map(
                  node ->
                      node.path("returnType").asText()
                          + "("
                          + StreamSupport.stream(node.path("parameterTypes").spliterator(), false)
                              .map(JsonNode::asText)
                              .collect(Collectors.joining(", "))
                          + ")")
              .collect(Collectors.toCollection(TreeSet::new));
    }
    bound.removeAll(registered);
    Assertions.assertEquals(Set.of(), bound, "downcalls of " + binding.getSimpleName());
  }

  private static String signature(FunctionDescriptor descriptor) {
    return descriptor.returnLayout().map(SecretsNativeImageMetadataTest::typeName).orElse("void")
        + "("
        + descriptor.argumentLayouts().stream()
            .map(SecretsNativeImageMetadataTest::typeName)
            .collect(Collectors.joining(", "))
        + ")";
  }

  /** The name that {@code native-image-agent} writes for {@code layout}. */
  private static String typeName(MemoryLayout layout) {
    return switch (layout) {
      case AddressLayout _ -> "void*";
      case ValueLayout.OfInt _ -> "jint";
      case ValueLayout.OfLong _ -> "jlong";
      case ValueLayout.OfBoolean _ -> "jboolean";
      default -> layout.toString();
    };
  }
}
