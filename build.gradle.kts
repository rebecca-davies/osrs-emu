plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

val protocolDomainProjects = listOf(
    ":protocol-osrs239-login",
    ":protocol-osrs239-js5",
    ":protocol-osrs239-game",
)

val verifyProtocolBoundaries by tasks.registering {
    group = "verification"
    description = "Verifies that revision protocol domains remain compile-isolated and framework-free."

    doLast {
        val allowedProjectDependencies = setOf(":net-core", ":buffer", ":crypto")
        val forbiddenImports = Regex("^import (org\\.koin|emu\\.protocol\\.osrs239\\.(login|js5|game))", RegexOption.MULTILINE)

        protocolDomainProjects.forEach { path ->
            val domain = requireNotNull(findProject(path)) { "missing protocol domain project $path" }
            val projectDependencies = domain.configurations
                .flatMap { configuration -> configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>() }
                .map { dependency -> dependency.path }
                .toSet()
            check(projectDependencies.all { it in allowedProjectDependencies }) {
                "$path has forbidden project dependencies: ${projectDependencies - allowedProjectDependencies}"
            }

            val koinDependencies = domain.configurations
                .flatMap { configuration -> configuration.dependencies }
                .filter { dependency -> dependency.group?.startsWith("io.insert-koin") == true }
            check(koinDependencies.isEmpty()) { "$path declares Koin dependencies: $koinDependencies" }

            val domainName = path.substringAfterLast('-')
            domain.projectDir.resolve("src").walkTopDown()
                .filter { source -> source.isFile && source.extension == "kt" }
                .forEach { source ->
                    forbiddenImports.findAll(source.readText()).forEach { match ->
                        val importedDomain = match.groupValues[2]
                        check(importedDomain == domainName) {
                            "$path imports sibling protocol domain '$importedDomain' in ${source.relativeTo(domain.projectDir)}"
                        }
                    }
                }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    tasks.withType<Test> { useJUnitPlatform() }

    if (path in protocolDomainProjects) {
        tasks.named("check") { dependsOn(verifyProtocolBoundaries) }
    }
}
