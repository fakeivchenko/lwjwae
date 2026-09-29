package dev.ivchenko.lwjwae.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Writes the native-image metadata of the types that cross the lwjwae bridge: every type annotated
 * with {@code @BridgeType}, and every type that one of them holds, gets reflection on its
 * constructors, methods, and fields, which is what a codec reads it and creates it through.
 *
 * <p>The processor follows what a codec follows: the components of a record, the instance fields of
 * a class, its superclasses, the component type of an array, and the type arguments of a declared
 * type, so {@code List<Point>} brings {@code Point}. The types of the JDK stay out; a native image
 * reaches the collections and the boxes on its own.
 *
 * <p>The metadata goes to one file, {@code
 * META-INF/native-image/dev.ivchenko.lwjwae/bridge-types/PACKAGE/reachability-metadata.json}, with
 * {@code PACKAGE} the package of the first annotated type in name order, so that two modules of an
 * application write two files. The option {@code lwjwae.metadataDirectory} names the directory
 * under {@code META-INF/native-image} instead. The processor is aggregating: Gradle runs it again
 * when an annotated type changes, and the file lists every type of the compilation.
 */
@SupportedAnnotationTypes(BridgeTypeProcessor.ANNOTATION)
@SupportedOptions(BridgeTypeProcessor.DIRECTORY_OPTION)
public final class BridgeTypeProcessor extends AbstractProcessor {
  static final String ANNOTATION = "dev.ivchenko.lwjwae.bridge.codec.BridgeType";
  static final String DIRECTORY_OPTION = "lwjwae.metadataDirectory";

  private static final String DEFAULT_DIRECTORY = "dev.ivchenko.lwjwae/bridge-types/";
  private static final List<String> JDK_PACKAGES =
      List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

  /** The binary names of every type found so far, in order. */
  private final Set<String> types = new TreeSet<>();

  /** The annotated types, which the file is made from, for incremental builds. */
  private final List<TypeElement> annotated = new ArrayList<>();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
    for (TypeElement annotation : annotations) {
      for (Element element : round.getElementsAnnotatedWith(annotation)) {
        if (element instanceof TypeElement type) {
          this.annotated.add(type);
          this.collect(type);
        }
      }
    }
    if (round.processingOver() && !this.annotated.isEmpty()) {
      this.write();
    }
    return false;
  }

  /** Adds {@code type} and what it holds, once. */
  private void collect(TypeElement type) {
    String name = this.processingEnv.getElementUtils().getBinaryName(type).toString();
    if (BridgeTypeProcessor.isJdk(name) || !this.types.add(name)) {
      return;
    }
    for (RecordComponentElement component :
        ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
      this.collect(component.asType());
    }
    if (type.getKind() != ElementKind.RECORD && type.getKind() != ElementKind.ENUM) {
      for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
        if (!field.getModifiers().contains(Modifier.STATIC)) {
          this.collect(field.asType());
        }
      }
    }
    this.collect(type.getSuperclass());
  }

  private void collect(TypeMirror type) {
    if (type.getKind() == TypeKind.ARRAY) {
      this.collect(((ArrayType) type).getComponentType());
    } else if (type.getKind() == TypeKind.DECLARED) {
      DeclaredType declared = (DeclaredType) type;
      if (declared.asElement() instanceof TypeElement element) {
        this.collect(element);
      }
      declared.getTypeArguments().forEach(this::collect);
    }
  }

  private void write() {
    String path = "META-INF/native-image/" + this.directory() + "/reachability-metadata.json";
    try {
      FileObject file =
          this.processingEnv
              .getFiler()
              .createResource(
                  StandardLocation.CLASS_OUTPUT, "", path, this.annotated.toArray(new Element[0]));
      try (Writer writer = file.openWriter()) {
        writer.write(BridgeTypeProcessor.json(this.types));
      }
    } catch (IOException e) {
      this.processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Could not write " + path + ": " + e.getMessage());
    }
  }

  /** The directory of the file under {@code META-INF/native-image}. */
  private String directory() {
    String option = this.processingEnv.getOptions().get(DIRECTORY_OPTION);
    if (option != null && !option.isBlank()) {
      return option.strip().replaceAll("^/+|/+$", "");
    }
    TypeElement first =
        this.annotated.stream()
            .min(Comparator.comparing(type -> type.getQualifiedName().toString()))
            .orElseThrow();
    PackageElement enclosing = this.processingEnv.getElementUtils().getPackageOf(first);
    return DEFAULT_DIRECTORY + (enclosing.isUnnamed() ? "default" : enclosing.getQualifiedName());
  }

  /** The metadata of {@code names}, one reflection entry each, in name order. */
  static String json(Set<String> names) {
    StringBuilder json = new StringBuilder("{\n  \"reflection\": [");
    String separator = "\n";
    for (String name : names) {
      json.append(separator)
          .append("    {\n      \"type\": \"")
          .append(name)
          .append("\",\n      \"allDeclaredConstructors\": true,\n")
          .append("      \"allDeclaredMethods\": true,\n")
          .append("      \"allDeclaredFields\": true\n    }");
      separator = ",\n";
    }
    return json.append("\n  ]\n}\n").toString();
  }

  private static boolean isJdk(String name) {
    return JDK_PACKAGES.stream().anyMatch(name::startsWith);
  }
}
