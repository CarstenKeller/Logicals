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
            implementation(project(":shared-ui"))
            implementation(libs.compose.mp.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "de.carstenkeller.logicals.web.resources"
}
