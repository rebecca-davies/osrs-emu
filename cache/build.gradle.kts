dependencies {
    implementation(project(":buffer"))
    implementation(project(":crypto"))
    implementation(libs.commons.compress)
    testImplementation(libs.kotlin.test)
}
