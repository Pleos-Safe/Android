plugins {
    id("pleos.android.library")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.core.vehicle"
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
