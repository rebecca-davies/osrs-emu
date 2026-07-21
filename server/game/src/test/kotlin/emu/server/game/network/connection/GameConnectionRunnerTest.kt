package emu.server.game.network.connection

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.server.game.GameServerDispatchers
import emu.server.game.TestPlayerContent
import emu.server.game.config.GameConnectionConfig
import emu.server.game.network.input.GameInboundReader
import emu.server.game.network.wire.TEST_PLAYER
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.runtime.lifecycle.WorldRuntime
import emu.server.game.world.World
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.testWorld
import emu.server.session.account.AccountId
import emu.server.session.account.AccountPrivilege
import emu.server.session.account.AuthenticatedAccount
import emu.server.session.authentication.IsaacBootstrap
import emu.server.session.handoff.ConnectionHandoff
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val OTHER_PLAYER_SLOTS = 2046
private const val GPI_INIT_BITS = 30 + OTHER_PLAYER_SLOTS * 18
private const val GPI_INIT_BYTES = (GPI_INIT_BITS + 7) / 8
private const val ZONE_BYTES = 6
private const val REBUILD_NORMAL_BODY_SIZE = GPI_INIT_BYTES + ZONE_BYTES

class GameConnectionRunnerTest {
    @Test
    fun `cancellation before connection execution releases the reserved world slot`() = runBlocking {
        val commands = WorldCommandQueue(capacity = 8)
        val world = testWorld(maxPlayerIndex = 1)
        val token = GameSessionToken("cancel-before-run")
        val reserved = async(start = CoroutineStart.UNDISPATCHED) { commands.reserve(TEST_PLAYER.id, token) }
        commands.drain(world)
        val accepted = assertIs<ReservationDecision.Accepted>(reserved.await())

        GameServerDispatchers(connectionWorkerThreads = 1, entryWorkerThreads = 1).use { dispatchers ->
            val workerStarted = CountDownLatch(1)
            val releaseWorker = CountDownLatch(1)
            val blocker =
                async(dispatchers.connections) {
                    workerStarted.countDown()
                    releaseWorker.await()
                }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
            val codecs = buildGameCodecRepository()
            val runner =
                GameConnectionRunner(
                    codecs,
                    GameConnectionConfig(idleTimeout = 2.seconds),
                    commands,
                    GameInboundReader(codecs, HuffmanCodec(ByteArray(256) { 8 }), 2.seconds),
                    dispatchers.connections,
                )
            val gameJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    runner.run(
                        ByteChannel(),
                        ByteChannel(),
                        TEST_PLAYER,
                        handoff(token, IntArray(4), accepted),
                        completeLogin = { true },
                    )
                }

            gameJob.cancel()
            releaseWorker.countDown()
            gameJob.join()
            blocker.await()
        }

        commands.drain(world)
        val replacementToken = GameSessionToken("replacement")
        val replacement =
            async(start = CoroutineStart.UNDISPATCHED) {
                commands.reserve(TEST_PLAYER.id, replacementToken)
            }
        commands.drain(world)

        assertEquals(ReservationDecision.Accepted(replacementToken, 1), replacement.await())
    }

    @Test
    fun `connection attaches persisted character to the shared world before initial output`() = runBlocking {
        val seeds = intArrayOf(11, 22, 33, 44)
        val input = ByteChannel()
        val output = ByteChannel(autoFlush = true)
        val commands = WorldCommandQueue(capacity = 16)
        val world = testWorld()
        val cycle = cycle(world, commands)
        val runtime = WorldRuntime(cycle, tickInterval = 1.milliseconds)
        val worldJob = launch { runtime.run() }
        val token = GameSessionToken("connection-test")
        val reservation = async { commands.reserve(TEST_PLAYER.id, token) }
        val accepted = assertIs<ReservationDecision.Accepted>(reservation.await())
        assertEquals(1, accepted.playerIndex)
        val codecs = buildGameCodecRepository()
        val runner =
            GameConnectionRunner(
                codecs,
                GameConnectionConfig(idleTimeout = 2.seconds),
                commands,
                GameInboundReader(codecs, HuffmanCodec(ByteArray(256) { 8 }), 2.seconds),
                Dispatchers.Default,
            )
        val gameJob =
            launch {
                runner.run(
                    input,
                    output,
                    TEST_PLAYER,
                    handoff(token, seeds, accepted),
                    completeLogin = { true },
                )
            }

        val oracle = IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 })
        assertEquals(
            (GameServerProt.REBUILD_NORMAL.opcode + oracle.nextInt()) and 0xFF,
            output.readByte().toInt() and 0xFF,
        )
        assertEquals(REBUILD_NORMAL_BODY_SIZE, readU16(output))
        val rebuild = ByteArray(REBUILD_NORMAL_BODY_SIZE).also { output.readFully(it) }
        val (plane, x, y) = decodePacked30(rebuild)
        assertEquals(TEST_PLAYER.position.plane, plane)
        assertEquals(TEST_PLAYER.position.x, x)
        assertEquals(TEST_PLAYER.position.y, y)

        input.close()
        gameJob.join()
        worldJob.cancelAndJoin()
        output.close()
    }

    private fun cycle(world: World, commands: WorldCommandQueue): WorldCycle {
        val movement = PlayerMovementProcess(OpenCollisionMap)
        return WorldCycle(
            world,
            commands,
            TestPlayerContent.actions(
                movement,
                PlayerChatActionProcess(
                    HuffmanCodec(ByteArray(256) { 8 }),
                    ChatAuditSink { true },
                ),
            ),
            TestPlayerContent.main(movement),
            TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
            PlayerOutputProcess(),
        )
    }

    private fun handoff(
        token: GameSessionToken,
        seeds: IntArray,
        reservation: ReservationDecision.Accepted,
    ): ConnectionHandoff {
        check(token == reservation.token)
        return ConnectionHandoff(
            AuthenticatedAccount(AccountId(TEST_PLAYER.id), AccountPrivilege.PLAYER),
            IsaacBootstrap.fromSeeds(seeds),
            reservation,
        )
    }

    private suspend fun readU16(channel: ByteReadChannel): Int {
        val high = channel.readByte().toInt() and 0xFF
        val low = channel.readByte().toInt() and 0xFF
        return (high shl 8) or low
    }

    private fun decodePacked30(body: ByteArray): Triple<Int, Int, Int> {
        var accumulator = 0L
        for (index in 0 until 4) {
            accumulator = (accumulator shl 8) or (body[index].toLong() and 0xFF)
        }
        val packed = (accumulator ushr 2).toInt()
        return Triple(
            (packed ushr 28) and 0x3,
            (packed ushr 14) and 0x3FFF,
            packed and 0x3FFF,
        )
    }
}
