// External plugin implementations used by the convention plugins.
plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

spotless {
    kotlin {
        target("app/**/*.kt", "core/**/*.kt", "feature/**/*.kt", "build-logic/**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file(".editorconfig").path)
            .editorConfigOverride(mapOf("max_line_length" to 120))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/**/*.gradle.kts",
            "core/**/*.gradle.kts",
            "feature/**/*.gradle.kts",
            "build-logic/**/*.gradle.kts",
        )
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file(".editorconfig").path)
            .editorConfigOverride(mapOf("max_line_length" to 120))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
