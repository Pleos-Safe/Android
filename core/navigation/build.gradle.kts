plugins {
    id("pleos.android.library.compose")
    id("pleos.android.navigation3")
}

android {
    namespace = "io.issac.pleosandroid.core.navigation"
}

dependencies {
    api(project(":core:domain"))
    api(libs.navigation3.runtime)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.foundation.layout)
    testImplementation(libs.junit)
}
