plugins {
    id("pleos.android.library.compose")
    id("pleos.android.navigation3")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.feature.main"
}

dependencies {
    implementation(project(":feature:nav"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.hilt.viewmodel.compose)
    implementation(libs.maplibre.android)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.navi.helper)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
