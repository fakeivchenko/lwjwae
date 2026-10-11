plugins {
    id("application")
    id("checkstyle")
    id("com.diffplug.spotless")
    id("org.graalvm.buildtools.native")
}

description = "An application that checks a build of lwjwae end to end, on the JVM and as a native executable."

// The modules that call native code, which the JVM lets do so only when named.
val nativeAccess = listOf("core", "glib", "gtk", "gtk4", "windows", "macos")
    .joinToString(",") { if (it == "core") "dev.ivchenko.lwjwae" else "dev.ivchenko.lwjwae.$it" }

application {
    mainModule = "dev.ivchenko.lwjwae.smoke"
    mainClass = "dev.ivchenko.lwjwae.smoke.SmokeApplication"
    applicationDefaultJvmArgs = listOf("--enable-native-access=$nativeAccess")
}

graalvmNative {
    toolchainDetection = true
    binaries {
        named("main") {
            imageName = "lwjwae-smoke"
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
                vendor = JvmVendorSpec.GRAAL_VM
            }
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
        }
    }
}

dependencies {
    // lwjwae: the API, and every backend, so that one build runs on each platform
    implementation(project(":lwjwae-core"))
    runtimeOnly(project(":lwjwae-gtk"))
    runtimeOnly(project(":lwjwae-gtk4"))
    runtimeOnly(project(":lwjwae-windows"))
    runtimeOnly(project(":lwjwae-macos"))
}
