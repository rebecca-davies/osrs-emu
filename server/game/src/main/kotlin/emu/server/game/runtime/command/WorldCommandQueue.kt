package emu.server.game.runtime.command

import emu.game.action.IncomingPlayerActionQueue
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.World
import emu.server.game.world.entry.WorldAttachment
import emu.server.game.world.entry.WorldReservations
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

/** Bounded cross-thread queue whose commands are applied only at the global world boundary. */
class WorldCommandQueue(private val config: WorldCommandQueueConfig) : WorldReservations {
    private val commands = Channel<Command>(config.capacity)
    private val stopping = AtomicBoolean(false)

    constructor(capacity: Int) : this(WorldCommandQueueConfig(capacity, capacity))

    override suspend fun reserve(playerId: Long, token: GameSessionToken): ReservationDecision {
        val result = CompletableDeferred<ReservationDecision>()
        if (!send(Command.Reserve(playerId, token, result))) {
            result.complete(ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE))
        }
        return result.await()
    }

    override suspend fun release(token: GameSessionToken) {
        send(Command.Release(token))
    }

    internal suspend fun attach(
        token: GameSessionToken,
        record: CharacterRecord,
        privilege: AccountPrivilege,
        actions: IncomingPlayerActionQueue,
        output: GameOutputSink,
    ): WorldAttachment {
        val attachment = WorldAttachment()
        val command = Command.Attach(token, record, privilege, actions, output, attachment)
        if (!send(command)) attachment.reject()
        return attachment
    }

    internal suspend fun activate(token: GameSessionToken) {
        send(Command.Activate(token))
    }

    internal suspend fun disconnect(token: GameSessionToken) {
        send(Command.Disconnect(token))
    }

    internal fun drain(world: World) {
        repeat(config.maxPerCycle) {
            if (!applyNext(world)) return
        }
    }

    internal fun close(world: World) {
        if (!stopping.compareAndSet(false, true)) return
        commands.close()
        while (rejectOrCleanNext(world)) {
            // Cleanup is unbounded by the ordinary cycle budget once shutdown starts.
        }
        world.rejectPendingLogins()
    }

    private fun rejectOrCleanNext(world: World): Boolean {
        val command = commands.tryReceive().getOrNull() ?: return false
        when (command) {
            is Command.Reserve ->
                command.result.complete(
                    ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE),
                )
            is Command.Attach -> command.attachment.reject()
            is Command.Release -> world.release(command.token)
            is Command.Disconnect -> world.disconnect(command.token)
            is Command.Activate -> Unit
        }
        return true
    }

    private fun applyNext(world: World): Boolean {
        val command = commands.tryReceive().getOrNull() ?: return false
        when (command) {
            is Command.Reserve -> command.result.complete(world.reserve(command.playerId, command.token))
            is Command.Release -> world.release(command.token)
            is Command.Attach ->
                world.stageLogin(
                    command.token,
                    command.record,
                    command.privilege,
                    command.actions,
                    command.output,
                    command.attachment,
                )
            is Command.Activate -> world.requestActivation(command.token)
            is Command.Disconnect -> world.disconnect(command.token)
        }
        return true
    }

    private suspend fun send(command: Command): Boolean =
        if (stopping.get()) {
            false
        } else {
            try {
                commands.send(command)
                true
            } catch (_: ClosedSendChannelException) {
                false
            }
        }

    private sealed interface Command {
        data class Reserve(
            val playerId: Long,
            val token: GameSessionToken,
            val result: CompletableDeferred<ReservationDecision>,
        ) : Command

        data class Release(val token: GameSessionToken) : Command

        data class Attach(
            val token: GameSessionToken,
            val record: CharacterRecord,
            val privilege: AccountPrivilege,
            val actions: IncomingPlayerActionQueue,
            val output: GameOutputSink,
            val attachment: WorldAttachment,
        ) : Command

        data class Activate(val token: GameSessionToken) : Command

        data class Disconnect(val token: GameSessionToken) : Command
    }
}
