plugins {
    id("pleos.android.library.compose")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.feature.records"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
}
