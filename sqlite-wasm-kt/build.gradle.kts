import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    explicitApi()

    // @sqlite.org/sqlite-wasm is browser-side JS (its own README: Node support is in-memory-only,
    // no OPFS persistence) — this library is Kotlin/Wasm (JS) only, same reasoning as Kormium's
    // kormium-sqlite-wasm.
    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // SQLite compiled to WASM, officially maintained by the SQLite project, published
                // in lockstep with SQLite releases. https://github.com/sqlite/sqlite-wasm
                implementation(npm("@sqlite.org/sqlite-wasm", "3.53.0-build1"))
            }
        }
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
        ),
    )

    pom {
        name.set("sqlite-wasm-kt")
        description.set(
            "Kotlin/Wasm bindings for the official @sqlite.org/sqlite-wasm — suspend-friendly " +
                "SQLite in the browser, in-memory or persistent OPFS via the opfs-wl VFS.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/kormium/sqlite-wasm-kt")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("knyazevs")
                name.set("Sergey Knyazev")
                email.set("sknyazev@vk.com")
                url.set("https://github.com/knyazevs")
            }
        }
        scm {
            url.set("https://github.com/kormium/sqlite-wasm-kt")
            connection.set("scm:git:git://github.com/kormium/sqlite-wasm-kt.git")
            developerConnection.set("scm:git:ssh://git@github.com/kormium/sqlite-wasm-kt.git")
        }
    }
}
