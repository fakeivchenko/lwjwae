# lwjwae-updater

Finds, downloads, and installs the new versions of an lwjwae application from a manifest at any
URL, signed with Ed25519. The module joins every application that finds it on the class path or the
module path; the Gradle plugin adds it when the build has an `updates {}` block.

```kotlin
dependencies {
    implementation("dev.ivchenko.lwjwae:lwjwae-updater:VERSION")
}
```

## From Java

```java
Updater updater = Updater.of(application);
updater.check().thenAccept(found -> found.ifPresent(update -> {
  DownloadedUpdate downloaded = updater.download(update, application.taskbar()::progress).join();
  updater.installAndRestart(downloaded);
}));
```

`Updater.of(application)` reads the manifest URL, the public key, and the running version that the
plugin writes into `META-INF/lwjwae/update.properties`. `Updater.of(application, parameters)` takes
them from the application instead. An application has one updater, which closes with it.

## From the page

With `PageCapability.UPDATES` in `ApplicationParameters.pageCapabilities`, a page has
`window.lwjwae.updates`:

```js
const update = await lwjwae.updates.check();
if (update?.installable) {
  await lwjwae.updates.install({ onProgress: (fraction) => bar.value = fraction });
}
```

`check` resolves to `{ version, notes, mandatory, installable, size }`, or to `null` when there is no
update. `install` downloads the update and installs it, which ends the application. Both reject with
`no-update`, `not-installable`, `update-rejected`, or `download-failed`.

## Trust

`check` verifies the signature of the manifest with the public key of the application before it
believes a word of it, and refuses a manifest whose `expires` has passed. The manifest names a file
for every platform with its size and SHA-256, and `download` checks both. A version that isn't newer
than the running one is no update, so a manifest can't take the application back.

| Platform | What gets installed |
|---|---|
| Windows | The `.msi` of the new version, with `msiexec /passive`. |
| macOS | The `.app` bundle of the new version, signed by the same team. |
| Linux | The AppImage. A Debian or Arch package updates through its package manager, so the update is found but not installable. |
