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

description = "The lwjwae API, page bridge, backend discovery and FFM helpers."

dependencies {
    // Test fixtures (the test source set inherits them)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.jackson.databind)

    // JUnit, and jqwik for the properties of the parsers
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The version of the library, which the user agent of every web view names: a resource rather than the manifest,
// which a native image doesn't keep.
val writeVersion = tasks.register<WriteProperties>("writeVersion") {
    destinationFile = layout.buildDirectory.file("generated/resources/version/dev/ivchenko/lwjwae/version.properties")
    property("version", project.version.toString())
}
sourceSets.main {
    resources.srcDir(writeVersion.map { layout.buildDirectory.dir("generated/resources/version") })
}
