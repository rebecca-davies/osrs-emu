plugins {
    application
}
application {
    mainClass.set("emu.gateway.MainKt")
}
dependencies {
    implementation(project(":net-core"))
    implementation(libs.ktor.network)
    testImplementation(libs.kotlin.test)
}
