import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ IDEA rather than PhpStorm: the tool window only needs the platform module,
        // so building against IDEA keeps the plugin installable in every JetBrains IDE.
        // The Community (IC) artifact is no longer published since 2025.3 — the unified
        // IntelliJ IDEA distribution replaces it.
        intellijIdea(providers.gradleProperty("platformVersion"))
        // No testFramework() on purpose: the discovery layer is plain JVM code and its tests need no
        // IDE fixture. Pulling in the platform test framework would register its own JUnit 5
        // LauncherSessionListener, which fails to instantiate outside a real IDE test run.
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}
