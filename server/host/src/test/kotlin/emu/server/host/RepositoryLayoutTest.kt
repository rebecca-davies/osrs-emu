package emu.server.host

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryLayoutTest {
    private val root = Path.of(checkNotNull(System.getProperty("repository.root")))

    @Test
    fun `reusable libraries share one parent and transport has an explicit identity`() {
        val settings = root.resolve("settings.gradle.kts").readText()

        for (library in listOf("buffer", "compression", "crypto", "transport")) {
            assertTrue(root.resolve("libraries/$library").exists(), "missing libraries/$library")
            assertTrue(settings.contains("file(\"libraries/$library\")"), "missing Gradle mapping for $library")
        }
        for (legacyRoot in listOf("buffer", "compression", "crypto", "net-core")) {
            assertFalse(root.resolve(legacyRoot).exists(), "legacy root module remains: $legacyRoot")
        }
        assertFalse(settings.contains("net-core"))
    }

    @Test
    fun `transport message directions are explicit contracts without a common marker`() {
        val messagePackage = root.resolve("libraries/transport/src/main/kotlin/emu/transport/message")

        assertTrue(messagePackage.resolve("IncomingMessage.kt").exists())
        assertTrue(messagePackage.resolve("OutgoingMessage.kt").exists())
        assertFalse(messagePackage.resolve("Message.kt").exists())
    }
}
