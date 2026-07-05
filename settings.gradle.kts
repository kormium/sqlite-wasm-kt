pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// The root project's own name must differ from the ":sqlite-wasm-kt" subproject's — otherwise a
// consumer's composite-build substitution for "io.github.kormium:sqlite-wasm-kt" is ambiguous
// between the two (same issue would hit any single-module repo named after its root).
rootProject.name = "sqlite-wasm-kt-workspace"

include(":sqlite-wasm-kt")
include(":sqlite-wasm-kt-worker")
