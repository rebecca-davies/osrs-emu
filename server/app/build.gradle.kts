plugins {
    application
}

application {
    mainClass.set("emu.server.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.withType<Test> {
    systemProperty("repository.root", rootProject.projectDir.absolutePath)
}

dependencies {
    implementation(project(":server-gateway"))
    implementation(project(":server-session"))
    implementation(project(":server-js5"))
    implementation(project(":server-login"))
    implementation(project(":server-game"))
    implementation(project(":game"))
    implementation(project(":net-core"))
    implementation(project(":persistence-api"))
    implementation(project(":persistence-postgres"))
    implementation(project(":protocol-login"))
    implementation(project(":protocol-js5"))
    implementation(project(":protocol-game"))
    implementation(project(":cache"))
    implementation(project(":crypto"))
    implementation(project(":compression"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    implementation(libs.koin.core)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}
