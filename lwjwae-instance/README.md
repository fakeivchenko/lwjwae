# lwjwae-instance

Keeps one process of an lwjwae application running per user: a later start hands its arguments and
its working directory to the first, and ends having opened nothing, not even the toolkit. The module
joins every application that finds it on the class path or the module path.

```kotlin
dependencies {
    implementation("dev.ivchenko.lwjwae:lwjwae-instance:VERSION")
}
```

## Use

`SingleInstance.create(parameters, args)` in place of `Application.create`:

```java
public static void main(String[] args) {
  ApplicationParameters parameters = ApplicationParameters.builder().name("notes").build();
  Optional<Application> created = SingleInstance.create(parameters, args);
  if (created.isEmpty()) {
    return;
  }
  try (Application application = created.get()) {
    SingleInstance.of(application)
        .onSecondInstance(start -> openFiles(start.workingDirectory(), start.arguments()));
    ...
  }
}
```

In the first process, the oldest window comes to the front, shown and restored if it was hidden or
minimized, and then the `onSecondInstance` listeners get a `SecondInstanceEvent`. A listener runs
on a virtual thread, and the later process waits until it returns, so it ends knowing that it was
heard. A start that comes before any listener waits for the first one. Where the system doesn't
let a process take the focus, on Wayland, the window comes back but may only ask for attention.

The links and the files among the arguments of every start go to `Application.onOpen` as well, so
an application that registers a scheme of links or a type of files opens them in the process that
runs.

## How the processes meet

The processes meet on a Unix domain socket named after a hash of the user and
`ApplicationParameters.name()`, which is therefore required: in `$XDG_RUNTIME_DIR` on Linux, the
temporary directory elsewhere, Windows 10 and later included. A lock file next to it lets only one
of two processes that start at the same moment become the first. The socket file stays when the
process ends, and the next first start replaces it. `quit()` gives the name up.
