import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.issac.pleosandroid.buildlogic"

// The build plugins target Java 17; the Android app still targets Java 11.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // The main build supplies the full AGP implementation at runtime.
    compileOnly(libs.android.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        register("androidNavigation3") {
            id = "pleos.android.navigation3"
            implementationClass = "io.issac.pleosandroid.buildlogic.AndroidNavigation3ConventionPlugin"
        }
        register("androidApplication") {
            id = "pleos.android.application"
            implementationClass = "io.issac.pleosandroid.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "pleos.android.application.compose"
            implementationClass = "io.issac.pleosandroid.buildlogic.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "pleos.android.library"
            implementationClass = "io.issac.pleosandroid.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "pleos.android.library.compose"
            implementationClass = "io.issac.pleosandroid.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}
