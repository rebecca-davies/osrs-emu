package emu.server.game.network

import emu.netcore.message.OutgoingMessage

/** A plain packet run or a rev-239 atomic packet group within a batch. */
internal sealed interface GameOutputSegment {
    data class Packets(val messages: List<OutgoingMessage>) : GameOutputSegment

    data class PacketGroup(val messages: List<OutgoingMessage>) : GameOutputSegment
}
