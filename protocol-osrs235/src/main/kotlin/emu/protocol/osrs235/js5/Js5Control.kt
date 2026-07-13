package emu.protocol.osrs235.js5

import emu.netcore.message.IncomingMessage

// A JS5 client->server control message. After the handshake the client interleaves 4-byte control
// frames with group requests: opcode 2 (logged in) / 3 (logged out) update the client's login
// state, opcode 4 sets the response XOR key, opcode 6/7 are connection init/keepalive. Each carries
// a fixed 3-byte payload and expects no response. The gateway consumes and ignores them so the JS5
// stream stays framed; without this the pipeline treats opcode 3 as "unknown" and drops the socket,
// which the client reports as error_game_js5io.
data class Js5Control(val opcode: Int, val b0: Int, val b1: Int, val b2: Int) : IncomingMessage
