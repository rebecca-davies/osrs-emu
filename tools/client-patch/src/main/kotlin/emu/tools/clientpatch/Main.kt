package emu.tools.clientpatch

import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import java.io.File

// The exact Jagex login RSA modulus (rev 239), stored as a plain 256-hex-char UTF-8 String
// literal in class `bg` (field bg.af), exponent bg.ag = "10001". See
// docs/superpowers/research/2026-07-14-rev239-login-facts.md §3.
private const val JAGEX_MODULUS_HEX =
    "c4cc48b4f69a621564fe6227e5ee0d9a58642f25b2e29800d4529bdb92f693b226f06c62fa3d61ce8b578b77b0bb2a4074c05a4e3ff901917d2db94e76718f712619ce0ec71239558f1753b28a0654a542375f6302df7c1e06d1df07cbc4297d792cba9df43ea09b2059c868eaffff0bad854574d270624794379cb5e8b061f3"

private const val DEFAULT_INJECTED_CLIENT_JAR =
    "/home/bec/.gradle/caches/modules-2/files-2.1/net.runelite/injected-client/1.12.33-SNAPSHOT/" +
        "e0ac662d37d913fecc85ce741ce5ae5a8aece0e1/injected-client-1.12.33-SNAPSHOT.jar"

// All paths below are repo-root relative; the `run`/`verifyRoundTrip` Gradle tasks pin
// workingDir to the repo root (see build.gradle.kts), matching tools:cache-fetch's convention.
private val SERVER_RSA_PROPERTIES = File("server-rsa.properties")
private val PATCHED_JAR_OUT = File("client/patches/injected-client-patched.jar")

fun main() {
    val keyPair = generateValidatedKeyPair()
    val modulusHex = keyPair.modulus.toString(16)
    check(modulusHex.length == 256) {
        "expected a 256-hex-char (1024-bit) modulus, got ${modulusHex.length} chars"
    }

    ServerRsaProperties.save(SERVER_RSA_PROPERTIES, keyPair)
    println("Wrote server RSA keypair to ${SERVER_RSA_PROPERTIES.absolutePath} (gitignored)")

    println("Server RSA public modulus (256 hex chars, exponent 10001):")
    println(modulusHex)

    val injectedClientJar = File(System.getenv("INJECTED_CLIENT_JAR") ?: DEFAULT_INJECTED_CLIENT_JAR)
    if (!injectedClientJar.exists()) {
        println("WARNING: injected-client jar not found at ${injectedClientJar.absolutePath}; skipping jar patch.")
        return
    }

    val patchedEntries = JarPatcher.patchAsciiLiteral(
        inputJar = injectedClientJar,
        outputJar = PATCHED_JAR_OUT,
        oldAscii = JAGEX_MODULUS_HEX,
        newAscii = modulusHex,
    )
    check(patchedEntries.isNotEmpty()) {
        "did not find the Jagex modulus literal in any entry of ${injectedClientJar.name} — " +
            "the client build may have changed; re-run the rev-239 recon before patching"
    }
    println("Patched RSA modulus literal in jar entr${if (patchedEntries.size == 1) "y" else "ies"}: $patchedEntries")
    println("Wrote patched client jar to ${PATCHED_JAR_OUT.absolutePath} (gitignored)")
}

/**
 * A genuine 1024-bit RSA modulus always has its top bit set, so [BigInteger.toString] with
 * radix 16 is always exactly 256 chars (no leading zero nibble is ever emitted for a positive
 * number whose top bit is 1) — but assert it rather than trust it, regenerating on the rare
 * chance the underlying JCE provider hands back a short modulus.
 */
private fun generateValidatedKeyPair(): RsaKeyPair {
    repeat(10) {
        val kp = Rsa.generateKeyPair(1024)
        if (kp.modulus.toString(16).length == 256) return kp
    }
    error("failed to generate a 1024-bit RSA modulus with a full 256-hex-char representation after 10 attempts")
}
