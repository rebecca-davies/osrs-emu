package emu.server.host

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositionBoundaryTest {
    private val root = Path.of(checkNotNull(System.getProperty("repository.root")))
    private val modules = listOf("host", "gateway", "login", "js5", "world", "session")
    private val peerProjects = listOf("server-gateway", "server-login", "server-js5", "server-world")
    private val peerPackages = listOf("emu.server.gateway", "emu.server.login", "emu.server.js5", "emu.server.world")

    @Test
    fun `legacy app and game capability names are absent`() {
        val settings = root.resolve("settings.gradle.kts").readText()

        assertFalse(root.resolve("server/app").toFile().exists())
        assertFalse(root.resolve("server/game").toFile().exists())
        assertFalse(settings.contains("server-app"))
        assertFalse(settings.contains("server-game"))
    }

    @Test
    fun `host declares only direct capability dependencies`() {
        val build = root.resolve("server/host/build.gradle.kts").readText()
        val projectDependencies = PROJECT_DEPENDENCY.findAll(build).map { it.groupValues[1] }.toSet()

        assertEquals(
            setOf(
                "server-gateway",
                "server-session",
                "server-js5",
                "server-login",
                "server-world",
                "transport",
                "persistence-api",
                "persistence-postgres",
                "protocol-login",
                "protocol-js5",
                "protocol-game",
                "cache",
                "compression",
                "crypto",
            ),
            projectDependencies,
        )
    }

    @Test
    fun `server host is the only executable runtime module`() {
        val builds = modules.associateWith { root.resolve("server/$it/build.gradle.kts").readText() }

        assertEquals(listOf("host"), builds.filterValues { it.contains("application") }.keys.toList())
        for (module in modules - "host") {
            assertFalse(serverSources(module).any { MAIN_FUNCTION.containsMatchIn(it.readText()) })
        }
    }

    @Test
    fun `server host exclusively owns process logging configuration`() {
        val owners = modules.filter { root.resolve("server/$it/src/main/resources/logback.xml").isRegularFile() }

        assertEquals(listOf("host"), owners)
    }

    @Test
    fun `peer services cannot depend on or import each other`() {
        for ((module, project) in mapOf("gateway" to "server-gateway", "login" to "server-login", "js5" to "server-js5", "world" to "server-world")) {
            val build = root.resolve("server/$module/build.gradle.kts").readText()
            for (peer in peerProjects - project) {
                assertFalse(build.contains("project(\":$peer\")"), "$module depends on peer $peer")
            }
            val ownPackage = peerPackages[peerProjects.indexOf(project)]
            for (source in serverSources(module)) {
                for (peerPackage in peerPackages - ownPackage) {
                    assertFalse(source.readText().contains("import $peerPackage"), "$module imports $peerPackage")
                }
            }
        }
    }

    @Test
    fun `gateway and session remain composition framework free leaves`() {
        val gatewayBuild = root.resolve("server/gateway/build.gradle.kts").readText()
        val sessionBuild = root.resolve("server/session/build.gradle.kts").readText()

        assertFalse(gatewayBuild.contains("project("))
        assertFalse(sessionBuild.contains("project("))
        for (module in modules - "host") {
            for (source in serverSources(module)) {
                val text = source.readText()
                assertFalse(text.contains("org.koin"), "Koin outside server/host: $source")
                assertFalse(text.contains("System.getenv"), "environment read outside server/host: $source")
            }
        }
    }

    @Test
    fun `gateway package is owned only by the gateway module`() {
        for (module in modules - "gateway") {
            assertTrue(serverSources(module).none { GATEWAY_PACKAGE.containsMatchIn(it.readText()) })
        }
    }

    @Test
    fun `persistence contracts are isolated from postgres and authentication policy`() {
        val settings = root.resolve("settings.gradle.kts").readText()
        assertTrue(settings.contains("file(\"persistence/api\")"))
        assertTrue(settings.contains("file(\"persistence/postgres\")"))

        val worldBuild = root.resolve("server/world/build.gradle.kts").readText()
        assertTrue(worldBuild.contains("project(\":persistence-api\")"))
        assertFalse(worldBuild.contains("project(\":persistence-postgres\")"))

        val loginSources = serverSources("login").map(Path::readText)
        assertTrue(loginSources.any { it.contains("interface PasswordHasher") })
        assertTrue(loginSources.any { it.contains("class BcryptPasswordHasher") })

        val postgresSources = root.resolve("persistence/postgres/src/main").kotlinSources()
        assertTrue(postgresSources.any { it.readText().contains("class PostgresAccountStore") })
        assertTrue(postgresSources.any { it.readText().contains("class PostgresCharacterStore") })
        assertFalse(postgresSources.any { it.readText().contains("class AccountService") })
    }

    @Test
    fun `host owns the world object graph`() {
        val application = root.resolve("server/host/src/main/kotlin/emu/server/host/ServerApplication.kt").readText()
        val wiring = root.resolve("server/host/src/main/kotlin/emu/server/host/WorldModule.kt").readText()

        assertTrue(application.contains("worldModule("))
        assertTrue(wiring.contains("single<WorldServer>"))
        assertFalse(application.contains("createWorldServer("))
        assertFalse(root.resolve("server/world/src/main/kotlin/emu/server/world/CreateWorldServer.kt").toFile().exists())
    }

    private fun serverSources(module: String): List<Path> = root.resolve("server/$module/src").kotlinSources()

    private fun Path.kotlinSources(): List<Path> =
        Files.walk(this).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" && it.name != "package-info.kt" }.toList()
        }
}

private val MAIN_FUNCTION = Regex("""\bfun\s+main\s*\(""")
private val GATEWAY_PACKAGE = Regex("""^package\s+emu\.server\.gateway(?:\s|$)""", RegexOption.MULTILINE)
private val PROJECT_DEPENDENCY = Regex("""project\(":([^"]+)"\)""")
