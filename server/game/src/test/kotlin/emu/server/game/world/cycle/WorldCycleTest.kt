package emu.server.game.world.cycle

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.PublicChatInput
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.NpcList
import emu.game.npc.NpcMovementUpdate
import emu.game.npc.NpcType
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.player.Player
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoBitCode
import emu.protocol.osrs239.game.message.playerinfo.PlayerMovement
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSegment
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldCycleTest {
    @Test
    fun `paused NPCs stay still and resumed NPCs publish one collision-valid step`() {
        val npcs = NpcList()
        val world = testWorld(maxPlayerIndex = 1, npcs = npcs)
        val outputs = mutableListOf<GameOutputBatch>()
        val player =
            world.addTestPlayer(
                player(1, 100, 100),
                actions(),
                GameOutputSink { output -> outputs += output; true },
            )
        world.activateTestPlayer(world.session(player).token)
        val npc =
            requireNotNull(
                npcs.add(
                    NpcType(1, "Jal-Nib"),
                    Tile(95, 100),
                    MapInstance.SHARED,
                    targetPlayerId = player.id,
                    paused = true,
                ),
            )
        val cycle = TestPlayerContent.cycle(world)

        cycle.tick(worldTick = 0)

        assertEquals(Tile(95, 100), npc.position)
        assertEquals(1, npcInfo(outputs.single()).additions.size)
        npcs.pause(MapInstance.SHARED, paused = false)

        cycle.tick(worldTick = 1)

        assertEquals(Tile(96, 100), npc.position)
        assertEquals(listOf(NpcInfoLocal.Walk(4)), npcInfo(outputs.last()).locals)
        assertEquals(NpcMovementUpdate.Idle, npc.movementUpdate)
    }

    @Test
    fun `login content teleport prepares its destination before first output`() {
        val requested = mutableListOf<Tile>()
        val map = GameMap(OpenCollisionMap, requestAreas = { tile -> requested += tile; true })
        val world = testWorld(maxPlayerIndex = 1, gameMap = map)
        val outputs = mutableListOf<GameOutputBatch>()
        val player =
            world.addTestPlayer(
                player(1, 3_222, 3_218),
                actions(),
                GameOutputSink { output -> outputs += output; true },
            )
        world.requestActivation(world.session(player).token)

        TestPlayerContent.cycle(world).tick(worldTick = 0)

        val hub = InfernoFreeModeCatalog.load().clanWarsArrival
        assertEquals(hub, player.movement.position)
        assertEquals(listOf(hub), requested)
        val info = playerInfo(outputs.single())
        val local = info.sections.highResolutionActive.single() as PlayerInfoBitCode.HighResolution
        assertEquals(PlayerMovement.Teleport(-95, 403, 0), local.movement)
    }

    @Test
    fun `every player route is applied before output at world capacity`() {
        val capacity = PlayerCapacity.PER_WORLD
        val world = testWorld(maxPlayerIndex = capacity)
        val outputs = IntArray(capacity)
        val startingX = IntArray(capacity)
        val players =
            (1..capacity).map { id ->
                val ordinal = id - 1
                val x = 64 + ordinal % 128 * 64
                val y = 64 + ordinal / 128 * 64
                startingX[ordinal] = x
                world.addTestPlayer(
                    player(id.toLong(), x, y),
                    actions(),
                    GameOutputSink { outputs[ordinal]++; true },
                ).also { player ->
                    world.activateTestPlayer(world.session(player).token)
                    world.session(player).actions.submit(PlayerAction.Route(x + 1, y))
                }
            }
        val cycle = TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))

        cycle.tick(worldTick = 0)
        players.forEach { player ->
            val position = player.movement.position
            world.session(player).actions.submit(PlayerAction.Route(position.x + 1, position.y))
        }
        cycle.tick(worldTick = 1)

        assertTrue(outputs.all { it == 2 })
        players.forEachIndexed { index, player ->
            assertEquals(startingX[index] + 2, player.movement.position.x)
            assertEquals(MovementUpdate.Idle, player.movement.update)
            assertNull(world.session(player).pendingOutput)
            assertTrue(world.contains(player.id))
        }
    }

    @Test
    fun `all pending routes are consumed in one global client input phase`() {
        val world = testWorld(maxPlayerIndex = 3)
        val outputs = IntArray(3)
        val players =
            (1L..3L).map { id ->
                world.addTestPlayer(
                    player(id, 3_200 + id.toInt() * 2),
                    actions(),
                    GameOutputSink { outputs[id.toInt() - 1]++; true },
                ).also { player ->
                    world.activateTestPlayer(world.session(player).token)
                    world.session(player).actions.submit(
                        PlayerAction.Route(player.movement.position.x + 1, 3_200),
                    )
                }
            }
        val cycle = TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))

        cycle.tick(worldTick = 0)

        players.forEachIndexed { index, player ->
            assertEquals(3_203 + index * 2, player.movement.position.x)
        }
        assertTrue(outputs.all { it == 1 })
    }

    @Test
    fun `nearby players are present in each others player-info output`() {
        val world = testWorld(maxPlayerIndex = 2)
        val cycle = TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))
        val firstOutput = mutableListOf<GameOutputBatch>()
        val secondOutput = mutableListOf<GameOutputBatch>()
        val first = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink { firstOutput += it; true })
        val second = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { secondOutput += it; true })
        world.activateTestPlayer(world.session(first).token)
        world.activateTestPlayer(world.session(second).token)

        cycle.tick(worldTick = 0)

        assertEquals(second.movement.position.x, addedPlayerX(firstOutput.single()))
        assertEquals(first.movement.position.x, addedPlayerX(secondOutput.single()))
    }

    @Test
    fun `every player mutates before any player publishes output`() {
        val world = testWorld(maxPlayerIndex = 2)
        val cycle = TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))
        lateinit var first: Player
        lateinit var second: Player
        val observed = mutableListOf<Pair<Int, Int>>()
        first = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink {
            observed += first.movement.position.x to second.movement.position.x
            true
        })
        second = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { true })
        world.activateTestPlayer(world.session(first).token)
        world.activateTestPlayer(world.session(second).token)
        world.session(first).actions.submit(PlayerAction.Route(3201, 3200))
        world.session(second).actions.submit(PlayerAction.Route(3211, 3200))

        cycle.tick(worldTick = 0)

        assertEquals(listOf(3201 to 3211), observed)
    }

    @Test
    fun `one player action failure does not abort the remaining player phase`() {
        val world = testWorld(maxPlayerIndex = 2)
        val cycle =
            TestPlayerContent.cycle(
                world,
                WorldCommandQueue(capacity = 8),
                audit =
                    ChatAuditSink { message ->
                        if (message.playerId == 1L) error("broken player content")
                        true
                    },
            )
        val broken = world.addTestPlayer(player(1, 3200), actions(), GameOutputSink { true })
        val healthy = world.addTestPlayer(player(2, 3210), actions(), GameOutputSink { true })
        world.activateTestPlayer(world.session(broken).token)
        world.activateTestPlayer(world.session(healthy).token)
        world.session(broken).actions.submit(PlayerAction.Chat(PublicChatInput(0, 0, "hello")))
        world.session(healthy).actions.submit(PlayerAction.Route(3211, 3200))

        cycle.tick(worldTick = 0)

        assertFalse(world.contains(broken.id))
        assertTrue(world.contains(healthy.id))
        assertEquals(3211, healthy.movement.position.x)
    }

    private fun actions() = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig())

    private fun addedPlayerX(batch: GameOutputBatch): Int {
        val info = playerInfo(batch)
        return info.sections.lowResolutionActive.filterIsInstance<PlayerInfoBitCode.Add>().single().x
    }

    private fun playerInfo(batch: GameOutputBatch): PlayerInfo =
        batch.segments
            .filterIsInstance<GameOutputSegment.PacketGroup>()
            .flatMap(GameOutputSegment.PacketGroup::messages)
            .filterIsInstance<PlayerInfo>()
            .single()

    private fun npcInfo(batch: GameOutputBatch): NpcInfo =
        batch.segments
            .filterIsInstance<GameOutputSegment.PacketGroup>()
            .flatMap(GameOutputSegment.PacketGroup::messages)
            .filterIsInstance<NpcInfo>()
            .single()

    private fun player(id: Long, x: Int, y: Int = 3200) =
        CharacterRecord(
            id = id,
            displayName = "Player$id",
            position = CharacterPosition(x, y, 0),
            playTimeSeconds = 0,
        )
}
