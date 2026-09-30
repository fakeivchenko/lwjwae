package dev.ivchenko.lwjwae.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeTypeProcessorTest {
  private static final String DEFAULT_FILE =
      "META-INF/native-image/dev.ivchenko.lwjwae/bridge-types/";

  @TempDir Path directory;

  @Test
  void annotatedTypesAndWhatTheyHoldGetReflection() throws IOException {
    String metadata =
        this.compile(
            Map.of(
                "com/example/shapes/Outline.java",
                """
                package com.example.shapes;

                import dev.ivchenko.lwjwae.bridge.codec.BridgeType;
                import java.util.List;
                import java.util.Map;

                @BridgeType
                public record Outline(String name, List<Point> points, Map<String, Style> styles,
                    Kind[] kinds) {}
                """,
                "com/example/shapes/Point.java",
                "package com.example.shapes; public record Point(int x, int y) {}",
                "com/example/shapes/Style.java",
                """
                package com.example.shapes;

                public class Style extends Base {
                  private static Unused cache;
                  private String color;
                  public static class Nested {}
                }
                """,
                "com/example/shapes/Base.java",
                "package com.example.shapes; public class Base { protected double width; }",
                "com/example/shapes/Kind.java",
                "package com.example.shapes; public enum Kind { LINE, CURVE }",
                "com/example/shapes/Unused.java",
                "package com.example.shapes; public class Unused {}"),
            List.of());

    Assertions.assertEquals(
        BridgeTypeProcessor.json(
            new TreeSet<>(
                List.of(
                    "com.example.shapes.Base",
                    "com.example.shapes.Kind",
                    "com.example.shapes.Outline",
                    "com.example.shapes.Point",
                    "com.example.shapes.Style"))),
        metadata);
  }

  @Test
  void nestedTypesGoByTheirBinaryName() throws IOException {
    String metadata =
        this.compile(
            Map.of(
                "com/example/shapes/Canvas.java",
                """
                package com.example.shapes;

                import dev.ivchenko.lwjwae.bridge.codec.BridgeType;

                public class Canvas {
                  @BridgeType
                  public record Layer(Cell cell) {}

                  public record Cell(int row) {}
                }
                """),
            List.of());

    Assertions.assertTrue(metadata.contains("\"com.example.shapes.Canvas$Layer\""), metadata);
    Assertions.assertTrue(metadata.contains("\"com.example.shapes.Canvas$Cell\""), metadata);
    Assertions.assertFalse(metadata.contains("\"com.example.shapes.Canvas\""), metadata);
  }

  @Test
  void boundsAndSealedSubtypesAreFollowed() throws IOException {
    String metadata =
        this.compile(
            Map.of(
                "com/example/shapes/Drawing.java",
                """
                package com.example.shapes;

                import dev.ivchenko.lwjwae.bridge.codec.BridgeType;
                import java.util.List;

                @BridgeType
                public record Drawing<T extends Layer & Comparable<T>>(
                    List<? extends Point> points, List<? super Label> labels, T layer, Shape shape) {}
                """,
                "com/example/shapes/Point.java",
                "package com.example.shapes; public record Point(int x, int y) {}",
                "com/example/shapes/Label.java",
                "package com.example.shapes; public record Label(String text) {}",
                "com/example/shapes/Layer.java",
                "package com.example.shapes; public class Layer { private Color color; }",
                "com/example/shapes/Color.java",
                "package com.example.shapes; public record Color(int rgb) {}",
                "com/example/shapes/Shape.java",
                "package com.example.shapes; public sealed interface Shape permits Circle, Square"
                    + " {}",
                "com/example/shapes/Circle.java",
                "package com.example.shapes; public record Circle(double radius) implements Shape"
                    + " {}",
                "com/example/shapes/Square.java",
                "package com.example.shapes; public record Square(double side) implements Shape"
                    + " {}"),
            List.of());

    for (String type : List.of("Point", "Label", "Layer", "Color", "Shape", "Circle", "Square")) {
      Assertions.assertTrue(metadata.contains("\"com.example.shapes." + type + "\""), metadata);
    }
  }

  @Test
  void modulesThatSharePackageWriteFilesOfTheirOwn() throws IOException {
    this.compile(
        Map.of(
            "com/example/shapes/Point.java",
            """
            package com.example.shapes;

            @dev.ivchenko.lwjwae.bridge.codec.BridgeType
            public record Point(int x, int y) {}
            """),
        List.of());

    Assertions.assertTrue(
        Files.isRegularFile(
            this.directory.resolve(
                "classes/"
                    + DEFAULT_FILE
                    + "com.example.shapes.Point/reachability-metadata.json")));
  }

  @Test
  void optionNamesTheDirectory() throws IOException {
    this.compile(
        Map.of(
            "com/example/shapes/Point.java",
            """
            package com.example.shapes;

            @dev.ivchenko.lwjwae.bridge.codec.BridgeType
            public record Point(int x, int y) {}
            """),
        List.of("-Alwjwae.metadataDirectory=/com.example/shapes/"));

    Assertions.assertTrue(
        Files.isRegularFile(
            this.directory.resolve(
                "classes/META-INF/native-image/com.example/shapes/reachability-metadata.json")));
  }

  @Test
  void compilationWithoutAnnotatedTypesWritesNothing() throws IOException {
    this.compile(
        Map.of("com/example/shapes/Point.java", "package com.example.shapes; record Point() {}"),
        List.of());

    Assertions.assertFalse(Files.exists(this.directory.resolve("classes/META-INF")));
  }

  /**
   * Compiles {@code sources} with the processor and returns the metadata that it wrote to the
   * default place, or an empty string.
   */
  private String compile(Map<String, String> sources, List<String> options) throws IOException {
    Path sourceRoot = this.directory.resolve("src");
    Path classes = this.directory.resolve("classes");
    Files.createDirectories(classes);
    List<Path> files = new ArrayList<>();
    for (Map.Entry<String, String> source : sources.entrySet()) {
      Path file = sourceRoot.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
      files.add(file);
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
      Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(files);
      List<String> arguments = new ArrayList<>(options);
      arguments.addAll(
          List.of(
              "-classpath",
              System.getProperty("java.class.path"),
              "-d",
              classes.toString(),
              "-proc:only"));
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, manager, null, arguments, null, units);
      task.setProcessors(List.of(new BridgeTypeProcessor()));
      Assertions.assertTrue(task.call(), "the sources compile");
    }
    Path types = classes.resolve(DEFAULT_FILE);
    if (!Files.isDirectory(types)) {
      return "";
    }
    try (var directories = Files.list(types)) {
      Path metadata = directories.findFirst().orElseThrow().resolve("reachability-metadata.json");
      return Files.readString(metadata, StandardCharsets.UTF_8);
    }
  }
}
