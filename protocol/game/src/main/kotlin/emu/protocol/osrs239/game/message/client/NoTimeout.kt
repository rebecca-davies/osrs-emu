package emu.protocol.osrs239.game.message.client

import emu.transport.message.IncomingMessage

/** Zero-body client keepalive whose receipt refreshes connection liveness. */
data object NoTimeout : IncomingMessage
