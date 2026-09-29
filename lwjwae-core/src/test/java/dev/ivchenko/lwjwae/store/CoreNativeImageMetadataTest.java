package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.testing.contract.NativeImageMetadataContractTest;
import java.util.List;

/** The SQLite of the store, whose downcalls the core lists for every backend. */
class CoreNativeImageMetadataTest extends NativeImageMetadataContractTest {
  @Override
  protected String metadataPath() {
    return "META-INF/native-image/dev.ivchenko.lwjwae/lwjwae-core/reachability-metadata.json";
  }

  @Override
  protected List<Class<?>> bindingClasses() {
    return List.of(Sqlite.class);
  }
}
