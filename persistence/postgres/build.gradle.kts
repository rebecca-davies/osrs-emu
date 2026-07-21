dependencies {
    api(project(":persistence-api"))
    implementation(project(":game"))
    implementation(libs.hikari)
    implementation(libs.kotlin.logging)
    implementation(libs.postgresql)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test)
}
