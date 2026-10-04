plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Gemeinsame Oberfläche (Compose) für die Android-App (app/) und die Web-App (web/).
    androidLibrary {
        namespace = "de.carstenkeller.logicals.ui"
        compileSdk = 35
        minSdk = 26
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.material3)
        }
    }
}

// Gleiches JVM-Ziel wie app/ (im androidLibrary-Block von AGP 8.11 nicht einstellbar).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
