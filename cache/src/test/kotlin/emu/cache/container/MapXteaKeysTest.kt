package emu.cache.container

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the map-region XTEA key loader and, when a real rev-239 cache dump is present, validates the
 * borrowed build-236 Lumbridge key by trial-decrypting the *served build-239* `l50_50` loc group.
 * Keys are revision-stable but must still be verified per group.
 */
class MapXteaKeysTest {
    /** Build-236 `l50_50` key for mapsquare 12850. */
    private val lumbridgeKey = XteaKey(2039325142, -1035995824, -1400488441, -1475825849)

    /** A minimal keys.json fixture in the OpenRS2 array schema, incl. the Lumbridge `l50_50` entry. */
    private val fixtureJson = """
        [
          {"archive":5,"group":5302,"name_hash":-1152549421,"name":"l50_50","mapsquare":12850,
           "key":[2039325142,-1035995824,-1400488441,-1475825849]},
          {"archive":5,"group":10,"name_hash":1,"name":"l49_49","mapsquare":12593,
           "key":[1,2,3,4]}
        ]
    """.trimIndent()

    @Test fun `parses OpenRS2 keys json into per-mapsquare keys`() {
        val keys = MapXteaKeys.parse(fixtureJson)
        assertEquals(2, keys.size)
        assertEquals(lumbridgeKey, keys[12850])
        assertEquals(XteaKey(1, 2, 3, 4), keys[12593])
        assertEquals(null, keys[99999])
    }

    @Test fun `resolves the key for an absolute world tile`() {
        val keys = MapXteaKeys.parse(fixtureJson)
        // Lumbridge spawn (3222, 3218) is in region 50,50 = mapsquare 12850.
        assertEquals(12850, MapXteaKeys.mapsquareOf(3222, 3218))
        assertEquals(lumbridgeKey, keys.forTile(3222, 3218))
    }

    @Test fun `name hash matches the index-5 group name hash for l50_50`() {
        assertEquals(-1152549421, MapXteaKeys.nameHash("l50_50"))
        assertEquals(-1123920270, MapXteaKeys.nameHash("m50_50"))
    }

    @Test fun `borrowed key decrypts the real encrypted l50_50 loc container`() {
        // Real-cache fixtures are optional and absent in CI.
        val keysFile = File("../cache-data/xtea/keys.json")
        val containerFile = File("../cache-data/xtea/l50_50.dat")
        if (!keysFile.isFile || !containerFile.isFile) return

        val keys = MapXteaKeys.load(keysFile)
        val key = keys.forTile(3222, 3218) // Lumbridge spawn -> mapsquare 12850 -> l50_50
        assertNotNull(key, "keys.json must contain the Lumbridge mapsquare 12850")
        assertEquals(lumbridgeKey, key)

        val encrypted = containerFile.readBytes()

        // Decompression acts as the key-validity check.
        val decoded = Container.decode(encrypted, key)
        assertTrue(decoded.data.isNotEmpty(), "l50_50 loc group decoded to a non-empty payload under the borrowed key")

        // The encrypted fixture must not decode with the plaintext sentinel key.
        assertFailsWith<Exception>("zero key must fail to decrypt the encrypted loc group") {
            Container.decode(encrypted, XteaKey.ZERO)
        }
    }
}
