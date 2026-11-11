pluginManagement {
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven(url = "https://nexus-playground.pleos.ai/repository/maven-releases/")
            }
            filter { includeGroup("ai.pleos.playground") }
        }
    }
}

rootProject.name = "PleosAndroid"
include(":app")
include(":core:navigation")
include(":core:designsystem")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:vehicle")
include(":feature:nav")
include(":feature:main")
include(":feature:badges")
include(":feature:records")
include(":feature:home")
include(":feature:explore")
include(":feature:settings")
include(":feature:membership")
