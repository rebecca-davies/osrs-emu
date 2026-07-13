plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("emu.tools.cachefetch.MainKt")
}

// The application plugin defaults the `run` task's working directory to this
// subproject's directory; the tool is documented to populate ./cache-data
// relative to the repo root, so pin it there.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)
}
