plugins {
    id("java-library")
    id("maven-publish")
    id("checkstyle")
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

description = "An annotation processor that writes the native-image metadata of the types of the lwjwae bridge."

dependencies {
    // lwjwae, for the annotation in the sources that the tests compile
    testImplementation(project(":lwjwae-core"))

    // JUnit
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
