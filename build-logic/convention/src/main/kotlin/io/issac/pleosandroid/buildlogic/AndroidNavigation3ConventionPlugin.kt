package io.issac.pleosandroid.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidNavigation3ConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("navigation3-runtime").get())
                add("implementation", libs.findLibrary("navigation3-ui").get())
                add(
                    "implementation",
                    libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get(),
                )
                add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
            }
        }
    }
}
