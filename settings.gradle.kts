pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TropeReader"

// Pure Kotlin/JVM parser, used as-is by the app.
include(":parser")
include(":app")
