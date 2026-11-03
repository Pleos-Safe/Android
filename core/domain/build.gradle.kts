plugins {
    id("pleos.kotlin.jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
