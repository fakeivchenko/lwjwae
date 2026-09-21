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
