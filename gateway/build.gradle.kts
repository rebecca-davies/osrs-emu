plugins {
    application
}
application {
    mainClass.set("emu.gateway.MainKt")
}
// FlatFileStore resolves the cache via the relative path `cache-data`, which lives at the repo
// root. Gradle's `run` task otherwise uses the subproject dir as the working directory, so the
// store would find no groups and the JS5 master-index request (255/255) would return null.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
dependencies {
    implementation(project(":game"))
    implementation(project(":persistence"))
    implementation(project(":net-core"))
    implementation(project(":protocol-osrs239-login"))
    implementation(project(":protocol-osrs239-js5"))
    implementation(project(":protocol-osrs239-game"))
    implementation(project(":cache"))
    implementation(project(":crypto"))
    implementation(project(":compression"))
    implementation(project(":buffer"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    implementation(libs.koin.core)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}
