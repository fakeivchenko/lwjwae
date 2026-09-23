plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

description = "GTK 3 + WebKitGTK 4.1 backend for lwjwae (Linux)."

dependencies {
    // lwjwae
    api(project(":lwjwae-core"))
    api(project(":lwjwae-glib"))

    // lwjwae test fixtures
    testImplementation(testFixtures(project(":lwjwae-core")))
    testImplementation(testFixtures(project(":lwjwae-glib")))

    // JUnit
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
