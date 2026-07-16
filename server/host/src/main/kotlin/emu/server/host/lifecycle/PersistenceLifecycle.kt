package emu.server.host.lifecycle

import emu.persistence.postgres.character.writeback.CharacterSaveWriter
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresDatabase
import java.util.concurrent.atomic.AtomicBoolean

/** Closes asynchronous writers before the isolated world and account database pools. */
internal class PersistenceLifecycle(
    private val characterSaves: CharacterSaveWriter,
    private val chatAudits: ChatAuditWriter,
    private val worldDatabase: PostgresDatabase,
    private val accountDatabase: PostgresDatabase,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        fun closeResource(close: () -> Unit) {
            try {
                close()
            } catch (closeFailure: Throwable) {
                val first = failure
                if (first == null) failure = closeFailure else first.addSuppressed(closeFailure)
            }
        }
        closeResource(chatAudits::close)
        closeResource(characterSaves::close)
        closeResource(worldDatabase::close)
        closeResource(accountDatabase::close)
        failure?.let { throw it }
    }
}
