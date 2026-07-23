package emu.server.game.network.output.playerinfo

import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.pathfinding.movement.MovementUpdate
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHitmark
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.playerinfo.PlayerMovement
import emu.protocol.osrs239.game.message.playerinfo.PlayerSequence

/** Immutable information-phase view of one active player. */
internal data class PlayerInfoSnapshot(
    val index: Int,
    val position: Tile,
    val movement: MovementUpdate,
    val runEnabled: Boolean,
    val appearance: PlayerAppearance,
    val mapInstance: MapInstance = MapInstance.SHARED,
    val publicChat: PlayerPublicChat? = null,
    val sequence: PlayerSequence? = null,
    val visuals: PlayerInfoVisualSnapshot? = null,
) {
    val movementOnlyCode: PlayerInfoBitCode.HighResolution? =
        when (movement) {
            MovementUpdate.Idle -> null
            is MovementUpdate.Walk ->
                PlayerInfoBitCode.HighResolution(PlayerMovement.Walk(movement.deltaX, movement.deltaY))
            is MovementUpdate.Run ->
                PlayerInfoBitCode.HighResolution(PlayerMovement.Run(movement.deltaX, movement.deltaY))
            is MovementUpdate.Teleport ->
                PlayerInfoBitCode.HighResolution(
                    PlayerMovement.Teleport(movement.deltaX, movement.deltaY, movement.planeDelta),
                )
        }

    val protocolMovement: PlayerMovement?
        get() = movementOnlyCode?.movement
}

/** Rev-239 visual blocks shared by all observers of one source player for one information phase. */
internal data class PlayerInfoVisualSnapshot(
    val selfHitmarks: List<InfoHitmark>,
    val otherHitmarks: List<InfoHitmark>,
    val headbars: List<InfoHeadbar>,
    val spotAnimations: List<InfoSpotAnimation>,
) {
    fun hitmarks(self: Boolean): List<InfoHitmark> = if (self) selfHitmarks else otherHitmarks
}
