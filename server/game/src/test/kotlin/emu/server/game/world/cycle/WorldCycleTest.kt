package emu.server.game.world.cycle

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.PublicChatInput
import emu.game.map.Tile
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.pathfinding.movement.PlayerMovement
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.game.pathfinding.route.PathRoute
import emu.game.pathfinding.route.PlayerRouteFinder
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSegment
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.player.ConnectedPlayer
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.testWorld
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldCycleTest {
    @Test
    fun `maximum player route flood is bounded rotated and followed by output cleanup`() {
        val capacity = PlayerCapacity.PER_WORLD
        val world = testWorld(maxPlayerIndex = capacity)
        val movementProcess = PlayerMovementProcess(OpenCollisionMap)
        val owners = IdentityHashMap<PlayerMovement, Int>(capacity)
        val searchedPlayers = mutableListOf<Int>()
        val routeFinder =
            object : PlayerRouteFinder {
                override fun routeTo(
                    movement: PlayerMovement,
                    destination: Tile,
                    temporaryRun: Boolean?,
                ): PathRoute {
                    searchedPlayers += checkNotNull(owners[movement])
                    return movementProcess.routeTo(movement, destination, temporaryRun)
                }
            }
        val outputs = IntArray(capacity)
        val players =
            (1..capacity).map { id ->
                val ordinal = id - 1
                val x = 64 + ordinal % 128 * 64
                val y = 64 + ordinal / 128 * 64
                world.addTestPlayer(
                    player(id.toLong(), x, y),
                    actions(),
                    GameOutputSink { outputs[id - 1]++; true },
                ).also { connected ->
                    owners[connected.player.movement] = id
                    world.activateTestPlayer(connected.connection.token)
                    connected.connection.actions.submit(PlayerAction.Route(x + 1, y))
                }
            }
        val cycle =
            WorldCycle(
                world,
                WorldCommandQueue(capacity = 8),
                TestPlayerContent.actions(
                    routeFinder,
                    PlayerChatActionProcess(
                        HuffmanCodec(ByteArray(256) { 8 }),
                        ChatAuditSink { true },
                    ),
                    routeSearchesPerCycle = 32,
                ),
                TestPlayerContent.scripts(),
                TestPlayerContent.movementCycle(movementProcess),
                TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
                PlayerOutputProcess(),
            )

        cycle.tick(worldTick = 0)
        players.forEach { connected ->
            val position = connected.player.movement.position
            connected.connection.actions.submit(PlayerAction.Route(position.x + 1, position.y))
        }
        cycle.tick(worldTick = 1)

        assertEquals((1..32).toList() + (2..33).toList(), searchedPlayers)
        assertTrue(outputs.all { it == 2 })
        players.forEach { connected ->
            assertEquals(MovementUpdate.Idle, connected.player.movement.update)
            assertNull(connected.connection.pendingOutput)
            assertTrue(world.contains(connected.player.id))
        }
    }

    @Test
    fun `rotated route budget serves every player without starving later world phases`() {
        val world = testWorld(maxPlayerIndex = 3)
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val outputs = IntArray(3)
        val players =
            (1L..3L).map { id ->
                world.addTestPlayer(
                    player(id, 3_200 + id.toInt() * 2),
                    actions(),
                    GameOutputSink { outputs[id.toInt() - 1]++; true },
                ).also { connected ->
                    world.activateTestPlayer(connected.connection.token)
                    connected.connection.actions.submit(
                        PlayerAction.Route(connected.player.movement.position.x + 1, 3_200),
                    )
                }
            }
        val cycle =
            WorldCycle(
                world,
                WorldCommandQueue(capacity = 8),
                TestPlayerContent.actions(
                    movement,
                    PlayerChatActionProcess(
                        HuffmanCodec(ByteArray(256) { 8 }),
                        ChatAuditSink { true },
                    ),
                    routeSearchesPerCycle = 1,
                ),
                TestPlayerContent.scripts(),
                TestPlayerContent.movementCycle(movement),
                TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
                PlayerOutputProcess(),
            )

        repeat(3) { cycle.tick(it.toLong()) }

        players.forEachIndexed { index, connected ->
            assertEquals(3_203 + index * 2, connected.player.movement.position.x)
        }
        assertTrue(outputs.all { it == 3 })
    }

    @Test
    fun `nearby players are present in each others player-info output`() {
        val world = testWorld(maxPlayerIndex = 2)
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val cycle = cycle(world, movement)
        val firstOutput = mutableListOf<GameOutputBatch>()
        val secondOutput = mutableListOf<GameOutputBatch>()
        val first = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink { firstOutput += it; true })
        val second = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { secondOutput += it; true })
        world.activateTestPlayer(first.connection.token)
        world.activateTestPlayer(second.connection.token)

        cycle.tick(worldTick = 0)

        assertEquals(second.player.movement.position.x, addedPlayerX(firstOutput.single()))
        assertEquals(first.player.movement.position.x, addedPlayerX(secondOutput.single()))
    }

    @Test
    fun `every player mutates before any player publishes output`() {
        val world = testWorld(maxPlayerIndex = 2)
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val cycle =
            WorldCycle(
                world = world,
                commands = WorldCommandQueue(capacity = 8),
                actions =
                    TestPlayerContent.actions(
                        movement,
                        PlayerChatActionProcess(
                            HuffmanCodec(ByteArray(256) { 8 }),
                            ChatAuditSink { true },
                        ),
                    ),
                scripts = TestPlayerContent.scripts(),
                movement = TestPlayerContent.movementCycle(movement),
                lifecycle =
                    TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
                output = PlayerOutputProcess(),
            )
        lateinit var first: ConnectedPlayer
        lateinit var second: ConnectedPlayer
        val observed = mutableListOf<Pair<Int, Int>>()
        first = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink {
            observed += first.player.movement.position.x to second.player.movement.position.x
            true
        })
        second = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { true })
        world.activateTestPlayer(first.connection.token)
        world.activateTestPlayer(second.connection.token)
        first.connection.actions.submit(PlayerAction.Route(3201, 3200))
        second.connection.actions.submit(PlayerAction.Route(3211, 3200))

        cycle.tick(worldTick = 0)

        assertEquals(listOf(3201 to 3211), observed)
    }

    @Test
    fun `one player action failure does not abort the remaining player phase`() {
        val world = testWorld(maxPlayerIndex = 2)
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val cycle =
            WorldCycle(
                world = world,
                commands = WorldCommandQueue(capacity = 8),
                actions =
                    TestPlayerContent.actions(
                        movement,
                        PlayerChatActionProcess(
                            HuffmanCodec(ByteArray(256) { 8 }),
                            ChatAuditSink { message ->
                                if (message.playerId == 1L) error("broken player content")
                                true
                            },
                        ),
                    ),
                scripts = TestPlayerContent.scripts(),
                movement = TestPlayerContent.movementCycle(movement),
                lifecycle =
                    TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
                output = PlayerOutputProcess(),
            )
        val broken = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink { true })
        val healthy = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { true })
        world.activateTestPlayer(broken.connection.token)
        world.activateTestPlayer(healthy.connection.token)
        broken.connection.actions.submit(PlayerAction.Chat(PublicChatInput(0, 0, "hello")))
        healthy.connection.actions.submit(PlayerAction.Route(3211, 3200))

        cycle.tick(worldTick = 0)

        assertFalse(world.contains(broken.player.id))
        assertTrue(world.contains(healthy.player.id))
        assertEquals(3211, healthy.player.movement.position.x)
    }

    private fun actions() = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig())

    private fun cycle(world: World, movement: PlayerMovementProcess) =
        WorldCycle(
            world = world,
            commands = WorldCommandQueue(capacity = 8),
            actions =
                TestPlayerContent.actions(
                    movement,
                    PlayerChatActionProcess(
                        HuffmanCodec(ByteArray(256) { 8 }),
                        ChatAuditSink { true },
                    ),
                ),
            scripts = TestPlayerContent.scripts(),
            movement = TestPlayerContent.movementCycle(movement),
            lifecycle = TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
            output = PlayerOutputProcess(),
        )

    private fun addedPlayerX(batch: GameOutputBatch): Int {
        val info =
            batch.segments
                .filterIsInstance<GameOutputSegment.PacketGroup>()
                .flatMap(GameOutputSegment.PacketGroup::messages)
                .filterIsInstance<PlayerInfo>()
                .single()
        return info.sections.lowResolutionActive.filterIsInstance<PlayerInfoBitCode.Add>().single().x
    }

    private fun player(id: Long, x: Int, y: Int = 3200) =
        CharacterRecord(
            id = id,
            displayName = "Player$id",
            position = CharacterPosition(x, y, 0),
            playTimeSeconds = 0,
        )
}
