package emu.crypto

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test fun `key pair string redacts the private exponent`() {
        val privateExponent = BigInteger("123456789012345678901234567890123456789")
        val keyPair = RsaKeyPair(
            modulus = BigInteger.ONE.shiftLeft(1023).add(BigInteger.valueOf(41)),
            publicExp = BigInteger.valueOf(65537),
            privateExp = privateExponent,
        )

        val rendered = keyPair.toString()

        assertFalse(rendered.contains(privateExponent.toString()))
        assertFalse(rendered.contains(privateExponent.toString(16), ignoreCase = true))
        assertTrue(rendered.contains("RsaKeyPair"))
        assertTrue(rendered.contains("modulusBits=1024"))
        assertTrue(rendered.contains("publicExp=65537"))
        assertTrue(rendered.contains("privateExp=<redacted>"))
    }

    @Test fun `key pair retains data value semantics`() {
        val keyPair = RsaKeyPair(
            modulus = BigInteger.valueOf(3233),
            publicExp = BigInteger.valueOf(17),
            privateExp = BigInteger.valueOf(2753),
        )

        assertEquals(keyPair, keyPair.copy())
        assertEquals(keyPair.privateExp, keyPair.component3())
    }
}
