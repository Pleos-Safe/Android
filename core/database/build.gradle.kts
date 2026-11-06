plugins {
    id("pleos.android.library")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid.core.database"
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.sqlcipher)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
