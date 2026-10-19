plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "An annotation processor that writes the native-image metadata of the types of the lwjwae bridge."

dependencies {
    // lwjwae, for the annotation in the sources that the tests compile
    testImplementation(project(":lwjwae-core"))

    // JUnit
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
