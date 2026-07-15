package emu.persistence.postgres.chat

/** Signals that accepted audit entries could not be proven durable before the shutdown deadline. */
class ChatAuditShutdownException(
    val undeliveredCount: Int,
    cause: Throwable? = null,
) : IllegalStateException("$undeliveredCount accepted chat audit entries remain undelivered", cause)
