plugins {
    id("pleos.android.library.compose")
    id("pleos.android.navigation3")
}

android {
    namespace = "io.issac.pleosandroid.feature.nav"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:navigation"))
    testImplementation(libs.junit)
}
