plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Node.js, Yarn und Binaryen werden über die Repositories aus settings.gradle.kts geladen.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
fun Project.wasmToolsFromSettingsRepositories() {
    fun noDownloadRepository(spec: org.jetbrains.kotlin.gradle.targets.js.EnvSpec<*>?) {
        spec?.downloadBaseUrl?.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
        noDownloadRepository(extensions.findByType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>())
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        noDownloadRepository(extensions.findByType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>())
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        noDownloadRepository(extensions.findByType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>())
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        noDownloadRepository(extensions.findByType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>())
    }
}

allprojects { wasmToolsFromSettingsRepositories() }
