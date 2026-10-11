package dev.ivchenko.lwjwae.smoke;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.PageCapability;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Opens one window whose page goes through the bridge every way that it has, and exits with 0 when
 * every way answered, with 1 otherwise.
 *
 * <p>A build of the library can pass every test on the JVM and still fail as a native executable,
 * where a native call or a callback that the reachability metadata doesn't list fails only when it
 * first runs. This application runs where the tests can't: as the native executable of an
 * application, and on the module path, where only the exported API is there. The page calls a bound
 * function, which takes the message channel; a handler, which takes the {@code fetch} of the scheme
 * of the application where the engine has it; and the store, which runs the SQL of a page under its
 * authorizer. Java evaluates a script of its own. Each answer is checked, and the first failure, or
 * a minute without the last answer, ends the run.
 */
public final class SmokeApplication {
  private static final long TIMEOUT_SECONDS = 60;

  private SmokeApplication() {}

  /** Runs the check, and exits with its outcome. */
  public static void main(String[] args) throws IOException {
    Path data = Files.createTempDirectory("lwjwae-smoke");
    ApplicationParameters parameters =
        ApplicationParameters.builder()
            .name("lwjwae-smoke")
            .dataDirectory(data)
            .pageCapabilities(Set.of(PageCapability.STORE))
            .build();
    int status;
    try (Application application = Application.create(parameters)) {
      status = SmokeApplication.check(application);
    } catch (Throwable t) {
      t.printStackTrace();
      status = 1;
    }
    System.exit(status);
  }

  private static int check(Application application) throws Exception {
    CompletableFuture<String> reported = new CompletableFuture<>();
    application.handle("echo", call -> call.reply("echo:" + call.text()));
    Window window = application.open(WindowParameters.of("lwjwae smoke", 480, 320));
    window.bind(
        "report",
        report -> {
          reported.complete(report);
          return "ok";
        });
    window.loadResource("smoke/index.html");
    window.show();

    String report;
    try {
      report = reported.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      // What the page got to, for the log of a run that failed.
      System.out.println("smoke page at " + window.url());
      System.out.println(
          "smoke page says "
              + window
                  .eval("document.getElementById('state')?.textContent")
                  .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
      throw e;
    }
    System.out.println("smoke engine " + application.engine());
    System.out.println("smoke page " + report);
    String evaluated = window.eval("21 * 2").get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    window.close();
    System.out.println("smoke eval " + evaluated);
    boolean passed = "echo:ping,store:42".equals(report) && "42".equals(evaluated);
    System.out.println(passed ? "smoke passed" : "smoke FAILED");
    return passed ? 0 : 1;
  }
}
