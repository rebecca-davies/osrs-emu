package emu.server.host

import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RuntimeAssetsTest {
    @Test
    fun `server startup requires its configured login key`() {
        val directory = Files.createTempDirectory("osrsemu-runtime-assets")
        try {
            val missingKey = directory.resolve("missing-rsa.properties")
            val config =
                loadServerConfig(
                    mapOf("OSRS_SERVER_RSA_PROPERTIES" to missingKey.toString()),
                )

            assertFailsWith<FileNotFoundException> { loadRuntimeAssets(config.assets) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
