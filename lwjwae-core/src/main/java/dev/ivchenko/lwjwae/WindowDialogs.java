package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.dialog.Dialogs;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The dialogs of one window, for Java and for its page.
 *
 * <p>The window shows a dialog, see {@link AbstractWindow#showDialog}, since it keeps the ones that
 * are up and cancels them as it closes; the backend presents it. A page that asks for a dialog gets
 * the same one as Java, so that both behave the same on every platform.
 */
final class WindowDialogs implements Dialogs {
  private final AbstractWindow window;

  WindowDialogs(AbstractWindow window) {
    this.window = window;
  }

  @Override
  public CompletableFuture<List<Path>> open(OpenDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.window.showDialog(
        completion -> this.window.presentOpenDialog(parameters, completion));
  }

  @Override
  public CompletableFuture<Optional<Path>> save(SaveDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.window.showDialog(
        completion -> this.window.presentSaveDialog(parameters, completion));
  }

  @Override
  public CompletableFuture<Boolean> message(MessageDialogParameters parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.window.showDialog(
        completion -> this.window.presentMessageDialog(parameters, completion));
  }

  /**
   * {@code window.lwjwae.dialog}: the body is {@code open}, {@code save}, or {@code message}, and
   * the fields of the dialog, see {@link BridgeProtocol#parseOpenDialog} and its siblings. The
   * answer is the picked paths separated by {@link BridgeProtocol#SEPARATOR}, empty for none, or
   * {@code 1} and {@code 0} for a message. A page that abandons the call closes the dialog.
   */
  void serve(RpcCall call) throws Exception {
    String[] parts = call.text().split(BridgeProtocol.SEPARATOR, 2);
    String fields = parts.length > 1 ? parts[1] : "";
    // The future of the dialog itself, which a cancellation must reach to close it, and the answer.
    CompletableFuture<?> dialog;
    CompletableFuture<String> answer;
    switch (parts[0]) {
      case "open" -> {
        OpenDialogParameters parameters = BridgeProtocol.parseOpenDialog(fields);
        CompletableFuture<List<Path>> open = parameters == null ? null : this.open(parameters);
        dialog = open;
        answer = open == null ? null : open.thenApply(WindowDialogs::joinPaths);
      }
      case "save" -> {
        SaveDialogParameters parameters = BridgeProtocol.parseSaveDialog(fields);
        CompletableFuture<Optional<Path>> save = parameters == null ? null : this.save(parameters);
        dialog = save;
        answer = save == null ? null : save.thenApply(path -> path.map(Path::toString).orElse(""));
      }
      case "message" -> {
        MessageDialogParameters parameters = BridgeProtocol.parseMessageDialog(fields);
        CompletableFuture<Boolean> message = parameters == null ? null : this.message(parameters);
        dialog = message;
        answer = message == null ? null : message.thenApply(yes -> yes ? "1" : "0");
      }
      default -> {
        dialog = null;
        answer = null;
      }
    }
    if (answer == null) {
      throw RpcException.badRequest("malformed-dialog", "Malformed dialog: " + parts[0]);
    }
    // A page that gives the call up interrupts this thread.
    try {
      if (call.isCancelled()) {
        throw new InterruptedException();
      }
      call.reply(answer.get());
    } catch (InterruptedException e) {
      dialog.cancel(false);
      throw e;
    }
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream().map(Path::toString).collect(Collectors.joining(BridgeProtocol.SEPARATOR));
  }
}
