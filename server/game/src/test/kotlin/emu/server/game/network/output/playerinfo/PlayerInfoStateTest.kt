package emu.server.game.network.output.playerinfo

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.chat.PublicChatInput
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.pathfinding.route.PathRoute
import emu.game.player.Player
import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.playerinfo.PlayerMovement
import emu.protocol.osrs239.game.message.playerinfo.PlayerSequence
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.PlayerOutput
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlayerInfoStateTest {
    @Test
    fun `nearby player is added with appearance`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)

        val info = world.session(observer).playerInfo.next(view(world))

        val addition = info.sections.lowResolutionActive.filterIsInstance<PlayerInfoBitCode.Add>().single()
        assertEquals(target.movement.position.x, addition.x)
        assertEquals("Player2", addition.update.appearance?.name)
    }

    @Test
    fun `tracked player movement is sent to the observer`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        world.session(observer).playerInfo.next(view(world))
        target.movement.queueRoute(
            PathRoute(listOf(Tile(3211, 3200)), alternative = false, success = true),
        )
        world.advanceMovement(target)

        val info = world.session(observer).playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        assertEquals(PlayerMovement.Walk(1, 0), update.movement)
    }

    @Test
    fun `public chat is visible to every nearby observer during the same information phase`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        world.session(observer).playerInfo.next(view(world))
        val chat = PlayerPublicChat(0, 0, 0, HUFFMAN.encode("hello"))
        target.publishPublicChat(PublicChatInput(0, 0, "hello"))

        val info = world.session(observer).playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        val published = requireNotNull(update.update?.publicChat)
        assertEquals(chat.colour, published.colour)
        assertEquals(chat.effect, published.effect)
        assertEquals(chat.modIcon, published.modIcon)
        assertContentEquals(chat.encodedText, published.encodedText)
    }

    @Test
    fun `animation is visible to every nearby observer during the same information phase`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        world.session(observer).playerInfo.next(view(world))
        target.playAnimation(id = 1234, delay = 2)

        val info = world.session(observer).playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        assertEquals(PlayerSequence(1234, 2), update.update?.sequence)
    }

    @Test
    fun `changed character appearance invalidates output and reaches existing observers`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        world.session(observer).playerInfo.next(view(world))
        val changed =
            CharacterAppearance(
                CharacterGender.FEMALE,
                CharacterBodyKits(hair = 55, jaw = 306, torso = 60, arms = 66, hands = 68, legs = 78, feet = 80),
                CharacterColors(hair = 29, torso = 28, legs = 27, feet = 5, skin = 13),
            )
        target.changeAppearance(changed)

        val info = world.session(observer).playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        assertEquals(PlayerAppearance.GENDER_FEMALE, update.update?.appearance?.gender)
        assertEquals(PlayerAppearance.identityKit(306), update.update?.appearance?.body?.equipment?.get(11))
    }

    @Test
    fun `detached tracked player is removed`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        world.session(observer).playerInfo.next(view(world))
        world.remove(target)

        val info = world.session(observer).playerInfo.next(view(world))

        assertEquals(1, info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.Remove>().size)
    }

    @Test
    fun `players at the same coordinates in different map instances cannot see each other`() {
        val (world, observer, target) = twoPlayers(targetX = 3_210)
        world.session(observer).playerInfo.next(view(world))
        target.teleportTo(target.movement.position, MapInstance.privateTo(target.id))

        val info = world.session(observer).playerInfo.next(view(world))

        assertEquals(1, removals(info).size)
    }

    @Test
    fun `player outside local view is not added`() {
        val (world, observer) = twoPlayers(targetX = 3216)

        val info = world.session(observer).playerInfo.next(view(world))

        assertFalse(info.sections.lowResolutionActive.any { it is PlayerInfoBitCode.Add })
    }

    @Test
    fun `viewport admits at most 249 remote players`() {
        val state = PlayerInfoState(localIndex = 1)

        val info = state.next(crowdedView(remotePlayers = 400, targetX = 3_200))

        assertEquals(249, additions(info).size)
    }

    @Test
    fun `crowd pressure contracts and later restores the player viewport`() {
        val state = PlayerInfoState(localIndex = 1)
        val view = crowdedView(remotePlayers = 249, targetX = 3_215)
        assertEquals(249, additions(state.next(view)).size)

        val contracted = state.next(view)

        assertEquals(249, removals(contracted).size)
        repeat(9) { assertEquals(0, additions(state.next(view)).size) }
        assertEquals(249, additions(state.next(view)).size)
    }

    @Test
    fun `local movement speed is cached and route tails use a temporary speed`() {
        val world = testWorld(maxPlayerIndex = 1)
        val observer = addPlayer(world, 1, 3200)
        world.activateTestPlayer(world.session(observer).token)
        val first = localUpdate(world.session(observer).playerInfo.next(view(world)))
        assertEquals(1, first.update?.moveSpeed)

        val unchanged = world.session(observer).playerInfo.next(view(world))
        assertNull(unchanged.sections.highResolutionActive.filterIsInstance<PlayerInfoBitCode.HighResolution>().singleOrNull())

        observer.movement.runEnabled = true
        observer.movement.queueRoute(
            PathRoute(listOf(Tile(3201, 3200)), alternative = false, success = true),
        )
        world.advanceMovement(observer)
        val routeTail = localUpdate(world.session(observer).playerInfo.next(view(world)))
        assertEquals(2, routeTail.update?.moveSpeed)
        assertEquals(1, routeTail.update?.temporaryMoveSpeed)
    }

    private fun twoPlayers(targetX: Int): Triple<World, Player, Player> {
        val world = testWorld(maxPlayerIndex = 2)
        val observer = addPlayer(world, 1, 3200)
        val target = addPlayer(world, 2, targetX)
        world.activateTestPlayer(world.session(observer).token)
        world.activateTestPlayer(world.session(target).token)
        return Triple(world, observer, target)
    }

    private fun addPlayer(world: World, id: Long, x: Int): Player =
        world.addTestPlayer(
            CharacterRecord(
                id,
                "Player$id",
                CharacterPosition(x, 3200, 0),
                0,
            ),
            IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
            GameOutputSink { true },
        )

    private fun view(world: World): PlayerInfoView =
        PlayerOutput(world, HUFFMAN, UiContentCatalog.load().gameframe).snapshot(world.allPlayers()).players

    private fun crowdedView(remotePlayers: Int, targetX: Int): PlayerInfoView =
        PlayerInfoView(
            buildList(remotePlayers + 1) {
                add(snapshot(index = 1, x = 3_200))
                repeat(remotePlayers) { add(snapshot(index = it + 2, x = targetX)) }
            },
        )

    private fun snapshot(index: Int, x: Int): PlayerInfoSnapshot =
        PlayerInfoSnapshot(
            index = index,
            position = Tile(x, 3_200),
            movement = MovementUpdate.Idle,
            runEnabled = false,
            appearance = PlayerAppearance(name = "Player$index"),
        )

    private fun additions(info: PlayerInfo): List<PlayerInfoBitCode.Add> =
        allCodes(info).filterIsInstance<PlayerInfoBitCode.Add>()

    private fun removals(info: PlayerInfo): List<PlayerInfoBitCode.Remove> =
        allCodes(info).filterIsInstance<PlayerInfoBitCode.Remove>()

    private fun allCodes(info: PlayerInfo): List<PlayerInfoBitCode> =
        info.sections.highResolutionActive +
            info.sections.highResolutionInactive +
            info.sections.lowResolutionInactive +
            info.sections.lowResolutionActive

    private fun localUpdate(info: PlayerInfo) =
        assertIs<PlayerInfoBitCode.HighResolution>(
            (info.sections.highResolutionActive + info.sections.highResolutionInactive)
                .filterIsInstance<PlayerInfoBitCode.HighResolution>()
                .single(),
        )

    private companion object {
        val HUFFMAN = HuffmanCodec(ByteArray(256) { 8 })
    }
}
