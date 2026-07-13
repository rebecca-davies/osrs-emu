plugins {
    application
}

application {
    mainClass.set("emu.tools.clientpatch.MainKt")
}

// Main/Verify resolve their paths (server-rsa.properties, client/patches/...) relative to the
// repo root; the application plugin's `run` task defaults to this subproject's directory, so pin
// it to the repo root, matching tools:cache-fetch.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// The real verification: read the persisted server-rsa.properties and prove the keypair
// round-trips a login-like RSA block end to end (public-encrypt, private-decrypt).
val javaToolchains = extensions.getByType<JavaToolchainService>()
tasks.register<JavaExec>("verifyRoundTrip") {
    group = "verification"
    description = "Reads server-rsa.properties and proves the persisted keypair round-trips through Rsa.crypt/Rsa.decrypt."
    mainClass.set("emu.tools.clientpatch.VerifyKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    // Match the toolchain the sources were compiled with (subprojects{} pins JavaLanguageVersion 21);
    // without this, JavaExec falls back to the JVM running the Gradle daemon and can hit
    // UnsupportedClassVersionError if that JVM is older.
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

dependencies {
    implementation(project(":crypto"))
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}
