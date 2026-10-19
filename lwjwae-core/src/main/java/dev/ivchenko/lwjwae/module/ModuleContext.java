package dev.ivchenko.lwjwae.module;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.PageCapability;
import dev.ivchenko.lwjwae.event.OpenEvent;
import dev.ivchenko.lwjwae.rpc.RpcHandler;

/**
 * What an {@link ApplicationModule} reaches of its application while it starts: the application
 * itself, the calls of its pages, and the API of its pages.
 */
public interface ModuleContext {
  /** The application that the module joins. */
  Application application();

  /**
   * Answers the calls that a page of any window makes under {@code name}, as {@link
   * Application#handle} does, but under a name that only a module can take.
   *
   * @param name The name of the call, which starts with {@code lwjwae:}, such as {@code
   *     lwjwae:update}.
   * @param capability What the application must grant its pages for a call to go through, or {@code
   *     null} for nothing; a call without it fails with 403 and the code {@code capability}.
   * @throws IllegalArgumentException If {@code name} doesn't start with {@code lwjwae:}, or a
   *     module took it already.
   */
  void handle(String name, PageCapability capability, RpcHandler handler);

  /**
   * Adds {@code window.lwjwae.NAME} to every page.
   *
   * @param name A JavaScript identifier that the core doesn't use itself.
   * @param script A JavaScript expression that evaluates to a function of the bridge, which returns
   *     the API: {@code (bridge) => ({ ... })}. The bridge has {@code call(name, text)}, which
   *     resolves to the text of the answer, {@code listen(name, handler)}, which resolves to the
   *     function that stops it, and {@code RpcError}.
   * @throws IllegalArgumentException If {@code name} isn't a JavaScript identifier, or is taken.
   */
  void pageApi(String name, String script);

  /**
   * Hands a request to open links or files to the listeners of {@link Application#onOpen}, as the
   * system does when the user opens them.
   */
  void openRequested(OpenEvent request);
}
