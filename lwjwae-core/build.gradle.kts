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
val generatedResources = layout.buildDirectory.dir("generated/resources/version")
val writeVersion = tasks.register("writeVersion") {
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(generatedResources)
    doLast {
        val file = generatedResources.get().file("dev/ivchenko/lwjwae/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}
sourceSets.main {
    resources.srcDir(writeVersion)
}
