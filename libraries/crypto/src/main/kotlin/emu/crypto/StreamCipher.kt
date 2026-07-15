package emu.crypto

interface StreamCipher {
    fun nextInt(): Int
}

object NopStreamCipher : StreamCipher {
    override fun nextInt(): Int = 0
}
