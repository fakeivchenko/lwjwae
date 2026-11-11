# lwjwae-secrets

Keeps the tokens and passwords of an lwjwae application in the store of credentials of the system,
encrypted for the user, rather than in a file of the application. The module joins every application
that finds it on the class path or the module path, and binds the store of each platform itself, so
it needs no backend of a toolkit.

```kotlin
dependencies {
    implementation("dev.ivchenko.lwjwae:lwjwae-secrets:VERSION")
}
```

## Use

```java
Secrets secrets = Secrets.of(application);
secrets.set("api-token", token);
Optional<String> saved = secrets.get("api-token");
secrets.delete("api-token");
```

The secrets belong to the application by `ApplicationParameters.name()`, which they need. A call
blocks until the system answers, and the system may ask the user to unlock the store first, so call
it off the UI thread. A value takes at most `Secrets.MAXIMUM_SIZE`, 2,560 bytes of UTF-8, on every
platform: the limit of the Credential Manager. A refusal of the system, such as a store that the
user didn't unlock, throws `SecretStoreFailedException`.

| Platform | Where the secrets go |
|---|---|
| Windows | Generic credentials of the Credential Manager, named `APPLICATION/KEY`, through `advapi32.dll`. |
| macOS | Generic passwords of the login keychain, with the application as the service and the key as the account, through the Security framework and Core Foundation. |
| Linux | Passwords of the Secret Service, GNOME Keyring or KWallet, through libsecret, under a schema of lwjwae with the application and the key as attributes. Without libsecret, every call throws `SecretStoreFailedException`. |
