package emu.gateway.login

import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.Tile
import emu.netcore.message.OutgoingMessage
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.AmbienceStop
import emu.protocol.osrs239.game.message.CamReset
import emu.protocol.osrs239.game.message.CamTargetPlayer
import emu.protocol.osrs239.game.message.ChatFilterSettings
import emu.protocol.osrs239.game.message.HideLocOps
import emu.protocol.osrs239.game.message.HideNpcOps
import emu.protocol.osrs239.game.message.HideObjOps
import emu.protocol.osrs239.game.message.IfOpenSub
import emu.protocol.osrs239.game.message.IfOpenTop
import emu.protocol.osrs239.game.message.IfResync
import emu.protocol.osrs239.game.message.MessageGame
import emu.protocol.osrs239.game.message.MinimapToggle
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.PacketGroupStart
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.message.ResetAnims
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.SiteSettings
import emu.protocol.osrs239.game.message.UpdateInvFull
import emu.protocol.osrs239.game.message.UpdateRunEnergy
import emu.protocol.osrs239.game.message.UpdateRunWeight
import emu.protocol.osrs239.game.message.UpdateStat
import emu.protocol.osrs239.game.message.UpdateZoneFullFollows
import emu.protocol.osrs239.game.message.VarpReset
import emu.protocol.osrs239.game.message.WorldEntityInfo
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** The chatbox notice the real server posts on every login. */
internal const val WELCOME_MESSAGE = "Welcome to RuneScape."

/**
 * The one-time chatbox notices shown after login, once the game frame (and its chatbox) is open.
 * Just the plain welcome line for now; server MOTD/broadcast lines would join it here.
 */
internal fun loginNoticeMessages(): List<MessageGame> = listOf(
    MessageGame(MessageGame.GAME_MESSAGE, WELCOME_MESSAGE),
)

/**
 * Builds the capture-shaped atomic initial world group: active-world context, NPC origin, empty
 * world-entity state, the local player's appearance-bearing GPI, empty NPC state, then the 49
 * scene zones the real rev-239 login clears. Dynamic loc/object deltas are deliberately absent:
 * static Lumbridge content comes from the cache, while the captured deltas were live-world state.
 */
internal fun initialWorldGroup(
    appearance: PlayerAppearance?,
    localPlayerIndex: Int,
    localPlayerX: Int,
    localPlayerY: Int,
): List<OutgoingMessage> = buildList {
    add(SetActiveWorld())
    add(SetNpcUpdateOrigin(localPlayerX, localPlayerY))
    add(WorldEntityInfo)
    add(PlayerInfo(appearance))
    add(NpcInfo)
    for ((x, z) in INITIAL_ZONE_SPIRAL) add(UpdateZoneFullFollows(x, z))
    add(CamTargetPlayer(localPlayerIndex))
}

/** The permanent rev-239 interface-161 game frame after the one-time welcome display is dismissed. */
internal fun initialFrameSubInterfaces(): List<IfOpenSub> = listOf(
    IfOpenSub(161, 96, 162),
    IfOpenSub(161, 6, 651),
    IfOpenSub(161, 5, 708),
    IfOpenSub(161, 93, 163),
    IfOpenSub(161, 2, 303),
    IfOpenSub(161, 33, 160),
    IfOpenSub(161, 9, 122),
    IfOpenSub(161, 35, 728),
    IfOpenSub(161, 36, 896),
    IfOpenSub(161, 77, 320),
    IfOpenSub(161, 78, 629),
    IfOpenSub(629, 43, 259),
    IfOpenSub(161, 79, 149),
    IfOpenSub(161, 80, 387),
    IfOpenSub(161, 81, 541),
    IfOpenSub(161, 82, 218),
    IfOpenSub(161, 85, 429),
    IfOpenSub(161, 84, 109),
    IfOpenSub(161, 86, 182),
    IfOpenSub(161, 87, 116),
    IfOpenSub(161, 88, 216),
    IfOpenSub(161, 89, 239),
    IfOpenSub(161, 83, 707),
    IfOpenSub(707, 7, 7),
    IfOpenSub(161, 76, 593),
)

/** Opens the post-welcome game frame directly, attaches its overlays, and publishes the tree. */
internal fun initialFrameMessages(): List<OutgoingMessage> {
    val frame = initialFrameSubInterfaces()
    return buildList {
        add(IfOpenTop(161))
        addAll(frame)
        add(CamReset)
        add(AmbienceStop(fade = true))
        add(IfResync(161, frame))
    }
}

/**
 * Sends rev-239's complete account-neutral cycle zero around the Lumbridge rebuild. The packet
 * group length is measured from the registered codecs, so it covers each member's smart opcode,
 * variable-length prefix, and body exactly as the client counts them.
 */
internal suspend fun sendInitialGameCycle(
    session: OutboundSession,
    spawnPlane: Int,
    spawnX: Int,
    spawnY: Int,
    localPlayerIndex: Int,
    appearance: PlayerAppearance?,
) {
    val buildArea = PlayerBuildArea(Tile(spawnX, spawnY, spawnPlane))
    session.send(RebuildLogin(spawnPlane, spawnX, spawnY, localPlayerIndex))

    session.send(SiteSettings())
    session.send(ChatFilterSettings())
    session.send(HideNpcOps())
    session.send(HideLocOps())
    session.send(HideObjOps())
    session.send(VarpReset)

    sendPacketGroup(
        session,
        initialWorldGroup(
            appearance,
            localPlayerIndex,
            buildArea.localX(spawnX),
            buildArea.localY(spawnY),
        ),
    )

    session.send(UpdateInvFull(-1, 64209, 93))
    session.send(UpdateInvFull(-1, 64208, 94))

    for (message in initialFrameMessages()) session.send(message)

    repeat(25) { stat -> session.send(UpdateStat(stat, 1, 1, 0)) }
    session.send(UpdateRunWeight())
    session.send(UpdateRunEnergy())
    session.send(ResetAnims)
    session.send(MinimapToggle())
    for (message in loginNoticeMessages()) session.send(message)

    session.send(ServerTickEnd)
    logger.info { "game stage: sent capture-shaped initial cycle (world group + full neutral frame/state)" }
}

/** Sends one rev-239 atomic packet group with its exact on-wire member byte count. */
internal suspend fun sendPacketGroup(session: OutboundSession, messages: List<OutgoingMessage>) {
    val length = messages.sumOf(session::wireSize)
    require(length <= Short.MAX_VALUE) { "packet group too large: $length" }
    session.send(PacketGroupStart(length))
    for (message in messages) session.send(message)
}

/** Capture order: a center-out spiral over scene-local zone origins 24..72. */
private val INITIAL_ZONE_SPIRAL: List<Pair<Int, Int>> = listOf(
    48 to 48, 56 to 48, 56 to 56, 48 to 56, 40 to 56, 40 to 48, 40 to 40,
    48 to 40, 56 to 40, 64 to 40, 64 to 48, 64 to 56, 64 to 64, 56 to 64,
    48 to 64, 40 to 64, 32 to 64, 32 to 56, 32 to 48, 32 to 40, 32 to 32,
    40 to 32, 48 to 32, 56 to 32, 64 to 32, 72 to 32, 72 to 40, 72 to 48,
    72 to 56, 72 to 64, 72 to 72, 64 to 72, 56 to 72, 48 to 72, 40 to 72,
    32 to 72, 24 to 72, 24 to 64, 24 to 56, 24 to 48, 24 to 40, 24 to 32,
    24 to 24, 32 to 24, 40 to 24, 48 to 24, 56 to 24, 64 to 24, 72 to 24,
)
