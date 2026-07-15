dependencies {
    implementation(project(":server-session"))
    implementation(project(":crypto"))
    implementation(project(":net-core"))
    implementation(project(":protocol-login"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(project(":buffer"))
    testImplementation(libs.kotlin.test)
}
