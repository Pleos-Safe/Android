plugins {
    id("pleos.android.library")
}

android {
    namespace = "io.issac.pleosandroid.core.ui"
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.kotlinx.coroutines.android)
}
