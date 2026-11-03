plugins {
    id("pleos.android.library.compose")
}

android {
    namespace = "io.issac.pleosandroid.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation.core)
    implementation(libs.androidx.compose.material3)
}
