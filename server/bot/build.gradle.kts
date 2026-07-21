dependencies {
    implementation(project(":buffer"))
    implementation(project(":crypto"))
    implementation(project(":protocol-login"))
    implementation(project(":protocol-game"))
    implementation(project(":transport"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test)
}
