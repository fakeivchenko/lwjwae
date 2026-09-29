package dev.ivchenko.lwjwae.bridge.codec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type that crosses the bridge through a {@link BridgeCodec}, so that a native image can
 * read and create it.
 *
 * <p>A codec finds the constructors, the accessors, and the fields of a type through reflection,
 * which a native image allows only for the types that its metadata lists. The annotation processor
 * of {@code lwjwae-processor} lists every type that carries this annotation, and every type that
 * one of them holds: the components of a record, the fields of a class, its superclasses, the
 * elements of an array, and the type arguments of a collection. Only the outermost types of a bind
 * or an event need the annotation.
 *
 * <pre>{@code
 * @BridgeType
 * public record Outline(String name, List<Point> points) {}
 *
 * window.bind("outline", Outline.class, outline -> outline.points().size());
 * }</pre>
 *
 * <p>The annotation does nothing at run time, and nothing on the JVM, where reflection needs no
 * metadata.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface BridgeType {}
