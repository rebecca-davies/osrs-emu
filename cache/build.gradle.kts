plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":buffer"))
    implementation(project(":crypto"))
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
