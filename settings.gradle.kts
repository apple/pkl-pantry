pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // TODO(oss) remove this
        maven {
            url = uri("https://s01.oss.sonatype.org/service/local/repositories/snapshots/content/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "pkl-pantry"
