package emu.protocol.osrs239.game.prot

import emu.transport.prot.Prot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameClientProtTest {
    @Test
    fun `rev 239 table covers every client opcode`() {
        assertEquals(117, GameClientProt.count)
        assertEquals(Prot(0, 2), GameClientProt.find(0))
        assertEquals(Prot(52, 15), GameClientProt.find(52))
        assertEquals(Prot(112, 1), GameClientProt.find(112))
        assertNull(GameClientProt.find(-1))
        assertNull(GameClientProt.find(117))
    }

    @Test
    fun `move game click has its injected client opcode and variable-byte size`() {
        assertEquals(Prot(114, Prot.VAR_BYTE), GameClientProt.MOVE_GAMECLICK)
        assertEquals(GameClientProt.MOVE_GAMECLICK, GameClientProt.find(114))
    }

    @Test
    fun `player control packets have their rev239 opcodes and fixed zero bodies`() {
        assertEquals(Prot(95, 0), GameClientProt.CLOSE_MODAL)
        assertEquals(Prot(4, 0), GameClientProt.IDLE)
    }
}
