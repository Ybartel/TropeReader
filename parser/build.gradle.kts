import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // Matches what the Android app will compile against.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.jsoup)
    // Jsoup's nullability annotations, needed by the Kotlin compiler.
    compileOnly(libs.jspecify)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("app.tropereader.parser.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("snapshotDir", layout.projectDirectory.dir("src/test/resources/snapshots").asFile.absolutePath)
    systemProperty("updateSnapshots", System.getProperty("updateSnapshots") ?: "false")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Resolve page paths given to `run` from the project root.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
