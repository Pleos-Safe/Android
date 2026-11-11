plugins {
    id("pleos.android.library.compose")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.feature.records"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.hilt.viewmodel.compose)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
