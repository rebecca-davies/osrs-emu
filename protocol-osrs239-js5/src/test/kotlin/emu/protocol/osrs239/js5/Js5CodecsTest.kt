package emu.protocol.osrs239.js5

import emu.protocol.osrs239.js5.prot.Js5Prot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Js5CodecsTest {
    @Test
    fun `repository contains every declared JS5 codec`() {
        val repository = buildJs5CodecRepository()

        Js5Codecs.decoders.forEach { decoder ->
            assertSame(decoder, repository.decoder(decoder.prot.opcode))
        }
        Js5Codecs.encoders.forEach { encoder ->
            assertSame(encoder, repository.encoder(encoder.messageType))
        }
        assertEquals(Js5Prot.CONTROL_OPCODES.size + 2, Js5Codecs.decoders.size)
        assertEquals(1, Js5Codecs.encoders.size)
    }
}
