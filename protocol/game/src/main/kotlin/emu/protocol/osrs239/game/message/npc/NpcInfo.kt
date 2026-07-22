package emu.protocol.osrs239.game.message.npc

import emu.transport.message.OutgoingMessage

/** One observer's retained and newly visible NPC information. */
data class NpcInfo(
    val locals: List<NpcInfoLocal>,
    val additions: List<NpcInfoAddition>,
) : OutgoingMessage {
    init {
        require(locals.size <= MAX_LOCAL_NPCS) { "NPC local count must fit the supported local-list cap" }
    }

    companion object {
        const val MAX_LOCAL_NPCS = 250
        val EMPTY = NpcInfo(emptyList(), emptyList())
    }
}

/** Retained-client-list operation for one NPC in local order. */
sealed interface NpcInfoLocal {
    data object Idle : NpcInfoLocal

    data class Walk(val direction: Int) : NpcInfoLocal {
        init {
            require(direction in 0..7) { "NPC walk direction must fit three bits" }
        }
    }

    data object Remove : NpcInfoLocal
}

/** One NPC entering the observer's local list. */
data class NpcInfoAddition(
    val index: Int,
    val type: Int,
    val deltaX: Int,
    val deltaY: Int,
    val orientation: Int,
) {
    init {
        require(index in 0 until NULL_INDEX) { "NPC index must fit below the protocol sentinel" }
        require(type in 0 until (1 shl TYPE_BITS)) { "NPC type must fit $TYPE_BITS bits" }
        require(deltaX in COORDINATE_DELTA && deltaY in COORDINATE_DELTA) {
            "NPC addition delta must fit a signed $COORDINATE_BITS-bit value"
        }
        require(orientation in 0..7) { "NPC orientation must fit three bits" }
    }

    companion object {
        const val NULL_INDEX = 0xFFFF
        private const val TYPE_BITS = 14
        private const val COORDINATE_BITS = 6
        private val COORDINATE_DELTA = -32..31
    }
}
