plugins {
    application
}
application {
    mainClass.set("emu.gateway.MainKt")
}
dependencies {
    implementation(project(":common"))
    implementation(libs.ktor.network)
    testImplementation(libs.kotlin.test)
}
