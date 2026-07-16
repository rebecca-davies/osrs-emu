package emu.server.game.network.output.playerinfo

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.map.Tile
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.route.PathRoute
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.playerinfo.PlayerMovement
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.player.ConnectedPlayer
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlayerInfoStateTest {
    @Test
    fun `nearby player is added with appearance`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)

        val info = observer.connection.playerInfo.next(view(world))

        val addition = info.sections.lowResolutionActive.filterIsInstance<PlayerInfoBitCode.Add>().single()
        assertEquals(target.player.movement.position.x, addition.x)
        assertEquals("Player2", addition.update.appearance?.name)
    }

    @Test
    fun `tracked player movement is sent to the observer`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        observer.connection.playerInfo.next(view(world))
        target.player.movement.queueRoute(
            PathRoute(listOf(Tile(3211, 3200)), alternative = false, success = true),
        )
        target.player.movement.process(OpenCollisionMap)

        val info = observer.connection.playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        assertEquals(PlayerMovement.Walk(1, 0), update.movement)
    }

    @Test
    fun `public chat is visible to every nearby observer during the same information phase`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        observer.connection.playerInfo.next(view(world))
        val chat = PlayerPublicChat(0, 0, 255, byteArrayOf(1, 2, 3))
        target.connection.publicChat.publish(chat)

        val info = observer.connection.playerInfo.next(view(world))

        val update = info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.HighResolution>().single()
        assertEquals(chat, update.update?.publicChat)
    }

    @Test
    fun `detached tracked player is removed`() {
        val (world, observer, target) = twoPlayers(targetX = 3210)
        observer.connection.playerInfo.next(view(world))
        world.remove(target)

        val info = observer.connection.playerInfo.next(view(world))

        assertEquals(1, info.sections.highResolutionInactive.filterIsInstance<PlayerInfoBitCode.Remove>().size)
    }

    @Test
    fun `player outside local view is not added`() {
        val (world, observer) = twoPlayers(targetX = 3216)

        val info = observer.connection.playerInfo.next(view(world))

        assertFalse(info.sections.lowResolutionActive.any { it is PlayerInfoBitCode.Add })
    }

    @Test
    fun `local movement speed is cached and route tails use a temporary speed`() {
        val world = testWorld(maxPlayerIndex = 1)
        val observer = addPlayer(world, 1, 3200)
        world.activateTestPlayer(observer.connection.token)
        val first = localUpdate(observer.connection.playerInfo.next(view(world)))
        assertEquals(1, first.update?.moveSpeed)

        val unchanged = observer.connection.playerInfo.next(view(world))
        assertNull(unchanged.sections.highResolutionActive.filterIsInstance<PlayerInfoBitCode.HighResolution>().singleOrNull())

        observer.player.movement.runEnabled = true
        observer.player.movement.queueRoute(
            PathRoute(listOf(Tile(3201, 3200)), alternative = false, success = true),
        )
        observer.player.movement.process(OpenCollisionMap)
        val routeTail = localUpdate(observer.connection.playerInfo.next(view(world)))
        assertEquals(2, routeTail.update?.moveSpeed)
        assertEquals(1, routeTail.update?.temporaryMoveSpeed)
    }

    private fun twoPlayers(targetX: Int): Triple<World, ConnectedPlayer, ConnectedPlayer> {
        val world = testWorld(maxPlayerIndex = 2)
        val observer = addPlayer(world, 1, 3200)
        val target = addPlayer(world, 2, targetX)
        world.activateTestPlayer(observer.connection.token)
        world.activateTestPlayer(target.connection.token)
        return Triple(world, observer, target)
    }

    private fun addPlayer(world: World, id: Long, x: Int): ConnectedPlayer =
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
        PlayerOutputProcess().snapshot(world.allPlayers())

    private fun localUpdate(info: PlayerInfo) =
        assertIs<PlayerInfoBitCode.HighResolution>(
            (info.sections.highResolutionActive + info.sections.highResolutionInactive)
                .filterIsInstance<PlayerInfoBitCode.HighResolution>()
                .single(),
        )
}
