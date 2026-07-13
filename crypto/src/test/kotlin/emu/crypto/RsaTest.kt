package emu.crypto

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class RsaTest {
    @Test fun `encrypt with public then decrypt with private recovers plaintext`() {
        val kp = Rsa.generateKeyPair(1024)
        val plaintext = "isaac-seeds-and-password".toByteArray()
        val cipher = Rsa.crypt(plaintext, kp.modulus, kp.publicExp)
        val recovered = Rsa.crypt(cipher, kp.modulus, kp.privateExp)
        // strip any leading zero BigInteger sign byte before compare
        val trimmed = recovered.dropWhile { it == 0.toByte() }.toByteArray()
        assertContentEquals(plaintext, trimmed)
    }

    @Test fun `generated modulus is at least requested bit length minus one`() {
        val kp = Rsa.generateKeyPair(1024)
        assertTrue(kp.modulus.bitLength() >= 1023)
    }
}
