# lwjwae-processor

An annotation processor that writes the native-image metadata of the types that cross the bridge
through a codec. On the JVM, a codec such as Jackson finds the constructors, accessors, and fields
of a type through reflection. A native image allows reflection only on the types that its metadata
lists, so without it a typed call fails at run time:

```text
UnsupportedFeatureError: Record components not available for record class com.example.Outline.
```

## Use

Add the processor to the build, and mark the outermost types of your binds and events with
`@BridgeType` from `lwjwae-core`:

```kotlin
dependencies {
    implementation("dev.ivchenko.lwjwae:lwjwae-core:VERSION")
    annotationProcessor("dev.ivchenko.lwjwae:lwjwae-processor:VERSION")
}
```

```java
@BridgeType
public record Outline(String name, List<Point> points, Map<String, Style> styles) {}

window.bind("outline", Outline.class, outline -> outline.points().size());
```

The processor lists every annotated type and every type that one of them holds, so `Point` and
`Style` need no annotation of their own. It follows:

- the components of a record;
- the instance fields of a class;
- the superclasses;
- the component type of an array;
- the type arguments of a declared type, such as the `Point` of `List<Point>`.

Types of the JDK stay out: a native image reaches the collections and the boxed values on its
own. Each type gets reflection on its declared constructors, methods, and fields, which covers
Jackson, Gson, and JSON-B.

## Where the metadata goes

The processor writes one file to the classes of the compilation:

```text
META-INF/native-image/dev.ivchenko.lwjwae/bridge-types/PACKAGE/reachability-metadata.json
```

`PACKAGE` is the package of the first annotated type in name order, so two modules of an
application write two files that don't replace each other. To choose the directory under
`META-INF/native-image` yourself, pass an option to the compiler:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Alwjwae.metadataDirectory=com.example/notes")
}
```

The processor is aggregating for the incremental compilation of Gradle: a change to an annotated
type runs it again, and the file lists every type of the compilation.
