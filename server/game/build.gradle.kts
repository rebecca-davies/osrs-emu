dependencies {
    implementation(project(":server-session"))
    implementation(project(":game"))
    implementation(project(":persistence-api"))
    implementation(project(":transport"))
    implementation(project(":protocol-game"))
    implementation(project(":cache"))
    implementation(project(":compression"))
    implementation(project(":crypto"))
    implementation(libs.ktor.network)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test)
}

tasks.register<JavaExec>("cycleBenchmark") {
    group = "verification"
    description = "Benchmarks stationary players through the complete authoritative world cycle."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("emu.server.game.world.cycle.CycleBenchmarkKt")
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
    args(
        providers.gradleProperty("cycleBenchmarkPlayers").getOrElse("250"),
        providers.gradleProperty("cycleBenchmarkWarmup").getOrElse("100"),
        providers.gradleProperty("cycleBenchmarkCycles").getOrElse("200"),
    )
    jvmArgs("-Xms2g", "-Xmx2g")
    providers.gradleProperty("cycleBenchmarkJfr").orNull?.let { recording ->
        jvmArgs("-XX:StartFlightRecording=filename=$recording,settings=profile")
    }
}
