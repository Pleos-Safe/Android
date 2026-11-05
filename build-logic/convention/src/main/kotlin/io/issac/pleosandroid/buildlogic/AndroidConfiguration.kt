package io.issac.pleosandroid.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion

internal fun configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(37)
        }
        defaultConfig.apply {
            minSdk = 28
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

internal fun configureAndroidLint(lint: Lint) {
    lint.apply {
        abortOnError = true
        warningsAsErrors = false
    }
}
