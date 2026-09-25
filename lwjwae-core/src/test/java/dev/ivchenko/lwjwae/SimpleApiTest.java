package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageButtons;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.MessageLevel;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.PresentedDialog;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests the short paths that the API offers next to the detailed ones. */
@Timeout(10)
class SimpleApiTest {
  @Test
  void targetWithSchemeIsUrlAndOneWithoutIsResource() {
    for (String url :
        List.of("https://example.com", "file:///tmp/a.html", "about:blank", "data:text/html,x")) {
      Assertions.assertTrue(ResourceUtil.isUrl(url), url);
    }
    for (String resource : List.of("app/index.html", "/app/index.html", "index.html", "C:/a")) {
      Assertions.assertFalse(ResourceUtil.isUrl(resource), resource);
    }
  }

  @Test
  void windowParametersOfTargetLoadItAsUrlOrResource() {
    WindowParameters page = WindowParameters.of("Docs", "https://example.com");
    WindowParameters resource = WindowParameters.of("Notes", "app/index.html");
    WindowParameters sized = WindowParameters.of("Small", 400, 300);

    Assertions.assertEquals("https://example.com", page.url());
    Assertions.assertNull(page.resource());
    Assertions.assertEquals("app/index.html", resource.resource());
    Assertions.assertNull(resource.url());
    Assertions.assertEquals("Small", sized.title());
    Assertions.assertEquals(new WindowSize(400, 300), sized.size());
  }

  @Test
  void loadNavigatesToUrlAndServesResource() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();

      window.load("https://example.com");
      window.load("app/index.html");

      Assertions.assertEquals(
          List.of("https://example.com", "app://local/app/index.html"), window.navigated);
    }
  }

  @Test
  void showOpensLoadsAndShowsWindow() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = (FakeWindow) application.show("app/index.html");

      Assertions.assertTrue(window.isShown());
      Assertions.assertEquals(List.of("app://local/app/index.html"), window.navigated);
      Assertions.assertTrue(application.show(WindowParameters.of("Two", 300, 200)).isVisible());
    }
  }

  @Test
  void alertAndConfirmAskWithTheButtonsOfTheirNames() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();

      final CompletableFuture<Void> alerted = window.alert("Saved");
      PresentedDialog alert = window.dialogs.poll(5, TimeUnit.SECONDS);
      MessageDialogParameters alertParameters = (MessageDialogParameters) alert.parameters();
      Assertions.assertEquals("Saved", alertParameters.message());
      Assertions.assertEquals(MessageButtons.OK, alertParameters.buttons());
      alert.answer(true);
      alerted.get(5, TimeUnit.SECONDS);

      final CompletableFuture<Boolean> confirmed = window.confirm("Delete?");
      PresentedDialog confirm = window.dialogs.poll(5, TimeUnit.SECONDS);
      MessageDialogParameters confirmParameters = (MessageDialogParameters) confirm.parameters();
      Assertions.assertEquals(MessageButtons.OK_CANCEL, confirmParameters.buttons());
      Assertions.assertEquals(MessageLevel.QUESTION, confirmParameters.level());
      confirm.answer(false);
      Assertions.assertFalse(confirmed.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void pickersAskForOneFileManyFilesFolderOrPlaceToSave() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      FileType text = FileType.of("Text", "txt");

      final CompletableFuture<Optional<Path>> file = window.pickFile(text);
      PresentedDialog one = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertEquals(List.of(text), ((OpenDialogParameters) one.parameters()).fileTypes());
      Assertions.assertFalse(((OpenDialogParameters) one.parameters()).multiple());
      one.answer(List.of(Path.of("/a.txt")));
      Assertions.assertEquals(Optional.of(Path.of("/a.txt")), file.get(5, TimeUnit.SECONDS));

      CompletableFuture<List<Path>> files = window.pickFiles();
      PresentedDialog many = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertTrue(((OpenDialogParameters) many.parameters()).multiple());
      many.answer(List.of());
      Assertions.assertEquals(List.of(), files.get(5, TimeUnit.SECONDS));

      CompletableFuture<Optional<Path>> folder = window.pickFolder();
      PresentedDialog directory = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertTrue(((OpenDialogParameters) directory.parameters()).directories());
      directory.answer(List.of());
      Assertions.assertEquals(Optional.empty(), folder.get(5, TimeUnit.SECONDS));

      CompletableFuture<Optional<Path>> saved = window.pickSaveFile("report.txt", text);
      PresentedDialog save = window.dialogs.poll(5, TimeUnit.SECONDS);
      Assertions.assertEquals("report.txt", ((SaveDialogParameters) save.parameters()).fileName());
      save.answer(Optional.of(Path.of("/report.txt")));
      Assertions.assertEquals(Optional.of(Path.of("/report.txt")), saved.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void cancelingShortcutClosesItsDialog() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();

      CompletableFuture<Optional<Path>> file = window.pickFile();
      PresentedDialog dialog = window.dialogs.poll(5, TimeUnit.SECONDS);
      file.cancel(false);
      window.awaitUiThread();

      Assertions.assertTrue(dialog.closed().get(), "canceling the shortcut closes the dialog");
    }
  }

  @Test
  void trayAndNotificationTakeTheirPartsDirectly() {
    try (FakeApplication application = new FakeApplication()) {
      TrayMenuItem quit = new TrayMenuItem("Quit", application::quit);

      application.tray("fixtures/dot.png", quit);
      application.showNotification("Saved", "report.pdf");

      Assertions.assertArrayEquals(
          ResourceUtil.read("fixtures/dot.png"), application.trayIcons.getFirst().icon());
      Assertions.assertEquals(List.of(quit), application.trayIcons.getFirst().menu());
      Assertions.assertEquals("report.pdf", application.shownNotifications.getFirst().body());
    }
  }
}
