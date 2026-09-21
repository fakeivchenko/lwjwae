package dev.ivchenko.lwjwae.testing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.io.InputStream;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the reachability metadata in a backend JAR file lists exactly the stubs that its
 * bindings create.
 *
 * <p>A native image can only perform a downcall, or accept an upcall, whose signature was
 * registered when the image was built. A missing entry doesn't fail {@code nativeCompile}. It fails
 * at runtime, inside the first window operation that reaches the unregistered stub. This test turns
 * that into a build failure. It initializes every binding class, which opens the libraries but uses
 * no display, and compares what {@link NativeLibraries} recorded with the JSON that the tracing
 * agent produced. Extra entries fail too, so the file can't go stale.
 */
public abstract class NativeImageMetadataContractTest {
  /**
   * Returns the classpath location of the {@code reachability-metadata.json} file of the module.
   */
  protected abstract String metadataPath();

  /** Returns every class whose static initializer binds native functions or callbacks. */
  protected abstract List<Class<?>> bindingClasses();

  @Test
  void metadataListsExactlyTheBoundStubs() throws Exception {
    String metadataPath = this.metadataPath();
    // Static initializers bind everything; no window, no toolkit initialization.
    this.bindingClasses().forEach(NativeImageMetadataContractTest::initialize);

    JsonNode foreign = readMetadata(metadataPath).path("foreign");

    Set<String> registeredDowncalls =
        stream(foreign.path("downcalls"))
            .map(NativeImageMetadataContractTest::signature)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> boundDowncalls =
        NativeLibraries.downcalls().stream()
            .map(NativeImageMetadataContractTest::signature)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Assertions.assertEquals(
        boundDowncalls,
        registeredDowncalls,
        "foreign.downcalls in " + metadataPath + " is out of sync with the bindings");

    Set<String> registeredUpcalls =
        stream(foreign.path("directUpcalls"))
            .map(
                node ->
                    node.path("class").asText()
                        + "#"
                        + node.path("method").asText()
                        + " "
                        + signature(node))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> boundUpcalls =
        NativeLibraries.upcalls().stream()
            .map(
                target ->
                    target.owner().getName()
                        + "#"
                        + target.method()
                        + " "
                        + signature(target.descriptor()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Assertions.assertEquals(
        boundUpcalls,
        registeredUpcalls,
        "foreign.directUpcalls in " + metadataPath + " is out of sync with the bindings");
  }

  private static void initialize(Class<?> type) {
    try {
      Class.forName(type.getName(), true, type.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode readMetadata(String metadataPath) throws Exception {
    ClassLoader loader = NativeImageMetadataContractTest.class.getClassLoader();
    try (InputStream stream = loader.getResourceAsStream(metadataPath)) {
      Assertions.assertNotNull(stream, "Missing " + metadataPath);
      return new ObjectMapper().readTree(stream);
    }
  }

  /** Returns {@code returnType(param, param, ...)} in the type vocabulary of the agent. */
  private static String signature(JsonNode node) {
    return node.path("returnType").asText()
        + "("
        + stream(node.path("parameterTypes"))
            .map(JsonNode::asText)
            .collect(Collectors.joining(", "))
        + ")";
  }

  private static String signature(FunctionDescriptor descriptor) {
    return descriptor.returnLayout().map(NativeImageMetadataContractTest::typeName).orElse("void")
        + "("
        + descriptor.argumentLayouts().stream()
            .map(NativeImageMetadataContractTest::typeName)
            .collect(Collectors.joining(", "))
        + ")";
  }

  /**
   * Returns the name that {@code native-image-agent} writes: {@code jint}, {@code jlong}, or {@code
   * void*}.
   */
  private static String typeName(MemoryLayout layout) {
    return switch (layout) {
      case AddressLayout _ -> "void*";
      case ValueLayout.OfInt _ -> "jint";
      case ValueLayout.OfLong _ -> "jlong";
      case ValueLayout.OfByte _ -> "jbyte";
      case ValueLayout.OfShort _ -> "jshort";
      case ValueLayout.OfChar _ -> "jchar";
      case ValueLayout.OfBoolean _ -> "jboolean";
      case ValueLayout.OfFloat _ -> "jfloat";
      case ValueLayout.OfDouble _ -> "jdouble";
      case StructLayout struct ->
          struct.memberLayouts().stream()
              .map(NativeImageMetadataContractTest::typeName)
              .collect(Collectors.joining(",", "struct(", ")"));
      default -> throw new IllegalArgumentException("Unmapped layout: " + layout);
    };
  }

  private static Stream<JsonNode> stream(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false);
  }
}
