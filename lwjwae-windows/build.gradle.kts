plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "Win32 + WebView2 backend for lwjwae (Windows)."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))

    // lwjwae test fixtures
    testImplementation(testFixtures(project(":lwjwae-core")))

    // JUnit
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
