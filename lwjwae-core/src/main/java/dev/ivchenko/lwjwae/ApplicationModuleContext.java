package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.OpenEvent;
import dev.ivchenko.lwjwae.module.ModuleContext;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import java.util.Objects;

/** What a module reaches of the application while it starts, see {@link ModuleContext}. */
final class ApplicationModuleContext implements ModuleContext {
  private final AbstractApplication application;

  ApplicationModuleContext(AbstractApplication application) {
    this.application = application;
  }

  @Override
  public Application application() {
    return this.application;
  }

  @Override
  public void handle(String name, PageCapability capability, RpcHandler handler) {
    this.application.addModuleCall(
        Objects.requireNonNull(name, "name"),
        new ModuleCall(capability, Objects.requireNonNull(handler, "handler")));
  }

  @Override
  public void pageApi(String name, String script) {
    this.application.addModulePageApi(
        Objects.requireNonNull(name, "name"), Objects.requireNonNull(script, "script"));
  }

  @Override
  public void openRequested(OpenEvent request) {
    this.application.openRequested(Objects.requireNonNull(request, "request"));
  }
}
