package emu.crypto

/**
 * A byte-XOR keystream cipher: every call to [nextInt] returns the same constant [key], so callers
 * XOR each outgoing byte with it. JS5-specific key mutation is owned by the JS5 service; a key of
 * zero leaves bytes unchanged.
 */
class XorStreamCipher(@Volatile var key: Int = 0) : StreamCipher {
    override fun nextInt(): Int = key
}
