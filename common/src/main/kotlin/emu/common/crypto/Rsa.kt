package emu.common.crypto

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

    fun generateKeyPair(bits: Int = 1024): RsaKeyPair {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }
        val kp = gen.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val priv = kp.private as RSAPrivateKey
        return RsaKeyPair(pub.modulus, pub.publicExponent, priv.privateExponent)
    }
}
