import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
}

/**
 * A standalone Worker entry point: opens one [io.github.kormium.sqlitewasm.SqliteWasmConnection]
 * and answers `postMessage` RPC calls against it (open/execute/query/close). No dependency on
 * Kormium — generic RPC over this library's own connection type, so `kormium-sqlite-wasm`'s
 * reader/writer pool (and any other caller wanting a pooled/off-main-thread SQLite connection) can
 * point a `Worker` at the built bundle without the consuming app needing its own second Kotlin/Wasm
 * executable target. Published as its own tiny npm package so webpack's `new Worker(new
 * URL(specifier, import.meta.url))` bundling picks it up automatically (verified empirically this
 * works with an npm-resolved specifier, no consumer-side webpack config needed).
 */
@OptIn(ExperimentalWasmDsl::class)
kotlin {
    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":sqlite-wasm-kt"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}

// DEV-ONLY (until this is actually published to npm): stages the compiled-but-NOT-webpack-bundled
// output (raw ESM .mjs + .wasm, straight from the Kotlin/Wasm compiler) under npm-package/dist/, so
// `implementation(npm("@kormium/sqlite-wasm-worker", "file:../sqlite-wasm-kt-worker/npm-package"))`
// resolves locally in kormium-sqlite-wasm during development.
//
// Deliberately NOT `wasmJsBrowserDistribution`'s webpack output: a consumer's own webpack treats an
// already-webpack-bundled UMD file as one opaque CommonJS module and does not re-run its own
// `new URL(..., import.meta.url)` asset analysis on what's inside it — so @sqlite.org/sqlite-wasm's
// own `sqlite3.wasm` reference (bundled *inside* that opaque blob) never gets rediscovered/emitted
// by the consumer's build (confirmed empirically: the asset silently doesn't make it into the final
// dist directory). Raw ESM source doesn't have this problem — the consumer's webpack traces it like
// any other source module, discovering and emitting `@sqlite.org/sqlite-wasm`'s assets itself.
val stageNpmPackage by tasks.registering(Copy::class) {
    dependsOn("compileProductionExecutableKotlinWasmJsOptimize")
    from(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/optimized"))
    into(layout.projectDirectory.dir("npm-package/dist"))
}
