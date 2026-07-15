dependencies {
    implementation(project(":server-session"))
    implementation(project(":game"))
    implementation(project(":persistence-api"))
    implementation(project(":net-core"))
    implementation(project(":protocol-game"))
    implementation(project(":cache"))
    implementation(project(":compression"))
    implementation(project(":crypto"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test)
}
