package emu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class RsaDecryptTest {
    @Test fun `public-encrypt then private-decrypt recovers a login-like block`() {
        val kp = Rsa.generateKeyPair(1024)
        // login-like plaintext: magic byte 1 + some payload
        val plain = byteArrayOf(1, 0x11, 0x22, 0x33, 0x44, 0x55)
        val cipher = Rsa.crypt(plain, kp.modulus, kp.publicExp)
        val recovered = Rsa.decrypt(cipher, kp.modulus, kp.privateExp)
        assertEquals(1, recovered[0].toInt())            // magic byte readable at index 0
        assertContentEquals(plain, recovered)
    }
}
