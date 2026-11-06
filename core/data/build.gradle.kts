plugins {
    id("pleos.android.library")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.core.data"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.sqlcipher)
}
