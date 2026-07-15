package emu.persistence.postgres.character

/** Signals that accepted character saves could not finish before the shutdown deadline. */
class CharacterSaveShutdownException(
    val pendingCount: Int,
    cause: Throwable? = null,
) : IllegalStateException("$pendingCount accepted character saves remain pending", cause)
