package emu.crypto

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

data class RsaKeyPair(
    val modulus: BigInteger,
    val publicExp: BigInteger,
    val privateExp: BigInteger,
)

object Rsa {
    fun crypt(data: ByteArray, mod: BigInteger, exp: BigInteger): ByteArray =
        BigInteger(1, data).modPow(exp, mod).toByteArray()

    fun decrypt(cipher: ByteArray, modulus: BigInteger, privateExp: BigInteger): ByteArray {
        val out = crypt(cipher, modulus, privateExp)
        // Strip a BigInteger sign byte if the top bit of the magnitude set one.
        return if (out.isNotEmpty() && out[0] == 0.toByte()) out.copyOfRange(1, out.size) else out
    }

    fun generateKeyPair(bits: Int = 1024): RsaKeyPair {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }
        val kp = gen.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val priv = kp.private as RSAPrivateKey
        return RsaKeyPair(pub.modulus, pub.publicExponent, priv.privateExponent)
    }
}
