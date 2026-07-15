dependencies {
    implementation(project(":server-session"))
    implementation(project(":persistence-api"))
    implementation(project(":crypto"))
    implementation(project(":transport"))
    implementation(project(":protocol-login"))
    implementation(libs.bcrypt)
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(project(":buffer"))
    testImplementation(libs.kotlin.test)
}
