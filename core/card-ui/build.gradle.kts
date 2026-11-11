plugins {
    id("pleos.android.library.compose")
}

android {
    testFixtures.enable = true
    namespace = "io.issac.pleosandroid.core.cardui"
}

dependencies {
    implementation(project(":core:designsystem"))
    api(project(":core:domain"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    testFixturesApi(platform(libs.androidx.compose.bom))
    testFixturesApi(libs.androidx.compose.ui.test.junit4)
    testFixturesImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.activity.compose)
}
