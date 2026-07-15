private val protocolDomainProjects =
    setOf(
        ":protocol-login",
        ":protocol-js5",
        ":protocol-game",
    )

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<Test> { useJUnitPlatform() }
    tasks.named("check") {
        dependsOn(
            rootProject.tasks.named("architectureCheck"),
            rootProject.tasks.named("declarationQualityCheck"),
            rootProject.tasks.named("structuralQualityCheck"),
        )
    }

    if (path in protocolDomainProjects) {
        tasks.named("check") { dependsOn(rootProject.tasks.named("verifyProtocolBoundaries")) }
    }
}
