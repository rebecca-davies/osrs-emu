package emu.protocol.osrs239.game.codec.npc

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import emu.protocol.osrs239.game.message.npc.NpcInfoUpdate
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes rev-239's small-coordinate NPC local-list and addition bit stream. */
object NpcInfoEncoder : CipherIndependentMessageEncoder<NpcInfo> {
    override val prot: Prot = GameServerProt.NPC_INFO
    override val messageType = NpcInfo::class.java

    override fun encode(message: NpcInfo): ByteArray {
        val bits = BitBuf()
        var updates: ArrayList<NpcInfoUpdate>? = null
        bits.writeBits(8, message.locals.size)
        for (local in message.locals) {
            val update = writeLocal(bits, local) ?: continue
            val pending = updates ?: ArrayList<NpcInfoUpdate>().also { updates = it }
            pending += update
        }
        for (addition in message.additions) {
            writeAddition(bits, addition)
            val update = addition.update ?: continue
            val pending = updates ?: ArrayList<NpcInfoUpdate>().also { updates = it }
            pending += update
        }
        val pendingUpdates = updates ?: return validatedBody(bits.toByteArray())
        bits.writeBits(16, NpcInfoAddition.NULL_INDEX)
        val localList = bits.toByteArray()
        val blocks = pendingUpdates.map(::encodeUpdate)
        val bodySize = localList.size + blocks.sumOf(ByteArray::size)
        val out = JagexBuffer.alloc(bodySize)
        out.writeBytes(localList)
        blocks.forEach(out::writeBytes)
        return validatedBody(out.array)
    }

    private fun writeLocal(
        bits: BitBuf,
        local: NpcInfoLocal,
    ): NpcInfoUpdate? =
        when (local) {
            NpcInfoLocal.Idle -> {
                bits.writeBits(1, 0)
                null
            }
            is NpcInfoLocal.Walk -> {
                writeWalk(bits, local, extended = false)
                null
            }
            NpcInfoLocal.Remove -> {
                bits.writeBits(1, 1).writeBits(2, REMOVE)
                null
            }
            is NpcInfoLocal.Extended -> {
                when (val movement = local.movement) {
                    NpcInfoLocal.Idle -> bits.writeBits(1, 1).writeBits(2, EXTENDED_ONLY)
                    is NpcInfoLocal.Walk -> writeWalk(bits, movement, extended = true)
                    else -> error("invalid extended NPC movement")
                }
                local.update
            }
        }

    private fun writeWalk(bits: BitBuf, walk: NpcInfoLocal.Walk, extended: Boolean) {
        bits.writeBits(1, 1).writeBits(2, WALK)
        bits.writeBits(3, walk.direction).writeBits(1, if (extended) 1 else 0)
    }

    private fun writeAddition(
        bits: BitBuf,
        addition: NpcInfoAddition,
    ) {
        bits.writeBits(16, addition.index)
        bits.writeBits(1, 0)
        bits.writeBits(1, if (addition.update == null) 0 else 1)
        bits.writeBits(6, addition.deltaX)
        bits.writeBits(3, addition.orientation)
        bits.writeBits(6, addition.deltaY)
        bits.writeBits(1, 1)
        bits.writeBits(14, addition.type)
    }

    private fun encodeUpdate(update: NpcInfoUpdate): ByteArray {
        var flags = 0
        if (update.sequence != null) flags = flags or SEQUENCE_FLAG
        if (update.hitmarks.isNotEmpty()) flags = flags or HITMARKS_FLAG
        if (update.headbars.isNotEmpty()) flags = flags or HEADBARS_FLAG
        if (update.spotAnimations.isNotEmpty()) flags = flags or SPOT_ANIMATIONS_FLAG
        if (flags ushr 24 != 0) flags = flags or EXTENDED_INT_FLAG
        if (flags ushr 16 != 0) flags = flags or EXTENDED_MEDIUM_FLAG
        if (flags ushr 8 != 0) flags = flags or EXTENDED_SHORT_FLAG

        val flagBytes =
            1 +
                (if (flags and EXTENDED_SHORT_FLAG != 0) 1 else 0) +
                (if (flags and EXTENDED_MEDIUM_FLAG != 0) 1 else 0) +
                (if (flags and EXTENDED_INT_FLAG != 0) 1 else 0)
        val out =
            JagexBuffer.alloc(
                flagBytes + hitmarkBlockSize(update) + headbarBlockSize(update) +
                    (if (update.sequence == null) 0 else SEQUENCE_SIZE) + spotAnimationBlockSize(update),
            )
        out.writeByte(flags)
        if (flags and EXTENDED_SHORT_FLAG != 0) out.writeByte(flags ushr 8)
        if (flags and EXTENDED_MEDIUM_FLAG != 0) out.writeByte(flags ushr 16)
        if (flags and EXTENDED_INT_FLAG != 0) out.writeByte(flags ushr 24)
        if (update.hitmarks.isNotEmpty()) {
            out.writeByteAlt1(update.hitmarks.size)
            for (hitmark in update.hitmarks) {
                out.writeSmart1or2(hitmark.type)
                out.writeSmart1or2(hitmark.value)
                out.writeSmart1or2(hitmark.delay)
                out.writeSmart1or2(hitmark.limit)
            }
        }
        if (update.headbars.isNotEmpty()) {
            out.writeByteAlt2(update.headbars.size)
            for (headbar in update.headbars) {
                out.writeSmart1or2(headbar.type)
                out.writeSmart1or2(headbar.endTime)
                out.writeSmart1or2(headbar.startTime)
                out.writeByteAlt1(headbar.startFill)
                if (headbar.endTime > 0) out.writeByteAlt3(headbar.endFill)
            }
        }
        update.sequence?.let { sequence ->
            out.writeShort(sequence.id)
            out.writeByteAlt2(sequence.delay)
        }
        if (update.spotAnimations.isNotEmpty()) {
            out.writeByteAlt2(update.spotAnimations.size)
            for (spotAnimation in update.spotAnimations) {
                out.writeByte(spotAnimation.slot)
                out.writeShort(spotAnimation.id)
                out.writeIntAlt2((spotAnimation.height shl 16) or spotAnimation.delay)
            }
        }
        return out.array
    }

    private fun hitmarkBlockSize(update: NpcInfoUpdate): Int =
        if (update.hitmarks.isEmpty()) 0 else 1 + update.hitmarks.sumOf {
            smartSize(it.type) + smartSize(it.value) + smartSize(it.delay) + smartSize(it.limit)
        }

    private fun headbarBlockSize(update: NpcInfoUpdate): Int =
        if (update.headbars.isEmpty()) 0 else 1 + update.headbars.sumOf {
            smartSize(it.type) + smartSize(it.endTime) + smartSize(it.startTime) +
                1 + if (it.endTime > 0) 1 else 0
        }

    private fun spotAnimationBlockSize(update: NpcInfoUpdate): Int =
        if (update.spotAnimations.isEmpty()) 0 else 1 + update.spotAnimations.size * SPOT_ANIMATION_SIZE

    private fun smartSize(value: Int): Int = if (value < 0x80) 1 else 2

    private fun validatedBody(body: ByteArray): ByteArray {
        require(body.size <= MAX_BODY_SIZE) { "NPC-info body exceeds its variable-short frame" }
        return body
    }

    private const val EXTENDED_ONLY = 0
    private const val WALK = 1
    private const val REMOVE = 3
    private const val EXTENDED_SHORT_FLAG = 0x40
    private const val EXTENDED_MEDIUM_FLAG = 0x800
    private const val EXTENDED_INT_FLAG = 0x200000
    private const val SEQUENCE_FLAG = 0x80
    private const val SPOT_ANIMATIONS_FLAG = 0x40000
    private const val HITMARKS_FLAG = 0x80000
    private const val HEADBARS_FLAG = 0x1000000
    private const val SEQUENCE_SIZE = 3
    private const val SPOT_ANIMATION_SIZE = 7
    private const val MAX_BODY_SIZE = 0xFFFF
}
