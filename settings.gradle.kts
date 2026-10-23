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
        // nanojson for the vendored extractor
        maven("https://jitpack.io") { content { includeGroup("com.github.TeamNewPipe") } }
    }
}

// YouTube extractor built from source, as Levyra does
includeBuild("third_party/LevyraExtractor") {
    dependencySubstitution {
        substitute(module("com.github.LUC4N3X:LevyraExtractor")).using(project(":"))
    }
}

rootProject.name = "Ctify"
include(":app")
