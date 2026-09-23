import com.diffplug.gradle.spotless.SpotlessExtension
import io.freefair.gradle.plugins.lombok.LombokExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    id("io.freefair.lombok").version("9.5.0").apply(false)
    id("com.diffplug.spotless").version("8.2.1").apply(false)
}

group = "dev.ivchenko.lwjwae"
version = providers.gradleProperty("projectVersion").getOrElse("0.0.0-dev")

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("java") {
        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
            withJavadocJar()
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
            options.encoding = "UTF-8"
        }
        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
        }

        // Every module has the same three test tasks. `test` is headless; the other two open a real window, so a
        // cached result from another machine proves nothing about this one.
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            testLogging {
                showStandardStreams = true
                events("passed", "failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
        tasks.named<Test>("test") {
            useJUnitPlatform {
                excludeTags("display", "network")
            }
        }
        val displayTest = tasks.register<Test>("displayTest") {
            description = "Runs the tests that open a real window."
            configureWindowTest()
            useJUnitPlatform {
                includeTags("display")
                excludeTags("network")
            }
            shouldRunAfter(tasks.named("test"))
        }
        val networkTest = tasks.register<Test>("networkTest") {
            description = "Runs the tests that load pages from the public internet."
            configureWindowTest()
            useJUnitPlatform {
                includeTags("network")
            }
            shouldRunAfter(displayTest)
        }
        tasks.named("check") {
            dependsOn(displayTest)
        }
    }

    plugins.withId("io.freefair.lombok") {
        configure<LombokExtension> {
            version = "1.18.48"
        }
    }

    plugins.withId("com.diffplug.spotless") {
        configure<SpotlessExtension> {
            java {
                googleJavaFormat("1.33.0").reflowLongStrings()
                formatAnnotations()
            }
        }
    }

    plugins.withId("checkstyle") {
        configure<CheckstyleExtension> {
            toolVersion = "11.1.0"
        }
        tasks.withType<Checkstyle>().configureEach {
            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }
    }

    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set(provider { project.description })
                        url.set("https://github.com/fakeivchenko/lwjwae")
                        inceptionYear.set("2026")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("fakeivchenko")
                                name.set("Anton Ivchenko")
                                email.set("fakeivchenko@gmail.com")
                                url.set("https://github.com/fakeivchenko")
                            }
                        }
                        scm {
                            url.set("https://github.com/fakeivchenko/lwjwae")
                            connection.set("scm:git:https://github.com/fakeivchenko/lwjwae.git")
                            developerConnection.set("scm:git:git@github.com:fakeivchenko/lwjwae.git")
                        }
                        issueManagement {
                            system.set("GitHub")
                            url.set("https://github.com/fakeivchenko/lwjwae/issues")
                        }
                        ciManagement {
                            system.set("GitHub Actions")
                            url.set("https://github.com/fakeivchenko/lwjwae/actions")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "reposilite"
                    url = uri("https://repo.ivchenko.dev/releases")
                    credentials {
                        username = System.getenv("REPOSILITE_USERNAME")
                        password = System.getenv("REPOSILITE_PASSWORD")
                    }
                }
            }
        }
    }
}

abstract class DisplayService : BuildService<BuildServiceParameters.None>

val display = gradle.sharedServices.registerIfAbsent("display", DisplayService::class) {
    maxParallelUsages.set(1)
}

fun Test.configureWindowTest() {
    group = "verification"
    usesService(display)
    val test = project.extensions.getByType<SourceSetContainer>().named("test").get()
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    systemProperty("lwjwae.requireDisplay", System.getProperty("lwjwae.requireDisplay", "false"))
    systemProperty("lwjwae.screenshots", System.getProperty("lwjwae.screenshots", "false"))
    systemProperty("lwjwae.screenshotsDir", project.layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
}
