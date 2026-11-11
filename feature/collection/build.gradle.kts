plugins {
    id("pleos.android.library.compose")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.feature.collection"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":core:card-ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.viewmodel.compose)
    androidTestImplementation(testFixtures(project(":core:card-ui")))
    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
