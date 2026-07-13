package emu.gateway.login

import emu.crypto.RsaKeyPair
import java.io.File
import java.math.BigInteger
import java.util.Properties

/**
 * Reads the gateway's RSA keypair from the repo-root `server-rsa.properties` file that
 * `tools:client-patch` generates (keys `modulus` / `publicExponent` / `privateExponent`, all hex
 * — see `tools/client-patch`'s `ServerRsaProperties`). Duplicated here rather than adding a
 * runtime dependency from `gateway` on the `tools:client-patch` dev tool module for a six-line
 * properties parse.
 */
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
