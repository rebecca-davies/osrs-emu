package emu.gateway.login

import emu.crypto.RsaKeyPair
import java.io.File
import java.math.BigInteger
import java.util.Properties

/** Reads hexadecimal RSA fields from `server-rsa.properties`. */
object ServerRsaKeyFile {
    fun load(file: File): RsaKeyPair {
        val props = Properties()
        file.inputStream().use { props.load(it) }
        fun hex(key: String): BigInteger {
            val value = props.getProperty(key) ?: error("missing property '$key' in ${file.path}")
            return BigInteger(value, 16)
        }
        return RsaKeyPair(
            modulus = hex("modulus"),
            publicExp = hex("publicExponent"),
            privateExp = hex("privateExponent"),
        )
    }
}
