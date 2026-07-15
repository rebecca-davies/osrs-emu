package emu.crypto

/** Supplies the next key-stream integer for protocol transformations. */
interface StreamCipher {
    fun nextInt(): Int
}

/** Zero-valued stream cipher for unencrypted protocol stages and tests. */
object NopStreamCipher : StreamCipher {
    override fun nextInt(): Int = 0
}
