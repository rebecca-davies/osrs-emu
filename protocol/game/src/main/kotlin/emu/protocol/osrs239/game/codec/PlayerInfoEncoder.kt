package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.PlayerInfoUpdate
import emu.protocol.osrs239.game.message.PlayerMovement
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the four rev-239 GPI sections followed by their ordered extended-info blocks. */
object PlayerInfoEncoder : MessageEncoder<PlayerInfo> {
    override val prot: Prot = GameServerProt.PLAYER_INFO
    override val messageType = PlayerInfo::class.java

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        val bits = BitBuf()
        val updates = mutableListOf<PlayerInfoUpdate>()
        for (section in message.sections.ordered) {
            for (code in section) encode(bits, code, updates)
            alignToByte(bits)
        }
        val gpi = bits.toByteArray()
        if (updates.isEmpty()) return gpi

        val blocks = updates.map(PlayerInfoExtendedEncoder::encode)
        val bodySize = gpi.size + blocks.sumOf(ByteArray::size)
        require(bodySize <= MAX_BODY_SIZE) { "player-info body exceeds its variable-short frame" }
        val out = JagexBuffer.alloc(bodySize)
        out.writeBytes(gpi)
        blocks.forEach(out::writeBytes)
        return out.array
    }

    private fun encode(
        bits: BitBuf,
        code: PlayerInfoBitCode,
        updates: MutableList<PlayerInfoUpdate>,
    ) {
        when (code) {
            is PlayerInfoBitCode.Skip -> writeSkip(bits, code.players)
            is PlayerInfoBitCode.HighResolution -> writeHighResolution(bits, code, updates)
            is PlayerInfoBitCode.Remove -> writeRemoval(bits, code)
            is PlayerInfoBitCode.Add -> writeAddition(bits, code, updates)
            is PlayerInfoBitCode.LowResolution -> {
                bits.writeBits(1, 1)
                writeLowResolution(bits, code)
            }
        }
    }

    private fun writeSkip(bits: BitBuf, players: Int) {
        bits.writeBits(1, 0)
        val additional = players - 1
        when {
            additional == 0 -> bits.writeBits(2, 0)
            additional <= 0x1F -> {
                bits.writeBits(2, 1)
                bits.writeBits(5, additional)
            }
            additional <= 0xFF -> {
                bits.writeBits(2, 2)
                bits.writeBits(8, additional)
            }
            else -> {
                bits.writeBits(2, 3)
                bits.writeBits(11, additional)
            }
        }
    }

    private fun writeHighResolution(
        bits: BitBuf,
        code: PlayerInfoBitCode.HighResolution,
        updates: MutableList<PlayerInfoUpdate>,
    ) {
        bits.writeBits(1, 1)
        bits.writeBits(1, if (code.update == null) 0 else 1)
        when (val movement = code.movement) {
            null -> bits.writeBits(2, 0)
            is PlayerMovement.Walk -> {
                bits.writeBits(2, 1)
                bits.writeBits(3, walkDirection(movement.deltaX, movement.deltaY))
            }
            is PlayerMovement.Run -> {
                bits.writeBits(2, 2)
                bits.writeBits(4, runDirection(movement.deltaX, movement.deltaY))
            }
        }
        code.update?.let(updates::add)
    }

    private fun writeRemoval(bits: BitBuf, code: PlayerInfoBitCode.Remove) {
        bits.writeBits(1, 1)
        bits.writeBits(1, 0)
        bits.writeBits(2, 0)
        bits.writeBits(1, if (code.regionChange == null) 0 else 1)
        code.regionChange?.let { writeLowResolution(bits, it) }
    }

    private fun writeAddition(
        bits: BitBuf,
        code: PlayerInfoBitCode.Add,
        updates: MutableList<PlayerInfoUpdate>,
    ) {
        bits.writeBits(1, 1)
        bits.writeBits(2, 0)
        bits.writeBits(1, if (code.regionChange == null) 0 else 1)
        code.regionChange?.let { writeLowResolution(bits, it) }
        bits.writeBits(13, code.x)
        bits.writeBits(13, code.y)
        bits.writeBits(1, 1)
        updates += code.update
    }

    private fun writeLowResolution(bits: BitBuf, code: PlayerInfoBitCode.LowResolution) {
        when (code) {
            is PlayerInfoBitCode.LowResolution.Plane -> {
                bits.writeBits(2, 1)
                bits.writeBits(2, code.delta)
            }
            is PlayerInfoBitCode.LowResolution.Step -> {
                bits.writeBits(2, 2)
                bits.writeBits(5, (code.planeDelta shl 3) or code.direction)
            }
            is PlayerInfoBitCode.LowResolution.Region -> {
                bits.writeBits(2, 3)
                bits.writeBits(18, (code.planeDelta shl 16) or (code.deltaX shl 8) or code.deltaY)
            }
        }
    }

    private fun alignToByte(bits: BitBuf) {
        val remainder = bits.bitPosition and 7
        if (remainder != 0) bits.writeBits(8 - remainder, 0)
    }

    private fun walkDirection(deltaX: Int, deltaY: Int): Int =
        when (deltaX to deltaY) {
            -1 to -1 -> 0
            0 to -1 -> 1
            1 to -1 -> 2
            -1 to 0 -> 3
            1 to 0 -> 4
            -1 to 1 -> 5
            0 to 1 -> 6
            1 to 1 -> 7
            else -> error("invalid walk delta: $deltaX,$deltaY")
        }

    private fun runDirection(deltaX: Int, deltaY: Int): Int =
        when (deltaX to deltaY) {
            -2 to -2 -> 0
            -1 to -2 -> 1
            0 to -2 -> 2
            1 to -2 -> 3
            2 to -2 -> 4
            -2 to -1 -> 5
            2 to -1 -> 6
            -2 to 0 -> 7
            2 to 0 -> 8
            -2 to 1 -> 9
            2 to 1 -> 10
            -2 to 2 -> 11
            -1 to 2 -> 12
            0 to 2 -> 13
            1 to 2 -> 14
            2 to 2 -> 15
            else -> error("invalid run delta: $deltaX,$deltaY")
        }

    private const val MAX_BODY_SIZE = 0xFFFF
}
