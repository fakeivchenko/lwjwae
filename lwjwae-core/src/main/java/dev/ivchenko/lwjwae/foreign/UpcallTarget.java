package dev.ivchenko.lwjwae.foreign;

import java.lang.foreign.FunctionDescriptor;

/**
 * A Java method exposed to native code as a function pointer.
 *
 * @param owner The class that declares the method.
 * @param method The name of the static method.
 * @param descriptor The C signature that the stub answers to.
 */
public record UpcallTarget(Class<?> owner, String method, FunctionDescriptor descriptor) {}
