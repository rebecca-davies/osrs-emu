package emu.server.world.entry

import emu.compression.HuffmanCodec
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovementProcess
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.DurableCharacterWrite
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.chat.ChatAuditSink
import emu.server.world.player.PlayerActionProcess
import emu.server.world.TestPlayerContent
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerOutputProcess
import emu.server.world.runtime.GameWorld
import emu.server.world.runtime.testGameWorld
import emu.server.world.runtime.WorldCommandQueue
import emu.server.world.cycle.WorldCycle
import emu.server.world.runtime.WorldRuntime
import emu.server.world.runtime.WorldReservationService
import emu.server.session.AccountId
import emu.server.session.AccountPrivilege
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class WorldEntryTest {
    @Test
    fun `cancelling cleanup still releases the session permit`() = runBlocking {
        val releaseStarted = CompletableDeferred<Unit>()
        val finishRelease = CompletableDeferred<Unit>()
        val reservations =
            object : WorldReservationService {
                override suspend fun reserve(
                    playerId: Long,
                    token: GameSessionToken,
                ): ReservationDecision = ReservationDecision.Accepted(token, playerIndex = 1)

                override suspend fun release(token: GameSessionToken) {
                    releaseStarted.complete(Unit)
                    finishRelease.await()
                }
            }
        val entry = WorldEntry(reservations, capacity = 1) { PLAYER }
        val first = assertIs<ReservationDecision.Accepted>(entry.prepare(ACCOUNT_ID))
        val cancellation = launch { entry.cancel(first.token) }
        releaseStarted.await()

        cancellation.cancel()
        finishRelease.complete(Unit)
        cancellation.join()

        val retried = entry.prepare(ACCOUNT_ID)

        assertIs<ReservationDecision.Accepted>(retried)
        entry.cancel(retried.token)
    }

    @Test
    fun `cancelled reservation releases its queued world slot and session permit`() = runBlocking {
        val commands = WorldCommandQueue(capacity = 8)
        val runtime = WorldRuntime(cycle(testGameWorld(maxPlayerIndex = 1), commands), 1.milliseconds)
        val entry = WorldEntry(commands, capacity = 1) { PLAYER }
        val cancelled = launch { entry.prepare(ACCOUNT_ID) }
        yield()
        cancelled.cancelAndJoin()

        val worldJob = launch { runtime.run() }
        val retried = entry.prepare(ACCOUNT_ID)

        assertIs<ReservationDecision.Accepted>(retried)
        entry.cancel(retried.token)
        worldJob.cancelAndJoin()
    }

    private fun cycle(world: GameWorld, commands: WorldCommandQueue): WorldCycle {
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
            TestPlayerContent.scripts(),
            TestPlayerContent.movementCycle(movement),
            TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
            PlayerOutputProcess(),
        )
    }

    private companion object {
        val ACCOUNT_ID = AccountId(1)
        val PLAYER = PlayerRecord(1, "Test_Player", PlayerPosition(3222, 3218, 0), 0)
    }
}
