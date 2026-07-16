package emu.server.game.network.output.varp

import emu.game.varp.PlayerVarps
import emu.game.varp.VarpValue
import emu.protocol.osrs239.game.message.varp.VarpLarge
import emu.protocol.osrs239.game.message.varp.VarpSmall
import emu.transport.message.OutgoingMessage

/** Converts authoritative player variables into revision-239 output messages. */
internal object PlayerVarpOutput {
    fun loginSync(varps: PlayerVarps): List<OutgoingMessage> =
        varps.loginSync().map(::message)

    fun message(varp: VarpValue): OutgoingMessage =
        if (varp.value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            VarpSmall(varp.id, varp.value)
        } else {
            VarpLarge(varp.id, varp.value)
        }
}
