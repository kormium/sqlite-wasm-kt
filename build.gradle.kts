plugins {
    // Applied in the library subproject; declared here so all modules share one version.
    kotlin("multiplatform") version "2.4.0" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    // Applied at the root: validates the public ABI of the subproject (JVM + klib).
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
}

apiValidation {
    // Also track the wasmJs klib ABI, not just the JVM one (there is no JVM target here, but
    // this keeps the config consistent with kormium/decimal's).
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

allprojects {
    repositories {
        mavenCentral()
    }
}
