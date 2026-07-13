package emu.tools.clientpatch

import emu.crypto.RsaKeyPair
import java.io.File
import java.math.BigInteger
import java.util.Properties

/**
 * Persists/loads the server's RSA keypair as a flat `key=hexvalue` properties file.
 * The gateway (Task 6) reads the same file to decrypt real client login blocks.
 */
object ServerRsaProperties {
    private const val MODULUS = "modulus"
    private const val PUBLIC_EXPONENT = "publicExponent"
    private const val PRIVATE_EXPONENT = "privateExponent"

    fun save(file: File, keyPair: RsaKeyPair) {
        val props = Properties()
        props.setProperty(MODULUS, keyPair.modulus.toString(16))
        props.setProperty(PUBLIC_EXPONENT, keyPair.publicExp.toString(16))
        props.setProperty(PRIVATE_EXPONENT, keyPair.privateExp.toString(16))
        file.outputStream().use { out ->
            props.store(out, "OSRS emulator server RSA keypair — generated, gitignored, DO NOT COMMIT")
        }
    }

    fun load(file: File): RsaKeyPair {
        val props = Properties()
        file.inputStream().use { props.load(it) }
        fun hex(key: String): BigInteger {
            val value = props.getProperty(key) ?: error("missing property '$key' in ${file.path}")
            return BigInteger(value, 16)
        }
        return RsaKeyPair(
            modulus = hex(MODULUS),
            publicExp = hex(PUBLIC_EXPONENT),
            privateExp = hex(PRIVATE_EXPONENT),
        )
    }
}
