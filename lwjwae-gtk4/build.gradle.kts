plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "GTK 4 + WebKitGTK 6.0 backend for lwjwae (Linux)."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))
    api(project(":lwjwae-glib"))

    // lwjwae test fixtures
    testImplementation(testFixtures(project(":lwjwae-core")))
    testImplementation(testFixtures(project(":lwjwae-glib")))

    // JUnit
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
