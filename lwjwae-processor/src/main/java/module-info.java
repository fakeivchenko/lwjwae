/**
 * The annotation processor that writes the native-image metadata of the types of the bridge. It
 * runs in the compiler and exports nothing.
 */
module dev.ivchenko.lwjwae.processor {
  requires java.compiler;

  provides javax.annotation.processing.Processor with
      dev.ivchenko.lwjwae.processor.BridgeTypeProcessor;
}
