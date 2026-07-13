package emu.protocol.osrs239.js5

import emu.buffer.JagexBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

// JS5 control frames carry a fixed 3-byte payload after the 1-byte opcode (already consumed by the
// pipeline before the decoder runs); see Js5Control / Js5Prot.CONTROL_*. One decoder instance is
// bound per opcode (2, 3, 4, 6, 7). These tests lock down: the prot each decoder advertises (opcode
// + 3-byte size), and that decode() reads the 3 payload bytes in order into b0/b1/b2 while tagging
// the message with the opcode it was constructed for.
class Js5ControlDecoderTest {
    private fun buf(b0: Int, b1: Int, b2: Int) = JagexBuffer(byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte()))

    @Test fun `prot advertises the bound opcode and the fixed 3-byte payload size`() {
        for (opcode in Js5Prot.CONTROL_OPCODES) {
            val decoder = Js5ControlDecoder(opcode)
            assertEquals(opcode, decoder.prot.opcode)
            assertEquals(3, decoder.prot.size)
        }
    }

    @Test fun `opcode 2 (logged in) decodes its payload bytes in order`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_LOGGED_IN).decode(buf(0x11, 0x22, 0x33))
        assertEquals(Js5Control(Js5Prot.CONTROL_LOGGED_IN, 0x11, 0x22, 0x33), msg)
    }

    @Test fun `opcode 3 (logged out) decodes its payload bytes in order`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_LOGGED_OUT).decode(buf(0x44, 0x55, 0x66))
        assertEquals(Js5Control(Js5Prot.CONTROL_LOGGED_OUT, 0x44, 0x55, 0x66), msg)
    }

    // Opcode 4 is the XOR-rekey control frame: b0 is the 1-byte XOR key, b1/b2 form a reserved u16
    // the gateway does not currently use (see Js5Handler, which only reads b0).
    @Test fun `opcode 4 (rekey) decodes the xor key byte and the reserved u16`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_XOR_KEY).decode(buf(0x7F, 0x01, 0x02))
        assertEquals(Js5Prot.CONTROL_XOR_KEY, msg.opcode)
        assertEquals(0x7F, msg.b0) // the XOR key
        val reservedU16 = (msg.b1 shl 8) or msg.b2
        assertEquals(0x0102, reservedU16)
    }

    @Test fun `opcode 4 with key byte 0 decodes to a no-op (plaintext) key`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_XOR_KEY).decode(buf(0, 0, 0))
        assertEquals(0, msg.b0)
    }

    @Test fun `opcode 6 (init) decodes its payload bytes in order`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_INIT).decode(buf(0x77, 0x88, 0x99))
        assertEquals(Js5Control(Js5Prot.CONTROL_INIT, 0x77, 0x88, 0x99), msg)
    }

    @Test fun `opcode 7 (keepalive) decodes its payload bytes in order`() {
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_KEEPALIVE).decode(buf(0xAA, 0xBB, 0xCC))
        assertEquals(Js5Control(Js5Prot.CONTROL_KEEPALIVE, 0xAA, 0xBB, 0xCC), msg)
    }

    @Test fun `unsigned bytes above 0x7F are not sign-extended`() {
        // readUByte must yield 0..255, not a negative Byte-sign-extended Int.
        val msg = Js5ControlDecoder(Js5Prot.CONTROL_KEEPALIVE).decode(buf(0xFF, 0xFE, 0x80))
        assertEquals(255, msg.b0)
        assertEquals(254, msg.b1)
        assertEquals(128, msg.b2)
    }
}
