plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "The updater of lwjwae: signed manifests, downloads, and the installers of each platform."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))

    // lwjwae test fixtures, and jqwik for the properties of the version order
    testImplementation(testFixtures(project(":lwjwae-core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}
