plugins {
    id("java-library")
    id("java-test-fixtures")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "GLib, GIO and D-Bus bindings shared by the GTK 3 and GTK 4 backends of lwjwae (Linux)."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))

    // lwjwae test fixtures
    testFixturesApi(testFixtures(project(":lwjwae-core")))
    testImplementation(testFixtures(project(":lwjwae-core")))

    // JUnit
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
