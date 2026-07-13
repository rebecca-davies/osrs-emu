package emu.tools.clientpatch

import emu.crypto.Rsa
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ServerRsaPropertiesTest {
    @Test fun `persisted keypair round-trips a login-like RSA block`() {
        val keyPair = Rsa.generateKeyPair(1024)
        val file = File.createTempFile("server-rsa", ".properties")
        try {
            ServerRsaProperties.save(file, keyPair)
            val loaded = ServerRsaProperties.load(file)

            assertEquals(keyPair.modulus, loaded.modulus)
            assertEquals(keyPair.publicExp, loaded.publicExp)
            assertEquals(keyPair.privateExp, loaded.privateExp)

            // login-like plaintext: magic byte 1 + payload
            val plaintext = byteArrayOf(1, 0x11, 0x22, 0x33, 0x44, 0x55)
            val cipher = Rsa.crypt(plaintext, loaded.modulus, loaded.publicExp)
            val recovered = Rsa.decrypt(cipher, loaded.modulus, loaded.privateExp)

            assertEquals(1, recovered[0].toInt())
            assertContentEquals(plaintext, recovered)
        } finally {
            file.delete()
        }
    }
}
