package emu.cache.container

/**
 * A 4-word XTEA key used to encrypt/decrypt [Container] payloads.
 *
 * Config groups (objects/npcs/items) are never encrypted, so callers pass [ZERO] — decode/encode
 * treat a zero key as identity (recon doc §1/§6). Only map-region groups (index 5) use a non-zero
 * key, sourced externally per region.
 */
data class XteaKey(val k0: Int, val k1: Int, val k2: Int, val k3: Int) {
    /** True when every word is zero — the identity key used for unencrypted (config) groups. */
    val isZero: Boolean get() = k0 == 0 && k1 == 0 && k2 == 0 && k3 == 0

    /** The 4 words in order, as expected by [emu.crypto.Xtea]. */
    fun toIntArray(): IntArray = intArrayOf(k0, k1, k2, k3)

    companion object {
        /** The identity key: XTEA encrypt/decrypt with this key is a no-op. */
        val ZERO = XteaKey(0, 0, 0, 0)

        fun fromIntArray(ints: IntArray): XteaKey {
            require(ints.size == 4) { "XTEA key must have exactly 4 words, got ${ints.size}" }
            return XteaKey(ints[0], ints[1], ints[2], ints[3])
        }

        /** Parses a 32-hex-character (16-byte) key, as commonly distributed in xteas.json dumps. */
        fun fromHex(hex: String): XteaKey {
            val clean = hex.trim()
            require(clean.length == 32) { "XTEA hex key must be 32 hex chars, got ${clean.length}" }
            val words = IntArray(4) { i -> clean.substring(i * 8, i * 8 + 8).toULong(16).toInt() }
            return fromIntArray(words)
        }
    }
}
