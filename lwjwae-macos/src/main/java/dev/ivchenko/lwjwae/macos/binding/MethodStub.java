package dev.ivchenko.lwjwae.macos.binding;

import java.lang.foreign.MemorySegment;

/**
 * One method of a class that {@link ObjC#defineClass} creates at runtime.
 *
 * @param implementation The upcall stub that Objective-C calls, with {@code self} and {@code _cmd}
 *     first.
 * @param typeEncoding The Objective-C type encoding of the method, for example {@code v@:@} for
 *     {@code -(void)method:(id)argument}.
 */
public record MethodStub(MemorySegment implementation, String typeEncoding) {}
