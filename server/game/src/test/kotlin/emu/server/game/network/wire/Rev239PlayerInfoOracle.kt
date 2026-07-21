package emu.server.game.network.wire

import emu.game.map.Tile

/** Independent test decoder transcribed from rsprox's revision-239 client GPI implementation. */
internal class Rev239PlayerInfoOracle(localIndex: Int, localPosition: Tile) {
    private val players = arrayOfNulls<Player>(PLAYER_SLOTS)
    private val lowPositions = IntArray(PLAYER_SLOTS)
    private val flags = ByteArray(PLAYER_SLOTS)
    private val extendedIndices = ArrayList<Int>()
    private val updates = linkedMapOf<Int, Update>()

    init {
        players[localIndex] = Player(localPosition)
    }

    fun player(index: Int): Player? = players[index]

    fun decode(body: ByteArray): Cycle {
        extendedIndices.clear()
        updates.clear()
        val wire = Wire(body)
        val high = (1 until PLAYER_SLOTS).filter { players[it] != null }
        val low = (1 until PLAYER_SLOTS).filter { players[it] == null }
        decodeSection(wire, high, highResolution = true, inactive = false)
        decodeSection(wire, high, highResolution = true, inactive = true)
        decodeSection(wire, low, highResolution = false, inactive = true)
        decodeSection(wire, low, highResolution = false, inactive = false)
        for (index in 1 until PLAYER_SLOTS) flags[index] = (flags[index].toInt() shr 1).toByte()
        extendedIndices.forEach { decodeExtended(wire, it) }
        check(wire.position == body.size) {
            "client oracle consumed ${wire.position} of ${body.size} player-info bytes"
        }
        return Cycle(updates.toMap(), wire.position)
    }

    private fun decodeSection(
        wire: Wire,
        indices: List<Int>,
        highResolution: Boolean,
        inactive: Boolean,
    ) {
        wire.beginBits()
        var skipped = 0
        for (index in indices) {
            if (isInactive(index) != inactive) continue
            if (skipped > 0) {
                skipped--
                markInactive(index)
                continue
            }
            if (wire.bits(1) == 0) {
                skipped = readSkip(wire)
                markInactive(index)
            } else if (highResolution) {
                decodeHigh(wire, index)
            } else if (decodeLow(wire, index)) {
                markInactive(index)
            }
        }
        check(skipped == 0) { "player-info skip crossed a section boundary" }
        wire.endBits()
    }

    private fun decodeHigh(wire: Wire, index: Int) {
        val extended = wire.bits(1) == 1
        if (extended) extendedIndices += index
        val player = checkNotNull(players[index])
        when (wire.bits(2)) {
            0 -> {
                if (extended) {
                    updates[index] = Update(UpdateType.IDLE, player.position)
                } else {
                    lowPositions[index] = packLow(player.position)
                    players[index] = null
                    updates[index] = Update(UpdateType.REMOVE, player.position)
                    if (wire.bits(1) == 1) decodeLow(wire, index)
                }
            }
            1 -> move(index, walkDelta(wire.bits(3)), UpdateType.WALK)
            2 -> move(index, runDelta(wire.bits(4)), UpdateType.RUN)
            3 -> teleport(wire, index)
        }
    }

    private fun decodeLow(wire: Wire, index: Int): Boolean =
        when (wire.bits(2)) {
            0 -> {
                if (wire.bits(1) == 1) decodeLow(wire, index)
                val localX = wire.bits(13)
                val localY = wire.bits(13)
                val extended = wire.bits(1) == 1
                if (extended) extendedIndices += index
                check(players[index] == null)
                val low = lowPositions[index]
                val regionX = low ushr 14 and 0xFF
                val regionY = low and 0xFF
                val position =
                    Tile(
                        (regionX shl 13) + localX,
                        (regionY shl 13) + localY,
                        low ushr 28,
                    )
                players[index] = Player(position)
                updates[index] = Update(UpdateType.ADD, position)
                true
            }
            1 -> {
                val low = lowPositions[index]
                lowPositions[index] = (((low ushr 28) + wire.bits(2)) and 3 shl 28) or (low and 0x0FFFFFFF)
                false
            }
            2 -> {
                val packed = wire.bits(5)
                updateLow(index, packed ushr 3, walkDelta(packed and 7))
                false
            }
            else -> {
                val packed = wire.bits(18)
                val low = lowPositions[index]
                val plane = ((low ushr 28) + (packed ushr 16)) and 3
                val x = ((low ushr 14) + (packed ushr 8 and 0xFF)) and 0xFF
                val y = (low + packed) and 0xFF
                lowPositions[index] = (plane shl 28) or (x shl 14) or y
                false
            }
        }

    private fun decodeExtended(wire: Wire, index: Int) {
        var extendedFlags = wire.u8()
        if (extendedFlags and EXTENDED_SHORT != 0) extendedFlags += wire.u8() shl 8
        val update = updates.getOrPut(index) { Update(UpdateType.IDLE, checkNotNull(players[index]).position) }
        if (extendedFlags and MOVE_SPEED != 0) update.moveSpeed = -wire.u8() and 0xFF
        if (extendedFlags and CHAT != 0) decodeChat(wire, update)
        if (extendedFlags and SEQUENCE != 0) {
            update.sequenceId = wire.u16Alt2().let { if (it == 0xFFFF) -1 else it }
            update.sequenceDelay = wire.u8()
        }
        if (extendedFlags and TEMP_MOVE_SPEED != 0) update.temporaryMoveSpeed = wire.u8()
        if (extendedFlags and APPEARANCE != 0) decodeAppearance(wire, update)
        check(extendedFlags and SUPPORTED_FLAGS.inv() == 0) { "oracle saw unsupported flags $extendedFlags" }
    }

    private fun decodeChat(wire: Wire, update: Update) {
        val style = wire.u16Alt2()
        update.chatColour = style ushr 8
        update.chatEffect = style and 0xFF
        update.modIcon = -wire.u8() and 0xFF
        update.autotyper = (wire.u8() - 128 and 0xFF) == 1
        val length = -wire.u8() and 0xFF
        update.chatText = ByteArray(length) { wire.u8().toByte() }.reversedArray()
        val patternLength = if (update.chatColour in 13..20) update.chatColour - 12 else 0
        update.pattern = ByteArray(patternLength) { (-wire.u8()).toByte() }.takeIf { it.isNotEmpty() }
    }

    private fun decodeAppearance(wire: Wire, update: Update) {
        val length = 128 - wire.u8() and 0xFF
        val appearance = Wire(ByteArray(length) { (wire.u8() - 128).toByte() })
        update.gender = appearance.i8()
        update.skullIcon = appearance.i8()
        update.prayerIcon = appearance.i8()
        repeat(12) { slot ->
            val high = appearance.u8()
            if (high != 0) {
                val value = (high shl 8) or appearance.u8()
                if (slot == 0 && value == 0xFFFF) appearance.u16()
            }
        }
        repeat(12) { if (appearance.u8() != 0) appearance.u8() }
        repeat(5) { appearance.u8() }
        repeat(7) { appearance.u16() }
        update.appearanceName = appearance.cString()
        appearance.u8()
        appearance.u16()
        appearance.u8()
        check(appearance.u16() == 0) { "test appearance unexpectedly carries customisation" }
        repeat(3) { appearance.cString() }
        appearance.i8()
        check(appearance.position == length) { "appearance block was not consumed exactly" }
    }

    private fun move(index: Int, delta: Pair<Int, Int>, type: UpdateType) {
        val previous = checkNotNull(players[index])
        val position =
            Tile(
                previous.position.x + delta.first,
                previous.position.y + delta.second,
                previous.position.plane,
            )
        players[index] = Player(position)
        updates[index] = Update(type, position)
    }

    private fun teleport(wire: Wire, index: Int) {
        val previous = checkNotNull(players[index]).position
        val far = wire.bits(1) == 1
        val packed = wire.bits(if (far) 30 else 12)
        val plane = (previous.plane + (packed ushr if (far) 28 else 10)) and 3
        val x =
            if (far) {
                (previous.x + (packed ushr 14 and 0x3FFF)) and 0x3FFF
            } else {
                previous.x + signed5(packed ushr 5)
            }
        val y =
            if (far) {
                (previous.y + (packed and 0x3FFF)) and 0x3FFF
            } else {
                previous.y + signed5(packed)
            }
        val position = Tile(x, y, plane)
        players[index] = Player(position)
        updates[index] = Update(UpdateType.TELEPORT, position)
    }

    private fun updateLow(index: Int, planeDelta: Int, delta: Pair<Int, Int>) {
        val low = lowPositions[index]
        val plane = ((low ushr 28) + planeDelta) and 3
        val x = ((low ushr 14 and 0xFF) + delta.first) and 0xFF
        val y = ((low and 0xFF) + delta.second) and 0xFF
        lowPositions[index] = (plane shl 28) or (x shl 14) or y
    }

    private fun isInactive(index: Int): Boolean = flags[index].toInt() and 1 != 0
    private fun markInactive(index: Int) { flags[index] = (flags[index].toInt() or 2).toByte() }
    private fun readSkip(wire: Wire): Int =
        when (wire.bits(2)) {
            0 -> 0
            1 -> wire.bits(5)
            2 -> wire.bits(8)
            else -> wire.bits(11)
        }
    private fun packLow(position: Tile): Int =
        (position.plane shl 28) or (position.x ushr 13 shl 14) or (position.y ushr 13)
    private fun signed5(value: Int): Int = (value and 31).let { if (it > 15) it - 32 else it }
    private fun walkDelta(direction: Int): Pair<Int, Int> = WALK_DELTAS[direction]
    private fun runDelta(direction: Int): Pair<Int, Int> = RUN_DELTAS[direction]

    data class Player(val position: Tile)
    data class Cycle(val updates: Map<Int, Update>, val bytesConsumed: Int)
    data class Update(val type: UpdateType, val position: Tile) {
        var moveSpeed: Int? = null
        var temporaryMoveSpeed: Int? = null
        var appearanceName: String? = null
        var gender: Int? = null
        var skullIcon: Int? = null
        var prayerIcon: Int? = null
        var chatColour: Int = 0
        var chatEffect: Int = 0
        var modIcon: Int = 0
        var autotyper: Boolean = false
        var chatText: ByteArray? = null
        var pattern: ByteArray? = null
        var sequenceId: Int? = null
        var sequenceDelay: Int? = null
    }
    enum class UpdateType { IDLE, ADD, REMOVE, WALK, RUN, TELEPORT }

    private class Wire(private val bytes: ByteArray) {
        var position = 0
        private var bitPosition = -1
        fun beginBits() { check(bitPosition == -1); bitPosition = position * 8 }
        fun endBits() { position = (bitPosition + 7) / 8; bitPosition = -1 }
        fun bits(count: Int): Int {
            var value = 0
            repeat(count) {
                val byte = bytes[bitPosition ushr 3].toInt()
                value = value shl 1 or (byte ushr (7 - (bitPosition and 7)) and 1)
                bitPosition++
            }
            return value
        }
        fun u8(): Int = bytes[position++].toInt() and 0xFF
        fun i8(): Int = bytes[position++].toInt()
        fun u16(): Int = (u8() shl 8) or u8()
        fun u16Alt2(): Int = (u8() shl 8) or (u8() - 128 and 0xFF)
        fun cString(): String {
            val start = position
            while (u8() != 0) {
                // Scan to the Jagex string terminator.
            }
            return String(bytes, start, position - start - 1, charset("windows-1252"))
        }
    }

    private companion object {
        const val PLAYER_SLOTS = 2_048
        const val EXTENDED_SHORT = 0x8
        const val APPEARANCE = 0x20
        const val CHAT = 0x100
        const val SEQUENCE = 0x40
        const val MOVE_SPEED = 0x400
        const val TEMP_MOVE_SPEED = 0x1000
        const val SUPPORTED_FLAGS =
            EXTENDED_SHORT or APPEARANCE or CHAT or SEQUENCE or MOVE_SPEED or TEMP_MOVE_SPEED
        val WALK_DELTAS =
            listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
        val RUN_DELTAS =
            listOf(
                -2 to -2, -1 to -2, 0 to -2, 1 to -2, 2 to -2, -2 to -1, 2 to -1,
                -2 to 0, 2 to 0, -2 to 1, 2 to 1, -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2,
            )
    }
}
