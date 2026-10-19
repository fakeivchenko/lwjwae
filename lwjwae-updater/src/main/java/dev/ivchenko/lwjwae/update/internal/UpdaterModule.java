package dev.ivchenko.lwjwae.update.internal;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.PageCapability;
import dev.ivchenko.lwjwae.module.ApplicationModule;
import dev.ivchenko.lwjwae.module.ModuleContext;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.update.DownloadedUpdate;
import dev.ivchenko.lwjwae.update.Update;
import dev.ivchenko.lwjwae.update.UpdateDownloadFailedException;
import dev.ivchenko.lwjwae.update.UpdateParameters;
import dev.ivchenko.lwjwae.update.UpdateRejectedException;
import dev.ivchenko.lwjwae.update.Updater;
import dev.ivchenko.lwjwae.util.JsonUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * The updater of one application, made on its first use, and {@code window.lwjwae.updates} of its
 * pages, behind {@link PageCapability#UPDATES}.
 *
 * <p>The updater is made lazily because most runs never look for an update, and its parameters may
 * come from the application before then; the ones that the Gradle plugin writes into the
 * application are the default. It closes with the application, which stops a download that goes on.
 */
public final class UpdaterModule implements ApplicationModule {
  /** The call of {@code window.lwjwae.updates}: {@code check} or {@code install}. */
  static final String CALL = "lwjwae:update";

  /** The event whose payload is how much of an update the page asked for is downloaded, 0 to 1. */
  static final String PROGRESS_EVENT = "lwjwae:update-progress";

  private static final String PAGE_SCRIPT = "/dev/ivchenko/lwjwae/update/internal/page.js";

  private Application application;
  private Updater updater;

  @Override
  public void start(ModuleContext context) {
    this.application = context.application();
    context.handle(CALL, PageCapability.UPDATES, this::serve);
    context.pageApi("updates", UpdaterModule.pageScript());
  }

  /**
   * The updater, made on the first call with the parameters that the plugin wrote.
   *
   * @throws IllegalStateException If the application has no update parameters, or is closed.
   */
  public synchronized Updater updater() {
    if (this.updater == null) {
      UpdateParameters parameters =
          UpdateParameters.discover()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "An updater needs update parameters: set them, or the updates block of"
                              + " the plugin"));
      return this.updater(parameters);
    }
    this.checkOpen();
    return this.updater;
  }

  /**
   * The updater with {@code parameters}, made on the first call.
   *
   * @throws IllegalStateException If the updater exists already with other parameters, or the
   *     application is closed.
   */
  public synchronized Updater updater(UpdateParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    this.checkOpen();
    if (this.updater == null) {
      this.updater = new Updater(parameters, this.application::quit);
    } else if (!this.updater.parameters().equals(parameters)) {
      throw new IllegalStateException("The updater of this application has other parameters");
    }
    return this.updater;
  }

  @Override
  public synchronized void stop() {
    if (this.updater != null) {
      this.updater.close();
    }
  }

  private void checkOpen() {
    if (this.application.isClosed()) {
      throw new IllegalStateException("The application is closed");
    }
  }

  /**
   * {@code window.lwjwae.updates}: {@code check} answers with the update as JSON, or nothing for
   * none; {@code install} checks again, downloads the update with its progress as events to the
   * window of the page, and installs it, which quits the application. No update answers {@code
   * 404}, one that this process can't install {@code 409}, and a manifest or a file that fails its
   * checks or its download {@code 502}.
   */
  private void serve(RpcCall call) throws Exception {
    Updater updater = this.updater();
    try {
      Optional<Update> found = updater.check().get();
      switch (call.text()) {
        case "check" -> call.reply(found.map(UpdaterModule::updateJson).orElse(""));
        case "install" -> {
          Update update =
              found.orElseThrow(() -> RpcException.notFound("no-update", "No newer version"));
          if (!update.installable()) {
            throw RpcException.conflict(
                "not-installable", "A package manager updates this application");
          }
          DownloadedUpdate downloaded =
              updater
                  .download(
                      update,
                      fraction -> call.window().emit(PROGRESS_EVENT, Double.toString(fraction)))
                  .get();
          updater.installAndRestart(downloaded);
        }
        default ->
            throw RpcException.badRequest("malformed-command", "Not a command: " + call.text());
      }
    } catch (ExecutionException e) {
      throw switch (e.getCause()) {
        case UpdateRejectedException rejected ->
            RpcException.badGateway("update-rejected", rejected.getMessage());
        case UpdateDownloadFailedException failed ->
            RpcException.badGateway("download-failed", failed.getMessage());
        default -> e;
      };
    }
  }

  private static String updateJson(Update update) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("version", update.version());
    fields.put("notes", update.notes());
    fields.put("mandatory", update.mandatory());
    fields.put("installable", update.installable());
    fields.put("size", update.artifact().size());
    return JsonUtil.write(fields);
  }

  private static String pageScript() {
    try (InputStream in = UpdaterModule.class.getResourceAsStream(PAGE_SCRIPT)) {
      return new String(
          Objects.requireNonNull(in, PAGE_SCRIPT).readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
