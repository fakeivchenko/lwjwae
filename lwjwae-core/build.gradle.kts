plugins {
    id("java-library")
    id("java-test-fixtures")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

description = "The lwjwae API, page bridge, backend discovery and FFM helpers."

dependencies {
    // Test fixtures (the test source set inherits them)
    testFixturesApi(platform("org.junit:junit-bom:6.0.0"))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("com.fasterxml.jackson.core:jackson-databind:2.22.2")

    // JUnit
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
