plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("logicals")
        browser {
            commonWebpackConfig {
                outputFileName = "logicals.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "de.carstenkeller.logicals.web.resources"
}
