plugins {
    id("pleos.android.application")
    id("pleos.android.application.compose")
    id("pleos.android.navigation3")
    id("pleos.android.hilt")
}

android {
    namespace = "io.issac.pleosandroid"

    defaultConfig {
        applicationId = "io.issac.pleosandroid"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:main"))
    implementation(project(":feature:home"))
    implementation(project(":feature:explore"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:membership"))
    implementation(project(":feature:collection"))
    implementation(project(":feature:forge"))
    implementation(project(":feature:records"))
    implementation(project(":core:vehicle"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(project(":feature:nav"))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
}
