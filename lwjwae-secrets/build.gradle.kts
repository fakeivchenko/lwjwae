plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("jacoco")
    id("org.gradle.test-retry")
}

description = "The secrets of an lwjwae application in the store of credentials of the system."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))

    // lwjwae test fixtures
    testImplementation(testFixtures(project(":lwjwae-core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
