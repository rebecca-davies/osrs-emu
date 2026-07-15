package emu.server.host

import emu.persistence.postgres.character.CharacterSaveWriter
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresDatabase
import java.util.concurrent.atomic.AtomicBoolean

/** Closes asynchronous persistence writers before their shared database pool. */
internal class PersistenceLifecycle(
    private val characterSaves: CharacterSaveWriter,
    private val chatAudits: ChatAuditWriter,
    private val database: PostgresDatabase,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            chatAudits.close()
        } finally {
            try {
                characterSaves.close()
            } finally {
                database.close()
            }
        }
    }
}
