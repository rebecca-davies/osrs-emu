private val protocolDomainProjects =
    listOf(
        ":protocol-login",
        ":protocol-js5",
        ":protocol-game",
    )

tasks.register("verifyProtocolBoundaries") {
    group = "verification"
    description = "Verifies that revision protocol domains remain compile-isolated and framework-free."

    doLast {
        val allowedProjectDependencies = setOf(":transport", ":buffer", ":crypto")
        val forbiddenImports =
            Regex(
                "^import (org\\.koin|emu\\.protocol\\.osrs239\\.(login|js5|game))",
                RegexOption.MULTILINE,
            )

        protocolDomainProjects.forEach { path ->
            val domain = requireNotNull(findProject(path)) { "missing protocol domain project $path" }
            val projectDependencies =
                domain.configurations
                    .flatMap { configuration ->
                        configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>()
                    }
                    .map { dependency -> dependency.path }
                    .toSet()
            check(projectDependencies.all { it in allowedProjectDependencies }) {
                "$path has forbidden project dependencies: ${projectDependencies - allowedProjectDependencies}"
            }

            val koinDependencies =
                domain.configurations
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
                            "$path imports sibling protocol domain '$importedDomain' in " +
                                source.relativeTo(domain.projectDir)
                        }
                    }
                }
        }
    }
}
