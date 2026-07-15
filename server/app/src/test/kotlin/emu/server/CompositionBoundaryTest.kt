package emu.server

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
    private val modules = listOf("app", "gateway", "login", "js5", "game", "session")
    private val peerProjects = listOf("server-gateway", "server-login", "server-js5", "server-game")
    private val peerPackages = listOf("emu.server.gateway", "emu.server.login", "emu.server.js5", "emu.server.game")

    @Test
    fun `server app is the only executable runtime module`() {
        val builds = modules.associateWith { root.resolve("server/$it/build.gradle.kts").readText() }

        assertEquals(listOf("app"), builds.filterValues { it.contains("application") }.keys.toList())
        for (module in modules - "app") {
            assertFalse(serverSources(module).any { MAIN_FUNCTION.containsMatchIn(it.readText()) })
        }
    }

    @Test
    fun `server app exclusively owns process logging configuration`() {
        val owners = modules.filter { root.resolve("server/$it/src/main/resources/logback.xml").isRegularFile() }

        assertEquals(listOf("app"), owners)
    }

    @Test
    fun `peer services cannot depend on or import each other`() {
        for ((module, project) in mapOf("gateway" to "server-gateway", "login" to "server-login", "js5" to "server-js5", "game" to "server-game")) {
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
        for (module in modules - "app") {
            for (source in serverSources(module)) {
                val text = source.readText()
                assertFalse(text.contains("org.koin"), "Koin outside server/app: $source")
                assertFalse(text.contains("System.getenv"), "environment read outside server/app: $source")
            }
        }
    }

    @Test
    fun `gateway package is owned only by the gateway module`() {
        for (module in modules - "gateway") {
            assertTrue(serverSources(module).none { GATEWAY_PACKAGE.containsMatchIn(it.readText()) })
        }
    }

    private fun serverSources(module: String): List<Path> = root.resolve("server/$module/src").kotlinSources()

    private fun Path.kotlinSources(): List<Path> =
        Files.walk(this).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" && it.name != "package-info.kt" }.toList()
        }
}

private val MAIN_FUNCTION = Regex("""\bfun\s+main\s*\(""")
private val GATEWAY_PACKAGE = Regex("""^package\s+emu\.server\.gateway(?:\s|$)""", RegexOption.MULTILINE)
