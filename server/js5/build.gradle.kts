dependencies {
    implementation(project(":cache"))
    implementation(project(":crypto"))
    implementation(project(":net-core"))
    implementation(project(":protocol-js5"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test)
}
