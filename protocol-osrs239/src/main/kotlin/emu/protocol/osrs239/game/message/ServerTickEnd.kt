package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * SERVER_TICK_END (opcode 83) — the empty, fixed-size marker the server sends at the **end of every
 * game tick**, after that tick's PLAYER_INFO / zone / inventory / stat updates. It carries no body;
 * its opcode alone tells the client "this server cycle's updates are complete, apply them".
 *
 * rsprot models it as a `NoOpMessageEncoder` (`ServerTickEndEncoder`) and rsmod writes it once per
 * player per cycle at the tail of its post-tick flush (`MiscOutput.serverTickEnd` in
 * `PlayerPostTickProcess.flushClient`). Without it the client keeps receiving PLAYER_INFO but never
 * gets the per-cycle terminator, so it never finalizes a tick and drops the connection.
 */
data object ServerTickEnd : OutgoingMessage
