plugins {
    id("pleos.android.library.compose")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.feature.badges"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.viewmodel.compose)
}
