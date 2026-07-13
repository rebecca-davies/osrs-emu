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
    implementation(project(":net-core"))
    implementation(project(":protocol-osrs239"))
    implementation(project(":cache"))
    implementation(project(":crypto"))
    implementation(project(":buffer"))
    implementation(libs.ktor.network)
    testImplementation(libs.kotlin.test)
}
