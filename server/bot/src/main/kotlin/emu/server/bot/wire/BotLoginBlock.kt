package emu.server.bot.wire

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaPublicKey
import emu.crypto.Xtea
import emu.protocol.osrs239.login.codec.LoginBlockParser
import emu.protocol.osrs239.login.prot.LoginProt

/** Builds the RSA/XTEA body of one rev-239 fresh-login packet. */
internal object BotLoginBlock {
    fun encode(
        key: RsaPublicKey,
        seeds: IntArray,
        serverKey: Long,
        username: String,
        password: CharArray,
    ): ByteArray {
        require(seeds.size == ISAAC_SEED_COUNT) { "login requires four ISAAC seeds" }
        require(password.isNotEmpty()) { "bot password must not be empty" }
        val rsa =
            JagexBuffer.alloc(
                1 + ISAAC_SEED_COUNT * Int.SIZE_BYTES + Long.SIZE_BYTES + AUTH_PAYLOAD_SIZE +
                    1 + password.size + 1,
            )
        return try {
            rsa.writeByte(RSA_MAGIC)
            seeds.forEach(rsa::writeInt)
            rsa.writeLong(serverKey)
            rsa.writeByte(FRESH_AUTH_METHOD)
            rsa.writeInt(0)
            rsa.writeByte(0)
            password.forEach { character ->
                require(character.code in 1..0x7F) { "bot password must contain only non-NUL ASCII" }
                rsa.writeByte(character.code)
            }
            rsa.writeByte(0)
            val encryptedRsa = Rsa.crypt(rsa.array, key.modulus, key.exponent)
            val usernameBytes = username.toByteArray(Charsets.ISO_8859_1) + 0
            val tail = usernameBytes.copyOf(roundToXteaBlock(usernameBytes.size))
            val encryptedTail = Xtea.encrypt(tail, seeds)
            JagexBuffer.alloc(
                LoginBlockParser.CLEARTEXT_HEADER_SIZE + U16_SIZE + encryptedRsa.size + encryptedTail.size,
            ).apply {
                writeInt(LoginProt.REVISION)
                writeInt(LoginProt.SUBVERSION)
                writeInt(LoginProt.BUILD_FLAGS)
                writeByte(DESKTOP_CLIENT_MODE)
                writeByte(FIXED_DISPLAY_MODE)
                writeByte(RESERVED_CLIENT_FLAG)
                writeShort(encryptedRsa.size)
                writeBytes(encryptedRsa)
                writeBytes(encryptedTail)
            }.array
        } finally {
            rsa.array.fill(0)
        }
    }

    private fun roundToXteaBlock(size: Int): Int = (size + XTEA_BLOCK_SIZE - 1) / XTEA_BLOCK_SIZE * XTEA_BLOCK_SIZE

    private const val RSA_MAGIC = 1
    private const val FRESH_AUTH_METHOD = 2
    private const val ISAAC_SEED_COUNT = 4
    private const val AUTH_PAYLOAD_SIZE = 1 + Int.SIZE_BYTES
    private const val U16_SIZE = 2
    private const val XTEA_BLOCK_SIZE = 8
    private const val DESKTOP_CLIENT_MODE = 0
    private const val FIXED_DISPLAY_MODE = 0
    private const val RESERVED_CLIENT_FLAG = 0
}
