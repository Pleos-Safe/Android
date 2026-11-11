plugins {
    id("pleos.kotlin.jvm")
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
