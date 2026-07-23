package emu.protocol.osrs239.game.message.npc

import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHitmark
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import emu.protocol.osrs239.game.message.entity.hasUniqueSlots
import emu.transport.message.OutgoingMessage

/** One observer's retained and newly visible NPC information. */
data class NpcInfo(
    val locals: List<NpcInfoLocal>,
    val additions: List<NpcInfoAddition>,
) : OutgoingMessage {
    init {
        require(locals.size <= MAX_LOCAL_NPCS) { "NPC local count must fit the supported local-list cap" }
        require(additions.size <= MAX_LOCAL_NPCS) { "NPC additions must fit the supported local-list cap" }
        var retained = 0
        var localIndex = 0
        while (localIndex < locals.size) {
            if (locals[localIndex] !== NpcInfoLocal.Remove) retained++
            localIndex++
        }
        require(retained + additions.size <= MAX_LOCAL_NPCS) {
            "retained NPCs and additions exceed the supported local-list cap"
        }
        require(additions.hasUniqueIndexes()) {
            "NPC addition indexes must be unique"
        }
    }

    companion object {
        const val MAX_LOCAL_NPCS = 250
        val EMPTY = NpcInfo(emptyList(), emptyList())
    }
}

/** Retained-client-list operation for one NPC in local order. */
sealed interface NpcInfoLocal {
    data object Idle : NpcInfoLocal

    enum class Walk(val direction: Int) : NpcInfoLocal {
        NORTH_WEST(0),
        NORTH(1),
        NORTH_EAST(2),
        WEST(3),
        EAST(4),
        SOUTH_WEST(5),
        SOUTH(6),
        SOUTH_EAST(7),

        ;

        companion object {
            fun from(direction: Int): Walk {
                return when (direction) {
                    0 -> NORTH_WEST
                    1 -> NORTH
                    2 -> NORTH_EAST
                    3 -> WEST
                    4 -> EAST
                    5 -> SOUTH_WEST
                    6 -> SOUTH
                    7 -> SOUTH_EAST
                    else -> throw IllegalArgumentException("NPC walk direction must fit three bits")
                }
            }
        }
    }

    data object Remove : NpcInfoLocal

    /** Movement retained this cycle with one following extended-information block. */
    data class Extended(val movement: NpcInfoLocal, val update: NpcInfoUpdate) : NpcInfoLocal {
        init {
            require(movement === Idle || movement is Walk) {
                "extended NPC movement must be idle or walking"
            }
        }
    }
}

/** Extended information appended for one retained or newly visible NPC. */
data class NpcInfoUpdate(
    val sequence: NpcSequence? = null,
    val hitmarks: List<InfoHitmark> = emptyList(),
    val headbars: List<InfoHeadbar> = emptyList(),
    val spotAnimations: List<InfoSpotAnimation> = emptyList(),
) {
    init {
        require(
            sequence != null || hitmarks.isNotEmpty() || headbars.isNotEmpty() ||
                spotAnimations.isNotEmpty(),
        ) {
            "NPC info update must contain at least one block"
        }
        require(hitmarks.size <= MAX_BLOCK_ENTRIES) { "too many NPC hitmarks" }
        require(headbars.size <= MAX_BLOCK_ENTRIES) { "too many NPC headbars" }
        require(spotAnimations.size <= MAX_BLOCK_ENTRIES) { "too many NPC spot animations" }
        require(spotAnimations.hasUniqueSlots()) {
            "NPC spot-animation slots must be unique"
        }
    }

    private companion object {
        const val MAX_BLOCK_ENTRIES = 0xFF
    }
}

private fun List<NpcInfoAddition>.hasUniqueIndexes(): Boolean {
    var index = 0
    while (index < size) {
        val npcIndex = this[index].index
        var previous = 0
        while (previous < index) {
            if (this[previous].index == npcIndex) return false
            previous++
        }
        index++
    }
    return true
}

/** NPC sequence id and client delay used by the revision-239 update block. */
data class NpcSequence(val id: Int, val delay: Int = 0) {
    init {
        require(id in -1..0xFFFE) { "NPC sequence id must fit below the null sentinel or be -1" }
        require(delay in 0..0xFF) { "NPC sequence delay must fit an unsigned byte" }
    }
}

/** One NPC entering the observer's local list. */
data class NpcInfoAddition(
    val index: Int,
    val type: Int,
    val deltaX: Int,
    val deltaY: Int,
    val orientation: Int,
    val update: NpcInfoUpdate? = null,
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
