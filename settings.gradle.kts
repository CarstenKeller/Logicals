pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Werkzeuge für den Wasm-Build. Das Kotlin-Plugin würde diese Repositories sonst
        // selbst im Projekt anlegen, was FAIL_ON_PROJECT_REPOS verbietet (siehe build.gradle.kts).
        toolRepository("https://nodejs.org/dist", "v[revision]/[artifact](-v[revision]-[classifier]).[ext]", "org.nodejs")
        toolRepository("https://github.com/yarnpkg/yarn/releases/download", "v[revision]/[artifact](-v[revision]).[ext]", "com.yarnpkg")
        toolRepository(
            "https://github.com/WebAssembly/binaryen/releases/download",
            "version_[revision]/binaryen-version_[revision]-[classifier].[ext]",
            "com.github.webassembly",
        )
    }
}

fun RepositoryHandler.toolRepository(url: String, pattern: String, group: String) {
    exclusiveContent {
        forRepository {
            ivy(url) {
                name = group
                patternLayout { artifact(pattern) }
                metadataSources { artifact() }
            }
        }
        filter { includeGroup(group) }
    }
}

rootProject.name = "Logicals"
include(":core")
include(":app")
include(":web")
