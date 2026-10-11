package dev.ivchenko.lwjwae.testing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
 *
 * <p>The list of the binding classes is written by hand, and a class that binds and isn't on it
 * would escape the comparison. So a second test reads the class files of the module and asks that
 * every class that calls {@code NativeLibraries.downcall} or {@code upcall} is on the list.
 */
public abstract class NativeImageMetadataContractTest {
  /**
   * Returns the classpath location of the {@code reachability-metadata.json} file of the module.
   */
  protected abstract String metadataPath();

  /** Returns every class whose static initializer binds native functions or callbacks. */
  protected abstract List<Class<?>> bindingClasses();

  /**
   * Returns the root package of the module, such as {@code dev.ivchenko.lwjwae.gtk}, whose classes
   * that bind {@link #bindingClasses} lists.
   */
  protected abstract String modulePackage();

  /**
   * Returns the metadata of the modules that this one depends on, whose binding classes its own
   * initialize in passing. {@code native-image} merges the metadata of every JAR file on the class
   * path, so the stubs of the module are its own metadata and these together. None by default.
   */
  protected List<String> inheritedMetadataPaths() {
    return List.of();
  }

  @Test
  void everyClassThatBindsIsListed() throws Exception {
    String root = this.modulePackage();
    Class<?> anchor =
        this.bindingClasses().stream()
            .filter(type -> type.getPackageName().startsWith(root))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No class of " + root + " is listed"));
    Path location = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
    Set<String> listed =
        this.bindingClasses().stream().map(Class::getName).collect(Collectors.toSet());
    Set<String> unlisted = new TreeSet<>();
    for (Map.Entry<String, byte[]> type : NativeImageMetadataContractTest.classFiles(location)) {
      String name = type.getKey();
      boolean inModule = name.equals(root) || name.startsWith(root + ".");
      if (inModule
          && !name.equals(NativeLibraries.class.getName())
          && !listed.contains(name)
          && NativeImageMetadataContractTest.binds(type.getValue())) {
        unlisted.add(name);
      }
    }
    Assertions.assertEquals(
        Set.of(), unlisted, "classes that bind native code, missing from bindingClasses()");
  }

  @Test
  void metadataListsExactlyTheBoundStubs() throws Exception {
    String metadataPath = this.metadataPath();
    // Static initializers bind everything; no window, no toolkit initialization.
    this.bindingClasses().forEach(NativeImageMetadataContractTest::initialize);

    List<JsonNode> sections = new ArrayList<>();
    sections.add(NativeImageMetadataContractTest.readMetadata(metadataPath).path("foreign"));
    for (String inherited : this.inheritedMetadataPaths()) {
      sections.add(NativeImageMetadataContractTest.readMetadata(inherited).path("foreign"));
    }

    Set<String> registeredDowncalls =
        sections.stream()
            .flatMap(foreign -> NativeImageMetadataContractTest.stream(foreign.path("downcalls")))
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
        sections.stream()
            .flatMap(
                foreign -> NativeImageMetadataContractTest.stream(foreign.path("directUpcalls")))
            .map(
                node ->
                    node.path("class").asText()
                        + "#"
                        + node.path("method").asText()
                        + " "
                        + NativeImageMetadataContractTest.signature(node))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> boundUpcalls =
        NativeLibraries.upcalls().stream()
            .map(
                target ->
                    target.owner().getName()
                        + "#"
                        + target.method()
                        + " "
                        + NativeImageMetadataContractTest.signature(target.descriptor()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Assertions.assertEquals(
        boundUpcalls,
        registeredUpcalls,
        "foreign.directUpcalls in " + metadataPath + " is out of sync with the bindings");
  }

  /**
   * The class files under {@code location}, a directory or a JAR file, by the name of their
   * outermost class: what an inner class binds, its outer class initializes.
   */
  private static List<Map.Entry<String, byte[]>> classFiles(Path location) throws IOException {
    List<Map.Entry<String, byte[]>> files = new ArrayList<>();
    if (Files.isDirectory(location)) {
      try (Stream<Path> walk = Files.walk(location)) {
        for (Path file : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
          String relative = location.relativize(file).toString().replace('\\', '/');
          files.add(
              Map.entry(
                  NativeImageMetadataContractTest.className(relative), Files.readAllBytes(file)));
        }
      }
    } else {
      try (JarFile jar = new JarFile(location.toFile())) {
        for (JarEntry entry : Collections.list(jar.entries())) {
          if (entry.getName().endsWith(".class")) {
            try (InputStream in = jar.getInputStream(entry)) {
              files.add(
                  Map.entry(
                      NativeImageMetadataContractTest.className(entry.getName()),
                      in.readAllBytes()));
            }
          }
        }
      }
    }
    return files;
  }

  /** {@code a/b/C$D.class} as {@code a.b.C}. */
  private static String className(String path) {
    String name = path.substring(0, path.length() - ".class".length()).replace('/', '.');
    int inner = name.indexOf('$');
    return inner < 0 ? name : name.substring(0, inner);
  }

  /** Whether a class file calls {@code NativeLibraries.downcall} or {@code upcall}. */
  private static boolean binds(byte[] classFile) {
    String text = new String(classFile, StandardCharsets.ISO_8859_1);
    return text.contains("dev/ivchenko/lwjwae/foreign/NativeLibraries")
        && (text.contains("downcall") || text.contains("upcall"));
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
        + NativeImageMetadataContractTest.stream(node.path("parameterTypes"))
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
