dependencies {
    implementation(project(":buffer"))
    implementation(project(":crypto"))
    implementation(libs.ktor.network)
    testImplementation(libs.kotlin.test)
}
