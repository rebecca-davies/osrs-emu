package emu.server.game.world.entry

import emu.compression.HuffmanCodec
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.server.game.TestPlayerContent
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
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class WorldEntryTest {
    @Test
    fun `cancelling cleanup still releases the session permit`() = runBlocking {
        val releaseStarted = CompletableDeferred<Unit>()
        val finishRelease = CompletableDeferred<Unit>()
        val reservations =
            object : WorldReservations {
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
        val first = assertIs<ReservationDecision.Accepted>(entry.reserve(ACCOUNT_ID))
        val cancellation = launch { entry.cancel(first.token) }
        releaseStarted.await()

        cancellation.cancel()
        finishRelease.complete(Unit)
        cancellation.join()

        val retried = entry.reserve(ACCOUNT_ID)

        assertIs<ReservationDecision.Accepted>(retried)
        entry.cancel(retried.token)
    }

    @Test
    fun `cancelled reservation releases its queued world slot and session permit`() = runBlocking {
        val commands = WorldCommandQueue(capacity = 8)
        val runtime = WorldRuntime(cycle(testWorld(maxPlayerIndex = 1), commands), 1.milliseconds)
        val entry = WorldEntry(commands, capacity = 1) { PLAYER }
        val cancelled = launch { entry.reserve(ACCOUNT_ID) }
        yield()
        cancelled.cancelAndJoin()

        val worldJob = launch { runtime.run() }
        val retried = entry.reserve(ACCOUNT_ID)

        assertIs<ReservationDecision.Accepted>(retried)
        entry.cancel(retried.token)
        worldJob.cancelAndJoin()
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

    private companion object {
        val ACCOUNT_ID = AccountId(1)
        val PLAYER = CharacterRecord(1, "Test_Player", CharacterPosition(3222, 3218, 0), 0)
    }
}
