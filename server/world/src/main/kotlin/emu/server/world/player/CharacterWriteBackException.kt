package emu.server.world.player

/** Fatal loss of durable write-back for an attached player. */
class CharacterWriteBackException(playerId: Long) :
    IllegalStateException("character write-back failed permanently for player $playerId")
