plugins {
    application
}
application {
    mainClass.set("emu.gateway.MainKt")
}
dependencies {
    implementation(project(":net-core"))
    implementation(project(":protocol-osrs235"))
    implementation(project(":cache"))
    implementation(project(":crypto"))
    implementation(libs.ktor.network)
    testImplementation(libs.kotlin.test)
}
