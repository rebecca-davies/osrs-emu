package emu.tools.clientpatch

import emu.crypto.Rsa
import java.io.File

// The real check for Task 5: read the persisted server-rsa.properties (the same file the
// gateway will read in Task 6) and prove the keypair round-trips a login-like RSA block —
// public-encrypt then private-decrypt recovers the plaintext, including the login block's
// leading magic byte (1).
private val SERVER_RSA_PROPERTIES = File("server-rsa.properties")

fun main() {
    check(SERVER_RSA_PROPERTIES.exists()) {
        "server-rsa.properties not found at ${SERVER_RSA_PROPERTIES.absolutePath} — " +
            "run `./gradlew :tools:client-patch:run` first to generate it"
    }
    val keyPair = ServerRsaProperties.load(SERVER_RSA_PROPERTIES)

    // login-like plaintext: magic byte 1 + arbitrary payload bytes (stand-in for
    // seeds/serverKey/auth/password — see rev-239 facts §2 for the real layout).
    val plaintext = byteArrayOf(1, 0x11, 0x22, 0x33, 0x44, 0x55)
    val cipher = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
    val recovered = Rsa.decrypt(cipher, keyPair.modulus, keyPair.privateExp)

    check(recovered.contentEquals(plaintext)) {
        "round-trip FAILED: recovered ${recovered.toList()} != expected ${plaintext.toList()}"
    }
    check(recovered[0].toInt() == 1) { "magic byte 1 was not recovered" }

    println(
        "OK: server-rsa.properties round-trips a login-like RSA block " +
            "(public-encrypt -> private-decrypt recovers magic byte 1)"
    )
}
