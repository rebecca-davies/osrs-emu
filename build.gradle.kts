plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

val protocolDomainProjects = listOf(
    ":protocol-login",
    ":protocol-js5",
    ":protocol-game",
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

val architectureCheck by tasks.registering {
    group = "verification"
    description = "Verifies service dependencies, package ownership, and composition boundaries."

    doLast {
        val allowed =
            mapOf(
                ":server-gateway" to emptySet(),
                ":server-session" to emptySet(),
                ":server-login" to
                    setOf(
                        ":server-session",
                        ":persistence-api",
                        ":buffer",
                        ":crypto",
                        ":net-core",
                        ":protocol-login",
                    ),
                ":server-js5" to setOf(":cache", ":crypto", ":net-core", ":protocol-js5"),
                ":server-game" to
                    setOf(
                        ":server-session",
                        ":game",
                        ":persistence-api",
                        ":net-core",
                        ":protocol-game",
                        ":cache",
                        ":compression",
                        ":crypto",
                    ),
            )
        for ((path, permitted) in allowed) {
            val service = requireNotNull(findProject(path)) { "missing service project $path" }
            val actual =
                service.configurations
                    .flatMap { it.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>() }
                    .map { it.path }
                    .toSet()
            check(actual == permitted) {
                "$path project dependencies differ: missing=${permitted - actual}, forbidden=${actual - permitted}"
            }
        }

        val persistenceApi = requireNotNull(findProject(":persistence-api"))
        val apiProjectDependencies =
            persistenceApi.configurations
                .flatMap { it.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>() }
                .map { it.path }
                .toSet()
        check(apiProjectDependencies.isEmpty()) {
            ":persistence-api must remain a dependency-free contract module: $apiProjectDependencies"
        }

        val postgres = requireNotNull(findProject(":persistence-postgres"))
        val postgresProjectDependencies =
            postgres.configurations
                .flatMap { it.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>() }
                .map { it.path }
                .toSet()
        check(postgresProjectDependencies == setOf(":persistence-api")) {
            ":persistence-postgres dependencies differ: $postgresProjectDependencies"
        }

        val apiForbidden = Regex("^import (java\\.sql|javax\\.sql|at\\.favre|emu\\.server|emu\\.protocol)", RegexOption.MULTILINE)
        persistenceApi.projectDir.resolve("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { source ->
                check(!apiForbidden.containsMatchIn(source.readText())) {
                    ":persistence-api contains an infrastructure or service import: $source"
                }
            }

        val postgresForbidden = Regex("^import (at\\.favre|emu\\.server|emu\\.protocol)", RegexOption.MULTILINE)
        postgres.projectDir.resolve("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { source ->
                val text = source.readText()
                check(!postgresForbidden.containsMatchIn(text)) {
                    ":persistence-postgres contains authentication or service policy: $source"
                }
                check(!text.contains("BCrypt") && !text.contains("fun authenticate")) {
                    ":persistence-postgres contains authentication policy: $source"
                }
            }

        val loginAuth = requireNotNull(findProject(":server-login")).projectDir.resolve("src/main/kotlin/emu/server/login/auth")
        val loginAuthForbidden =
            Regex(
                "^import (java\\.sql|javax\\.sql|com\\.zaxxer|org\\.koin|emu\\.protocol|" +
                    "emu\\.server\\.(game|js5|gateway)|emu\\.persistence\\.postgres)",
                RegexOption.MULTILINE,
            )
        loginAuth.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { source ->
                val text = source.readText()
                check(!loginAuthForbidden.containsMatchIn(text) && !text.contains("System.getenv")) {
                    "login authentication contains infrastructure or peer-service coupling: $source"
                }
            }

        val servicePackages =
            mapOf(
                ":server-gateway" to "emu.server.gateway",
                ":server-login" to "emu.server.login",
                ":server-js5" to "emu.server.js5",
                ":server-game" to "emu.server.game",
            )
        for ((path, ownPackage) in servicePackages) {
            val service = requireNotNull(findProject(path))
            val sources = service.projectDir.resolve("src").walkTopDown().filter { it.isFile && it.extension == "kt" }
            for (source in sources) {
                val text = source.readText()
                check(!text.contains("import emu.persistence.postgres")) {
                    "$path imports a concrete PostgreSQL adapter in $source"
                }
                for (peerPackage in servicePackages.values - ownPackage) {
                    check(!text.contains("import $peerPackage")) { "$path imports peer service package in $source" }
                }
            }
        }

        for (subproject in subprojects.filter { it.path != ":server-gateway" }) {
            val main = subproject.projectDir.resolve("src/main").takeIf { it.exists() } ?: continue
            for (source in main.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
                val text = source.readText()
                check(!text.contains("package emu.server.gateway")) { "gateway package is owned by :server-gateway: $source" }
                if (subproject.path != ":server-app" && !subproject.path.startsWith(":tools:")) {
                    check(!text.contains("org.koin")) { "Koin is owned by :server-app: $source" }
                    check(!text.contains("System.getenv")) { "environment reads are owned by :server-app: $source" }
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
    tasks.named("check") { dependsOn(architectureCheck) }

    if (path in protocolDomainProjects) {
        tasks.named("check") { dependsOn(verifyProtocolBoundaries) }
    }
}
